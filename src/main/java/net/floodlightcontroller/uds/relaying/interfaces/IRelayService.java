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

package net.floodlightcontroller.uds.relaying.interfaces;

import java.util.Map;

import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.TransportPort;

import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.uds.relaying.model.IPTransportPortPair;
import net.floodlightcontroller.uds.relaying.model.RelayInfo;

/**
 * The Interface IRelayService. The relay service module keeps track of all
 * network relays running our relay software. The module can dynamically at
 * runtime add and remove relays for certain IP-port combinations.
 * 
 * @author Tobias Theobald <theobald@intel-vci.uni-saarland.de>
 */
public interface IRelayService extends IFloodlightService {

	/**
	 * Removes a udp relay for a given ip and port.
	 *
	 * @param ip
	 *            the ip
	 * @param port
	 *            the port
	 * @return true, if successful
	 */
	public boolean removeUDPRelay(int ip, short port);

	/**
	 * Removes a tcp relay for a given ip and port.
	 *
	 * @param ip
	 *            the ip
	 * @param port
	 *            the port
	 * @return true, if successful
	 */
	public boolean removeTCPRelay(int ip, short port);

	/**
	 * Global switch that decides whether UDP relaying is enabled. Note that
	 * this is done automatically when adding a UDP relay.
	 *
	 * @param enabled
	 *            the new switch state
	 * @return true, if successful
	 */
	public boolean setUDPRelayingEnabled(boolean enabled);

	/**
	 * Global switch that decides whether TCP relaying is enabled. Note that
	 * this is done automatically when adding a TCP relay.
	 *
	 * @param enabled
	 *            the new switch state
	 * @return true, if successful
	 */
	public boolean setTCPRelayingEnabled(boolean enabled);

	/**
	 * Adds a UDP relay. IP and / or port may be set to 0 for a IP and / or port
	 * wild card
	 *
	 * @param relayInfo
	 *            the relay info
	 * @param ipv4
	 *            the ipv4
	 * @param port
	 *            the port
	 */
	public void addUDPRelay(RelayInfo relayInfo, int ipv4, short port);

	/**
	 * Adds a UDP relay with string representations of the details. IP and / or
	 * port may be set to 0 for a IP and / or port wild card
	 *
	 * @param relayIpAddress
	 *            the relay ip address
	 * @param relayTransportPort
	 *            the relay transport port
	 * @param relayMac
	 *            the relay mac
	 * @param relaySwitch
	 *            the relay switch
	 * @param relaySwitchPort
	 *            the relay switch port
	 * @param ip
	 *            the ip
	 * @param port
	 *            the port
	 * @return true, if successful
	 */
	public boolean addUDPRelay(String relayIpAddress,
			String relayTransportPort, String relayMac, String relaySwitch,
			String relaySwitchPort, String filterIP, String filterPort);

	/**
	 * Adds a TCP relay. IP and / or port may be set to 0 for a IP and / or port
	 * wild card
	 *
	 * @param relayInfo
	 *            the relay info
	 * @param ipv4
	 *            the ipv4
	 * @param port
	 *            the port
	 */
	public void addTCPRelay(RelayInfo relayInfo, int ipv4, short port);

	/**
	 * Adds a TCP relay with string representations of the details. IP and / or
	 * port may be set to 0 for a IP and / or port wild card
	 *
	 * @param relayIpAddress
	 *            the relay ip address
	 * @param relayTransportPort
	 *            the relay transport port
	 * @param relayMac
	 *            the relay mac
	 * @param relaySwitch
	 *            the relay switch
	 * @param relaySwitchPort
	 *            the relay switch port
	 * @param ip
	 *            the ip
	 * @param port
	 *            the port
	 * @return true, if successful
	 */
	public boolean addTCPRelay(String relayIpAddress,
			String relayTransportPort, String relayMac, String relaySwitch,
			String relaySwitchPort, String filterIP, String filterPort);

	/**
	 * Returns the first TCP relay that matches the given ip and port
	 *
	 * @param ip
	 *            the ip
	 * @param port
	 *            the port
	 * @return the TCP relay
	 */
	public RelayInfo getTCPRelay(IPv4Address ip, TransportPort port);

