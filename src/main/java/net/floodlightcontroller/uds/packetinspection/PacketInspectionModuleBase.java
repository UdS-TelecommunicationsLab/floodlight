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

package net.floodlightcontroller.uds.packetinspection;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.devicemanager.IDeviceService;
import net.floodlightcontroller.packet.ARP;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.packet.TCP;
import net.floodlightcontroller.packet.UDP;
import net.floodlightcontroller.uds.routing.interfaces.IRoutingService;

import org.projectfloodlight.openflow.protocol.OFFactories;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFPacketIn;
import org.projectfloodlight.openflow.protocol.OFType;
import org.projectfloodlight.openflow.protocol.OFVersion;
import org.projectfloodlight.openflow.protocol.match.Match;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.types.ArpOpcode;
import org.projectfloodlight.openflow.types.EthType;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.IpProtocol;
import org.projectfloodlight.openflow.types.OFVlanVidMatch;

public abstract class PacketInspectionModuleBase implements IFloodlightModule,
		IOFMessageListener {

	IFloodlightProviderService floodlightProvider;
	IDeviceService deviceManager;
	IOFSwitchService switchManager;
	// IRestApiService restApi;

	IRoutingService routing;

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleServices() {
		return Collections.emptyList();
	}

	@Override
	public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
		return Collections.emptyMap();
	}

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
		ArrayList<Class<? extends IFloodlightService>> deps = new ArrayList<Class<? extends IFloodlightService>>();
		deps.add(IFloodlightProviderService.class);
		deps.add(IDeviceService.class);
		deps.add(IOFSwitchService.class);
		// deps.add(IRestApiService.class);
		deps.add(IRoutingService.class);
		return deps;
	}

	@Override
	public void init(FloodlightModuleContext context)
			throws FloodlightModuleException {
		floodlightProvider = context
				.getServiceImpl(IFloodlightProviderService.class);
		deviceManager = context.getServiceImpl(IDeviceService.class);
		switchManager = context.getServiceImpl(IOFSwitchService.class);
		routing = context.getServiceImpl(IRoutingService.class);
	}

	@Override
	public void startUp(FloodlightModuleContext context)
			throws FloodlightModuleException {
		floodlightProvider.addOFMessageListener(OFType.PACKET_IN, this);
	}

	@Override
	abstract public String getName();

	abstract public boolean inspectPacket(Ethernet ethPacket, Match match,
			byte[] packetData, OFPacketIn ofPktIn, IOFSwitch sw);

	@Override
	public boolean isCallbackOrderingPrereq(OFType type, String name) {
		return name.equals("UdS Multicast");
	}

	@Override
	public boolean isCallbackOrderingPostreq(OFType type, String name) {
		return name.equals("UdS Routing");
	}

	@Override
	public net.floodlightcontroller.core.IListener.Command receive(
			IOFSwitch sw, OFMessage msg, FloodlightContext cntx) {
		switch (msg.getType()) {
		case PACKET_IN:
			OFPacketIn ofPktIn = (OFPacketIn) msg;
			// Check if this is a packet that has to be handled specially
			Ethernet ethPacket = IFloodlightProviderService.bcStore.get(cntx,
					IFloodlightProviderService.CONTEXT_PI_PAYLOAD);
			Match match = extractMatch(ofPktIn, ethPacket); // ofPktIn.getMatch()
															// only supported
															// from OF1.2 on
			if (inspectPacket(ethPacket, match, ofPktIn.getData(), ofPktIn, sw)) {
				return Command.STOP;
			} else {
				return Command.CONTINUE;
			}
		default:
			return Command.CONTINUE;
		}
	}

	public static Match extractMatch(OFPacketIn in, Ethernet eth) {
		if (!in.getVersion().equals(OFVersion.OF_10))
			return in.getMatch();

		Match.Builder mb = OFFactories.getFactory(OFVersion.OF_10).buildMatch();

		mb.setExact(MatchField.IN_PORT, in.getInPort());

		// If it is not an ethernet packet return now
		if (eth == null)
			return mb.build();

		mb.setExact(MatchField.ETH_SRC, eth.getSourceMACAddress())
				.setExact(MatchField.ETH_DST, eth.getDestinationMACAddress())
				.setExact(MatchField.ETH_TYPE, EthType.of(eth.getEtherType()));

		if (EthType.of(eth.getEtherType()).equals(EthType.VLAN_FRAME)) {
			mb.setExact(MatchField.VLAN_VID,
					OFVlanVidMatch.ofVlanOF10(eth.getVlanID()));
		}

		if (eth.getPayload() instanceof ARP) {
			ARP arpPacket = (ARP) eth.getPayload();
			mb.setExact(MatchField.ARP_SPA,
					IPv4Address.of(arpPacket.getSenderProtocolAddress()))
					.setExact(
							MatchField.ARP_TPA,
							IPv4Address.of(arpPacket.getTargetProtocolAddress()))
					.setExact(MatchField.ARP_OP,
							ArpOpcode.of(arpPacket.getOpCode()));
			return mb.build();
		}

		// If the payload is unparsable for Matches
		if (!(eth.getPayload() instanceof IPv4))
			return mb.build();

		// So it is an ipv4 packet
		IPv4 ipPacket = (IPv4) eth.getPayload();
		mb.setExact(MatchField.IP_PROTO, ipPacket.getProtocol())
				.setExact(MatchField.IPV4_SRC, ipPacket.getSourceAddress())
				.setExact(MatchField.IPV4_DST, ipPacket.getDestinationAddress());
		// .setExact(MatchField.IP_DSCP, IpDscp.of((byte)
		// (ipPacket.getDiffServ() & 0x3f)))

		// Transport protocol
		if (ipPacket.getProtocol().equals(IpProtocol.TCP)) {
			TCP tcpPacket = (TCP) ipPacket.getPayload();
			mb.setExact(MatchField.TCP_SRC, tcpPacket.getSourcePort());
			mb.setExact(MatchField.TCP_DST, tcpPacket.getDestinationPort());
		} else if (ipPacket.getProtocol().equals(IpProtocol.UDP)) {
			UDP udpPacket = (UDP) ipPacket.getPayload();
			mb.setExact(MatchField.UDP_SRC, udpPacket.getSourcePort());
			mb.setExact(MatchField.UDP_DST, udpPacket.getDestinationPort());
			// } else if (ipPacket.getProtocol().equals(IpProtocol.ICMP)) {
			// ICMP icmpPacket = (ICMP) ipPacket.getPayload();
			// mb.setExact(MatchField.ICMPV4_TYPE,
			// ICMPv4Type.of(icmpPacket.getIcmpType()));
			// mb.setExact(MatchField.ICMPV4_CODE,
			// ICMPv4Code.of(icmpPacket.getIcmpCode()));
		} // else do nothing. This is not accurate as SCTP might be supported,
			// but it isn't used anyways

		return mb.build();
	}

}
