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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.Set;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.IListener.Command;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.devicemanager.IDevice;
import net.floodlightcontroller.devicemanager.IDeviceService;
import net.floodlightcontroller.devicemanager.SwitchPort;
import net.floodlightcontroller.linkdiscovery.ILinkDiscoveryService;
import net.floodlightcontroller.packet.ARP;
import net.floodlightcontroller.packet.Data;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.ICMP;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.packet.TCP;
import net.floodlightcontroller.packet.UDP;
import net.floodlightcontroller.routing.Link;
import net.floodlightcontroller.uds.flow.interfaces.IFlowService;
import net.floodlightcontroller.uds.multicast.interfaces.IMulticastService;
import net.floodlightcontroller.uds.multicast.model.MulticastGroup;
import net.floodlightcontroller.uds.packetinspection.PacketInspectionModuleBase;
import net.floodlightcontroller.uds.relaying.interfaces.IRelayService;
import net.floodlightcontroller.uds.relaying.model.RelayInfo;
import net.floodlightcontroller.uds.routing.interfaces.IConnectionGraphCostFunction;
import net.floodlightcontroller.uds.routing.interfaces.IRoutingService;
import net.floodlightcontroller.uds.routing.interfaces.IUnicastPathAlgorithm;
import net.floodlightcontroller.uds.routing.model.CostFunctionType;
import net.floodlightcontroller.uds.routing.model.SwitchPair;
import net.floodlightcontroller.uds.routing.internal.DelayedRoutingThread;

import org.projectfloodlight.openflow.protocol.OFFactories;
import org.projectfloodlight.openflow.protocol.OFFlowAdd;
import org.projectfloodlight.openflow.protocol.OFFlowMod;
import org.projectfloodlight.openflow.protocol.OFFlowModFlags;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFPacketIn;
import org.projectfloodlight.openflow.protocol.OFPacketOut;
import org.projectfloodlight.openflow.protocol.OFPortDesc;
import org.projectfloodlight.openflow.protocol.OFVersion;
import org.projectfloodlight.openflow.types.EthType;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.IpProtocol;
import org.projectfloodlight.openflow.types.MacAddress;
import org.projectfloodlight.openflow.types.OFBufferId;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.TransportPort;
import org.projectfloodlight.openflow.types.U64;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.protocol.action.OFActions;
import org.projectfloodlight.openflow.protocol.match.Match;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.util.HexString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;

// TODO: Auto-generated Javadoc
/**
 * The Class Routing.
 */
public class Routing implements IRoutingService {

	/** The Constant log. */
	protected static final Logger log = LoggerFactory.getLogger(Routing.class);

	/** The device manager. */
	protected IDeviceService deviceManager;

	/** The link manager. */
	protected ILinkDiscoveryService linkManager;

	/** The floodlight provider. */
	protected IFloodlightProviderService floodlightProvider;
	protected IOFSwitchService switchManager;

	/** The sssp algo. */
	protected IUnicastPathAlgorithm ssspAlgo;

	/** The flow manager. */
	protected IFlowService flowManager;

	/** The relay manager. */
	protected IRelayService relayManager;

	/** The multicast manager. */
	protected IMulticastService multicastManager;

	/** The route cache. */
	protected Map<CostFunctionType, Map<SwitchPair, List<Link>>> routeCache;

	protected Map<CostFunctionType, IConnectionGraphCostFunction> costFunctions;
	protected CostFunctionType defaultMetric;

	/** */
	protected Multimap<IPv4Address, DelayedRoutingThread> delayedRoutingMap;

	public Routing() {
		// Prepare cost functions
		IConnectionGraphCostFunction constantCostFunction = new IConnectionGraphCostFunction() {
			@Override
			public double getCost(Link link) {
				return 1.0;
			}
		};
		this.costFunctions = new HashMap<>(CostFunctionType.values().length);
		costFunctions.put(CostFunctionType.CONSTANT, constantCostFunction);
		defaultMetric = CostFunctionType.CONSTANT;

		delayedRoutingMap = HashMultimap.create();

		// Prepare route cache
		routeCache = new HashMap<>();
		for (CostFunctionType tos : CostFunctionType.values()) {
			routeCache.put(tos, new HashMap<SwitchPair, List<Link>>());
		}
	}

	/**
	 * Instantiates a new routing.
	 *
	 * @param deviceManager
	 *            the device manager
	 * @param linkManager
	 *            the link manager
	 * @param floodlightProvider
	 *            the floodlight provider
	 * @param counterStore
	 *            the counter store
	 * @param flowManager
	 *            the flow manager
	 * @param relayManager
	 *            the relay manager
	 * @param ofControl
	 *            the of control
	 * @param multicastManager
	 *            the multicast manager
	 */
	public void init(IDeviceService deviceManager,
			ILinkDiscoveryService linkManager,
			IFloodlightProviderService floodlightProvider,
			IFlowService flowManager, IRelayService relayManager,
			IMulticastService multicastManager, IOFSwitchService switchManager) {
		this.deviceManager = deviceManager;
		this.linkManager = linkManager;
		this.floodlightProvider = floodlightProvider;
		this.flowManager = flowManager;
		this.relayManager = relayManager;
		this.multicastManager = multicastManager;
		this.switchManager = switchManager;
	}

	@Override
	public void addConnectionGraphCostFunction(CostFunctionType tos,
			IConnectionGraphCostFunction function) {
		log.info("Cost function for {} was registered", tos);
		this.costFunctions.put(tos, function);
	}

	/**
	 * Gets the sssp algo.
	 *
	 * @return the sssp algo
	 */
	public IUnicastPathAlgorithm getSsspAlgo() {
		return ssspAlgo;
	}

	public Command handlePacketIn(IOFSwitch sw, OFPacketIn msg,
			FloodlightContext cntx) {
		log.debug("PACKET IN at switch " + sw.getId().toString() + ", port "
				+ msg.getInPort() + ", msg:" + msg.toString());
		RelayInfo relayToUse = null;

		// Check if this is a packet that has to be handled specially
		Ethernet ethPacket = IFloodlightProviderService.bcStore.get(cntx,
				IFloodlightProviderService.CONTEXT_PI_PAYLOAD);

		// Give packet to routing module for processing
		Match match = PacketInspectionModuleBase.extractMatch(msg, ethPacket);// msg.getMatch();
		log.trace("Match: " + match.toString());

		if (ethPacket.getPayload() instanceof IPv4) {
			IPv4 ipv4Packet = (IPv4) ethPacket.getPayload();

			// Relay handlers
			if (relayManager.isUDPRelayingEnabled()
					&& ipv4Packet.getPayload() instanceof UDP) {
				UDP udpPacket = (UDP) ipv4Packet.getPayload();
				relayToUse = relayManager.getActiveUDPRelay(
						ipv4Packet.getDestinationAddress(),
						udpPacket.getDestinationPort());
			} else if (relayManager.isTCPRelayingEnabled()
					&& ipv4Packet.getPayload() instanceof TCP) {
				TCP tcpPacket = (TCP) ipv4Packet.getPayload();
				relayToUse = relayManager.getActiveTCPRelay(
						ipv4Packet.getDestinationAddress(),
						tcpPacket.getDestinationPort());
			}
		}

		if (relayToUse != null) {
			log.debug("Routing via relay");
			doRelayedRouting(sw, msg, match, ethPacket, relayToUse);
		} else {
			doRouting(sw, msg, match, ethPacket);
		}
		return Command.STOP;
	}

