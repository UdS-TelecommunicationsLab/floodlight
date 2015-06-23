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

package net.floodlightcontroller.uds.multicast.internal;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.devicemanager.IDevice;
import net.floodlightcontroller.devicemanager.IDeviceService;
import net.floodlightcontroller.devicemanager.SwitchPort;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IGMP;
import net.floodlightcontroller.packet.IGMP.IGMPv3GroupRecord;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.uds.multicast.interfaces.IMulticastService;
import net.floodlightcontroller.uds.multicast.model.MulticastGroup;
import net.floodlightcontroller.uds.routing.internal.Routing;

import org.projectfloodlight.openflow.protocol.OFPacketIn;
import org.projectfloodlight.openflow.protocol.OFPacketOut;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.IpProtocol;
import org.projectfloodlight.openflow.types.VlanVid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MulticastManager implements IMulticastService {

	public int pingInterval = 10;
	public int clientTimeout = 60;

	public static final int MULTICAST_IGMP_IP_ADDRESS = IPv4
			.toIPv4Address("224.0.0.1");
	public final byte[] MULTICAST_IGMP_MAC_ADDRESS;

	protected static final Logger log = LoggerFactory
			.getLogger(MulticastManager.class);

	Map<IPv4Address, MulticastGroup> groups;

	protected IFloodlightProviderService floodlightProvider;
	protected IDeviceService deviceManager;
	protected IOFSwitchService switchManager;

	public MulticastManager() {
		// Starts with OF OUI
		MULTICAST_IGMP_MAC_ADDRESS = Ethernet.toMACAddress("00:26:E1:00:00:00");
		Random r = new Random();
		// Randomize remainder
		MULTICAST_IGMP_MAC_ADDRESS[3] = (byte) r.nextInt();
		MULTICAST_IGMP_MAC_ADDRESS[4] = (byte) r.nextInt();
		MULTICAST_IGMP_MAC_ADDRESS[5] = (byte) r.nextInt();

		groups = new HashMap<>();
	}

	public void init(IFloodlightProviderService floodlightProvider,
			IDeviceService deviceManager, IOFSwitchService switchManager) {
		this.floodlightProvider = floodlightProvider;
		this.deviceManager = deviceManager;
		this.switchManager = switchManager;
	}

	public void startUp(ScheduledExecutorService executor) {

		// Schedule ping and removal task
		executor.scheduleWithFixedDelay(new Runnable() {

			private List<MulticastGroup> groupsToRemove = new ArrayList<>();

			@Override
			public void run() {
				log.debug("Checking for MulticastGroup timeouts");
				synchronized (groups) {
					// Remove timed out clients
					for (MulticastGroup group : groups.values()) {
						if (group.removeTimedOutClients(clientTimeout))
							groupsToRemove.add(group);
					}
					for (MulticastGroup multicastGroup : groupsToRemove) {
						groups.remove(multicastGroup.getGroupAddress());
					}
					groupsToRemove.clear();
				}

				// ping all known clients
				for (IDevice device : deviceManager.getAllDevices()) {
					IPv4Address[] ipv4Addresses = device.getIPv4Addresses();
					VlanVid[] vlanIds = device.getVlanId();
					if (ipv4Addresses == null || ipv4Addresses.length == 0)
						continue;

					VlanVid vlanId;
					if (vlanIds == null || vlanIds.length == 0) {
						vlanId = VlanVid.ZERO;
					} else {
						vlanId = vlanIds[0];
					}

					IPv4Address clientIP = null;
					for (IPv4Address ip : ipv4Addresses) {
						if (ip.getBytes()[0] == 10)
							clientIP = ip;
					}
					if (clientIP == null) {
						// no valid IP found for this client
						continue;
					}
					
					IPv4Address srcRouterIp = clientIP.and(
							IPv4Address.of(0xffffff00)).or(IPv4Address.of(254));
					log.trace("IGMP pinging device with IP {}", clientIP);

					SwitchPort attachmentPoint = device.getAttachmentPoints()[0];
					IGMP igmpQuery = new IGMP()
							.setType(IGMP.TYPE_IGMP_V3_MEMBERSHIP_QUERY)
							.setMaxRespCode((byte) 0b01111111)
							.setGroupAddress(0)
							// General query
							.setSuppressRouterSideProcessing(false)
							.setQueriersRobustnessVariable((byte) 2)
							.setQueriersQueryIntervalCode((byte) pingInterval)
							.setNumberOfSources((short) 0)
							.setSourceAddresses(new int[0]);
					IPv4 ipPacket = (IPv4) new IPv4()
							.setSourceAddress(srcRouterIp)
							.setDestinationAddress(MULTICAST_IGMP_IP_ADDRESS)
							.setProtocol(IpProtocol.IGMP).setTtl((byte) 1)
							.setPayload(igmpQuery);
					Ethernet ethPacket = (Ethernet) new Ethernet()
							.setSourceMACAddress(MULTICAST_IGMP_MAC_ADDRESS)
							.setDestinationMACAddress(
									device.getMACAddress().getBytes())
							// MACAddress.valueOf(device.getMACAddress()).toBytes()
							.setEtherType(Ethernet.TYPE_IPv4).setVlanID(vlanId.getVlan())
							.setPayload(ipPacket);
					byte[] packet = ethPacket.serialize();
					IOFSwitch sw = switchManager.getSwitch(attachmentPoint
							.getSwitchDPID());
					OFPacketOut membershipQueryPacketOut = Routing
							.createPacketOutForPort(floodlightProvider, sw,
									attachmentPoint.getPort(), packet, log);
					try {
						sw.write(membershipQueryPacketOut);
						sw.flush();
					} catch (Exception e) {
						log.error("Error while sending IGMP Query", e);
					}
				}
			}

		}, pingInterval, pingInterval, TimeUnit.SECONDS);

	}

	public void handleMembershipReportIn(IOFSwitch sw, OFPacketIn msg,
			IGMP igmpPacket) {
		assert (igmpPacket.isIGMPv3MembershipReportMessage());
		IPv4 ipv4Packet = (IPv4) igmpPacket.getParent();
		IPv4Address clientIP = ipv4Packet.getSourceAddress();

		IGMPv3GroupRecord[] groupRecords = igmpPacket.getGroupRecords();
		for (IGMPv3GroupRecord groupRecord : groupRecords) {
			IPv4Address groupIP = groupRecord.getMulticastAddress();
			MulticastGroup group;
			synchronized (groups) {
				if (groups.containsKey(groupIP))
					group = groups.get(groupIP);
				else
					groups.put(groupIP, group = new MulticastGroup(groupIP));

				// Bring sources in order'
				List<IPv4Address> sources = Arrays.asList(groupRecord
						.getSourceAddresses());
				if (sources != null)
					Collections.sort(sources);

				boolean groupEmpty = false;
				switch (groupRecord.getRecordType()) {
				case IGMPv3GroupRecord.RECORD_TYPE_CHANGE_TO_EXCLUDE_MODE:
					log.info(
							"Device {} in group {} changed to exclude mode for sources {}",
							clientIP, groupIP, sources);
					groupEmpty = group.signalFromClient(clientIP, sources,
							false, true);
					break;
				case IGMPv3GroupRecord.RECORD_TYPE_MODE_IS_EXCLUDE:
					log.debug(
							"Device {} in group {} replied to membership query ",
							clientIP, groupIP);
					groupEmpty = group.signalFromClient(clientIP, sources,
							false, false);
					break;
				case IGMPv3GroupRecord.RECORD_TYPE_CHANGE_TO_INCLUDE_MODE:
					log.info(
							"Device {} in group {} changed to include mode for sources {}",
							clientIP, groupIP, sources);
					groupEmpty = group.signalFromClient(clientIP, sources,
							true, true);
					break;
				case IGMPv3GroupRecord.RECORD_TYPE_MODE_IS_INCLUDE:
					log.debug(
							"Device {} in group {} replied to membership query ",
							clientIP, groupIP);
					groupEmpty = group.signalFromClient(clientIP, sources,
							true, false);
					break;
				case IGMPv3GroupRecord.RECORD_TYPE_ALLOW_NEW_SOURCES:
					log.info("Device {} in group {} allows new sources {}",
							clientIP, groupIP, sources);
					try {
						groupEmpty = group.changeClientSources(clientIP,
								sources, true);
					} catch (IllegalArgumentException e) {
						log.warn(
								"Illegal command {} for client IP {} and multicast group IP {}",
								"ALLOW_NEW_SOURCES", clientIP, groupIP);
					}
					break;
				case IGMPv3GroupRecord.RECORD_TYPE_BLOCK_OLD_SOURCES:
					log.info("Device {} in group {} blocks old sources {}",
							clientIP, groupIP, sources);
					try {
						groupEmpty = group.changeClientSources(clientIP,
								sources, false);
					} catch (IllegalArgumentException e) {
						log.warn(
								"Illegal command {} for client IP {} and multicast group IP {}",
								"BLOCK_OLD_SOURCES", clientIP, groupIP);
					}
					break;
				}
				if (groupEmpty) {
					groups.remove(groupIP);
				}
			}
		}
	}

	public MulticastGroup getGroup(IPv4Address groupAddress) {
		return groups.get(groupAddress);
	}

	public int getPingInterval() {
		return pingInterval;
	}

	public void setPingInterval(int pingInterval) {
		this.pingInterval = pingInterval;
	}

	public int getClientTimeout() {
		return clientTimeout;
	}

	public void setClientTimeout(int clientTimeout) {
		this.clientTimeout = clientTimeout;
	}
}
