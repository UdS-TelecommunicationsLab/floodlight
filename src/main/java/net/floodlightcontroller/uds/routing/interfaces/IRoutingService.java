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
import java.util.Map;

import org.projectfloodlight.openflow.protocol.OFPacketIn;
import org.projectfloodlight.openflow.protocol.match.Match;

import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.devicemanager.IDevice;
import net.floodlightcontroller.devicemanager.SwitchPort;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.uds.routing.model.CostFunctionType;

/**
 * The Interface IRoutingService.
 */
public interface IRoutingService extends IFloodlightService {

	/**
	 * Sets the unicast path algorithm. The default algorithm that is used is a
	 * simple, CPU-based version of Dijkstra's algorithm
	 *
	 * @param algo
	 *            the new unicast path algorithm
	 */
	void setUnicastPathAlgorithm(IUnicastPathAlgorithm algo);

	/**
	 * Invalidates the route caches. Usually, routes are cached, but if the cost
	 * for a route changes, it may be necessary to invalidate those cached
	 * routes.
	 */
	void invalidateAllCaches();

	/**
	 * Returns a map with current cost function mappings. If a certain
	 * {@link CostFunctionType} does not have a cost function, it uses the
	 * constant function when computing a route with this cost function
	 *
	 * @return the cost function map
	 */
	Map<CostFunctionType, IConnectionGraphCostFunction> getCostFunctionMap();

	/**
	 * Sets the cost function for a specific cost function.
	 *
	 * @param tos
	 *            the tos
	 * @param function
	 *            the function
	 */
	public void addConnectionGraphCostFunction(CostFunctionType tos,
			IConnectionGraphCostFunction function);

	/**
	 * Gets the available metrics for which cost functions has been set.
	 *
	 * @return the available metrics
	 */
	public List<CostFunctionType> getAvailableMetrics();

	/**
	 * Gets the current default metric.
	 *
	 * @return the current default metric
	 */
	public CostFunctionType getCurrentDefaultMetric();

	/**
	 * Sets the default metric. The default metric denotes, which metric's cost
	 * function is used when none is specifically given by the packet.
	 *
	 * @param tos
	 *            the tos
	 * @return true, if successful
	 */
	public boolean setDefaultMetric(String tos);

	/**
	 * Finds a suitable attachment point for a device.
	 *
	 * @param findDevice
	 *            the find device
	 * @return the switch port
	 */
	SwitchPort findSuitableAttachmentPoint(IDevice findDevice);

	/**
	 * Do unicast routing. 
	 *
	 * @param dstSwitch
	 *            the dst switch
	 * @param packetIn
	 *            the packet in
	 * @param srcAttachmentPoint
	 *            the src attachment point
	 * @param setTransportDestination
	 *            the set transport destination
	 * @param lowDelay
	 *            the low delay
	 * @param doNotSendPacket
	 *            whether to send the packet
	 * @param description
	 *            the description string
	 * @param installReverseFlow
	 *            whether to also the install reverse flow
	 * @param eth
	 *            the ethernet packet
	 */
	void doUnicastRouting(IOFSwitch dstSwitch, OFPacketIn packetIn,
			SwitchPort srcAttachmentPoint, Match setTransportDestination,
			CostFunctionType lowDelay, boolean doNotSendPacket, String description,
			boolean installReverseFlow, Ethernet eth);
}
