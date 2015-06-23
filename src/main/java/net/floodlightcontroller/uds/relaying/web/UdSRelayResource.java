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
import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UdSRelayResource extends ServerResource {
	protected static final Logger log = LoggerFactory.getLogger(UdSRelayResource.class);
	private IRelayService relayManager;
	@Get("json")
    public Map<String, Object> retrieve() {
		relayManager = (IRelayService)getContext().getAttributes().get(IRelayService.class.getCanonicalName());
		Map<String, Object> m = new LinkedHashMap<String, Object>();
		Map<String, Object> udp = new LinkedHashMap<String, Object>();
		try{
			udp.put("enabled", relayManager.isUDPRelayingEnabled());
			udp.put("count", relayManager.getAllUDPRelays().size());	
		}catch(Exception e){
			udp.put("enabled", false);
			udp.put("count", 0);
			udp.put("inconsistency", true);
		}
		Map<String, Object> tcp = new LinkedHashMap<String, Object>();
		try{
			tcp.put("enabled", relayManager.isTCPRelayingEnabled());
			tcp.put("count", relayManager.getAllTCPRelays().size());	
		}catch(Exception e){
			tcp.put("enabled", false);
			tcp.put("count", 0);
			tcp.put("inconsistency", true);
		}
		m.put("udp", udp );
		m.put("tcp", tcp );
		return m;
	}
	
}