	/**
	 * Do routing.
	 *
	 * @param sw
	 *            the sw
	 * @param msg
	 *            the msg
	 * @param match
	 *            the match
	 * @param eth
	 *            the eth
	 */
	public void doRouting(IOFSwitch sw, OFPacketIn msg, Match match,
			Ethernet eth) {
		// Check if packet is coming in on a switch port.
		Set<Link> switchLinks = linkManager.getSwitchLinks().get(sw.getId());
		if (switchLinks != null) {
			for (Link link : switchLinks) {
				if (link.getSrc().equals(sw.getId())
						&& link.getSrcPort().equals(msg.getInPort())) {
					log.warn("Message came in on switch link! Dropping.");
					return;
				}
			}
		}

		MacAddress dstMac = match.get(MatchField.ETH_DST);

		// Check for broadcast / multicast
		if (dstMac.isBroadcast()) {
			log.debug("Broadcast packet found.");
			doControllerBroadcast(sw, msg);
			return;
		}

		if (dstMac.isMulticast()) {
			// We do not care about non-IP multicast
			if (eth.getPayload() instanceof IPv4)
				doMulticastRouting(sw, msg, match, (IPv4) eth.getPayload(),
						"Multicast Route for " + generateDescription(eth));
			else
				log.debug("Throwing away non-IPv4 multicast packet.");
			return;
		}

		// Check for ARP Reply to request we sent
		if (eth.getPayload() instanceof ARP) {
			ARP arp = (ARP) eth.getPayload();
			if (eth.getDestinationMACAddress().equals(
					DelayedRoutingThread.SENDER_MAC_ADDRESS)) {
				IPv4Address ip = IPv4Address.of(arp.getSenderProtocolAddress());
				log.debug(
						"Found client for IP {} after ARP search, reviving waiting threads",
						ip);
				synchronized (delayedRoutingMap) {
					if (delayedRoutingMap.containsKey(ip)) {
						for (DelayedRoutingThread thread : delayedRoutingMap
								.get(ip)) {
							thread.interrupt();
						}
					}
				}
				return;
			}
		}

		// So it is a unicast packet. Try to find recipient
		Iterator<? extends IDevice> targetClientIt = deviceManager
				.queryDevices(
						dstMac,
						match.isExact(MatchField.VLAN_VID) ? match.get(
								MatchField.VLAN_VID).getVlanVid() : null,
						match.get(MatchField.IPV4_DST), null, null);
		IDevice targetClient = null;
		if (targetClientIt.hasNext()) {
			targetClient = targetClientIt.next();
		}

		// Check if we should also install reverse flow
		boolean installReverseFlow;
		installReverseFlow = eth.getPayload() instanceof IPv4
				&& eth.getPayload().getPayload() instanceof TCP;

		doUnicastRouting(sw, msg, findSuitableAttachmentPoint(targetClient),
				match, getTosFromEthernet(eth), false, "Unicast Route for "
						+ generateDescription(eth), installReverseFlow, eth);
	}

	/**
	 * Do multicast routing.
	 *
	 * @param srcSwitch
	 *            the src switch
	 * @param msg
	 *            the msg
	 * @param match
	 *            the match
	 * @param ipPacket
	 *            the ip packet
	 * @param description
	 *            the description
	 */
	private void doMulticastRouting(IOFSwitch srcSwitch, OFPacketIn msg,
			Match match, IPv4 ipPacket, String description) {
		log.debug("Multicast routing the packet");
		MulticastGroup group = multicastManager.getGroup(ipPacket
				.getDestinationAddress());

		if (group == null) {
			// Noone is interested in this packet.
			log.debug("Nobody is ready to receive this packet. Ignoring packet.");
			return;
		}

		// obtain interested targets (in group and source ip not blocked)
		IPv4Address[] targetIPs = group.getInterestedTargets(ipPacket
				.getSourceAddress());

		// Check for empty group
		if (targetIPs == null || targetIPs.length == 0) {
			log.debug("Nobody is ready to receive this packet. Ignoring packet.");
			return;
		}

		ArrayList<List<Link>> paths = new ArrayList<>(targetIPs.length);
		Multimap<DatapathId, SwitchPort> targetSwitchPorts = HashMultimap
				.create();

		// compute paths to all clients
		for (IPv4Address targetIP : targetIPs) {
			Iterator<? extends IDevice> deviceIterator = deviceManager
					.queryDevices(null, null, targetIP, null, null);
			if (!deviceIterator.hasNext()) {
				// Ignore this client as it is not a known device
				continue;
			}
			IDevice device = deviceIterator.next();
			SwitchPort attachmentPoint = findSuitableAttachmentPoint(device);
			IOFSwitch sw = switchManager.getSwitch(attachmentPoint
					.getSwitchDPID());
			List<Link> route = acquireOptimalRoute(srcSwitch, msg,
					getTosFromEthernet((Ethernet) ipPacket.getParent()), sw);
			if (route == null) {
				// If we can not find a route, we ignore it
				continue;
			}

			paths.add(route);
			targetSwitchPorts.put(sw.getId(), attachmentPoint);
		}

		// remove unnecessary links
		// this registers which links already exist
		Multimap<DatapathId, DatapathId> linkTargets = HashMultimap.create();

		// these are used for route calculation later
		HashMap<DatapathId, Link> incomingLinks = new HashMap<DatapathId, Link>();
		Multimap<DatapathId, Link> outgoingLinks = HashMultimap.create();
		for (List<Link> path : paths) {
			for (Link link : path) {
				// Returns true if link is new
				if (linkTargets.put(link.getSrc(), link.getDst())) {
					incomingLinks.put(link.getDst(), link);
					outgoingLinks.put(link.getSrc(), link);
				}
			}
		}

		// now iterate over all switches and install the flows there
		// This ensures BFS
		LinkedList<DatapathId> nextHops = new LinkedList<>();

		// add incoming link to first hop
		incomingLinks.put(srcSwitch.getId(),
				new Link(DatapathId.of(-1), OFPort.of(-1), srcSwitch.getId(),
						msg.getInPort()));
		nextHops.add(srcSwitch.getId());

		// FlowManager stuff
		U64 cookie = flowManager.getNewRandomCookie();
		List<IOFSwitch> switches = new ArrayList<>(incomingLinks.size() + 1);
		List<OFFlowMod> flowMods = new ArrayList<>(incomingLinks.size() + 1);
		OFFlowMod computedFlowMod;

		// State variables
		DatapathId currentSwitchDPID;
		IOFSwitch currentSwitch;
		OFPort currentInPort;

		// PacketOut msgs for later...
		HashMultimap<IOFSwitch, OFPacketOut> packetOutMessages = HashMultimap
				.create();

		while (!nextHops.isEmpty()) {

			currentSwitchDPID = nextHops.removeFirst(); // nextHops.removeAt(0);
			currentSwitch = switchManager.getSwitch(currentSwitchDPID);
			currentInPort = incomingLinks.get(currentSwitchDPID).getDstPort();
			switches.add(currentSwitch);

			Collection<Link> currentOutgoingLinks = outgoingLinks
					.get(currentSwitchDPID);
			List<OFAction> outputActions = new ArrayList<>(
					currentOutgoingLinks.size() + 16);

			// Add output actions for next switches
			for (Link link : currentOutgoingLinks) {
				nextHops.add(link.getDst());
				outputActions.add(currentSwitch.getOFFactory().actions()
						.output(link.getSrcPort(), 0));
			}

			// Add output actions for attached client(s, if any)
			for (SwitchPort switchPort : targetSwitchPorts
					.get(currentSwitchDPID)) {
				outputActions.add(currentSwitch.getOFFactory().actions()
						.output(switchPort.getPort(), 0));
				packetOutMessages.put(
						currentSwitch,
						createPacketOutForPort(floodlightProvider,
								currentSwitch, switchPort.getPort(),
								msg.getData(), log));
			}

			// Set flow

			computedFlowMod = setFlowOnSwitch(currentSwitch, currentInPort,
					match, OFBufferId.NO_BUFFER, cookie, outputActions, true,
					null); // OFPacketOut.BUFFER_ID_NONE
			if (computedFlowMod == null) {
				doControllerBroadcast(srcSwitch, msg);
				return;
			}
			flowMods.add(computedFlowMod);
		}

		// Finally send packet to all recipients
		for (Entry<IOFSwitch, Collection<OFPacketOut>> entry : packetOutMessages
				.asMap().entrySet()) {
			ArrayList<OFMessage> packetOuts = new ArrayList<OFMessage>(
					entry.getValue());
			// nextHop is here the last hop (hop at which the target client
			// is connected)
			entry.getKey().write(packetOuts);
			entry.getKey().flush();
		}

		flowManager.addFlow(switches, new ArrayList<>(outgoingLinks.values()),
				flowMods, cookie, match, "");
	}

