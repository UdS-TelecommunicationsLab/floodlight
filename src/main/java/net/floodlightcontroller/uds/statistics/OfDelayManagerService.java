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

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.projectfloodlight.openflow.protocol.OFEchoReply;
import org.projectfloodlight.openflow.protocol.OFEchoRequest;
import org.projectfloodlight.openflow.protocol.OFFactories;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFPacketIn;
import org.projectfloodlight.openflow.protocol.OFPacketOut;
import org.projectfloodlight.openflow.protocol.OFType;
import org.projectfloodlight.openflow.protocol.OFVersion;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.OFPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.devicemanager.IDeviceService;
import net.floodlightcontroller.linkdiscovery.ILinkDiscoveryService;
import net.floodlightcontroller.packet.Data;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.restserver.IRestApiService;
import net.floodlightcontroller.routing.Link;
import net.floodlightcontroller.threadpool.IThreadPoolService;
import net.floodlightcontroller.uds.routing.interfaces.IRoutingService;
import net.floodlightcontroller.uds.routing.internal.Routing;
import net.floodlightcontroller.uds.routing.model.CostFunctionType;
import net.floodlightcontroller.uds.statistics.interfaces.IOfDelayManagerService;
import net.floodlightcontroller.uds.statistics.interfaces.IOfStatistics;
import net.floodlightcontroller.uds.statistics.web.UdSDelayManagerRouter;

