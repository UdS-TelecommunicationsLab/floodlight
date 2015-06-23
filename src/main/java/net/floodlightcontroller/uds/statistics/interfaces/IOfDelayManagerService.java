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

import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.routing.Link;

public interface IOfDelayManagerService extends IFloodlightService {
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
	public double getCSw1Sw2CDelay(Link link);
	public Map<Link, Double> getDelay();
	public Map<DatapathId, Double> getControllerSwitchDelays();
	public double getCSwCDelay(DatapathId dpid);
}