	/**
	 * Do unicast routing.
	 *
	 * @param srcSwitch
	 *            the src switch
	 * @param msg
	 *            the msg
	 * @param attachmentPoint
	 *            the attachment point
	 * @param match
	 *            the match
	 * @param tos
	 *            the tos
	 * @param doNotSendPacket
	 *            the do not send packet
	 * @param description
	 *            the description
	 */
	public void doUnicastRouting(IOFSwitch srcSwitch, OFPacketIn msg,
			SwitchPort attachmentPoint, Match match, CostFunctionType tos,
			boolean doNotSendPacket, String description,
			boolean buildReverseFlow, Ethernet eth) {
		// TODO optimize the reverse flow sending
		log.trace("Unicast target client attachment point: " + attachmentPoint);
		if (attachmentPoint == null) {
			// Only consider sending ARP if we actually want to send a packet
			if (doNotSendPacket)
				return;

			// Only do delayed routing with IP packets
			if (!(eth.getPayload() instanceof IPv4)) {
				// We can't do the ARP method with Non-IP packets
				doControllerBroadcast(msg.getData());
				return;
			}
			log.debug("Unicast client unknown. Sending ARP packet to try delayed routing");
			// As we do not know the target, we have to do stuff
			new DelayedRoutingThread(srcSwitch, msg,
					match.get(MatchField.IPV4_DST), this, deviceManager, eth,
					match).start();
			return;
		}

		// Extract target switch information
		IOFSwitch dstSwitch = switchManager.getSwitch(attachmentPoint
				.getSwitchDPID());
		log.trace("Target client attached to {}", attachmentPoint);

		// optimize arp packets by not creating a flow, as there are not usually
		// many of them in a flow
		if (eth.getPayload() instanceof ARP) {
			log.debug("Unicast arp packet detected. Delivering instantaneously.");
			OFPacketOut packetOut = createPacketOutForPort(floodlightProvider,
					dstSwitch, attachmentPoint.getPort(), eth.serialize(), log);
			dstSwitch.write(packetOut);
			return;
		}

		// If no unicast route algorithm exists broadcast packet
		if (ssspAlgo == null) {
			log.warn("No SSSP algo installed!");
			doControllerBroadcast(srcSwitch, msg);
			return;
		}

		// Check if we are to build the reverse flow.
		Match reverseMatch = null;
		if (buildReverseFlow) {
			reverseMatch = match
					.createBuilder()
					.setExact(MatchField.ETH_SRC, match.get(MatchField.ETH_DST))
					.setExact(MatchField.ETH_DST, match.get(MatchField.ETH_SRC))
					.setExact(MatchField.IPV4_SRC,
							match.get(MatchField.IPV4_DST))
					.setExact(MatchField.IPV4_DST,
							match.get(MatchField.IPV4_SRC))
					.setExact(MatchField.TCP_SRC, match.get(MatchField.TCP_DST))
					.setExact(MatchField.TCP_DST, match.get(MatchField.TCP_SRC))
					.build();
		}

		OFFlowMod computedFlowMod;

		LinkedList<IOFSwitch> switches = new LinkedList<>();
		LinkedList<OFFlowMod> flowMods = new LinkedList<>();
		LinkedList<OFMessage> flowModsToSend = new LinkedList<>();
		List<Link> route;
		U64 cookie = flowManager.getNewRandomCookie();
		log.trace("Cookie for this transaction is {}",
				HexString.toHexString(cookie.getValue()));

		// Check if clients are on same switch
		if (attachmentPoint.getSwitchDPID() == srcSwitch.getId()) {
			// Case: Clients are on same switch
			log.trace("Clients on same switch");
			computedFlowMod = setFlowOnSwitch(srcSwitch, msg.getInPort(),
					attachmentPoint.getPort(), match, msg.getBufferId(),
					cookie, null, !buildReverseFlow, null);
			if (computedFlowMod == null) {
				log.warn("Error computing FlowMod");
				doControllerBroadcast(srcSwitch, msg);
				return;
			}
			if (buildReverseFlow) {
				OFFlowMod reserveFlowMod = setFlowOnSwitch(
						srcSwitch,
						attachmentPoint.getPort(),
						msg.getInPort(),
						reverseMatch
								.createBuilder()
								.setExact(MatchField.IN_PORT,
										attachmentPoint.getPort()).build(),
						OFBufferId.NO_BUFFER, cookie, null, false, null);
				flowModsToSend.add(computedFlowMod);
				flowModsToSend.add(reserveFlowMod);
				srcSwitch.write(flowModsToSend);
				srcSwitch.flush();
				flowMods.add(reserveFlowMod);
				flowModsToSend.clear();
			}
			switches.add(srcSwitch);
			flowMods.add(computedFlowMod);
			route = Collections.emptyList();
		} else {
			// Case: More than one switch between clients
			log.trace("Clients on different switches.");
			route = acquireOptimalRoute(srcSwitch, msg, tos, dstSwitch);

			if (route == null) {
				log.warn("Non-connected client in network!");// Broadcasting");
				sendICMPHostUnreachable(srcSwitch, msg, match, eth);
				// non-connected network
				// doControllerBroadcast(srcSwitch, msg);
				return;
			}

			// Route set algo state
			IOFSwitch currentHop = srcSwitch;
			OFPort currentInPort = msg.getInPort();

			// Send packet to first hop last
			IOFSwitch firstHop = currentHop;
			OFPort firstInPort = currentInPort;
			OFPort firstOutPort = OFPort.of(-1);

			// Set flows on intermediate hops
			for (Link linkToNextHop : route) {
				// Install route on first hop at the end
				if (firstOutPort.getShortPortNumber() == (short) -1) {
					firstOutPort = linkToNextHop.getSrcPort();
				} else {
					computedFlowMod = setFlowOnSwitch(currentHop,
							currentInPort, linkToNextHop.getSrcPort(), match,
							OFBufferId.NO_BUFFER, cookie, null,
							!buildReverseFlow, null);
					if (computedFlowMod == null) {
						doControllerBroadcast(srcSwitch, msg);
						return;
					}
					if (buildReverseFlow) {
						OFFlowMod reserveFlowMod = setFlowOnSwitch(
								currentHop,
								linkToNextHop.getSrcPort(),
								currentInPort,
								reverseMatch
										.createBuilder()
										.setExact(MatchField.IN_PORT,
												linkToNextHop.getSrcPort())
										.build(), OFBufferId.NO_BUFFER, cookie,
								null, false, null);
						flowModsToSend.add(computedFlowMod);
						flowModsToSend.add(reserveFlowMod);
						currentHop.write(flowModsToSend);
						currentHop.flush();
						flowMods.add(reserveFlowMod);
						flowModsToSend.clear();
					}
					switches.add(currentHop);
					flowMods.add(computedFlowMod);
				}

				currentHop = switchManager.getSwitch(linkToNextHop.getDst());
				currentInPort = linkToNextHop.getDstPort();
			}

			// Set flow from last switch to client
			computedFlowMod = setFlowOnSwitch(currentHop, currentInPort,
					attachmentPoint.getPort(), match, OFBufferId.NO_BUFFER,
					cookie, null, !buildReverseFlow, null);
			if (computedFlowMod == null) {
				doControllerBroadcast(srcSwitch, msg);
				return;
			}
			if (buildReverseFlow) {
				computedFlowMod = setFlowOnSwitch(
						currentHop,
						attachmentPoint.getPort(),
						currentInPort,
						reverseMatch
								.createBuilder()
								.setExact(MatchField.IN_PORT,
										attachmentPoint.getPort()).build(),
						OFBufferId.NO_BUFFER, cookie, null, true,
						Collections.singletonList((OFMessage) computedFlowMod));
				flowMods.add(computedFlowMod);
				flowModsToSend.clear();
			}
			switches.add(currentHop);
			flowMods.add(computedFlowMod);

			// Set flow on first switch
			computedFlowMod = setFlowOnSwitch(firstHop, firstInPort,
					firstOutPort, match, OFBufferId.NO_BUFFER, cookie, null,
					!buildReverseFlow, null);
			if (computedFlowMod == null) {
				doControllerBroadcast(srcSwitch, msg);
				return;
			}
			if (buildReverseFlow) {
				OFFlowMod reserveFlowMod = setFlowOnSwitch(firstHop,
						firstOutPort, firstInPort, reverseMatch.createBuilder()
								.setExact(MatchField.IN_PORT, firstOutPort)
								.build(), OFBufferId.NO_BUFFER, cookie, null,
						false, null);
				flowModsToSend.add(computedFlowMod);
				flowModsToSend.add(reserveFlowMod);
				firstHop.write(flowModsToSend);
				firstHop.flush();
				flowMods.addFirst(reserveFlowMod);
				flowModsToSend.clear();
			}
			switches.addFirst(firstHop);
			flowMods.addFirst(computedFlowMod);

			if (!doNotSendPacket) {
				// Send packet directly to client via controller because
				// switches are too slow?
				OFPacketOut po = createPacketOutForPort(floodlightProvider,
						currentHop, attachmentPoint.getPort(), msg.getData(),
						log);
				// nextHop is here the last hop (hop at which the target
				// client is connected)
				currentHop.write(po);
				currentHop.flush();
			}
		}

		// There must be one fewer link than switches and flowMods
		assert (buildReverseFlow || route.size() + 1 == switches.size()
				&& switches.size() == flowMods.size());
		flowManager.addFlow(switches, route, flowMods, cookie, match,
				description);
		log.debug("Successfully set unicast route.");
	}

