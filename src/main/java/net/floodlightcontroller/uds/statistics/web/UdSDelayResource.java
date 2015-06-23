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
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import net.floodlightcontroller.routing.Link;
import net.floodlightcontroller.uds.statistics.interfaces.IOfDelayManagerService;

import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UdSDelayResource extends ServerResource {
	protected static final Logger log = LoggerFactory.getLogger(UdSDelayResource.class);

	private IOfDelayManagerService delayManager;

	@Get("json")
    public Object retrieve() {				
		// that's what we need, our tools
		delayManager = (IOfDelayManagerService)getContext().getAttributes().get(IOfDelayManagerService.class.getCanonicalName());
		return computeDelay();
	}
			
	// updated
	private List<Map<String, Object>> computeDelay(){
		List<Map<String, Object>> list = new LinkedList<Map<String, Object>>();
		Map<Link, Double> delays = delayManager.getDelay();
		for(Link link : delays.keySet()){
			Map<String, Object> currLinkMap = new LinkedHashMap<String, Object>();
			currLinkMap.put("srcDpid", link.getSrc().toString());
			currLinkMap.put("dstDpid", link.getDst().toString());
			currLinkMap.put("srcPort", link.getSrcPort().toString());
			currLinkMap.put("dstPort", link.getDstPort().toString());
			currLinkMap.put("unit", "ms");
			
			double sw1Delay = delayManager.getCSwCDelay(link.getSrc());
			if(sw1Delay!=-1.0){
				currLinkMap.put("src-ctl-Delay", sw1Delay / 1000000.0);
			}else{
				currLinkMap.put("src-ctl-Delay", null);
			}

			double sw2Delay = delayManager.getCSwCDelay(link.getDst());
			if(sw2Delay!=-1.0){
				currLinkMap.put("dst-ctl-Delay", sw2Delay / 1000000.0);
			}else{
				currLinkMap.put("dst-ctl-Delay", null);
			}

			double sw1sw2Delay = delayManager.getCSw1Sw2CDelay(link);
			if(sw1sw2Delay >= 0){
				currLinkMap.put("fullDelay", sw1sw2Delay / 1000000.0);
			}else{
				currLinkMap.put("fullDelay", null);
			}
			
			// if any of those is false, we detected inconsistency:
			boolean consistency  = sw1Delay!=-1.0;							// because -1 indicates mission C-Sw1 data
					consistency &= sw2Delay!=-1.0;							// because -1 indicates mission C-Sw2 data
					consistency &= sw1sw2Delay >= 0.0;						// because neg inf indicates missing C-Sw1-Sw2-C data
					consistency &= (sw1sw2Delay > 0.5*(sw1Delay+sw2Delay));	// because C-Sw1-Sw2-C can not have less delay than C-Sw1 + C-Sw2
			currLinkMap.put("inconsistency", !consistency);
			
			list.add(currLinkMap);
		}
		return list;
	}
}
