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

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.devicemanager.IDevice;
import net.floodlightcontroller.devicemanager.SwitchPort;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.packet.RTSP;
import net.floodlightcontroller.packet.TCP;
import net.floodlightcontroller.packet.UDP;
import net.floodlightcontroller.uds.routing.internal.Routing;
import net.floodlightcontroller.uds.routing.model.CostFunctionType;

import org.projectfloodlight.openflow.protocol.OFPacketIn;
import org.projectfloodlight.openflow.protocol.OFPacketOut;
import org.projectfloodlight.openflow.protocol.match.Match;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.types.EthType;
import org.projectfloodlight.openflow.types.IpProtocol;
import org.projectfloodlight.openflow.types.OFBufferId;
import org.projectfloodlight.openflow.types.TransportPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RTSPInspector extends PacketInspectionModuleBase {

	protected static final Logger log = LoggerFactory
			.getLogger(RTSPInspector.class);

	public boolean inspectPacket(Ethernet ethPacket, Match match,
			byte[] packetData, OFPacketIn ofPktIn, IOFSwitch srcSw) {

		if (ethPacket.getEtherType() != Ethernet.TYPE_IPv4)
			return false;

		IPv4 ipv4Packet = (IPv4) ethPacket.getPayload();
		TransportPort rtspPort = UDP.RTSP_SERVER_PORT;

		// Check if packet is actually RTSP
		if (ipv4Packet.getProtocol().equals(IpProtocol.UDP)) {
			UDP udpPacket = (UDP) ipv4Packet.getPayload();
			if (!udpPacket.getDestinationPort().equals(rtspPort)
					&& !udpPacket.getSourcePort().equals(rtspPort)) {
				return false;
			}
			log.trace("Packet is probably RTSP/UDP");
		} else if (ipv4Packet.getProtocol().equals(IpProtocol.TCP)) {
			TCP tcpPacket = (TCP) ipv4Packet.getPayload();
			if (!tcpPacket.getDestinationPort().equals(rtspPort)
					&& !tcpPacket.getSourcePort().equals(rtspPort)) {
				return false;
			}
			log.trace("Packet is probably RTSP/TCP");
		} else {
			return false;
		}

		SwitchPort dstAttachmentPoint, srcAttachmentPoint;
		IOFSwitch dstSwitch, srcSwitch;

		Iterator<? extends IDevice> devices = deviceManager.queryDevices(match
				.get(MatchField.ETH_DST), EthType.VLAN_FRAME.equals(EthType
				.of(ethPacket.getEtherType())) ? match.get(MatchField.VLAN_VID)
				.getVlanVid() : null, match.get(MatchField.IPV4_DST), null,
				null);
		if (!devices.hasNext()) {
			// device not yet known
			log.trace("Destination device is unknown");
			return false;
		}
		dstAttachmentPoint = routing
				.findSuitableAttachmentPoint(devices.next());
		if (dstAttachmentPoint == null) {
			// device not yet known
			log.trace("Destination device has no suitable attachment point");
			return false;
		}

		srcAttachmentPoint = new SwitchPort(srcSw.getId(), ofPktIn.getInPort());

		dstSwitch = switchManager.getSwitch(dstAttachmentPoint.getSwitchDPID());
		srcSwitch = srcSw;

		// if this is actually an RTSP packet, we may be able to extract
		// useful information
		if (ipv4Packet.getPayload().getPayload() instanceof RTSP) {
			RTSP rtspPacket = (RTSP) ipv4Packet.getPayload().getPayload();
			log.trace("RTSP packet: {}", rtspPacket.toString());
			// if this is a SETUP response packet, set up a low-delay
			// flow for the media
			if (!rtspPacket.isRequest()
					&& rtspPacket.getHeaderParameters()
							.containsKey("Transport")) {

				// Parse the transport parameters in a way such that we
				// can parse them
				String transportParamsString = rtspPacket.getHeaderParameters()
						.get("Transport");
				String[] transportParamsStrings = transportParamsString
						.split(";");
				Map<String, String> transportParams = new HashMap<String, String>(
						transportParamsStrings.length);
				for (String param : transportParamsStrings) {
					if (param.contains("=")) {
						String[] split = param.split("=");
						transportParams.put(split[0], split[1]);
					} else {
						transportParams.put(param, null);
					}
				}

				// Extract whether TCP or UDP is used
				boolean isTCP = transportParams.containsKey("RTP/AVP/TCP");

				// extract ports
				String[] clientPorts = transportParams.get("client_port")
						.split("-");
				String[] serverPorts = transportParams.get("server_port")
						.split("-");
				TransportPort clientRTPPort = TransportPort.of(Integer
						.valueOf(clientPorts[0]));
				TransportPort clientRTCPPort = TransportPort.of(Integer
						.valueOf(clientPorts[1]));
				TransportPort serverRTPPort = TransportPort.of(Integer
						.valueOf(serverPorts[0]));
				TransportPort serverRTCPPort = TransportPort.of(Integer
						.valueOf(serverPorts[1]));

				Match.Builder mb = match.createBuilder();
				mb.wildcard(MatchField.IN_PORT)
						.wildcard(MatchField.IP_DSCP)
						.setExact(MatchField.IP_PROTO,
								isTCP ? IpProtocol.TCP : IpProtocol.UDP)
						.wildcard(MatchField.TCP_SRC);

				Match cltToSrvMatch = mb
						.setExact(MatchField.ETH_DST,
								match.get(MatchField.ETH_SRC))
						.setExact(MatchField.ETH_SRC,
								match.get(MatchField.ETH_DST))
						.setExact(MatchField.IPV4_DST,
								match.get(MatchField.IPV4_SRC))
						.setExact(MatchField.IPV4_SRC,
								match.get(MatchField.IPV4_DST)).build();
				Match srvToCltMatch = mb
						.setExact(MatchField.ETH_DST,
								match.get(MatchField.ETH_DST))
						.setExact(MatchField.ETH_SRC,
								match.get(MatchField.ETH_SRC))
						.setExact(MatchField.IPV4_DST,
								match.get(MatchField.IPV4_DST))
						.setExact(MatchField.IPV4_SRC,
								match.get(MatchField.IPV4_SRC)).build();

				// Prepare mock packet-in messages
				OFPacketIn.Builder pib = ofPktIn.createBuilder()
						.setBufferId(OFBufferId.NO_BUFFER).setData(new byte[0]);
				pib.setInPort(dstAttachmentPoint.getPort());
				OFPacketIn piCltToSrv = pib.build();
				pib.setInPort(srcAttachmentPoint.getPort());
				OFPacketIn piSrvToClt = pib.build();

				log.debug("Installing RTP flows");
				// set RTP flows
				routing.doUnicastRouting(
						dstSwitch,
						piCltToSrv,
						srcAttachmentPoint,
						cltToSrvMatch.createBuilder()
								.setExact(MatchField.TCP_DST, serverRTPPort)
								.build(), CostFunctionType.LOW_DELAY, true,
						"RTP flow cli->srv due to RTSP setup response "
								+ rtspPacket, false, null);
				routing.doUnicastRouting(
						srcSwitch,
						piSrvToClt,
						dstAttachmentPoint,
						srvToCltMatch.createBuilder()
								.setExact(MatchField.TCP_DST, clientRTPPort)
								.build(), CostFunctionType.LOW_DELAY, true,
						"RTP flow srv->cli due to RTSP setup response "
								+ rtspPacket, false, null);

				// set RTCP flows
				routing.doUnicastRouting(
						dstSwitch,
						piCltToSrv,
						srcAttachmentPoint,
						cltToSrvMatch.createBuilder()
								.setExact(MatchField.TCP_DST, serverRTCPPort)
								.build(), CostFunctionType.LOW_DELAY, true,
						"RTCP flow cli->srv due to RTSP setup response "
								+ rtspPacket, false, null);
				routing.doUnicastRouting(
						srcSwitch,
						piSrvToClt,
						dstAttachmentPoint,
						srvToCltMatch.createBuilder()
								.setExact(MatchField.TCP_DST, clientRTCPPort)
								.build(), CostFunctionType.LOW_DELAY, true,
						"RTCP flow srv->cli due to RTSP setup response "
								+ rtspPacket, false, null);
				log.debug("Finished installing RTP flows");
			}
		} else {
			log.trace("Packet is not an RTSP setup packet, so maybe a TCP connection establishment packet? Outputting to client...");
		}

		OFPacketOut packetOut = Routing.createPacketOutForPort(
				floodlightProvider, dstSwitch, dstAttachmentPoint.getPort(),
				packetData, log);

		dstSwitch.write(packetOut);
		dstSwitch.flush();

		return true;

	}

	@Override
	public String getName() {
		return "UdS RTSP Inspector";
	}
}
