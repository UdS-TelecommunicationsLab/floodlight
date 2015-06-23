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

package net.floodlightcontroller.uds.packetinspection.web;

import java.util.LinkedHashMap;
import java.util.Map;

import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UdSPacketinspectionResource extends ServerResource {
	protected static final Logger log = LoggerFactory.getLogger(UdSPacketinspectionResource.class);

	@Get("json")
    public Map<String, Boolean> retrieve() {
		//RTSPInspector = (IRelayService)getContext().getAttributes().get(IRelayService.class.getCanonicalName());
		
		Map<String, Boolean> m = new LinkedHashMap<String, Boolean>();
		//m.put("udpRelaying", relayManager.isTCPRelayingEnabled() );
		//m.put("tcpRelaying", relayManager.isUDPRelayingEnabled() );
		return m;
	}
}