	/**
	 * Gets the tos from ethernet.
	 *
	 * @param eth
	 *            the eth
	 * @return the tos from ethernet
	 */
	private CostFunctionType getTosFromEthernet(Ethernet eth) {
		CostFunctionType tos = CostFunctionType.CONSTANT;
		if (eth.getPayload() instanceof IPv4) {
			IPv4 ipPacket = (IPv4) eth.getPayload();
			byte tosByte = ipPacket.getDiffServ();
			tosByte >>= 2;
			if (tosByte != 0)
				if ((tosByte & (1 << 2)) != 0)
					tos = CostFunctionType.LOW_DELAY;
				else if ((tosByte & (1 << 1)) != 0)
					tos = CostFunctionType.HIGH_THROUGHPUT;
				else if ((tosByte & 1) != 0)
					tos = CostFunctionType.LOW_PACKET_LOSS;
		}
		return tos;
	}

	/**
	 * Acquire optimal route.
	 *
	 * @param srcSwitch
	 *            the src switch
	 * @param msg
	 *            the msg
	 * @param tos
	 *            the tos
	 * @param dstSwitch
	 *            the dst switch
	 * @return the list
	 */
	private List<Link> acquireOptimalRoute(IOFSwitch srcSwitch, OFPacketIn msg,
			CostFunctionType tos, IOFSwitch dstSwitch) {
		List<Link> route;

		// Calculate Type of Service
		// Prioritize cost function set via REST API if not overwritten by
		// packet or requested is not available
		if (!costFunctions.containsKey(tos) || tos == CostFunctionType.CONSTANT) {
			tos = getCurrentDefaultMetric();
		}
		// Check if there is a cost function. This should never fail.
		if (!costFunctions.containsKey(tos)) {
			log.warn("Can not use default metric {} because there is not cost function associated with it, falling back to constant cost function");
			tos = CostFunctionType.CONSTANT;
		}
		log.trace("Packet QoS property: {}", tos);

		// Try to use cached route
		SwitchPair pair = new SwitchPair(srcSwitch.getId(), dstSwitch.getId());
		route = routeCache.get(tos).get(pair);

		if (route == null) {
			log.trace("Route not cached. Computing new route.");
			// Compute route as it was not cached
			route = ssspAlgo.computeShortestPath(srcSwitch, dstSwitch,
					costFunctions.get(tos));

			// Some error happened, warning was output previously already
			if (route == null) {
				return null;
			}

			log.trace("Computed route, adding to cache.");
			// Add route to cache
			routeCache.get(tos).put(pair, route);
		} else {
			log.trace("Using cached route.");
		}
		return route;
	}

	/**
	 * Find suitable attachment point.
	 *
	 * @param targetClient
	 *            the target client
	 * @return the switch port
	 */
	public SwitchPort findSuitableAttachmentPoint(IDevice targetClient) {
		if (targetClient == null)
			return null;

		SwitchPort[] attachmentPoints = targetClient.getAttachmentPoints();
		if (attachmentPoints.length == 0) {
			return null;
		}

		SwitchPort attachmentPoint;
		if (attachmentPoints.length > 1) {
			// Use random attachment point if more than one exist
			attachmentPoint = attachmentPoints[new Random()
					.nextInt(attachmentPoints.length)];
		} else {
			attachmentPoint = attachmentPoints[0];
		}
		return attachmentPoint;
	}

	public void doControllerBroadcast(byte[] data) {
		doControllerBroadcast(null, data, null);
	}

	private void doControllerBroadcast(IOFSwitch sw, OFPacketIn msg) {
		doControllerBroadcast(sw, msg.getData(), msg.getInPort());
	}

