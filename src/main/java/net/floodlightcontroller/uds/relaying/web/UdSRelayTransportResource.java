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

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map;

import net.floodlightcontroller.uds.relaying.interfaces.IRelayService;
import net.floodlightcontroller.uds.relaying.model.IPTransportPortPair;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.restlet.data.Status;
import org.restlet.resource.Get;
import org.restlet.resource.Post;
import org.restlet.resource.ServerResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UdSRelayTransportResource extends ServerResource {
	protected static final Logger log = LoggerFactory.getLogger(UdSRelayTransportResource.class);
	private IRelayService relayManager;
	
	@Post
	public Object post(String fmJson){	
		relayManager = (IRelayService)getContext().getAttributes().get(IRelayService.class.getCanonicalName());
		String transport = (String) getRequestAttributes().get("transport");
		JSONTokener tokener = new JSONTokener(fmJson);
		
		Map<String, Object> m = new LinkedHashMap<String, Object>();
		try{
			JSONObject root = new JSONObject(tokener);
			boolean value = (root.getString("enabled").equals("true")) ? true : false;		// true, false
			switch(transport){
				case "tcp":
					return m.put("result", relayManager.setTCPRelayingEnabled(value));
				case "udp":
					return m.put("result", relayManager.setUDPRelayingEnabled(value));
				default:
					m.put("result", false);
					m.put("error", "invalid transport");
					return m;
			}
		}catch(JSONException e) {
			setStatus(Status.CLIENT_ERROR_BAD_REQUEST);
			m.put("result", false);
			m.put("errormsg", "malformed json or something's missing..");
			return m;
		}catch(Exception ee){
			setStatus(Status.SERVER_ERROR_INTERNAL);
			m.put("result", false);
			m.put("errormsg", ee.getMessage());
			return m;			
		}
	}
	
	@Get("json")
	public Object retrieve() {
		relayManager = (IRelayService)getContext().getAttributes().get(IRelayService.class.getCanonicalName());
		String transport = (String) getRequestAttributes().get("transport");
		
		LinkedList<Map<String, String>> l = new LinkedList<>();
		switch(transport){
		case "tcp":
			try{
				for(IPTransportPortPair relayKey : relayManager.getAllTCPRelays().keySet()){
					//RelayInfo currRelay = relayManager.getAllTCPRelays().get(relayKey);
					Map<String, String> m = new HashMap<>();
					m.put("filterIP", relayKey.getIp().toString());
					m.put("filterPort", relayKey.getPort().toString());
					l.add(m);
				}
			}catch(Exception e){
				setStatus(Status.CONNECTOR_ERROR_INTERNAL);
				Map<String, String> m = new HashMap<>();
				m.put("inconsistency", "true");
				m.put("exception", e.getMessage());
				l.add(m);
			}
			return l;
		case "udp":
			try{
				for(IPTransportPortPair relayKey : relayManager.getAllUDPRelays().keySet()){
					//RelayInfo currRelay = relayManager.getAllUDPRelays().get(relayKey);
					Map<String, String> m = new HashMap<>();
					m.put("filterIP", relayKey.getIp().toString());
					m.put("filterPort", relayKey.getPort().toString());
					l.add(m);
				}
			}catch(Exception e){
				setStatus(Status.CONNECTOR_ERROR_INTERNAL);
				Map<String, String> m = new HashMap<>();
				m.put("inconsistency", "true");
				m.put("exception", e.getMessage());
				l.add(m);
			}
			return l;
		default:
			setStatus(Status.CLIENT_ERROR_BAD_REQUEST);
			Map<String, Object> m = new LinkedHashMap<String, Object>();
			m.put("error", "invalid transport");
			return m;
		}
	}
}
