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

package net.floodlightcontroller.uds.multicast.web;

import java.util.LinkedHashMap;
import java.util.Map;

import net.floodlightcontroller.uds.multicast.interfaces.IMulticastService;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.restlet.data.Status;
import org.restlet.resource.Get;
import org.restlet.resource.Post;
import org.restlet.resource.ServerResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UdSMulticastResource extends ServerResource {
	protected static final Logger log = LoggerFactory.getLogger(UdSMulticastResource.class);
	private IMulticastService multicastService;
	
	@Get("json")
    public Map<String, Object> retrieve() {
		multicastService = (IMulticastService)getContext().getAttributes().get(IMulticastService.class.getCanonicalName()); 
		
		Map<String, Object> out = new LinkedHashMap<String, Object>();
				
		out.put("pingInterval", multicastService.getPingInterval());
		out.put("clientTimeout", multicastService.getClientTimeout());
		return out;
	}
	
	@Post
	public Object post(String fmJson){
		multicastService = (IMulticastService)getContext().getAttributes().get(IMulticastService.class.getCanonicalName());
		JSONTokener tokener = new JSONTokener(fmJson);
		
		Map<String, Object> m = new LinkedHashMap<String, Object>();
		
		try {
			JSONObject root = new JSONObject(tokener);
			String action = root.getString("action");
			
			switch(action){
			case "getInterval":
				m.put("interval", multicastService.getPingInterval());
				break;
			case "getClientTimeout":
				m.put("timeout", multicastService.getClientTimeout());
				break;
			case "setInterval":
				int interval = Integer.parseInt(root.getString("interval"));
				multicastService.setPingInterval(interval);
				m.put("result", true);
				break;
			case "setClientTimeout":
				int timeout = Integer.parseInt(root.getString("timeout"));
				multicastService.setClientTimeout(timeout);
				m.put("result", true);
				break;
			case "getGroup":
				IPv4Address ipv4 = IPv4Address.of(root.getString("ip"));
				m.put("multicastgroup", multicastService.getGroup(ipv4));
				break;
			default:
				setStatus(Status.CLIENT_ERROR_BAD_REQUEST, "RelayManager exception");
				m.put("result", false);
				m.put("errormsg", "malformed json or something's missing..");	
			}
			
		} catch (JSONException e) {
			setStatus(Status.CLIENT_ERROR_BAD_REQUEST, "RelayManager exception");
			m.put("result", false);
			m.put("errormsg", "malformed json or something's missing..");
		}

		return m;
	}
	
}
