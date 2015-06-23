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

package net.floodlightcontroller.uds.routing.algorithms;

import gnu.trove.iterator.TLongIterator;
import gnu.trove.map.hash.TLongDoubleHashMap;
import gnu.trove.map.hash.TLongLongHashMap;
import gnu.trove.map.hash.TLongObjectHashMap;
import gnu.trove.set.hash.TLongHashSet;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.projectfloodlight.openflow.types.DatapathId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.routing.Link;
import net.floodlightcontroller.uds.routing.interfaces.IConnectionGraphCostFunction;
import net.floodlightcontroller.uds.routing.interfaces.IRoutingService;
import net.floodlightcontroller.uds.routing.interfaces.IUnicastPathAlgorithm;

public class DijkstraSSSPAlgorithm extends RoutingAlgorithmBase implements IUnicastPathAlgorithm {

	protected static final Logger log = LoggerFactory
			.getLogger(DijkstraSSSPAlgorithm.class);
	
	
	@Override
	public List<Link> computeShortestPath(IOFSwitch swSrc, IOFSwitch swDst,
			IConnectionGraphCostFunction costFunction) {
		// If source and destination switch are the same, just return an empty
		// list
		if (swSrc.equals(swDst))
			return Collections.emptyList();

		//Map<Long, IOFSwitch> allSwitchMapSlow = floodlightProvider.getAllSwitchMap();
		Map<DatapathId, IOFSwitch> allSwitchMapSlow = new HashMap<>(switchManager.getAllSwitchMap());
		Map<DatapathId, Set<Link>> switchLinksSlow = new HashMap<>(linkManager.getSwitchLinks());
		
		int numberOfSwitches = allSwitchMapSlow.size();

		TLongObjectHashMap<Set<Link>> switchLinks = new TLongObjectHashMap<>(
				numberOfSwitches);
		TLongObjectHashMap<IOFSwitch> allSwitchMap = new TLongObjectHashMap<>(
				numberOfSwitches);
		TLongDoubleHashMap distances = new TLongDoubleHashMap(numberOfSwitches);
		TLongLongHashMap predecessors = new TLongLongHashMap(numberOfSwitches);
		TLongObjectHashMap<Link> predecessorLinks = new TLongObjectHashMap<>(
				numberOfSwitches);
		TLongHashSet activeNodes = new TLongHashSet(numberOfSwitches);

		DatapathId dpidSrc = swSrc.getId();
		DatapathId dpidDst = swDst.getId();

		// init phase
		for (Entry<DatapathId, IOFSwitch> entry : allSwitchMapSlow.entrySet()) {
			if (!switchLinksSlow.containsKey(entry.getKey())) {
				switchLinks.put(entry.getKey().getLong(), new HashSet<Link>());
			} else {
				switchLinks.put(entry.getKey().getLong(), new HashSet<>(switchLinksSlow.get(entry.getKey())));
			}
			activeNodes.add(entry.getKey().getLong());
			distances.put(entry.getKey().getLong(), Double.POSITIVE_INFINITY);
			allSwitchMap.put(entry.getKey().getLong(), entry.getValue());
			predecessors.put(entry.getKey().getLong(), Long.MIN_VALUE);
			predecessorLinks.put(entry.getKey().getLong(), null);
		}
		distances.put(dpidSrc.getLong(), 0.0);

		// loop over all nodes that are still active
		while (!activeNodes.isEmpty()) {
			// Find min distance of all nodes still in active nodes
			long currentDpid = -1;
			double minCost = Double.POSITIVE_INFINITY;
			TLongIterator it = activeNodes.iterator();
			while (it.hasNext()) {
				long dpid = it.next();
				double cost = distances.get(dpid);
				if (cost < minCost) {
					minCost = cost;
					currentDpid = dpid;
				}
			}

			// if only unconnected still nodes exist, end loop.
			if (currentDpid == -1) {
				break;
			}

			// Remove this node from active nodes
			activeNodes.remove(currentDpid);
			// Iterate over neighbors to relax edges
			if (!switchLinks.containsKey(currentDpid))
				continue;
			for (Link link : switchLinks.get(currentDpid)) {
				// Skip links in which we currently active link is not source
				if (link.getSrc().getLong() != currentDpid) {
					continue;
				}
				// Only update cost of still active nodes
				DatapathId neighborDpid = link.getDst();
				if (!activeNodes.contains(neighborDpid.getLong()))
					continue;

				// Compute potentially new cost
				double newCost = distances.get(currentDpid)
						+ costFunction.getCost(link);
				// Set new cost and predecessor if new cost < old cost
				if (newCost < distances.get(neighborDpid.getLong())) {
					distances.put(neighborDpid.getLong(), newCost);
					predecessors.put(neighborDpid.getLong(), currentDpid);
					predecessorLinks.put(neighborDpid.getLong(), link);
				}
			}
		}

		// Check for non-connected target
		if (Double.isInfinite(distances.get(dpidDst.getLong())))
			return null;

		// Compute path
		LinkedList<Link> path = new LinkedList<>();
		DatapathId currentSwitch = dpidDst;
		while (!currentSwitch.equals(dpidSrc)) {
			path.addFirst(predecessorLinks.get(currentSwitch.getLong()));
			currentSwitch = DatapathId.of(predecessors.get(currentSwitch.getLong()));
		}

		return path;
	}

	@Override
	protected void registerSelf(IRoutingService routing) {
		routing.setUnicastPathAlgorithm(this);
	}

}
