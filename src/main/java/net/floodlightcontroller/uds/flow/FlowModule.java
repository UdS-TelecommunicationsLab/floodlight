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

package net.floodlightcontroller.uds.flow;

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
import net.floodlightcontroller.linkdiscovery.ILinkDiscoveryService;
import net.floodlightcontroller.restserver.IRestApiService;
import net.floodlightcontroller.threadpool.IThreadPoolService;
import net.floodlightcontroller.uds.flow.interfaces.IFlowService;
import net.floodlightcontroller.uds.flow.internal.FlowManager;
import net.floodlightcontroller.uds.flow.web.UdSFlowRouter;
import net.floodlightcontroller.uds.routing.interfaces.IRoutingService;

import org.projectfloodlight.openflow.protocol.OFFlowRemoved;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFType;
import org.projectfloodlight.openflow.types.U64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FlowModule implements IFloodlightModule, IOFMessageListener {
	
	protected static final Logger log = LoggerFactory
			.getLogger(FlowModule.class);

	protected IFloodlightProviderService floodlightProvider;
	protected IThreadPoolService threadPool;
	protected ILinkDiscoveryService linkManager;
	protected IRestApiService restApi;
	protected IOFSwitchService switchManager;

	protected IRoutingService routingModule;

	protected FlowManager flowManager = new FlowManager();

	// // IFloodlightModule

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleServices() {
		Collection<Class<? extends IFloodlightService>> col = new ArrayList<Class<? extends IFloodlightService>>();
		col.add(IFlowService.class);
		return col;
	}

	@Override
	public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
		Map<Class<? extends IFloodlightService>, IFloodlightService> map = new HashMap<Class<? extends IFloodlightService>, IFloodlightService>();
		map.put(IFlowService.class, flowManager);
		return map;
	}

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
		ArrayList<Class<? extends IFloodlightService>> deps = new ArrayList<Class<? extends IFloodlightService>>();
		deps.add(IFloodlightProviderService.class);
		deps.add(IThreadPoolService.class);
		deps.add(ILinkDiscoveryService.class);
		deps.add(IRestApiService.class);
		deps.add(IRoutingService.class);
		deps.add(IOFSwitchService.class);
		return deps;
	}

	@Override
	public void init(FloodlightModuleContext context)
			throws FloodlightModuleException {
		floodlightProvider = context
				.getServiceImpl(IFloodlightProviderService.class);
		threadPool = context.getServiceImpl(IThreadPoolService.class);
		linkManager = context.getServiceImpl(ILinkDiscoveryService.class);
		routingModule = context.getServiceImpl(IRoutingService.class);
		restApi = context.getServiceImpl(IRestApiService.class);
		switchManager = context.getServiceImpl(IOFSwitchService.class);
		flowManager.init(floodlightProvider, threadPool, routingModule, switchManager);
	}

	@Override
	public void startUp(FloodlightModuleContext context)
			throws FloodlightModuleException {
		linkManager.addListener(flowManager);
		floodlightProvider.addOFMessageListener(OFType.FLOW_REMOVED, this);
		restApi.addRestletRoutable(new UdSFlowRouter());
		// read our config options
		Map<String, String> configOptions = context.getConfigParams(this);

		// Read hardTimeout from config, otherwise use default (30s)
		String hardTimeoutString = configOptions.get("hardTimeout");
		if (hardTimeoutString != null) {
			try {
				flowManager.setFlowmodHardTimeout(Short.valueOf(hardTimeoutString));
			} catch (NumberFormatException e) {
				log.error("hardTimeout is not a valid short: {}", hardTimeoutString);
			}
		}

		// Read idleTimeout from config, otherwise use default (10s)
		String idleTimeoutString = configOptions.get("idleTimeout");
		if (idleTimeoutString != null) {
			try {
				flowManager.setFlowmodIdleTimeout(Short.valueOf(idleTimeoutString));
			} catch (NumberFormatException e) {
				log.error("idleTimeout is not a valid short: {}", idleTimeoutString);
			}
		}
	}
	
	// // IOFMessageListener

	@Override
	public String getName() {
		return "UdS FlowManager";
	}

	@Override
	public boolean isCallbackOrderingPrereq(OFType type, String name) {
		return false;
	}

	@Override
	public boolean isCallbackOrderingPostreq(OFType type, String name) {
		return false;
	}

	@Override
	public net.floodlightcontroller.core.IListener.Command receive(
			IOFSwitch sw, OFMessage msg, FloodlightContext cntx) {
		switch (msg.getType()) {
		case FLOW_REMOVED:
			OFFlowRemoved flowRemoved = (OFFlowRemoved) msg;
			if (flowRemoved.getCookie().and(U64.of(0xffffffff00000000l)).equals(flowManager.getOfcontrolCookieAppId()))
				flowManager.flowRemoved(sw, flowRemoved);
			return Command.CONTINUE;
		default:
			return Command.CONTINUE;
		}
	}

}