	/**
	 * Do controller broadcast.
	 *
	 * @param sw
	 *            the sw
	 * @param msg
	 *            the msg
	 */
	private void doControllerBroadcast(IOFSwitch sw, byte[] data, OFPort inPort) {
		log.debug("Falling back to broadcasting packet");

		if (data.length == 0) {
			// We just got a virtual packet
			log.warn("Something went wrong while pre-setting flows");
			return;
		}

		// Flood packets to all non-switch ports
		Map<DatapathId, Set<Link>> switchLinks = linkManager.getSwitchLinks();

		if (switchManager == null) {
			log.warn("SwitchManager is null");
			return;
		}
		if (switchManager.getAllSwitchMap() == null) {
			log.warn("AllSwitchMap is null");
			return;
		}

		// Iterate over switches
		for (Entry<DatapathId, IOFSwitch> entry : switchManager
				.getAllSwitchMap().entrySet()) {
			DatapathId dpid = entry.getKey();
			IOFSwitch dstSw = entry.getValue();
			Set<Link> specificSwitchLinks = switchLinks.get(entry.getKey());

			// Create array of switch ports...
			Collection<OFPortDesc> portsCollection = dstSw.getPorts();
			Map<OFPort, OFPortDesc> ports = new HashMap<OFPort, OFPortDesc>();
			for (OFPortDesc port : portsCollection) {
				ports.put(port.getPortNo(), port);
			}

			// ... in which those that are switch links will be set to null...
			if (specificSwitchLinks != null) {
				for (Link link : specificSwitchLinks) {
					// if (linkManager
					// .getLinkType(link, linkManager.getLinkInfo(link)) ==
					// LinkType.INVALID_LINK)
					// continue;
					if (link.getSrc().equals(dpid)) {
						ports.remove(link.getSrcPort());
					} else if (link.getDst().equals(dpid)) {
						ports.remove(link.getDstPort());
					} else
						throw new IllegalStateException(
								"Non-switch link in links for this switch");
				}
			}

			// also remove inport of message
			if (sw != null && sw.getId().equals(dpid)) {
				ports.remove(inPort);
			}

			// also remove local ports
			ports.remove(OFPort.LOCAL);

			// ... so we know which ones are not switch links later on.
			List<OFMessage> packetOutMessageList = new ArrayList<>();
			// Create packet out message
			for (OFPort portNumber : ports.keySet()) {
				OFPacketOut po = createPacketOutForPort(floodlightProvider,
						dstSw, portNumber, data, log);
				packetOutMessageList.add(po);
			}
			if (packetOutMessageList.isEmpty()) {
				// no non-switch links
				continue;
			}

			// Send packet out
			dstSw.write(packetOutMessageList);
			dstSw.flush();
		}
	}

	/**
	 * Creates the packet out for port.
	 *
	 * @param floodlightProvider
	 *            the floodlight provider
	 * @param dpid
	 *            the dpid
	 * @param portNumber
	 *            the port number
	 * @param packetData
	 *            the packet data
	 * @return the OF packet out
	 */
	public static OFPacketOut createPacketOutForPort(
			IFloodlightProviderService floodlightProvider, IOFSwitch sw,
			OFPort portNumber, byte[] packetData, Logger log) {
		assert (portNumber != OFPort.CONTROLLER);

		log.trace("Outputting msg to switch {} , port {}", sw.getId(),
				portNumber);
		OFPacketOut.Builder pob = sw.getOFFactory().buildPacketOut();

		// set actions
		pob.setActions(Collections.singletonList((OFAction) sw.getOFFactory()
				.actions().output(portNumber, 0)));

		// As we send this message it is stored nowhere and appears out
		// of nowhere ;)
		pob.setBufferId(OFBufferId.NO_BUFFER);
		pob.setInPort(OFPort.CONTROLLER);

		// Set packet data from original packetIn event
		pob.setData(packetData);

		// Set final length
		return pob.build();
	}

	/**
	 * Sets the flow on switch. Returns OFFlowMod iff flow was successfully set
	 *
	 * @param sw
	 *            the sw
	 * @param ofPort
	 *            the in port
	 * @param ofPort2
	 *            the out port
	 * @param match
	 *            the match
	 * @param ofBufferId
	 *            the buffer id
	 * @param cookie
	 *            the cookie
	 * @param additionalActions
	 *            the additional actions
	 * @return sent message, if successful, null otherwise
	 */
	private OFFlowMod setFlowOnSwitch(IOFSwitch sw, OFPort inPort,
			OFPort outPort, Match match, OFBufferId ofBufferId, U64 cookie,
			List<OFAction> additionalActions, boolean send,
			List<OFMessage> additionalMessages) {
		assert (outPort != OFPort.CONTROLLER);

		List<OFAction> actions;
		if (additionalActions == null) {
			actions = Arrays.asList((OFAction) sw.getOFFactory().actions()
					.output(outPort, 0));
		} else {
			actions = new ArrayList<>(additionalActions.size() + 1);
			actions.addAll(additionalActions);
			actions.add(sw.getOFFactory().actions().output(outPort, 0));
		}

		return setFlowOnSwitch(sw, inPort, match, ofBufferId, cookie, actions,
				send, additionalMessages);
	}

	/**
	 * Sets the flow on switch.
	 *
	 * @param sw
	 *            the sw
	 * @param currentInPort
	 *            the in port
	 * @param match
	 *            the match
	 * @param bufferId
	 *            the buffer id
	 * @param cookie
	 *            the cookie
	 * @param actions
	 *            the actions
	 * @return the OF flow mod
	 */
	private OFFlowMod setFlowOnSwitch(IOFSwitch sw, OFPort currentInPort,
			Match match, OFBufferId bufferId, U64 cookie,
			List<OFAction> actions, boolean send,
			List<OFMessage> additionalMessages) {

		OFFlowAdd.Builder fab = sw.getOFFactory().buildFlowAdd();

		// Create new match first
		match = match.createBuilder()
				.setExact(MatchField.IN_PORT, currentInPort).build();
		// Set necessary parameters
		fab.setMatch(match);
		fab.setCookie(cookie);
		fab.setBufferId(bufferId);
		fab.setIdleTimeout(flowManager.getFlowmodIdleTimeout());
		fab.setHardTimeout(flowManager.getFlowmodHardTimeout());
		fab.setFlags(Collections.singleton(OFFlowModFlags.SEND_FLOW_REM));
		fab.setActions(actions);
		OFFlowAdd flowMod = fab.build();

		List<OFMessage> messages;
		if (additionalMessages != null) {
			// don't forget the additional messages
			messages = new ArrayList<>(additionalMessages);
			messages.add(flowMod);
		} else
			messages = Collections.singletonList((OFMessage) flowMod);

		log.trace("Adding flow on {} inPort {} for {} actions {}", sw,
				currentInPort, match, actions);

		// and write it out
		if (send) {
			sw.write(messages);
			sw.flush();
		}
		return flowMod;
	}

	/**
	 * Invalidate cache.
	 *
	 * @param tos
	 *            the tos
	 */
	public void invalidateCache(CostFunctionType tos) {
		log.debug("Flushing route cache for ToS {}", tos);
		routeCache.get(tos).clear();
	}

	/**
	 * Invalidate all caches.
	 */
	public void invalidateAllCaches() {
		log.debug("Flushing all route caches");
		for (CostFunctionType tos : CostFunctionType.values())
			routeCache.get(tos).clear();
	}

