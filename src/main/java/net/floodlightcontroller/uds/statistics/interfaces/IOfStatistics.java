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

package net.floodlightcontroller.uds.statistics.interfaces;

import java.util.Map;

import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.OFPort;

import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.routing.Link;

public interface IOfStatistics extends IFloodlightService {

	//public Map<String, JSONObject> getCachedStatistics();
	//public Map<String, JSONObject> getLastStatistics();
	//public void flushCachedStatistics();
		
	public Map<String, Map<String, Map<String, Long>>> averageDatarate();
	public Map<String, Map<String, Double>> averagePacketLoss();
	public Long getDpidUptime(DatapathId dpid);
	public long linkUptime(Link l);
	public Map<String, Long> getAllLinksUptime();
	public long getStatsInterval();
	public int getHistoryLength();
	public void setHistoryLength(int newlength);
	
	public Long getPortCurrentSpeed(DatapathId dpid, OFPort port);
	public Long getPortMaxSpeed(DatapathId dpid, OFPort port);	
}
