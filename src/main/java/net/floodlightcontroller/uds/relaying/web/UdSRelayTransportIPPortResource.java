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

package net.floodlightcontroller.uds.relaying.web;

import java.util.LinkedHashMap;
import java.util.Map;

import net.floodlightcontroller.uds.relaying.interfaces.IRelayService;
import net.floodlightcontroller.uds.relaying.internal.RelayManager;
import net.floodlightcontroller.uds.relaying.model.RelayInfo;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.TransportPort;
import org.restlet.data.Status;
import org.restlet.resource.Delete;
import org.restlet.resource.Get;
import org.restlet.resource.Post;
import org.restlet.resource.Put;
import org.restlet.resource.ServerResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UdSRelayTransportIPPortResource extends ServerResource {
	protected static final Logger log = LoggerFactory.getLogger(UdSRelayTransportIPPortResource.class);
	private IRelayService relayManager;
	
	// get relay information for the specified filter
	@Get("json")
	public Object retrieve() {
		relayManager = (IRelayService)getContext().getAttributes().get(IRelayService.class.getCanonicalName());
		String transport = (String) getRequestAttributes().get("transport");
		IPv4Address ip;
		TransportPort port;
		try{
			ip = IPv4Address.of(RelayManager.IPfromString((String)getRequestAttributes().get("ip")));
			port = TransportPort.of(Integer.parseInt((String) getRequestAttributes().get("port")));
		}catch(IllegalArgumentException e1){
			setStatus(Status.CLIENT_ERROR_BAD_REQUEST);
			Map<String, Object> m = new LinkedHashMap<>();
			m.put("error", "could not parse ip and/ or port");
			return m;
		}
		
		Map<String, Object> map;
		switch(transport){
		case "tcp":
			map = RelayInfoToMap(relayManager.getTCPRelay(ip, port));
			
			try{
				boolean filterStatus = relayManager.filterStatusTCP(ip.toString(), port.toString());
				map.put("enabled", filterStatus);
			}catch(IllegalArgumentException e){
				setStatus(Status.CLIENT_ERROR_NOT_FOUND);
				map.put("error", "relay not found");
				//map.put("enabled", filterStatus);
			}
			
			return map;
		case "udp":
			map = RelayInfoToMap(relayManager.getUDPRelay(ip, port));
			
			try{
				boolean filterStatus = relayManager.filterStatusUDP(ip.toString(), port.toString());
				map.put("enabled", filterStatus);
			}catch(IllegalArgumentException e){
				setStatus(Status.CLIENT_ERROR_NOT_FOUND);
				map.put("error", "relay not found");
				//map.put("enabled", filterStatus);
			}

			return map;
		default:
			setStatus(Status.CLIENT_ERROR_BAD_REQUEST);
			map = new LinkedHashMap<>();
			map.put("error", "invalid transport");
			return map;
		}
	}
	
	// simple mapping function
	private Map<String, Object> RelayInfoToMap(RelayInfo ri){
		if(ri==null){return null;}
		Map<String, Object> relayMap = new LinkedHashMap<String, Object>();
		relayMap.put("relayip", ri.getRelayIpAddress().toString());
		relayMap.put("relaymac", ri.getRelayMacAddress().toString());
		relayMap.put("swdpid", ri.getRelaySwitch().toString());
		relayMap.put("swport", ri.getRelaySwitchPort().toString());
		relayMap.put("transportport", ri.getRelayTransportPort().toString());
		return relayMap;
	}
	
	// add or update relays (Post/ Put)
	@Put
	@Post
	public Object put(String fmJson) {
		relayManager = (IRelayService)getContext().getAttributes().get(IRelayService.class.getCanonicalName());
		JSONTokener tokener = new JSONTokener(fmJson);

		String transport = (String)getRequestAttributes().get("transport");
		String filterIP = (String)getRequestAttributes().get("ip");
		String filterPort = (String)getRequestAttributes().get("port");
		
		try{
			boolean result = false;
			
			JSONObject root = new JSONObject(tokener);
			
			String ip = root.getString("ip");
			String port = root.getString("port");

			boolean enabled = root.getString("enabled").equals("true");
			switch(transport){
			case "tcp":
				
				if(!relayManager.existsTCP(filterIP, filterPort)){
					// if the relay does not exist, add it
					setStatus(Status.SUCCESS_CREATED);
					result = relayManager.addTCPRelay(ip, port, root.getString("relayMac"), root.getString("switchDpid"), root.getString("switchPort"), filterIP, filterPort);
					// and if status should be enabled (true) set it to true
					result &= (enabled ? relayManager.toggleTCPFilter(filterIP, filterPort, enabled) : true);
				}else if(relayManager.filterStatusTCP(filterIP, filterPort)!=enabled){
					// if the relay exists and the status differs from the requested one, toggle the status
					setStatus(Status.SUCCESS_ACCEPTED);
					result = relayManager.toggleTCPFilter(filterIP, filterPort, enabled);
				}else{
					// if the relay exists but it's status doesn't differ from the requested current one
					setStatus(Status.CLIENT_ERROR_NOT_ACCEPTABLE);
					result = true;
				}
				
				return result;
			case "udp":
				// same code as for tcp, same comments could go here
				// if relay exists it will be overwritten
				if(!relayManager.existsUDP(filterIP, filterPort)){
					// if the relay does not exist, add it
					setStatus(Status.SUCCESS_CREATED);
					result = relayManager.addUDPRelay(ip, port, root.getString("relayMac"), root.getString("switchDpid"), root.getString("switchPort"), filterIP, filterPort);
					// and if status should be enabled (true) set it to true
					result &= (enabled ? relayManager.toggleUDPFilter(filterIP, filterPort, enabled) : true);
				}else if(relayManager.filterStatusUDP(filterIP, filterPort)!=enabled){
					// if the relay exists and the status differs from the requested one, toggle the status
					setStatus(Status.SUCCESS_ACCEPTED);
					result = relayManager.toggleUDPFilter(filterIP, filterPort, enabled);
				}else{
					// if the relay exists but it's status doesn't differ from the requested current one
					setStatus(Status.CLIENT_ERROR_NOT_ACCEPTABLE);
					result = true;
				}
				
				return result;
			default:
				// unknown transport
				setStatus(Status.CLIENT_ERROR_BAD_REQUEST);
				Map<String, Object> m = new LinkedHashMap<String, Object>();
				m.put("error", "invalid transport");
				return m;
			}
		}catch(IllegalArgumentException iae){
			// happens if the specified relay doesn't exist, this should never happen since
			// we watched out for that before
			setStatus(Status.CLIENT_ERROR_BAD_REQUEST);
			Map<String, Object> m = new LinkedHashMap<String, Object>();
			m.put("error", "wrong relay data or illegal request");
			return m;
		}catch(JSONException e) {
			// happens if malformed, unparsable or if we're missing parameters
			setStatus(Status.CLIENT_ERROR_BAD_REQUEST);
			Map<String, Object> m = new LinkedHashMap<String, Object>();
			m.put("error", "malformed json or something's missing..");
			return m;
		}
	}
	
	@Delete("json")
	public Object del() {
		relayManager = (IRelayService)getContext().getAttributes().get(IRelayService.class.getCanonicalName());
		String transport  = (String)getRequestAttributes().get("transport");
		String ipString   = (String)getRequestAttributes().get("ip");
		String portString = (String)getRequestAttributes().get("port");
		
		try{
			IPv4Address ip = IPv4Address.of(RelayManager.IPfromString(ipString));
			TransportPort port = TransportPort.of(portString.equals("*") ? 0 : Integer.parseInt(portString));
			boolean result = false;
			
			switch(transport){
			case "tcp":
				if(!relayManager.existsTCP(ipString, portString)){
					setStatus(Status.CLIENT_ERROR_BAD_REQUEST);
					return result;
				}else{
					result = relayManager.removeTCPRelay(ip.getInt(), (short)port.getPort());
					if(result){
						setStatus(Status.SUCCESS_ACCEPTED);
					}else{
						setStatus(Status.SERVER_ERROR_INTERNAL);
					}
					return result;
				}
			case "udp":
				if(!relayManager.existsUDP(ipString, portString)){
					setStatus(Status.CLIENT_ERROR_BAD_REQUEST);
					return result;
				}else{
					result = relayManager.removeUDPRelay(ip.getInt(), (short)port.getPort());
					if(result){
						setStatus(Status.SUCCESS_ACCEPTED);
					}else{
						setStatus(Status.SERVER_ERROR_INTERNAL);
					}
					return result;
				}
			default:
				setStatus(Status.CLIENT_ERROR_BAD_REQUEST);
				Map<String, Object> m = new LinkedHashMap<>();
				m.put("error", "invalid transport");
				return m;
			}
		}catch(NumberFormatException e){
			setStatus(Status.CLIENT_ERROR_BAD_REQUEST);
			Map<String, Object> m = new LinkedHashMap<String, Object>();
			m.put("error", "invalid ip or port, impossible to parse that");
			return m;
		}
	}
}