	/**
	 * Do relayed routing.
	 *
	 * @param sw
	 *            the sw
	 * @param msg
	 *            the msg
	 * @param srcToDstMatch
	 *            the match
	 * @param ethPacket
	 *            the eth packet
	 * @param relay
	 *            the relay
	 * @param isAnswerPacket
	 *            the is answer packet
	 */
	public void doRelayedRouting(IOFSwitch sw, OFPacketIn msg,
			Match srcToDstMatch, Ethernet ethPacket, RelayInfo relay) {

		// Check if packet is coming in on a switch port.
		Set<Link> switchLinks = linkManager.getSwitchLinks().get(sw.getId());
		if (switchLinks != null) {
			for (Link link : switchLinks) {
				if (link.getSrc() == sw.getId()
						&& link.getSrcPort() == msg.getInPort()) {
					log.warn("Message came in on switch link! Dropping.");
					return;
				}
			}
		}

		// look for target device
		Iterator<? extends IDevice> devices = deviceManager
				.queryDevices(
						srcToDstMatch.get(MatchField.ETH_DST),
						(ethPacket.getEtherType() == EthType.VLAN_FRAME
								.getValue() ? srcToDstMatch.get(
								MatchField.VLAN_VID).getVlanVid() : null),
						srcToDstMatch.get(MatchField.IPV4_DST), null, null);

		if (!devices.hasNext()) {
			log.info("Could not relay message as the client is not known "
					+ "yet. Trying to find it...");
			new DelayedRoutingThread(sw, msg,
					srcToDstMatch.get(MatchField.IPV4_DST), this,
					deviceManager, ethPacket, srcToDstMatch).start();
			return;
		}
		IDevice targetClient = devices.next();

		log.trace("Starting relayed routing via {}", relay);
		MatchField<TransportPort> tpSrcMatchField, tpDstMatchField;
		if (srcToDstMatch.get(MatchField.IP_PROTO).equals(IpProtocol.TCP)) {
			tpSrcMatchField = MatchField.TCP_SRC;
			tpDstMatchField = MatchField.TCP_DST;
		} else if (srcToDstMatch.get(MatchField.IP_PROTO)
				.equals(IpProtocol.UDP)) {
			tpSrcMatchField = MatchField.UDP_SRC;
			tpDstMatchField = MatchField.UDP_DST;
		} else {
			throw new IllegalStateException(
					"Tried relaying on unsupported transport protocol");
		}

		// Build matches and rewrite actions
		MacAddress dstMac = srcToDstMatch.get(MatchField.ETH_DST);
		MacAddress srcMac = srcToDstMatch.get(MatchField.ETH_SRC);
		MacAddress relMac = relay.getRelayMacAddress();
		IPv4Address dstIp = srcToDstMatch.get(MatchField.IPV4_DST);
		IPv4Address srcIp = srcToDstMatch.get(MatchField.IPV4_SRC);
		IPv4Address relIp = relay.getRelayIpAddress();
		TransportPort dstTp = srcToDstMatch.get(tpDstMatchField);
		TransportPort srcTp = srcToDstMatch.get(tpSrcMatchField);
		TransportPort relTp = relay.getRelayTransportPort();
		TransportPort relToDstSrcTp = TransportPort.of(srcTp.getPort() - 14);
		TransportPort relToDstDstTp = TransportPort.of(srcTp.getPort() - 13);

		Match dstToSrcMatch = srcToDstMatch.createBuilder()
				.setExact(MatchField.ETH_SRC, dstMac)
				.setExact(MatchField.IPV4_SRC, dstIp)
				.setExact(tpSrcMatchField, dstTp)
				.setExact(MatchField.ETH_DST, srcMac)
				.setExact(MatchField.IPV4_DST, srcIp)
				.setExact(tpDstMatchField, srcTp).build();

		Match relToSrcMatch = srcToDstMatch.createBuilder()
				.setExact(MatchField.ETH_SRC, relMac)
				.setExact(MatchField.IPV4_SRC, relIp)
				.setExact(tpSrcMatchField, relTp)
				.setExact(MatchField.ETH_DST, srcMac)
				.setExact(MatchField.IPV4_DST, srcIp)
				.setExact(tpDstMatchField, srcTp).build();

		Match relToDstMatch = relToSrcMatch.createBuilder()
				.setExact(tpSrcMatchField, relToDstSrcTp)
				.setExact(tpDstMatchField, relToDstDstTp).build();

		OFActions actionsFactory = OFFactories.getFactory(OFVersion.OF_10)
				.actions();

		List<OFAction> srcToRelActions = new ArrayList<>();
		srcToRelActions.add(actionsFactory.setDlDst(relMac));
		srcToRelActions.add(actionsFactory.setNwDst(relIp));
		srcToRelActions.add(actionsFactory.setTpDst(relTp));

		List<OFAction> relToSrcActions = new ArrayList<>();
		relToSrcActions.add(actionsFactory.setDlSrc(dstMac));
		relToSrcActions.add(actionsFactory.setNwSrc(dstIp));
		relToSrcActions.add(actionsFactory.setTpSrc(dstTp));

		List<OFAction> relToDstActions = new ArrayList<>();
		relToDstActions.add(actionsFactory.setDlSrc(srcMac));
		relToDstActions.add(actionsFactory.setNwSrc(srcIp));
		relToDstActions.add(actionsFactory.setTpSrc(srcTp));
		relToDstActions.add(actionsFactory.setDlDst(dstMac));
		relToDstActions.add(actionsFactory.setNwDst(dstIp));
		relToDstActions.add(actionsFactory.setTpDst(dstTp));

		List<OFAction> dstToRelActions = new ArrayList<>();
		dstToRelActions.add(actionsFactory.setDlSrc(srcMac));
		dstToRelActions.add(actionsFactory.setNwSrc(srcIp));
		dstToRelActions.add(actionsFactory.setTpSrc(relToDstDstTp));
		dstToRelActions.add(actionsFactory.setDlDst(relMac));
		dstToRelActions.add(actionsFactory.setNwDst(relIp));
		dstToRelActions.add(actionsFactory.setTpDst(relToDstSrcTp));

		// Compute the src->relay and relay->dst routes
		log.trace("Computing routes");
		SwitchPort relayPort = relay.getAttachmentPoint();
		SwitchPort clientPort = findSuitableAttachmentPoint(targetClient);

		IOFSwitch clientSwitch = switchManager.getSwitch(clientPort
				.getSwitchDPID());

		IOFSwitch relaySwitch = switchManager.getSwitch(relayPort
				.getSwitchDPID());
		if (relaySwitch == null) {
			log.warn("Switch with relay not known, falling back to unicast routing");
			doUnicastRouting(sw, msg, clientPort, srcToDstMatch,
					getTosFromEthernet(ethPacket), false,
					generateDescription(ethPacket), false, ethPacket);
			return;
		}

		List<Link> srcToRelay = acquireOptimalRoute(sw, msg,
				getTosFromEthernet(ethPacket), relaySwitch);
		List<Link> relayToDst = acquireOptimalRoute(relaySwitch, msg,
				getTosFromEthernet(ethPacket), clientSwitch);

		// Check if both routes exist. Note that empty routes are lists of size
		// 0, so null lists are errors
		if (srcToRelay == null || relayToDst == null) {
			log.warn("Either source, relay or destination switch are not connected");
			doControllerBroadcast(sw, msg);
			return;
		}

		// Stuff for keeping track of the flow
		OFFlowMod computedFlowMod;
		LinkedList<IOFSwitch> switches = new LinkedList<>();
		LinkedList<OFFlowMod> flowMods = new LinkedList<>(), flowModsResponse = new LinkedList<>();
		U64 cookie = flowManager.getNewRandomCookie(), cookieResponse = flowManager
				.getNewRandomCookie();
		log.trace("Cookie for this transaction is {}",
				Long.toHexString(cookie.getValue()));

		// Route set algo state
		IOFSwitch currentHop = sw;
		OFPort currentInPort = msg.getInPort();

		// Set flows on src->relay intermediate hops
		log.trace("Setting Source -> Relay routes");
		for (Link linkToNextHop : srcToRelay) {
			computedFlowMod = setFlowOnSwitch(currentHop, currentInPort,
					linkToNextHop.getSrcPort(), srcToDstMatch,
					OFBufferId.NO_BUFFER, cookie, null, false, null);
			if (computedFlowMod == null) {
				log.warn("Error while setting route over relay");
				doControllerBroadcast(sw, msg);
				return;
			}
			flowMods.add(computedFlowMod);
			// Don't forget the reversed direction
			computedFlowMod = setFlowOnSwitch(currentHop,
					linkToNextHop.getSrcPort(), currentInPort, dstToSrcMatch,
					OFBufferId.NO_BUFFER, cookieResponse, null, true,
					Collections.singletonList((OFMessage) computedFlowMod));
			if (computedFlowMod == null) {
				log.warn("Error while setting route over relay");
				doControllerBroadcast(sw, msg);
				return;
			}
			flowModsResponse.add(computedFlowMod);
			switches.add(currentHop);
			currentHop = switchManager.getSwitch(linkToNextHop.getDst());
			currentInPort = linkToNextHop.getDstPort();
		}

		List<OFMessage> relaySwitchMessagesToSend = new ArrayList<>();

		log.trace("Setting necessary routes on relay switch");
		// Set src->relay flow on relay port
		computedFlowMod = setFlowOnSwitch(currentHop, currentInPort,
				relay.getRelaySwitchPort(), srcToDstMatch,
				OFBufferId.NO_BUFFER, cookie, srcToRelActions, false, null);
		if (computedFlowMod == null) {
			log.warn("Error while setting route over relay");
			doControllerBroadcast(sw, msg);
			return;
		}
		flowMods.add(computedFlowMod);
		relaySwitchMessagesToSend.add(computedFlowMod);

		// Set relay->src flow on relay port
		computedFlowMod = setFlowOnSwitch(currentHop,
				relay.getRelaySwitchPort(), currentInPort, relToSrcMatch,
				OFBufferId.NO_BUFFER, cookieResponse, relToSrcActions, false,
				null);
		if (computedFlowMod == null) {
			log.warn("Error while setting route over relay");
			doControllerBroadcast(sw, msg);
			return;
		}
		flowModsResponse.add(computedFlowMod);
		relaySwitchMessagesToSend.add(computedFlowMod);

		// now the dst-related stuff
		switches.add(currentHop);
		currentInPort = relay.getRelaySwitchPort();

		// Just in case the dst and relay are on same switch...
		Iterator<Link> relayToDstIt = relayToDst.iterator();
		OFPort firstOutPortRelToDst;
		Link firstLinkOnRelToDstRoute;
		if (relayToDstIt.hasNext()) {
			firstLinkOnRelToDstRoute = relayToDstIt.next();
			firstOutPortRelToDst = firstLinkOnRelToDstRoute.getSrcPort();
		} else {
			firstOutPortRelToDst = clientPort.getPort();
			firstLinkOnRelToDstRoute = null;
		}

		// relay->dst flow mod
		computedFlowMod = setFlowOnSwitch(currentHop, currentInPort,
				firstOutPortRelToDst, relToDstMatch, OFBufferId.NO_BUFFER,
				cookie, relToDstActions, false, null);
		if (computedFlowMod == null) {
			log.warn("Error while setting route over relay");
			doControllerBroadcast(sw, msg);
			return;
		}
		flowMods.add(computedFlowMod);
		relaySwitchMessagesToSend.add(computedFlowMod);

		// dst->relay flow mod (and send all mods here)
		computedFlowMod = setFlowOnSwitch(currentHop, firstOutPortRelToDst,
				currentInPort, dstToSrcMatch, OFBufferId.NO_BUFFER,
				cookieResponse, dstToRelActions, true,
				relaySwitchMessagesToSend);
		if (computedFlowMod == null) {
			log.warn("Error while setting route over relay");
			doControllerBroadcast(sw, msg);
			return;
		}
		flowModsResponse.add(computedFlowMod);

		// Set to data of first switch in route (note that this may be null but
		// would then not be used)
		if (firstLinkOnRelToDstRoute != null) {
			currentHop = switchManager.getSwitch(firstLinkOnRelToDstRoute
					.getDst());
			currentInPort = firstLinkOnRelToDstRoute.getDstPort();
		} else {
			currentHop = null;
			currentInPort = null;
		}

		// Set flows on relay->dst intermediate hops
		log.trace("Setting relay -> destination route");
		while (relayToDstIt.hasNext()) {
			Link linkToNextHop = relayToDstIt.next();
			computedFlowMod = setFlowOnSwitch(currentHop, currentInPort,
					linkToNextHop.getSrcPort(), srcToDstMatch,
					OFBufferId.NO_BUFFER, cookie, null, false, null);
			if (computedFlowMod == null) {
				log.warn("Error while setting route over relay");
				doControllerBroadcast(sw, msg);
				return;
			}
			flowMods.add(computedFlowMod);
			// Don't forget the reversed direction
			computedFlowMod = setFlowOnSwitch(currentHop,
					linkToNextHop.getSrcPort(), currentInPort, dstToSrcMatch,
					OFBufferId.NO_BUFFER, cookieResponse, null, true,
					Collections.singletonList((OFMessage) computedFlowMod));
			if (computedFlowMod == null) {
				log.warn("Error while setting route over relay");
				doControllerBroadcast(sw, msg);
				return;
			}
			flowModsResponse.add(computedFlowMod);
			switches.add(currentHop);
			currentHop = switchManager.getSwitch(linkToNextHop.getDst());
			currentInPort = linkToNextHop.getDstPort();
		}

		// Set last hop to destination host
		// note that this might have already happened if relay and client are on
		// same switch, so we must take care not to do it again.
		if (currentHop != null
				&& !currentHop.getId().equals(
						relay.getAttachmentPoint().getSwitchDPID())) {
			log.trace("Setting flows on last hop to destination");
			// relay and receiver are not on same switch
			computedFlowMod = setFlowOnSwitch(currentHop, currentInPort,
					clientPort.getPort(), srcToDstMatch, OFBufferId.NO_BUFFER,
					cookie, null, false, null);
			if (computedFlowMod == null) {
				log.warn("Error while setting route over relay");
				doControllerBroadcast(sw, msg);
				return;
			}
			flowMods.add(computedFlowMod);

			// and back again
			computedFlowMod = setFlowOnSwitch(currentHop, clientPort.getPort(),
					currentInPort, dstToSrcMatch, OFBufferId.NO_BUFFER,
					cookieResponse, null, true,
					Collections.singletonList((OFMessage) computedFlowMod));
			if (computedFlowMod == null) {
				log.warn("Error while setting route over relay");
				doControllerBroadcast(sw, msg);
				return;
			}

			flowModsResponse.add(computedFlowMod);
			switches.add(currentHop);
			currentInPort = clientPort.getPort();
		}

		// Prepare original packet for sending it out
		log.trace("Preparing and sending out original packet to relay");
		ethPacket.setDestinationMACAddress(relay.getRelayMacAddress());
		IPv4 ipPacket = (IPv4) ethPacket.getPayload();
		ipPacket.setDestinationAddress(relay.getRelayIpAddress());
		if (ipPacket.getPayload() instanceof UDP) {
			UDP udpPacket = (UDP) ipPacket.getPayload();
			udpPacket.setDestinationPort(relay.getRelayTransportPort());
			udpPacket.resetChecksum();
		} else {
			TCP tcpPacket = (TCP) ipPacket.getPayload();
			tcpPacket.setDestinationPort(relay.getRelayTransportPort());
			tcpPacket.resetChecksum();
		}

		// Send packet out to relay switch
		OFPacketOut po = createPacketOutForPort(floodlightProvider,
				relaySwitch, relay.getRelaySwitchPort(), ethPacket.serialize(),
				log);
		relaySwitch.write(po);
		relaySwitch.flush();

		ArrayList<Link> route = new ArrayList<>(srcToRelay.size()
				+ relayToDst.size());
		route.addAll(srcToRelay);
		route.addAll(relayToDst);
		String desc = generateDescription(ethPacket);

		flowManager.addFlow(switches, route, flowMods, cookie, srcToDstMatch,
				"Relayed route via " + relay + " for " + desc);
		flowManager.addFlow(switches, route, flowModsResponse, cookieResponse,
				dstToSrcMatch, "Relayed reverse route via " + relay + " for "
						+ desc);

		log.debug("Successfully set relayed unicast route.");

	}

