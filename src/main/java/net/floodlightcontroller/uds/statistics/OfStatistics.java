/**
 *    Copyright 2015, Saarland University
 *
 *    Licensed under the Apache License, Version 2.0 (the "License"); you may
 *    not use this file except in compliance with the License. You may obtain
 *    a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 *    WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 *    License for the specific language governing permissions and limitations
 *    under the License.
 **/

package net.floodlightcontroller.uds.statistics;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.OFPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.devicemanager.SwitchPort;
import net.floodlightcontroller.linkdiscovery.ILinkDiscoveryService;
import net.floodlightcontroller.restserver.IRestApiService;
import net.floodlightcontroller.routing.Link;
import net.floodlightcontroller.threadpool.IThreadPoolService;
import net.floodlightcontroller.uds.statistics.interfaces.IOfStatistics;
import net.floodlightcontroller.uds.statistics.web.UdSStatisticsRouter;

public class OfStatistics implements IFloodlightModule, IFloodlightService, Runnable, IOfStatistics {

	protected static final Logger log = LoggerFactory.getLogger(OfStatistics.class);
	protected IThreadPoolService threadPool;
	protected IFloodlightProviderService floodlightProvider;
	protected ILinkDiscoveryService linkManager;
	protected IRestApiService restApi;
	protected IOFSwitchService switchManager;
	protected ScheduledExecutorService sExecService;
	private TimeUnit unit = TimeUnit.SECONDS;
	private long statsInterval = 3;
	private long statsInitialDelay = 10;
	private int MAXHISTORY = 10;
	
	// I'm a module
	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleServices() {
		LinkedList<Class<? extends IFloodlightService>> c = new LinkedList<Class<? extends IFloodlightService>>();
		c.add(IOfStatistics.class);
		return c;
	}

