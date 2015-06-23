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

import java.util.List;

import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.routing.Link;

/**
 * The Interface IUnicastPathAlgorithm. Represents a unicast algorithm that finds 
 */
public interface IUnicastPathAlgorithm {

	/**
	 * Compute shortest path. Returns list of links if path was found or null if
	 * no path exists.
	 *
	 * @param swSrc
	 *            the source switch 
	 * @param swDst
	 *            the destination switch 
	 * @param prio
	 *            the priority of the packet
	 * @return the list
	 */
	public List<Link> computeShortestPath(IOFSwitch swSrc, IOFSwitch swDst,
			IConnectionGraphCostFunction costFunction);
	
}