	public List<CostFunctionType> getAvailableMetrics() {
		return new ArrayList<>(costFunctions.keySet());
	}

	public CostFunctionType getCurrentDefaultMetric() {
		return defaultMetric;
	}

	public boolean setDefaultMetric(String tosString) {
		CostFunctionType tos;
		try {
			tos = CostFunctionType.valueOf(tosString);
		} catch (Exception e) {
			log.warn("Could not set default metric", e);
			return false;
		}

		defaultMetric = tos;
		return true;
	}

	/**
	 * Generates description for some ethernet packets depending on the content.
	 *
	 * @param ethPacket
	 *            the ethernet packet
	 * @return the description
	 */
	private static String generateDescription(Ethernet ethPacket) {
		try {
			switch (ethPacket.getEtherType()) {
			case Ethernet.TYPE_ARP:
				ARP arpPacket = (ARP) ethPacket.getPayload();
				switch (arpPacket.getOpCode()) {
				case ARP.OP_REPLY:
					return "ARP reply: "
							+ IPv4.fromIPv4Address(IPv4.toIPv4Address(arpPacket
									.getSenderProtocolAddress()))
							+ " is at "
							+ MacAddress.of(
									arpPacket.getSenderHardwareAddress())
									.toString()
							+ " (requested by "
							+ IPv4.fromIPv4Address(IPv4.toIPv4Address(arpPacket
									.getTargetProtocolAddress())) + ")";
				case ARP.OP_REQUEST:
					return "ARP request: Who has "
							+ IPv4.fromIPv4Address(IPv4.toIPv4Address(arpPacket
									.getTargetProtocolAddress()))
							+ "? Tell "
							+ IPv4.fromIPv4Address(IPv4.toIPv4Address(arpPacket
									.getSenderProtocolAddress()));
				default:
					return "unknown ARP packet: " + arpPacket;
				}
			case Ethernet.TYPE_IPv4:
				IPv4 ipv4Packet = (IPv4) ethPacket.getPayload();
				if (ipv4Packet.getProtocol().equals(IpProtocol.ICMP)) {
					try {
						ICMP icmpPacket = (ICMP) ipv4Packet.getPayload();
						switch (icmpPacket.getIcmpType()) {
						case ICMP.ECHO_REPLY:
							return "ICMP Ping reply from "
									+ ipv4Packet.getSourceAddress().toString()
									+ " to "
									+ ipv4Packet.getDestinationAddress()
											.toString();
						case ICMP.ECHO_REQUEST:
							return "ICMP Ping request from "
									+ ipv4Packet.getSourceAddress().toString()
									+ " to "
									+ ipv4Packet.getDestinationAddress()
											.toString();
						case ICMP.DESTINATION_UNREACHABLE:
							Data data = (Data) icmpPacket.getPayload();
							IPv4 ipPayload = new IPv4();
							try {
								ipPayload.deserialize(data.getData(), 0,
										data.getData().length);
							} catch (Exception e) {
								return "ICMP destination unreachable for unparsable packet";
							}
							return "ICMP destination unreachable for packet "
									+ ipPayload;
						default:
							return "unknown ICMP packet";
						}
					} catch (Exception e) {
						return "unknown ICMP packet";
					}
				} else if (ipv4Packet.getProtocol().equals(IpProtocol.UDP)) {
					UDP udpPacket = (UDP) ipv4Packet.getPayload();
					return "UDP packets from "
							+ ipv4Packet.getSourceAddress().toString()
							+ ":"
							+ (udpPacket.getSourcePort().getPort() & 0xffff)
							+ " to "
							+ ipv4Packet.getDestinationAddress().toString()
							+ ":"
							+ (udpPacket.getDestinationPort().getPort() & 0xffff);
				} else if (ipv4Packet.getProtocol().equals(IpProtocol.TCP)) {
					TCP tcpPacket = (TCP) ipv4Packet.getPayload();
					return "TCP packets from "
							+ ipv4Packet.getSourceAddress().toString()
							+ ":"
							+ (tcpPacket.getSourcePort().getPort() & 0xffff)
							+ " to "
							+ ipv4Packet.getDestinationAddress()
							+ ":"
							+ (tcpPacket.getDestinationPort().getPort() & 0xffff);
				} else {
					return "unknown IPv4 packet: " + ipv4Packet;
				}
			default:
				return "unknown ethernet packet: " + ethPacket;
			}
		} catch (Exception e) {
			log.warn("Exception while generating packet description", e);
			return "Exception while generating packet description";
		}

	}

