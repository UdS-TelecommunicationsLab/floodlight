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

package net.floodlightcontroller.uds.routing.algorithms;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.linkdiscovery.ILinkDiscoveryService;
import net.floodlightcontroller.uds.routing.interfaces.IConnectionGraphCostFunction;
import net.floodlightcontroller.uds.routing.interfaces.IRoutingService;
import net.floodlightcontroller.uds.routing.model.CostFunctionType;

public abstract class RoutingAlgorithmBase implements IFloodlightModule {
	
	IFloodlightProviderService floodlightProvider;
	ILinkDiscoveryService linkManager;
	IRoutingService routing;
	IOFSwitchService switchManager;
	
	Map<CostFunctionType, IConnectionGraphCostFunction> costFunctions;

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
		deps.add(ILinkDiscoveryService.class);
		deps.add(IRoutingService.class);
		deps.add(IOFSwitchService.class);
		return deps;
	}

	@Override
	public void init(FloodlightModuleContext context)
			throws FloodlightModuleException {
		floodlightProvider = context.getServiceImpl(IFloodlightProviderService.class);
		linkManager = context.getServiceImpl(ILinkDiscoveryService.class);
		routing = context.getServiceImpl(IRoutingService.class);
		switchManager = context.getServiceImpl(IOFSwitchService.class);
	}

	@Override
	public void startUp(FloodlightModuleContext context)
			throws FloodlightModuleException {
		costFunctions = routing.getCostFunctionMap();
		registerSelf(routing);
	}
	
	abstract protected void registerSelf(IRoutingService routing);

}
