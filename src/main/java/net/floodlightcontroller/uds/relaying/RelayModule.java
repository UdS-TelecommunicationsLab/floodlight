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

package net.floodlightcontroller.uds.relaying;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.restserver.IRestApiService;
import net.floodlightcontroller.threadpool.IThreadPoolService;
import net.floodlightcontroller.uds.relaying.interfaces.IRelayService;
import net.floodlightcontroller.uds.relaying.internal.RelayManager;
import net.floodlightcontroller.uds.relaying.web.UdSRelayRouter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RelayModule implements IFloodlightModule {
	
	protected static final Logger log = LoggerFactory
			.getLogger(RelayModule.class);
	
	IFloodlightProviderService floodlightProvider;
	IThreadPoolService threadPool;
	RelayManager relayManager = new RelayManager();
	protected IRestApiService restApi;

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleServices() {
		Collection<Class<? extends IFloodlightService>> col = new ArrayList<Class<? extends IFloodlightService>>();
		col.add(IRelayService.class);
		return col;
	}

	@Override
	public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
		Map<Class<? extends IFloodlightService>, IFloodlightService> map = new HashMap<Class<? extends IFloodlightService>, IFloodlightService>();
		map.put(IRelayService.class, relayManager);
		return map;
	}

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
		ArrayList<Class<? extends IFloodlightService>> deps = new ArrayList<Class<? extends IFloodlightService>>();
		deps.add(IFloodlightProviderService.class);
		deps.add(IThreadPoolService.class);
		deps.add(IRestApiService.class);
		return deps;
	}

	@Override
	public void init(FloodlightModuleContext context)
			throws FloodlightModuleException {
		floodlightProvider = context.getServiceImpl(IFloodlightProviderService.class);
		threadPool = context.getServiceImpl(IThreadPoolService.class);
		restApi = context.getServiceImpl(IRestApiService.class);
	}

	@Override
	public void startUp(FloodlightModuleContext context)
			throws FloodlightModuleException {
		// read our config options
		Map<String, String> configOptions = context.getConfigParams(this);
		relayManager.startUp(configOptions, threadPool.getScheduledExecutor());
		restApi.addRestletRoutable(new UdSRelayRouter());
		log.info("Relay module started");
	}

}
