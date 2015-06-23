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

package net.floodlightcontroller.uds.routing.internal;

import java.util.Iterator;

import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.devicemanager.IDevice;
import net.floodlightcontroller.devicemanager.IDeviceService;
import net.floodlightcontroller.packet.ARP;
import net.floodlightcontroller.packet.Ethernet;

import org.projectfloodlight.openflow.protocol.OFPacketIn;
import org.projectfloodlight.openflow.protocol.match.Match;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.types.EthType;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.MacAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DelayedRoutingThread extends Thread {

	public static final MacAddress SENDER_MAC_ADDRESS = MacAddress
			.of("02:0f:10:0d:11:7e");
	public static final int SECONDS_TO_WAIT = 5;

	protected static final Logger log = LoggerFactory
			.getLogger(DelayedRoutingThread.class);

	private IOFSwitch srcSwitch;
	private OFPacketIn msg;
	private Match match;
	private Ethernet ethOriginal;

	private IPv4Address targetIP;
	private Routing routing;
	private IDeviceService deviceManager;

	public DelayedRoutingThread(IOFSwitch srcSwitch, OFPacketIn msg,
			IPv4Address targetIP, Routing routing,
			IDeviceService deviceManager, Ethernet ethOriginal, Match match) {
		
		if (targetIP == null)
			throw new NullPointerException("Cannot wait for IP null");

		this.srcSwitch = srcSwitch;
		this.msg = msg;
		this.targetIP = targetIP;
		this.deviceManager = deviceManager;
		this.routing = routing;
		this.match = match;

		routing.getDelayedRoutingMap().put(targetIP, this);
	}

	@Override
	public void run() {
		log.debug("Trying to find client for IP {}", targetIP);
		// First off, construct and flood an ARP request for that client
		ARP arp = new ARP();
		arp.setHardwareType(ARP.HW_TYPE_ETHERNET)
				.setProtocolType(ARP.PROTO_TYPE_IP)
				.setHardwareAddressLength((byte) 6)
				.setProtocolAddressLength((byte) 4).setOpCode(ARP.OP_REQUEST)
				.setSenderHardwareAddress(SENDER_MAC_ADDRESS.getBytes())
				.setSenderProtocolAddress(targetIP.getInt() & 0xffffff00 | 254)
				.setTargetHardwareAddress(new byte[6])
				.setTargetProtocolAddress(targetIP.getBytes());
		Ethernet eth = new Ethernet()
				.setDestinationMACAddress(MacAddress.BROADCAST)
				.setSourceMACAddress(SENDER_MAC_ADDRESS.getBytes())
				.setEtherType(Ethernet.TYPE_ARP);
		eth.setPayload(arp);
		log.debug("Broadcasting ARP packet to find client");
		routing.doControllerBroadcast(eth.serialize());

		// Then wait for an answer
		try {
			sleep(SECONDS_TO_WAIT * 1000);
			// send ICMP fail if nothing comes
			log.trace("ARP discovery timed out. Sending ICMP Host Unreachable.");
			routing.sendICMPHostUnreachable(srcSwitch, msg, match, ethOriginal);
		} catch (InterruptedException e) {
			log.trace("Got revived by incoming ARP packet. Sending out own payload");
			// if one comes, we are interrupted and can deliver the original
			// packet
			// So it is a unicast packet. Try to find recipient
			Iterator<? extends IDevice> targetClientIt = deviceManager
					.queryDevices(
							match.get(MatchField.ETH_DST),
							match.get(MatchField.ETH_TYPE).equals(
									EthType.VLAN_FRAME) ? match.get(
									MatchField.VLAN_VID).getVlanVid() : null,
							match.get(MatchField.IPV4_DST), null, null);
			if (targetClientIt.hasNext()) {
				// Give this packet back into the processing pipeline
				srcSwitch.write(msg);
			} else {
				log.warn("Target client allegedly there but actually isn't");
			}
		} finally {
			synchronized (routing.getDelayedRoutingMap()) {
				// oh and don't forget to remove us from this map
				routing.getDelayedRoutingMap().remove(targetIP, this);
			}
		}
	}

}