	@Override
	public void setUnicastPathAlgorithm(IUnicastPathAlgorithm algo) {
		ssspAlgo = algo;
		log.info("Unicast path algorithm registered");
	}

	@Override
	public Map<CostFunctionType, IConnectionGraphCostFunction> getCostFunctionMap() {
		return costFunctions;
	}

	public Multimap<IPv4Address, DelayedRoutingThread> getDelayedRoutingMap() {
		return delayedRoutingMap;
	}

	public void sendICMPHostUnreachable(IOFSwitch srcSwitch,
			OFPacketIn packetIn, Match match, Ethernet ethPayload) {
		log.debug("Sending an ICMP Host Unreachable to {}",
				match.get(MatchField.IPV4_SRC));
		ICMP icmp = new ICMP().setIcmpType(ICMP.DESTINATION_UNREACHABLE)
				.setIcmpCode((byte) 3);
		icmp.setPayload(ethPayload);

		IPv4 ipv4 = new IPv4()
				.setDestinationAddress(match.get(MatchField.IPV4_SRC))
				.setSourceAddress(IPv4Address.NONE)
				.setProtocol(IpProtocol.ICMP);
		ipv4.setPayload(icmp);

		Ethernet eth = new Ethernet();
		eth.setDestinationMACAddress(match.get(MatchField.ETH_SRC))
				.setSourceMACAddress(MacAddress.NONE)
				.setEtherType(Ethernet.TYPE_IPv4);
		if (match.get(MatchField.VLAN_VID) != null
				&& match.get(MatchField.VLAN_VID).getVlan() != -1)
			eth.setVlanID(match.get(MatchField.VLAN_VID).getVlan());
		eth.setPayload(ipv4);

		OFPacketOut packetOut = createPacketOutForPort(floodlightProvider,
				srcSwitch, packetIn.getInPort(), eth.serialize(), log);
		srcSwitch.write(packetOut);
		srcSwitch.flush();
	}
}