	// I provide services
	@Override
	public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
		Map<Class<? extends IFloodlightService>, IFloodlightService> m = new HashMap<Class<? extends IFloodlightService>, IFloodlightService>();
		m.put(IOfStatistics.class, this);
		return m;
	}

	// I need this to provide my service
	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
		Collection<Class<? extends IFloodlightService>> l = new ArrayList<Class<? extends IFloodlightService>>();
		l.add(IThreadPoolService.class);
		l.add(ILinkDiscoveryService.class);
		l.add(IOFSwitchService.class);
		return l;
	}

	@Override
	public void startUp(FloodlightModuleContext context)
			throws FloodlightModuleException {
		log.info("OfStatistics: starting up statistic module");
		switchManager = context.getServiceImpl(IOFSwitchService.class);
		floodlightProvider = context.getServiceImpl(IFloodlightProviderService.class);
		
		restApi.addRestletRoutable(new UdSStatisticsRouter());
		
		Map<String, String> configOptions = context.getConfigParams(this);
		if(configOptions.get("requestInterval")!=null){
			log.debug("OfStatistics: found manual configuration for stats request delay: {} {}", configOptions.get("requestInterval"), unit);
			this.statsInterval = Long.valueOf(configOptions.get("requestInterval"));
		}
		if(configOptions.get("historysize")!=null){
			log.debug("OfStatistics: found manual configuration for history size: {} entries", configOptions.get("historysize"));
			this.MAXHISTORY = Integer.valueOf(configOptions.get("historysize"));
		}
		if(configOptions.get("initialDelay")!=null){
			this.statsInitialDelay = Long.valueOf(configOptions.get("initialDelay"));
		}
		
		log.info("OfStatistics: statistic module started");
	}

	@Override
	public void init(FloodlightModuleContext context)
			throws FloodlightModuleException {
		log.info("OfStatistics: init statistic module");
		linkManager = context.getServiceImpl(ILinkDiscoveryService.class);
		threadPool = context.getServiceImpl(IThreadPoolService.class);
		restApi = context.getServiceImpl(IRestApiService.class);
		sExecService = threadPool.getScheduledExecutor();
		sExecService.scheduleAtFixedRate(this, statsInitialDelay, statsInterval, unit);
		
		log.info("OfStatistics: init statistic module finished");
	}

	// I'm a runnable
	// since ofv1 ipv4 is not providing awesome
	// information about switches, links and ports
	// as we need it here, we have two possibilities:
	// fetching the information via rest api
	// or reimplementating the same functionalities
	// seems to be ok asking the rest api
	@Override
	public void run() {
		/* OFStatisticsType
		 * port, queue, flow, aggregate, desc, table, features
		 */
		log.debug("OfStatistics: grabbing new stats");
		try{
			URI uri = new URI("http://localhost:8080/wm/core/switch/all/port/json");
			JSONTokener tokener = new JSONTokener(uri.toURL().openStream());
			JSONObject root = new JSONObject(tokener);
			// since we now have raw data we need to prepare them
			prepareStats(root);
		}catch(Exception e){
			log.debug("OfStatistics: exception happended requesting URI.. {}", e.getMessage());
		}		
	}
	
	// switchport to pages of (key => value)
	Map<SwitchPort, LinkedList<Map<String, Long>>> packetHistory = new LinkedHashMap<SwitchPort, LinkedList<Map<String, Long>>>();
	
	// I need to do this whenever I request new stats..
	public void prepareStats(JSONObject lastHistoryPage){		
		@SuppressWarnings("unchecked")
		Iterator<String> switchKeys = lastHistoryPage.keys();
		try{
			// iterate over all switches in this object
			while(switchKeys.hasNext()){
				String switchKey = switchKeys.next();
				
				JSONArray sw = lastHistoryPage.getJSONObject(switchKey).getJSONArray("port");
				// iterate over all ports of the current switch
				for(int i=0; i<sw.length(); i++){
					JSONObject swPort = sw.getJSONObject(i);
					int portNo = 0;
					// since for some reasons floodlight now provides 'local' port
					try{
						portNo = Integer.parseInt(swPort.getString("portNumber"));
					}catch(NumberFormatException ee){
						continue;
					}
					SwitchPort sp = new SwitchPort(DatapathId.of(switchKey), OFPort.of(portNo));
					
					Map<String, Long> pageMap = new HashMap<String, Long>();
					//TODO uncomment as soon as it is available ("not in this version")
					//pageMap.put("currentPortSpeed", switchManager.getSwitch(DatapathId.of(switchKey)).getPort(OFPort.of(portNo)).getCurrSpeed());
					//pageMap.put("maxPortSpeed", switchManager.getSwitch(DatapathId.of(switchKey)).getPort(OFPort.of(portNo)).getMaxSpeed());
					pageMap.put("receiveBytes", swPort.getLong("receiveBytes"));
					pageMap.put("transmitBytes", swPort.getLong("transmitBytes"));
					pageMap.put("receivePackets", swPort.getLong("receivePackets"));
					pageMap.put("transmitPackets", swPort.getLong("transmitPackets"));
					
					// we are holding back a history of MAXHISTORY entries
					// this is needed to calculate stats, as explained below
					boolean addedCurrentPage = false;
					for(SwitchPort spInMap : packetHistory.keySet()){
						if(spInMap.equals(sp)){
							packetHistory.get(spInMap).addLast(pageMap);
							while(packetHistory.get(spInMap).size()> MAXHISTORY){
								packetHistory.get(spInMap).removeFirst();
							}
							addedCurrentPage = true;
						}
					}
					
					if(!addedCurrentPage){
						LinkedList<Map<String, Long>> newHistoryBook = new LinkedList<Map<String, Long>>();
						newHistoryBook.addLast(pageMap);
						packetHistory.put(sp, newHistoryBook);
					}
				}
			}
		} catch (JSONException e) {
			log.debug("exception occured during preparation of stats, json, {} on page {}", e.getMessage(), lastHistoryPage);
			e.getStackTrace();
		}
	}
    
	// This is my service
	public Long getPortCurrentSpeed(DatapathId dpid, OFPort port){
		return switchManager.getSwitch(dpid).getPort(port).getCurrSpeed();
	}
	
	public Long getPortMaxSpeed(DatapathId dpid, OFPort port){
		return switchManager.getSwitch(dpid).getPort(port).getMaxSpeed();
	}
	
	// to calculate the average data rate
	// we are computing:
	//     delta transmitBytes / delta history entries
	//     delta receiveBytes / delta history entries
	// delta is transmit bytes of the most current lookup - transmit bytes from the oldest lookup in our history
	// this gives us a more or less accurate approximation of the current datarate
	// better statistics are not possible with openflow 1.0
	public Map<String, Map<String, Map<String, Long>>> averageDatarate(){
		LinkedHashMap<String, Map<String, Map<String, Long>>> dataRatesForAll = new LinkedHashMap<String, Map<String, Map<String, Long>>>();
		
		// switchport to pages of (key => value)
		for(SwitchPort swp : packetHistory.keySet()){
			if(packetHistory.get(swp) == null){continue;}
			
			int size = packetHistory.get(swp).size();
			Map<String, Long> firstPage = packetHistory.get(swp).getFirst();
			Map<String, Long> lastPage  = packetHistory.get(swp).getLast();

			// iff there's a reset of internal counters
			// there will be negative values, we fix this
			// by hard setting the result of our calculation
			// to zero by hand
			long datarateTransmit = (lastPage.get("transmitBytes") - firstPage.get("transmitBytes"));
			long datarateReceive = (lastPage.get("receiveBytes") - firstPage.get("receiveBytes"));
			if(datarateTransmit < 0L){datarateTransmit = 0;}
			if(datarateReceive < 0L){datarateReceive = 0;}
			if(size <= 0){size = 1;}
			
			Map<String, Long> averages = new LinkedHashMap<String, Long>();
			averages.put("transmitBytes", datarateTransmit / size);
			averages.put("receiveBytes", datarateReceive / size);
					
			if(dataRatesForAll.get(swp.getSwitchDPID().toString())!=null){
				dataRatesForAll.get(swp.getSwitchDPID().toString()).put(swp.getPort().toString(), averages);
			}else{
				LinkedHashMap<String, Map<String, Long>> portMap = new LinkedHashMap<String, Map<String, Long>>();
				portMap.put(swp.getPort().toString(), averages);	
				dataRatesForAll.put(swp.getSwitchDPID().toString(), portMap);
			}
		}
		return dataRatesForAll;
	}
	
	// computation works very much the same as for data rates
	// ((delta of packages transmit by source) - (delta of packages received by destination history entries)) / (delta of packages transmit by source) / history size
	// quite a little bit more to compute it
	public Map<String, Map<String, Double>> averagePacketLoss(){
		Map<String, Map<String, Double>> packetLossPerLink = new LinkedHashMap<String, Map<String, Double>>();
				
		for(Link link : linkManager.getLinks().keySet()){
			
			double pk_src_transmit = 0.0;
			double pk_src_received = 0.0;
			double pk_dst_transmit = 0.0;
			double pk_dst_received = 0.0;
			
			SwitchPort srcSwPort = new SwitchPort(link.getSrc(), link.getSrcPort());
			SwitchPort dstSwPort = new SwitchPort(link.getDst(), link.getDstPort());
			
			if(packetHistory.get(srcSwPort)!=null && packetHistory.get(srcSwPort).size() -1 > 0){
				pk_src_transmit = (packetHistory.get(srcSwPort).getLast().get("transmitPackets") - packetHistory.get(srcSwPort).getFirst().get("transmitPackets"))
									/ (packetHistory.get(srcSwPort).size() -1);
				pk_src_received = (packetHistory.get(srcSwPort).getLast().get("receivePackets") - packetHistory.get(srcSwPort).getFirst().get("receivePackets"))
									/ (packetHistory.get(srcSwPort).size() -1);
			}
			if(packetHistory.get(dstSwPort)!=null && packetHistory.get(dstSwPort).size() -1 > 0){
				pk_dst_transmit = (packetHistory.get(dstSwPort).getLast().get("transmitPackets") - packetHistory.get(dstSwPort).getFirst().get("transmitPackets"))
									/ (packetHistory.get(dstSwPort).size() -1);
				pk_dst_received = (packetHistory.get(dstSwPort).getLast().get("receivePackets") - packetHistory.get(dstSwPort).getFirst().get("receivePackets"))
									/ (packetHistory.get(dstSwPort).size() -1);
			}
			
			double loss_src = 0.0;
			double loss_dst = 0.0;
			
			if(pk_src_transmit != 0.0 && pk_src_transmit >= pk_dst_received){
				loss_src = Math.rint( ((pk_src_transmit - pk_dst_received)/pk_src_transmit) * 10000.0) / 10000.0000;
			}
			if(pk_dst_transmit != 0.0 && pk_dst_transmit >= pk_src_received){
				loss_dst = Math.rint( ((pk_dst_transmit - pk_src_received)/pk_dst_transmit) * 10000.0) / 10000.0000;
			}
			
			if(packetLossPerLink.get(link.getSrc().toString())!=null){
				packetLossPerLink.get(link.getSrc().toString()).put(link.getSrcPort().toString(), loss_src);
			}else{
				LinkedHashMap<String, Double> portToPLMapSource = new LinkedHashMap<>();
				portToPLMapSource.put(link.getSrcPort().toString(), loss_src);
				packetLossPerLink.put(link.getSrc().toString(), portToPLMapSource);
			}

			if(packetLossPerLink.get(link.getDst().toString())!=null){
				packetLossPerLink.get(link.getDst().toString()).put(link.getDstPort().toString(), loss_dst);
			}else{
				LinkedHashMap<String, Double> portToPLMapDestination = new LinkedHashMap<>();
				portToPLMapDestination.put(link.getDstPort().toString(), loss_dst);
				packetLossPerLink.put(link.getDst().toString(), portToPLMapDestination);
			}
		}
		
		return packetLossPerLink;
	}
    
	public long linkUptime(Link l){
		return getDpidUptime(l.getSrc());
	}
	
	public Long getDpidUptime(DatapathId dpid){
		return (Long)new Date().getTime() - switchManager.getSwitch(dpid).getConnectedSince().getTime();
	}
	
	public Map<String, Long> getAllLinksUptime(){
		log.debug("all links uptime requested");
		Map<String, Long> dpidUptimes = new LinkedHashMap<String, Long>();
		for(DatapathId dpid : linkManager.getSwitchLinks().keySet()){
			log.debug("uptime for {} is {}", dpid, this.getDpidUptime(dpid));
			dpidUptimes.put(dpid.toString(), this.getDpidUptime(dpid));
		}
		
		return dpidUptimes;
	}
	
	public long getStatsInterval(){
		return statsInterval;
	}
	
	public int getHistoryLength(){
		return MAXHISTORY;
	}
	
	public void setHistoryLength(int newlength){
		MAXHISTORY = newlength;
	}
}
