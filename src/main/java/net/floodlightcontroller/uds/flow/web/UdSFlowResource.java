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

package net.floodlightcontroller.uds.flow.web;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.routing.Link;
import net.floodlightcontroller.uds.flow.interfaces.IFlowService;
import net.floodlightcontroller.uds.flow.model.Flow;

import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.U64;
import org.projectfloodlight.openflow.util.HexString;
import org.restlet.data.Status;
import org.restlet.resource.Get;
import org.restlet.resource.Post;
import org.restlet.resource.ServerResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ArrayListMultimap;

public class UdSFlowResource extends ServerResource {
	protected static final Logger log = LoggerFactory.getLogger(UdSFlowResource.class);

	protected IFlowService flowService;
	
	@Post
	public Object post(String fmJson){	
		flowService = (IFlowService)getContext().getAttributes().get(IFlowService.class.getCanonicalName());
		
		//log.debug(fmJson);
		/*
		try{
			JSONTokener tokener = new JSONTokener(fmJson);
			JSONObject root = new JSONObject(tokener);
			
			long cookie = root.getLong("cookie");
			String description = root.getString("description");
			
			JSONObject matchJSON = root.getJSONObject("match");
			OFMatch match = new OFMatch();
			
			JSONArray dstmacsJSON = matchJSON.getJSONArray("dstmacs");
			byte[] dstmacs = new byte[dstmacsJSON.length()]; 
			for(int i=0; i<dstmacsJSON.length(); i++){
				dstmacs[i] = Byte.parseByte(dstmacsJSON.getString(i));
			}
			match.setDataLayerDestination(dstmacs);
			
			JSONArray srcmacsJSON = matchJSON.getJSONArray("srcmacs");
			byte[] srcmacs = new byte[srcmacsJSON.length()];
			for(int i=0; i<srcmacsJSON.length(); i++){
				srcmacs[i] = Byte.parseByte(srcmacsJSON.getString(i));
			}
			match.setDataLayerSource(srcmacs);
			
			short dataLayerType = (short)matchJSON.getInt("dataLayerType");
			match.setDataLayerType(dataLayerType);
			
			short dataLayerVirtualLan = (short)matchJSON.getInt("dataLayerVirtualLan");
			match.setDataLayerVirtualLan(dataLayerVirtualLan);
			byte pcp = Byte.parseByte(matchJSON.getString("pcp"));
			match.setDataLayerVirtualLanPriorityCodePoint(pcp);
			short inputPort = (short)matchJSON.getInt("inputPort");
			match.setInputPort(inputPort);
			int networkDestination = matchJSON.getInt("networkDestination");
			match.setNetworkDestination(networkDestination);
			byte networkProtocol = Byte.parseByte(matchJSON.getString("networkProtocol"));
			match.setNetworkProtocol(networkProtocol);
			int networkSource = matchJSON.getInt("networkSource");
			match.setNetworkSource(networkSource);
			byte networkTypeOfService = Byte.parseByte(matchJSON.getString("networkTypeOfService"));
			match.setNetworkTypeOfService(networkTypeOfService);
			short transportDestination = (short)matchJSON.getInt("transportDestination");
			match.setTransportDestination(transportDestination);
			short transportSource = (short)matchJSON.getInt("transportSource");
			match.setTransportSource(transportSource);
			int wildcards = matchJSON.getInt("wildcards");
			match.setWildcards(wildcards);
			
			
			List<OFFlowMod> flowMods = new LinkedList<OFFlowMod>();
			JSONArray flowModsJSON = root.getJSONArray("flowMods");
						
			for(int i=0; i<flowModsJSON.length(); i++){
				JSONObject flowModJSON = flowModsJSON.getJSONObject(i);
			
				OFFlowMod mod = new OFFlowMod();
				
				List<OFAction> actions;
				mod.setActions(actions);
				OFActionFactory actionFactory;
				mod.setActionFactory(actionFactory);
				
				int bufferId = flowModJSON.getInt("bufferId");
				mod.setBufferId(bufferId);
				
				// computed out of loop
				mod.setCookie(cookie);
				
				short command = (short)flowModJSON.getInt("command");
				mod.setCommand(command);
				
				short flags = (short)flowModJSON.getInt("flags");
				mod.setFlags(flags);
				
				short hardTimeout = (short)flowModJSON.getInt("hardTimeout");
				mod.setHardTimeout(hardTimeout);
				
				short idleTimeout = (short)flowModJSON.getInt("idleTimeout");
				mod.setIdleTimeout(idleTimeout);
				
				// computed out of loop
				mod.setMatch(match);
								
				short outPort = (short)flowModJSON.getInt("outPort");
				mod.setOutPort(outPort);
				
				short priority = (short)flowModJSON.getInt("priority");
				mod.setPriority(priority);
				
				OFType type = null;
				switch(flowModJSON.getString("oftype")){
				case "FLOW_MOD" 			: type = OFType.FLOW_MOD; break;
				case "FLOW_REMOVED" 		: type = OFType.FLOW_REMOVED; break;
				// do we need those?
				case "BARRIER_REPLY"		: type = OFType.BARRIER_REPLY; break;
				case "BARRIER_REQUEST"		: type = OFType.BARRIER_REQUEST; break;
				case "ECHO_REPLY"			: type = OFType.ECHO_REPLY; break;
				case "ECHO_REQUEST"			: type = OFType.ECHO_REQUEST; break;
				case "ERROR"				: type = OFType.ERROR; break;
				case "FEATURES_REPLY"		: type = OFType.FEATURES_REPLY; break;
				case "FEATURES_REQUEST"		: type = OFType.FEATURES_REQUEST; break;
				case "GET_CONFIG_REPLY" 	: type = OFType.GET_CONFIG_REPLY; break;
				case "GET_CONFIG_REQUEST"	: type = OFType.GET_CONFIG_REQUEST; break;
				case "HELLO"				: type = OFType.HELLO; break;
				case "PACKET_IN"			: type = OFType.PACKET_IN; break;
				case "PACKET_OUT"			: type = OFType.PACKET_OUT; break;
				case "PORT_MOD"				: type = OFType.PORT_MOD; break;
				case "PORT_STATUS"			: type = OFType.PORT_STATUS; break;
				case "QUEUE_GET_CONFIG_REPLY"	: type = OFType.QUEUE_GET_CONFIG_REPLY; break;
				case "QUEUE_GET_CONFIG_REQUEST"	: type = OFType.GET_CONFIG_REQUEST; break;
				case "SET_CONFIG"			: type = OFType.SET_CONFIG; break;
				case "STATS_REPLY"			: type = OFType.STATS_REPLY; break;
				case "STATS_REQUEST"		: type = OFType.STATS_REQUEST; break;
				case "VENDOR"				: type = OFType.VENDOR; break;
				}
				mod.setType(type);
				
				byte version = Byte.valueOf(flowModJSON.getString("version"));
				mod.setVersion(version);
				int xid = flowModJSON.getInt("xid");
				mod.setXid(xid);
				
				flowMods.add(mod);
				
			}
		
			List<IOFSwitch> switches = new LinkedList<IOFSwitch>();
			JSONArray switchesJSON = root.getJSONArray("switches");
			for(int i=0; i<switchesJSON.length(); i++){
				//switches.add();
			}
			
			List<Link> links = new LinkedList<Link>();
			
			
			
			flowService.addFlow(switches, links, flowMods, cookie, match, description);
			
			Map<String, Object> m = new LinkedHashMap<String, Object>();
			m.put("result", true);
			return m;
		}catch(JSONException e) {
			Map<String, Object> m = new LinkedHashMap<String, Object>();
			m.put("result", false);
			m.put("errormsg", "malformed json or something's missing..");
		}
		*/
		return false;
	}
	
