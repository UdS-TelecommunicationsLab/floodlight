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

package net.floodlightcontroller.uds.multicast.interfaces;

import org.projectfloodlight.openflow.types.IPv4Address;

import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.uds.multicast.model.MulticastGroup;

/**
 * The Interface IMulticastService. It allows interfacing with the
 * MulticastModule. The module handles and answers network-internal IGMP
 * messages and regularly asks the clients for membership in the groups. By
 * keeping the groups, the module can know, which hosts subscribed to a packet
 * to a certain multicast IP.
 * 
 * @author Tobias Theobald <theobald@intel-vci.uni-saarland.de>
 */
public interface IMulticastService extends IFloodlightService {

	/**
	 * Gets the ping interval.
	 *
	 * @return the ping interval
	 */
	public int getPingInterval();

	/**
	 * Sets the ping interval. This describes, how many seconds there are
	 * between two multicast pings. It defaults to 10s.
	 *
	 * @param pingInterval
	 *            the new ping interval in seconds
	 */
	public void setPingInterval(int pingInterval);

	/**
	 * Gets the client timeout.
	 *
	 * @return the client timeout
	 */
	public int getClientTimeout();

	/**
	 * Sets the client timeout. Timeout after which clients are removed from
	 * multicast groups. This is used to determine how many seconds there may be
	 * between the last IGMP reply and the current time.
	 *
	 * @param clientTimeout
	 *            the new client timeout in seconds
	 */
	public void setClientTimeout(int clientTimeout);

	/**
	 * Gets the group for a certain multicast IP address
	 *
	 * @param ipv4Address
	 *            the ipv4 address of the multicast group
	 * @return the group
	 */
	public MulticastGroup getGroup(IPv4Address ipv4Address);

}
