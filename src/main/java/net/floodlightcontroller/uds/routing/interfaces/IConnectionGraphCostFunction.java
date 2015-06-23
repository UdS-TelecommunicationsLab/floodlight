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

package net.floodlightcontroller.uds.routing.interfaces;

import net.floodlightcontroller.routing.Link;

/**
 * The Interface IConnectionGraphCostFunction represents a cost function that is
 * used when calculating a route in the network. It usually represents a certain
 * link metric, such as delay, packet loss, etc.
 * 
 * Such a cost function can be given to the {@link IRoutingService} with a
 * certain metric.
 * 
 * @author Tobias Theobald <theobald@intel-vci.uni-saarland.de>
 */
public interface IConnectionGraphCostFunction {

	/**
	 * Returns the cost for a certain link accoring to the metric. The returned
	 * cost MUST be a __positive__ number that is __not infinity__. Note that
	 * relatively bigger link cost result in the link being less likely to be
	 * used. If no metric is available yet for a certain link, you should return
	 * a reasonable maximum value
	 *
	 * @param link
	 *            the link
	 * @return the cost
	 */
	public double getCost(Link link);

}