	@Get("json")
    public Object retrieve() {
		flowService = (IFlowService)getContext().getAttributes().get(IFlowService.class.getCanonicalName());
		String cat = (String) getRequestAttributes().get("cat");
		
		switch(cat){
		case "dpid":
			return decodeDpid(flowService.getDpidToFlowMap());
		case "cookie":
			return decodeCookie(flowService.getCookieToFlowMap());
		case "link":
			return decodeLink(flowService.getLinkToFlowMap());
		case "timeouts":
			return flowTimeouts();
		default:
			setStatus(Status.CLIENT_ERROR_BAD_REQUEST, "RelayManager exception");
			Map<String, Object> m = new LinkedHashMap<String, Object>();
			m.put("result", false);
			m.put("errormsg", "unknown option.. <dpid, cookie, link, timeouts>");
			return m;
		}
	}
	
	private Map<String, Short> flowTimeouts(){
		Map<String, Short> result = new LinkedHashMap<>();
		result.put("idletimeout", flowService.getFlowmodIdleTimeout());
		result.put("hardtimeout", flowService.getFlowmodHardTimeout());
		return result;
	}
	
	private Map<U64, Object> decodeCookie(Map<U64, Flow> in){
		Map<U64, Object> out = new LinkedHashMap<U64, Object>();
		if(in == null){return out;}
		
		Set<U64> keys = in.keySet();
		for(U64 key : keys){
			Map<String, Object> flows = new LinkedHashMap<String, Object>();
			Flow flow = in.get(key);
			flows.put("cookie", flow.getCookie());
			//flows.put("links", flow.getLinks());
			List<Map<String, String>> linksList = new LinkedList<Map<String, String>>();
			for(Link currlink : flow.getLinks()){
				Map<String, String> linkMap = new LinkedHashMap<String, String>();
				linkMap.put("srcsw", currlink.getSrc().toString());
				linkMap.put("srcport", currlink.getSrcPort().toString());
				linkMap.put("dstsw", currlink.getDst().toString());
				linkMap.put("dstport", currlink.getDstPort().toString());
				linksList.add(linkMap);
			}
			flows.put("links", linksList);
			flows.put("match", flow.getMatch());
			List<String> dpids = new ArrayList<String>();
			for(IOFSwitch s : flow.getSwitches()){
				dpids.add(HexString.toHexString(s.getId().getBytes()));
			}
			flows.put("switches", dpids);
			out.put(key, flows);
		}
		return out;
	}
	