	/**
	 * Returns the first UDP relay that matches the given ip and port
	 *
	 * @param iPv4Address
	 *            the i pv4 address
	 * @param transportPort
	 *            the transport port
	 * @return the UDP relay
	 */
	public RelayInfo getUDPRelay(IPv4Address iPv4Address,
			TransportPort transportPort);

	/**
	 * Returns the first active TCP relay that matches the given ip and port
	 *
	 * @param ip
	 *            the ip
	 * @param port
	 *            the port
	 * @return the TCP relay
	 */
	public RelayInfo getActiveTCPRelay(IPv4Address ip, TransportPort port);

	/**
	 * Returns the first active UDP relay that matches the given ip and port
	 *
	 * @param iPv4Address
	 *            the i pv4 address
	 * @param transportPort
	 *            the transport port
	 * @return the UDP relay
	 */
	public RelayInfo getActiveUDPRelay(IPv4Address iPv4Address,
			TransportPort transportPort);

	/**
	 * Returns all TCP relays as pairs of IP:Port and RelayInfo objects.
	 *
	 * @return all tcp relays
	 */
	public Map<IPTransportPortPair, RelayInfo> getAllTCPRelays();

	/**
	 * Returns all UDP relays as pairs of IP:Port and RelayInfo objects.
	 *
	 * @return all udp relays
	 */
	public Map<IPTransportPortPair, RelayInfo> getAllUDPRelays();

	/**
	 * Returns all active TCP relays as pairs of IP:Port and RelayInfo objects.
	 *
	 * @return all active tcp relays
	 */
	public Map<IPTransportPortPair, RelayInfo> getAllActiveTCPRelays();

	/**
	 * Returns all active UDP relays as pairs of IP:Port and RelayInfo objects.
	 *
	 * @return the active all udp relays
	 */
	public Map<IPTransportPortPair, RelayInfo> getAllActiveUDPRelays();

	/**
	 * Returns all inactive TCP relays as pairs of IP:Port and RelayInfo
	 * objects.
	 *
	 * @return the inactive all tcp relays
	 */
	public Map<IPTransportPortPair, RelayInfo> getAllInactiveTCPRelays();

	/**
	 * Returns all inactive UDP relays as pairs of IP:Port and RelayInfo
	 * objects.
	 *
	 * @return the inactive all udp relays
	 */
	public Map<IPTransportPortPair, RelayInfo> getAllInactiveUDPRelays();

	/**
	 * Checks if TCP relaying is globally enabled.
	 *
	 * @return true, if is TCP relaying enabled
	 */
	public boolean isTCPRelayingEnabled();

	/**
	 * Checks if UDP relaying is globally enabled.
	 *
	 * @return true, if is UDP relaying enabled
	 */
	public boolean isUDPRelayingEnabled();

	/**
	 * Activates or deactivates an UDP filter/ relay identified by ip and port.
	 * if enabled is true this filter and global filtering will be set to true,
	 * on false only(!) this filter/ relay will be set false, global will remain
	 * as it current state
	 * 
	 * @return true, if it is enabled
	 */
	public boolean toggleUDPFilter(String ip, String port, boolean enabled);

	/**
	 * Activates or deactivates an TCP filter/ relay identified by ip and port.
	 * if enabled is true this filter and global filtering will be set to true,
	 * on false only(!) this filter/ relay will be set false, global will remain
	 * as it current state
	 * 
	 * @return true, if it is enabled
	 */
	public boolean toggleTCPFilter(String ip, String port, boolean enabled);

	/**
	 * Checks if a UDP filter/ relay is enabled
	 *
	 * @return true, if it is enabled
	 */
	public boolean filterStatusUDP(String ip, String port);

	/**
	 * Checks if a TCP filter/ relay is enabled
	 *
	 * @return true, if it is enabled
	 */
	public boolean filterStatusTCP(String ip, String port);
	
	/**
	 * Tells whether a UDP relay exists or not
	 * 
	 * @return true, if the specified relay exists
	 */
	public boolean existsUDP(String ip, String port);
	
	/**
	 * Tells whether a TCP relay exists or not
	 * 
	 * @return true, if the specified relay exists
	 */
	public boolean existsTCP(String ip, String port);

}
