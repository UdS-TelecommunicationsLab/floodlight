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

package net.floodlightcontroller.uds.routing;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFPacketIn;
import org.projectfloodlight.openflow.protocol.OFType;

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
import net.floodlightcontroller.linkdiscovery.ILinkDiscoveryService;
import net.floodlightcontroller.restserver.IRestApiService;
import net.floodlightcontroller.uds.flow.interfaces.IFlowService;
import net.floodlightcontroller.uds.multicast.interfaces.IMulticastService;
import net.floodlightcontroller.uds.relaying.interfaces.IRelayService;
import net.floodlightcontroller.uds.routing.interfaces.IRoutingService;
import net.floodlightcontroller.uds.routing.internal.Routing;
import net.floodlightcontroller.uds.routing.web.UdSRoutingRouter;

public class RoutingModule implements IFloodlightModule, IOFMessageListener {

	IFloodlightProviderService floodlightProvider;
	IDeviceService deviceManager;
	ILinkDiscoveryService linkManager;
	IRestApiService restApi;
	IOFSwitchService switchManager;

	IFlowService flowManager;
	IRelayService relayManager;
	IMulticastService multicastManager;

	Routing routing = new Routing();

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleServices() {
		Collection<Class<? extends IFloodlightService>> col = new ArrayList<Class<? extends IFloodlightService>>();
		col.add(IRoutingService.class);
		return col;
	}

	@Override
	public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
		Map<Class<? extends IFloodlightService>, IFloodlightService> map = new HashMap<Class<? extends IFloodlightService>, IFloodlightService>();
		map.put(IRoutingService.class, routing);
		return map;
	}

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
		ArrayList<Class<? extends IFloodlightService>> deps = new ArrayList<Class<? extends IFloodlightService>>();
		deps.add(IFloodlightProviderService.class);
		deps.add(IDeviceService.class);
		deps.add(ILinkDiscoveryService.class);
		deps.add(IRestApiService.class);
		deps.add(IFlowService.class);
		deps.add(IRelayService.class);
		deps.add(IMulticastService.class);
		deps.add(IOFSwitchService.class);
		return deps;
	}

	@Override
	public void init(FloodlightModuleContext context)
			throws FloodlightModuleException {
		floodlightProvider = context
				.getServiceImpl(IFloodlightProviderService.class);
		deviceManager = context.getServiceImpl(IDeviceService.class);
		linkManager = context.getServiceImpl(ILinkDiscoveryService.class);
		restApi = context.getServiceImpl(IRestApiService.class);
		switchManager = context.getServiceImpl(IOFSwitchService.class);
		flowManager = context.getServiceImpl(IFlowService.class);
		relayManager = context.getServiceImpl(IRelayService.class);
		multicastManager = context.getServiceImpl(IMulticastService.class);
		routing.init(deviceManager, linkManager, floodlightProvider,
				flowManager, relayManager, multicastManager, switchManager);
	}

	@Override
	public void startUp(FloodlightModuleContext context)
			throws FloodlightModuleException {
		floodlightProvider.addOFMessageListener(OFType.PACKET_IN, this);
		restApi.addRestletRoutable(new UdSRoutingRouter());
	}

	@Override
	public String getName() {
		return "UdS Routing";
	}

	@Override
	public boolean isCallbackOrderingPrereq(OFType type, String name) {
		return name.equals("devicemanager") || name.equals("linkdiscovery")
				|| name.equals("topology");
	}

	@Override
	public boolean isCallbackOrderingPostreq(OFType type, String name) {
		return false;
	}

	@Override
	public net.floodlightcontroller.core.IListener.Command receive(
			IOFSwitch sw, OFMessage msg, FloodlightContext cntx) {
		switch (msg.getType()) {
		case PACKET_IN:
			return routing.handlePacketIn(sw, (OFPacketIn) msg, cntx);
		default:
			return Command.CONTINUE;
		}
	}

}