	private Map<Long, Object> decodeDpid(ArrayListMultimap<DatapathId, Flow> in){
		Map<Long, Object> out = new LinkedHashMap<Long, Object>();
		if(in == null){return out;}
		Set<DatapathId> keys = in.asMap().keySet();
		for(DatapathId key : keys){
			Collection<Flow> flowCollection = in.asMap().get(key);
			for(Flow flow : flowCollection){
				LinkedHashMap<String, Object> flows = new LinkedHashMap<String, Object>();
				flows.put("cookie", flow.getCookie());
				//flows.put("links", flow.getLinks());
				List<Map<String, String>> linksList = new LinkedList<Map<String, String>>();
				for(Link currlink : flow.getLinks()){
					Map<String, String> linkMap = new LinkedHashMap<String, String>();
					linkMap.put("srcsw", currlink.getSrc().toString());
					linkMap.put("srcport", currlink.getSrcPort().toString());
					linkMap.put("dstsw", currlink.getDst().toString());
					linkMap.put("dstport", currlink.getDstPort().toString());
					linksList.add(linkMap);
				}
				flows.put("links", linksList);
				
				flows.put("match", flow.getMatch());
				List<String> dpids = new ArrayList<String>();
				for(IOFSwitch s : flow.getSwitches()){
					dpids.add(HexString.toHexString(s.getId().getBytes()));
				}
				flows.put("switches", dpids);
				out.put(key.getLong(), flows);
			}
		}
		return out;
	}
	
	private Map<Link, Object> decodeLink(ArrayListMultimap<Link, Flow> in){
		Map<Link, Object> out = new LinkedHashMap<Link, Object>();
		if(in == null){return out;}
		Set<Link> keys = in.asMap().keySet();
		for(Link key : keys){	
			Collection<Flow> flowCollection = in.asMap().get(key);
			for(Flow flow : flowCollection){
				LinkedHashMap<String, Object> flows = new LinkedHashMap<String, Object>();
				flows.put("cookie", flow.getCookie());
				//flows.put("links", flow.getLinks());
				List<Map<String, String>> linksList = new LinkedList<Map<String, String>>();
				for(Link currlink : flow.getLinks()){
					Map<String, String> linkMap = new LinkedHashMap<String, String>();
					linkMap.put("srcsw", currlink.getSrc().toString());
					linkMap.put("srcport", currlink.getSrcPort().toString());
					linkMap.put("dstsw", currlink.getDst().toString());
					linkMap.put("dstport", currlink.getDstPort().toString());
					linksList.add(linkMap);
				}
				flows.put("links", linksList);
				
				flows.put("match", flow.getMatch());
				List<String> dpids = new ArrayList<String>();
				for(IOFSwitch s : flow.getSwitches()){
					dpids.add(HexString.toHexString(s.getId().getBytes()));
				}
				flows.put("switches", dpids);
				out.put(key, flows);
			}
		}
		return out;
	}
}
