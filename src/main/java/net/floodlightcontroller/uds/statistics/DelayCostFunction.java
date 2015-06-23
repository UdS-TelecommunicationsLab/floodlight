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

package net.floodlightcontroller.uds.statistics;

import net.floodlightcontroller.routing.Link;
import net.floodlightcontroller.uds.routing.interfaces.IConnectionGraphCostFunction;
import net.floodlightcontroller.uds.statistics.interfaces.IOfDelayManagerService;

public class DelayCostFunction implements IConnectionGraphCostFunction {

	private IOfDelayManagerService ofDelayManagerService;
	
	public DelayCostFunction(IOfDelayManagerService ofDelayManagerService) {
		this.ofDelayManagerService = ofDelayManagerService;
	}

	@Override
	public double getCost(Link link) {
		double delay = ofDelayManagerService.getCSw1Sw2CDelay(link);
		
		// if delay unknown return 1s
		if (Double.isInfinite(delay))
			return 1000000000.0;
		
		return delay;
	}

}
