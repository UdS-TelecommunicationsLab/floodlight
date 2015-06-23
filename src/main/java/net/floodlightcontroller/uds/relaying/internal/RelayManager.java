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

package net.floodlightcontroller.uds.relaying.internal;

import gnu.trove.iterator.TLongObjectIterator;
import gnu.trove.map.hash.TLongObjectHashMap;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;

import net.floodlightcontroller.uds.relaying.interfaces.IRelayService;
import net.floodlightcontroller.uds.relaying.model.IPTransportPortPair;
import net.floodlightcontroller.uds.relaying.model.RelayInfo;

import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.TransportPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RelayManager implements IRelayService {

	protected static final Logger log = LoggerFactory
			.getLogger(RelayManager.class);

	// the key for this is ipv4 (32 bit), 16 zero bits, then port (16 bit)
	private TLongObjectHashMap<RelayInfo> udpRelays = new TLongObjectHashMap<>();
	private TLongObjectHashMap<RelayInfo> tcpRelays = new TLongObjectHashMap<>();

	private TLongObjectHashMap<RelayInfo> udpDisabledRelays = new TLongObjectHashMap<>();
	private TLongObjectHashMap<RelayInfo> tcpDisabledRelays = new TLongObjectHashMap<>();

	private boolean enableUDPRelaying = false;
	private boolean enableTCPRelaying = false;

	public void addUDPRelay(RelayInfo relayInfo, int ipv4, short port) {
		udpDisabledRelays.put((((long) ipv4) << 32) | port, relayInfo);
		log.info("Added udp relay {} for target {}:{}", relayInfo, ipv4, port);
	}

	public void addTCPRelay(RelayInfo relayInfo, int ipv4, short port) {
		tcpDisabledRelays.put((((long) ipv4) << 32) | port, relayInfo);
		log.info("Added tcp relay {} for target {}:{}", relayInfo, ipv4, port);
	}

	public boolean addUDPRelay(String relayIpAddress,
			String relayTransportPort, String relayMac, String relaySwitch,
			String relaySwitchPort, String filterIP, String filterPort) {
		RelayInfo relay = new RelayInfo(relayIpAddress, relayTransportPort,
				relayMac, relaySwitch, relaySwitchPort);
		addUDPRelay(
				relay,
				IPfromString(filterIP),
				"*".equals(filterPort) ? (short) 0 : (short) Integer.valueOf(
						filterPort).intValue());
		return true;
	}

	public boolean addTCPRelay(String relayIpAddress,
			String relayTransportPort, String relayMac, String relaySwitch,
			String relaySwitchPort, String filterIP, String filterPort) {
		RelayInfo relay = new RelayInfo(relayIpAddress, relayTransportPort,
				relayMac, relaySwitch, relaySwitchPort);
		addTCPRelay(
				relay,
				IPfromString(filterIP),
				"*".equals(filterPort) ? (short) 0 : (short) Integer.valueOf(
						filterPort).intValue());
		return true;
	}

	public boolean removeTCPRelay(int ip, short port) {
		// this makes it possible to remove inactive relays
		TLongObjectHashMap<RelayInfo> relayMap;
		
		try{
			// the map we're going to remove from
			if(this.filterStatusTCP(String.valueOf(ip), String.valueOf(port))){
				// if it's an active relay the map is udpRelays
				relayMap = tcpRelays;
			}else{
				// otherwise it should be in udpDisabledRelays
				relayMap = tcpDisabledRelays;
			}
		}catch(IllegalArgumentException iae){
			// there is no such relay (filterStatusUDP throws)
			return false;
		}
		
		RelayInfo result = relayMap.remove((((long) ip) << 32) | port);
		// FIXME there is no need to do it here
		//if (tcpRelays.size() == 0) {
		//	enableTCPRelaying = false;
		//}
		if (result == null) {
			// Check for complete wildcard
			result = relayMap.remove(0);
			if (result == null) {
				// Check for host wildcard
				result = relayMap.remove(port);
				if (result == null) {
					// Check for port wildcard
					result = relayMap.remove(((long) ip) << 32);
				}
			}
		}
		if (tcpRelays.size() == 0) {
			enableTCPRelaying = false;
		}
		return result != null;
	}

	public boolean removeUDPRelay(int ip, short port) {
		// this makes it possible to remove inactive relays
		TLongObjectHashMap<RelayInfo> relayMap;
		
		try{
			// the map we're going to remove from
			if(this.filterStatusUDP(String.valueOf(ip), String.valueOf(port))){
				// if it's an active relay the map is udpRelays
				relayMap = udpRelays;
			}else{
				// otherwise it should be in udpDisabledRelays
				relayMap = udpDisabledRelays;
			}
		}catch(IllegalArgumentException iae){
			// there is no such relay (filterStatusUDP throws)
			return false;
		}
		
		RelayInfo result = relayMap.remove((((long) ip) << 32) | port);
		if (result == null) {
			// Check for complete wildcard
			result = relayMap.remove(0);
			if (result == null) {
				// Check for host wildcard
				result = relayMap.remove(port);
				if (result == null) {
					// Check for port wildcard
					result = relayMap.remove(((long) ip) << 32);
				}
			}
		}
		if (udpRelays.size() == 0) {
			enableUDPRelaying = false;
		}
		return result != null;
	}

	public boolean isUDPRelayingEnabled() {
		return enableUDPRelaying;
	}

	public boolean isTCPRelayingEnabled() {
		return enableTCPRelaying;
	}

	@Override
	public RelayInfo getUDPRelay(IPv4Address ip, TransportPort port) {
		RelayInfo relayInfo;
		// first check active relays
		relayInfo = getActiveUDPRelay(ip, port);
		if (relayInfo != null)
			return relayInfo;

		// Now the inactive ones
		// start with fully specified ones
		relayInfo = udpDisabledRelays.get((((long) ip.getInt()) << 32)
				| port.getPort());
		if (relayInfo != null)
			return relayInfo;

		// Check for complete wildcard
		relayInfo = udpDisabledRelays.get(0);
		if (relayInfo != null)
			return relayInfo;

		// Check for host wildcard
		relayInfo = udpDisabledRelays.get(port.getPort());
		if (relayInfo != null)
			return relayInfo;

		// Check for port wildcard
		relayInfo = udpDisabledRelays.get(((long) ip.getInt()) << 32);
		if (relayInfo != null)
			return relayInfo;

		return relayInfo;
	}

	@Override
	public RelayInfo getTCPRelay(IPv4Address ip, TransportPort port) {
		RelayInfo relayInfo;
		// first check active relays
		relayInfo = getActiveTCPRelay(ip, port);
		if (relayInfo != null)
			return relayInfo;

		// Now the inactive ones
		// start with fully specified ones
		relayInfo = tcpDisabledRelays.get((((long) ip.getInt()) << 32)
				| port.getPort());
		if (relayInfo != null)
			return relayInfo;

		// Check for complete wildcard
		relayInfo = tcpDisabledRelays.get(0);
		if (relayInfo != null)
			return relayInfo;

		// Check for host wildcard
		relayInfo = tcpDisabledRelays.get(port.getPort());
		if (relayInfo != null)
			return relayInfo;

		// Check for port wildcard
		relayInfo = tcpDisabledRelays.get(((long) ip.getInt()) << 32);
		if (relayInfo != null)
			return relayInfo;

		return relayInfo;
	}

	public void startUp(Map<String, String> configOptions,
			ScheduledExecutorService executor) {
		// Read relays from config, otherwise use none. udp first
		String relayString = configOptions.get("udpRelays");
		if (relayString != null) {
			for (String singleRelay : relayString.split(",")) {
				try {
					String split[] = singleRelay.split("::");
					addUDPRelay(split[0], split[1], split[2], split[3],
							split[4], split[5], split[6]);
					setUDPRelayingEnabled(true);
					toggleUDPFilter(split[5], split[6], true);
				} catch (Exception e) {
					log.error("udpRelay is not a valid udp relay string: {}"
							+ ", should be of format udpRelayIP::"
							+ "udpRelayPort::udpRelayMAC::"
							+ "AttachmentSwitchDPID::AttachmentPort"
							+ "::{targetIP,*}::{targetPort,*}[,...]",
							relayString);
					e.printStackTrace();
				}
			}
		}

		relayString = configOptions.get("tcpRelays");
		if (relayString != null) {
			for (String singleRelay : relayString.split(",")) {
				try {
					String split[] = singleRelay.split("::");
					addTCPRelay(split[0], split[1], split[2], split[3],
							split[4], split[5], split[6]);
					setTCPRelayingEnabled(true);
					toggleTCPFilter(split[5], split[6], true);
				} catch (Exception e) {
					log.error("tcpRelay is not a valid udp relay string: {}"
							+ ", should be of format tcpRelayIP::"
							+ "tcpRelayPort::tcpRelayMAC::"
							+ "AttachmentSwitchDPID::AttachmentPort"
							+ "::{targetIP,*}::{targetPort,*}[,...]",
							relayString);
					e.printStackTrace();
				}
			}
		}
	}

	public static int IPfromString(String ip) {
		if (ip.equals("*"))
			return 0;
		return IPv4Address.of(ip).getInt();
	}

	@Override
	public boolean setUDPRelayingEnabled(boolean enabled) {
		boolean prevState = enableUDPRelaying;
		enableUDPRelaying = enabled;
		return prevState;
	}

	@Override
	public boolean setTCPRelayingEnabled(boolean enabled) {
		boolean prevState = enableTCPRelaying;
		enableTCPRelaying = enabled;
		return prevState;
	}

	@Override
	public Map<IPTransportPortPair, RelayInfo> getAllUDPRelays() {
		Map<IPTransportPortPair, RelayInfo> map = getAllActiveUDPRelays();
		map.putAll(getAllInactiveUDPRelays());
		return map;
	}

	@Override
	public Map<IPTransportPortPair, RelayInfo> getAllTCPRelays() {
		Map<IPTransportPortPair, RelayInfo> map = getAllActiveTCPRelays();
		map.putAll(getAllInactiveTCPRelays());
		return map;
	}

	@Override
	public boolean toggleUDPFilter(String ip, String port, boolean enabled) {
		long key = (((long) IPfromString(ip)) << 32)
				| (port.equals("*") ? (short) 0 : (short) Integer.valueOf(port)
						.intValue());
		if (enabled) {
			// set from disabled to enabled
			if (!udpDisabledRelays.contains(key)) {
				throw new IllegalArgumentException(
						"Given filter does not describe a disabled relay");
			}
			RelayInfo relay = udpDisabledRelays.remove(key);
			udpRelays.put(key, relay);
			enableUDPRelaying = true;
		} else {
			// set from enabled to disabled
			if (!udpRelays.contains(key)) {
				throw new IllegalArgumentException(
						"Given filter does not describe an enabled relay");
			}
			RelayInfo relay = udpRelays.remove(key);
			udpDisabledRelays.put(key, relay);
		}
		return enabled & enableUDPRelaying;
	}

	@Override
	public boolean toggleTCPFilter(String ip, String port, boolean enabled) {
		long key = (((long) IPfromString(ip)) << 32)
				| (port.equals("*") ? (short) 0 : (short) Integer.valueOf(port)
						.intValue());
		if (enabled) {
			// set from disabled to enabled
			if (!tcpDisabledRelays.contains(key)) {
				throw new IllegalArgumentException(
						"Given filter does not describe a disabled relay");
			}
			RelayInfo relay = tcpDisabledRelays.remove(key);
			tcpRelays.put(key, relay);
			enableTCPRelaying = true;
		} else {
			// set from enabled to disabled
			if (!tcpRelays.contains(key)) {
				throw new IllegalArgumentException(
						"Given filter does not describe an enabled relay");
			}
			RelayInfo relay = tcpRelays.remove(key);
			tcpDisabledRelays.put(key, relay);

		}
		return enabled & enableTCPRelaying;
	}

	@Override
	public boolean filterStatusUDP(String ip, String port) {
		long key = (((long) IPfromString(ip)) << 32)
				| (port.equals("*") ? (short) 0 : (short) Integer.valueOf(port)
						.intValue());
		
		if(udpRelays.contains(key) || udpDisabledRelays.contains(key))
			return udpRelays.contains(key);
		throw new IllegalArgumentException("Relay not found");
		/*
		if (udpRelays.contains(key))
			return true & enableUDPRelaying;
		else if (udpDisabledRelays.contains(key))
			return false & enableUDPRelaying;
		else
			throw new IllegalArgumentException("Relay not found");
		*/
	}

	@Override
	public boolean filterStatusTCP(String ip, String port) {
		long key = (((long) IPfromString(ip)) << 32)
				| (port.equals("*") ? (short) 0 : (short) Integer.valueOf(port)
						.intValue());
		
		if(tcpRelays.contains(key) || tcpDisabledRelays.contains(key))
			return tcpRelays.contains(key);
		throw new IllegalArgumentException("Relay not found");
		/*
		if (tcpRelays.contains(key))
			return true & enableTCPRelaying;
		else if (tcpDisabledRelays.contains(key))
			return false & enableTCPRelaying;
		else
			throw new IllegalArgumentException("Relay not found");
		*/
	}

	@Override
	public boolean existsTCP(String ip, String port){
		long key = (((long) IPfromString(ip)) << 32)
				| (port.equals("*") ? (short) 0 : (short) Integer.valueOf(port)
						.intValue());
		
		return (tcpRelays.contains(key) || tcpDisabledRelays.contains(key));
	}

	@Override
	public boolean existsUDP(String ip, String port){
		long key = (((long) IPfromString(ip)) << 32)
				| (port.equals("*") ? (short) 0 : (short) Integer.valueOf(port)
						.intValue());
		
		return (udpRelays.contains(key) || udpDisabledRelays.contains(key));
	}
	
	@Override
	public Map<IPTransportPortPair, RelayInfo> getAllActiveTCPRelays() {
		HashMap<IPTransportPortPair, RelayInfo> map = new HashMap<IPTransportPortPair, RelayInfo>(
				tcpRelays.size());
		TLongObjectIterator<RelayInfo> iterator = tcpRelays.iterator();
		while (iterator.hasNext()) {
			iterator.advance();
			map.put(new IPTransportPortPair(iterator.key()), iterator.value());
		}
		return map;
	}

	@Override
	public Map<IPTransportPortPair, RelayInfo> getAllActiveUDPRelays() {
		HashMap<IPTransportPortPair, RelayInfo> map = new HashMap<IPTransportPortPair, RelayInfo>(
				udpRelays.size());
		TLongObjectIterator<RelayInfo> iterator = udpRelays.iterator();
		while (iterator.hasNext()) {
			iterator.advance();
			map.put(new IPTransportPortPair(iterator.key()), iterator.value());
		}
		return map;
	}

	@Override
	public Map<IPTransportPortPair, RelayInfo> getAllInactiveTCPRelays() {
		HashMap<IPTransportPortPair, RelayInfo> map = new HashMap<IPTransportPortPair, RelayInfo>(
				tcpDisabledRelays.size());
		TLongObjectIterator<RelayInfo> iterator = tcpDisabledRelays.iterator();
		while (iterator.hasNext()) {
			iterator.advance();
			map.put(new IPTransportPortPair(iterator.key()), iterator.value());
		}
		return map;
	}

	@Override
	public Map<IPTransportPortPair, RelayInfo> getAllInactiveUDPRelays() {
		HashMap<IPTransportPortPair, RelayInfo> map = new HashMap<IPTransportPortPair, RelayInfo>(
				udpDisabledRelays.size());
		TLongObjectIterator<RelayInfo> iterator = udpDisabledRelays.iterator();
		while (iterator.hasNext()) {
			iterator.advance();
			map.put(new IPTransportPortPair(iterator.key()), iterator.value());
		}
		return map;
	}

	@Override
	public RelayInfo getActiveTCPRelay(IPv4Address ip, TransportPort port) {
		RelayInfo relayInfo;
		// first check active relays
		// start with fully specified ones
		relayInfo = tcpRelays
				.get((((long) ip.getInt()) << 32) | port.getPort());
		if (relayInfo != null)
			return relayInfo;

		// Check for complete wildcard
		relayInfo = tcpRelays.get(0);
		if (relayInfo != null)
			return relayInfo;

		// Check for host wildcard
		relayInfo = tcpRelays.get(port.getPort());
		if (relayInfo != null)
			return relayInfo;

		// Check for port wildcard
		relayInfo = tcpRelays.get(((long) ip.getInt()) << 32);

		return relayInfo;
	}

	@Override
	public RelayInfo getActiveUDPRelay(IPv4Address ip, TransportPort port) {
		RelayInfo relayInfo;
		// first check active relays
		// start with fully specified ones
		relayInfo = udpRelays
				.get((((long) ip.getInt()) << 32) | port.getPort());

		if (relayInfo != null)
			return relayInfo;

		// Check for complete wildcard
		relayInfo = udpRelays.get(0);
		if (relayInfo != null)
			return relayInfo;

		// Check for host wildcard
		relayInfo = udpRelays.get(port.getPort());
		if (relayInfo != null)
			return relayInfo;

		// Check for port wildcard
		relayInfo = udpRelays.get(((long) ip.getInt()) << 32);
		return relayInfo;
	}
}