//@JsonSerialize(using=OfDelayManagerServiceJSONSerializer.class)
public class OfDelayManagerService implements IFloodlightModule,
		IFloodlightService, IOFMessageListener, Runnable, IOfDelayManagerService {

	protected static final Logger log = LoggerFactory.getLogger(OfDelayManagerService.class);
	protected ILinkDiscoveryService linkManager;
	protected IThreadPoolService threadPool;
	protected IDeviceService deviceManager;
	protected ScheduledExecutorService sExecService;
	protected IFloodlightProviderService floodlightProvider;
	protected IRestApiService restApi;
	protected IOfStatistics statsManager;
	protected IOFSwitchService switchManager;
	
	private final static String identString = "DELAY";
	private byte[] ident;
	
	private short etherType = 0x0F11;
	private TimeUnit unit = TimeUnit.SECONDS;
	private long interval = 5;

	private Map<Link, Long> allSwitchDelays;
	private Map<DatapathId, Long> controllerSwitchDelays;
	private Map<Link, Long> allSwitchDelaysOLD;
	private Map<DatapathId, Long> controllerSwitchDelaysOLD;
	
	
	// I'm a service

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleServices() {
		LinkedList<Class<? extends IFloodlightService>> c = new LinkedList<Class<? extends IFloodlightService>>();
		c.add(IOfDelayManagerService.class);
		return c;
	}

	@Override
	public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
		Map<Class<? extends IFloodlightService>, IFloodlightService> m = new HashMap<Class<? extends IFloodlightService>, IFloodlightService>();
		m.put(IOfDelayManagerService.class, this);
		return m;
	}

	// I'm a module

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
		Collection<Class<? extends IFloodlightService>> l = new ArrayList<Class<? extends IFloodlightService>>();
		l.add(IFloodlightProviderService.class);
		l.add(IDeviceService.class);
		l.add(IThreadPoolService.class);
		l.add(ILinkDiscoveryService.class);
		l.add(IRestApiService.class);
		l.add(IOFSwitchService.class);
		return l;
	}

	@Override
	public void init(FloodlightModuleContext context)
			throws FloodlightModuleException {

		threadPool = context.getServiceImpl(IThreadPoolService.class);
		linkManager = context.getServiceImpl(ILinkDiscoveryService.class);
		deviceManager = context.getServiceImpl(IDeviceService.class);
	    restApi = context.getServiceImpl(IRestApiService.class);
	    statsManager = context.getServiceImpl(IOfStatistics.class);

		allSwitchDelays = new HashMap<>();
		controllerSwitchDelays = new HashMap<>();
		allSwitchDelaysOLD = new HashMap<>();
		controllerSwitchDelaysOLD = new HashMap<>();
	    
		ident = new byte[identString.getBytes().length];
		ident = identString.getBytes();
		
		sExecService = threadPool.getScheduledExecutor();
		sExecService.scheduleAtFixedRate(this, interval, interval, unit);
	}

	@Override
	public void startUp(FloodlightModuleContext context)
			throws FloodlightModuleException {
		switchManager = context.getServiceImpl(IOFSwitchService.class);
		floodlightProvider = context.getServiceImpl(IFloodlightProviderService.class);
		floodlightProvider.addOFMessageListener(OFType.PACKET_IN, this);
		floodlightProvider.addOFMessageListener(OFType.ECHO_REPLY, this);
		// for debugging purposes only
		//floodlightProvider.addOFMessageListener(OFType.PACKET_OUT, this);
		//floodlightProvider.addOFMessageListener(OFType.ECHO_REQUEST, this);
		
		restApi.addRestletRoutable(new UdSDelayManagerRouter());

		Map<String, String> configOptions = context.getConfigParams(this);
		if (configOptions.get("delay") != null) {
			this.interval = Long.valueOf(configOptions.get("delay"));
			log.debug("taking delay out of config file");
		}
		if(configOptions.get("etherType")!=null){
			this.etherType = Short.decode(configOptions.get("etherType"));
			log.debug("taking ethertype out of config file");
		}	
		context.getServiceImpl(IRoutingService.class).addConnectionGraphCostFunction(CostFunctionType.LOW_DELAY, new DelayCostFunction(this));
	}

	// I'm a runnable

	@Override
	public void run() {
		allSwitchDelaysOLD = allSwitchDelays;
		controllerSwitchDelaysOLD = controllerSwitchDelays;
		
		// check if there is anything to compute the delay on
		if (linkManager.getLinks().keySet().size() < 1) {
			return;
		}else{
			log.debug("OfDelayCalculator: found #{} (directed) link(s) to test delay on", linkManager.getLinks().keySet().size());
		}
		
		// test delay on Controller - AP - Controller route
		runC2AP();
		
		// test delay on Controller - AP - AP - Controller route
		runAP2AP();
	}

	private void runC2AP() {
		//log.trace("RUNNING C 2 AP ECHO");
		Set<Link> allSwitchLinks = linkManager.getLinks().keySet();
		for(Link link : allSwitchLinks){
			ByteBuffer bb = ByteBuffer.allocate(13);
			bb.put(ident);
			bb.putLong(System.nanoTime());
			
			OFFactory factory = OFFactories.getFactory(OFVersion.OF_10);
			OFEchoRequest echoRequest = factory.buildEchoRequest().setData(bb.array()).build();
			IOFSwitch sw = switchManager.getSwitch(link.getSrc());
			sw.write(echoRequest);
			sw.flush();
			//log.debug("Written: {}", bb.array());
		}
	}
	
	private void runAP2AP(){
		//log.trace("RUNNING AP 2 AP MSG SENDER");
		// we need clean maps from this point on
		allSwitchDelays = new HashMap<>();
		controllerSwitchDelays = new HashMap<>();
		
		Set<Link> allSwitchLinks = linkManager.getLinks().keySet();
		for (Link link : allSwitchLinks) {
			
			// build a valid packet
			byte[] ofpo = msgBuilder(link.getSrc().getBytes(), link.getDst().getBytes());
			if(ofpo == null){ log.warn("Could not prepare msg for delay.. maybe no IPs in network.."); return;	}
			
			// prepare as as OFPacketOut for sending
			IOFSwitch sw = switchManager.getActiveSwitch(link.getSrc());
	        OFPacketOut msg = Routing.createPacketOutForPort(floodlightProvider, sw, link.getSrcPort(), ofpo, log);

			// send msg
			sw.write(msg);
			sw.flush();
		}
	}

	private byte[] msgBuilder(byte[] srcMac, byte[] dstMac){
		//log.debug("BUILDING ONE MSG FOR AP 2 AP MSG SENDER");
		// build packet data
		ByteBuffer bb = ByteBuffer.allocate(13);
		bb.put(ident);
		bb.putLong(System.nanoTime());
		// build msg
		Data msg = new Data(bb.array());
		
		// encapsulate
		try{
			Ethernet ethPacket = (Ethernet) new Ethernet()
					.setSourceMACAddress(Ethernet.toMACAddress("00:26:E1:AB:CD:EF"))
					.setDestinationMACAddress(Ethernet.toMACAddress("00:26:E1:FE:DC:BA"))
					.setEtherType(etherType)
					//.setVlanID(vlanId.getVlan())
					// VlanID is evil, do not use it!
					// some nodes will refuse to handle packs with vlanid set
					.setPayload(msg);
			
			byte[] packet = ethPacket.serialize();
			return packet;
		}catch(NullPointerException | NoSuchElementException nsee){
			//log.debug("msgBuilder failed, cause: {}", nsee.getMessage());
			return null;
		}
	}
	
	// I'm a Message Listener
	@Override
	public String getName() {
		return "ofdelaymanagerservice";
	}

	@Override
	public boolean isCallbackOrderingPrereq(OFType type, String name) {
		return false;
	}

	// VERY IMPORTANT TO RUN BEFORE ALL OTHERS
	// SINCE SOMEONE ELSE DROPS MY MESSAGES OTHERWISE
	@Override
	public boolean isCallbackOrderingPostreq(OFType type, String name) {
		return !name.equals("ofdelaymanagerservice");
	}

	@Override
	public Command receive(
			IOFSwitch sw, OFMessage msg, FloodlightContext cntx) {
		switch (msg.getType()) {
		case PACKET_IN:
			return handlePacketIn(sw, (OFPacketIn) msg, cntx, System.nanoTime());
		case ECHO_REPLY:
			return handleEchoReply(sw, (OFEchoReply) msg, cntx, System.nanoTime());
		// debugging purposes only, remember to unregister as packet_out listener!
		//case ECHO_REQUEST:
			//log.debug("ECHO_REQUEST noticed");
			//return Command.CONTINUE;
		//case PACKET_OUT:
			//log.debug("Packet_OUT noticed");
			//return Command.CONTINUE;
		default:
			return Command.CONTINUE;
		}
	}

	// that's what I'm doing with packages

	private boolean packetIdentifier(byte[] content) {
		boolean result = true;
		
		for (int i = 0; i < ident.length; i++) {
			// there is some 14 bit crap in front of the pk
			result &= content[14+i] == ident[i];
		}
		return result;
	}

	private Long extractDelay(byte[] bmsg) {
		// same as for ident, there's 14bit of crap
		// cut away 14bit crap + ident string
		ByteBuffer bb = ByteBuffer.wrap(bmsg, 14+ident.length, 8);
		return bb.getLong();
	}

	private Command handleEchoReply(
			IOFSwitch sw, OFEchoReply msg, FloodlightContext cntx, long now) {
		
		byte[] strippedMsg = msg.getData();
		
		boolean result = true;
		if(strippedMsg.length >= ident.length){
			for (int i = 0; i < ident.length; i++) {
				// there is some 14 bit crap in front of the pk
				result &= strippedMsg[i] == ident[i];
			}
		}else{
			result = false;
		}
		
		if(	!result ){
			//log.debug("ether found {}, expected {}", eth.getEtherType(), etherType);
			return Command.CONTINUE;
		}
		
		//log.debug("Message: {}", strippedMsg);
		long packageAge = 0L;
		try {
			Long packageBirthDayTime = 0L;
			packageBirthDayTime = ByteBuffer.wrap(strippedMsg, ident.length, 8).getLong();
			packageAge = now - packageBirthDayTime;
			//log.debug("Message: {} | {}, {}, {}", strippedMsg, packageBirthDayTime, now, packageAge);
		} catch (Exception e) {
			return Command.STOP;
		}

		controllerSwitchDelays.put(sw.getId(), packageAge);
		// since it's still my dummy packet and it has no more need, we're ready
		return Command.STOP;
	}

	private Command handlePacketIn(IOFSwitch sw, OFPacketIn msg,
			FloodlightContext cntx, long now) {
		
		Ethernet eth = IFloodlightProviderService.bcStore.
                       get(cntx, IFloodlightProviderService.CONTEXT_PI_PAYLOAD);
		
		// Ethernet packages
		byte[] strippedMsg;
		if( !(eth.getEtherType() == etherType)){return Command.CONTINUE;}
		
		strippedMsg = ((Data)eth.getPayload()).getData();
		//log.debug("Payload: {}", strippedMsg);
		
		if(	!packetIdentifier(strippedMsg) ){
			//log.debug("ether found {}, expected {}", eth.getEtherType(), etherType);
			return Command.CONTINUE;
		}
		
		
		// compute the delay between {unknown} and sw
		long packageAge = 0L;
		try {
			Long packageBirthDayTime = 0L;
			packageBirthDayTime = extractDelay(strippedMsg);
			packageAge = now - packageBirthDayTime;
		} catch (Exception e) {
			return Command.STOP;
		}

		// reverse lookup the predecessor:
		DatapathId swDpid = sw.getId();
		OFPort ip = msg.getInPort();
		// all links of interest
		Set<Link> allLinks = linkManager.getLinks().keySet();
		for (Link l : allLinks){
			// if there's one link [*, *, msg.dstSwitch, msg.dstInPort]
			if((l.getDst().equals(swDpid)) && (l.getDstPort().equals(ip))){
				allSwitchDelays.put(l, packageAge);
				// since my dummy packet has no more need
				// nobody else should be able to handle it anymore
				// we're ready
				return Command.STOP;
			}
		}
		// since it's still my dummy packet and it has no more need, we're ready
		return Command.STOP;
	}

	// This is my service
	/**
	 * returns the delay between two switches
	 * @input1 Long sw1Dpid : the dpid of the first switch
	 * @input2 Long sw2Dpid : the dpid of the second switch
	 * @return double delay in ns
	 * <hr>
	 * if there aren't any stats, Double.NEGATIVE_INFINITY will
	 * be returned, otherwise it returns an approximative delay
	 * between sw1 and sw2 (which might be different from sw2 - sw1) 
	 */
	public double getCSw1Sw2CDelay(Link link) {
		if(allSwitchDelaysOLD.get(link)==null){return Double.NEGATIVE_INFINITY;}
		Long csw12Delay = allSwitchDelaysOLD.get(link);
		return (double) csw12Delay;
	}
	
	public Map<Link, Double> getDelay(){
		Map<Link, Double> res = new LinkedHashMap<>();
		for(Link link : allSwitchDelaysOLD.keySet()){
			res.put(link, getCSw1Sw2CDelay(link));
		}
		return res;
	}
	
	public double getCSwCDelay(DatapathId dpid){
		return controllerSwitchDelaysOLD.containsKey(dpid) ? controllerSwitchDelaysOLD.get(dpid) : -1.0;
	}
	
	public Map<DatapathId, Double> getControllerSwitchDelays(){
		Map<DatapathId, Double> res = new LinkedHashMap<>();
		for(DatapathId dpid : controllerSwitchDelaysOLD.keySet()){
			res.put(dpid, getCSwCDelay(dpid));
		}
		return res;
	}
}
