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

package net.floodlightcontroller.uds.statistics.web;

import java.util.LinkedHashMap;
import java.util.Map;

import net.floodlightcontroller.uds.statistics.interfaces.IOfStatistics;

import org.restlet.data.Status;
import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UdSStatisticsResource extends ServerResource {
	protected static final Logger log = LoggerFactory.getLogger(UdSStatisticsResource.class);

	private IOfStatistics statsManager;

	@Get("json")
    public Object retrieve() {
		// what do we need to do?
		String queryString = (String) getRequestAttributes().get("option");
				
		// that's what we need, our tools
		statsManager = (IOfStatistics)getContext().getAttributes().get(IOfStatistics.class.getCanonicalName());

		// now lets work now
		switch(queryString){
		case "interval":
			return statsManager.getStatsInterval();
		case "packetloss" :
			return statsManager.averagePacketLoss();
		case "datarate" :
			return statsManager.averageDatarate();
		case "linkuptimes" :
			return statsManager.getAllLinksUptime();
		case "portspeeds" :
			return null; //computePortSpeeds();
		default :
			setStatus(Status.CLIENT_ERROR_BAD_REQUEST, "Bad request");
			Map<String, Object> m = new LinkedHashMap<String, Object>();
			m.put("result", false);
			m.put("errormsg", "none or wrong option set");
			return m;
		}
	}
	
	@SuppressWarnings("unused")
	private void computePortSpeeds(){
		//statsManager.getPortCurrentSpeed(dpid, port);
		//statsManager.getPortMaxSpeed(dpid, port);
	}
}
