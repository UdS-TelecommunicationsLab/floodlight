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

package net.floodlightcontroller.uds.multicast;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
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
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IGMP;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.restserver.IRestApiService;
import net.floodlightcontroller.threadpool.IThreadPoolService;
import net.floodlightcontroller.uds.multicast.interfaces.IMulticastService;
import net.floodlightcontroller.uds.multicast.internal.MulticastManager;
import net.floodlightcontroller.uds.multicast.web.UdSMulticastRouter;

import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFPacketIn;
import org.projectfloodlight.openflow.protocol.OFType;
import org.projectfloodlight.openflow.types.IpProtocol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MulticastModule implements IFloodlightModule, IOFMessageListener {

	protected static final Logger log = LoggerFactory
			.getLogger(MulticastModule.class);

	IFloodlightProviderService floodlightProvider;
	IDeviceService deviceManager;
	IThreadPoolService threadPool;
	IRestApiService restApi;

	MulticastManager multicastManager = new MulticastManager();

	protected IOFSwitchService switchManager;

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleServices() {
		Collection<Class<? extends IFloodlightService>> col = new ArrayList<Class<? extends IFloodlightService>>();
		col.add(IMulticastService.class);
		return col;
	}

	@Override
	public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
		Map<Class<? extends IFloodlightService>, IFloodlightService> map = new HashMap<Class<? extends IFloodlightService>, IFloodlightService>();
		map.put(IMulticastService.class, multicastManager);
		return map;
	}

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
		ArrayList<Class<? extends IFloodlightService>> deps = new ArrayList<Class<? extends IFloodlightService>>();
		deps.add(IFloodlightProviderService.class);
		deps.add(IDeviceService.class);
		deps.add(IThreadPoolService.class);
		deps.add(IRestApiService.class);
		deps.add(IOFSwitchService.class);
		return deps;
	}

	@Override
	public void init(FloodlightModuleContext context)
			throws FloodlightModuleException {
		floodlightProvider = context
				.getServiceImpl(IFloodlightProviderService.class);
		threadPool = context.getServiceImpl(IThreadPoolService.class);
		deviceManager = context.getServiceImpl(IDeviceService.class);
		restApi = context.getServiceImpl(IRestApiService.class);
		switchManager = context.getServiceImpl(IOFSwitchService.class);

		multicastManager.init(floodlightProvider, deviceManager, switchManager);
	}

	@Override
	public void startUp(FloodlightModuleContext context)
			throws FloodlightModuleException {
		multicastManager.startUp(threadPool.getScheduledExecutor());
		floodlightProvider.addOFMessageListener(OFType.PACKET_IN, this);
		restApi.addRestletRoutable(new UdSMulticastRouter());
		// TODO read options
	}

	@Override
	public String getName() {
		return "UdS MulticastManager";
	}

	@Override
	public boolean isCallbackOrderingPrereq(OFType type, String name) {
		return false;
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

			if (ethPacket.getPayload() instanceof IPv4) {
				IPv4 ipv4Packet = (IPv4) ethPacket.getPayload();
				if (ipv4Packet.getProtocol() != IpProtocol.IGMP)
					return Command.CONTINUE;
				log.debug("Found IGMP Packet");
				if (ipv4Packet.getPayload() instanceof IGMP) {
					// IGMP message handling
					IGMP igmp = (IGMP) ipv4Packet.getPayload();
					if (igmp.isIGMPv3MembershipReportMessage()) {
						// This should be the only messages we get from our
						// clients
						multicastManager.handleMembershipReportIn(sw, ofPktIn,
								igmp);
					} else {
						log.warn(
								"Detected non-membership report IGMP message of "
										+ "type {}", igmp.getType());
					}
					return Command.STOP;
				} else {
					// log.warn("Unparsable IGMP message: {}",
					// ofPktIn.getPacketData());
					log.warn("Unparsable IGMP message: {}", ofPktIn.getData());
					return Command.STOP;
				}
			}

		default:
			return Command.CONTINUE;
		}
	}

}
