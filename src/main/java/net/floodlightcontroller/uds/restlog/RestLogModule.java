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

package net.floodlightcontroller.uds.restlog;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.restserver.IRestApiService;
import net.floodlightcontroller.uds.restlog.interfaces.IRingBufferAppender;
import net.floodlightcontroller.uds.restlog.internal.RingBufferAppender;
import net.floodlightcontroller.uds.restlog.web.LogRouter;
import net.floodlightcontroller.uds.restlog.web.WebSocketServer;

public class RestLogModule implements IFloodlightModule {

	RingBufferAppender appender = new RingBufferAppender();
	private IRestApiService restApi;

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleServices() {
		Collection<Class<? extends IFloodlightService>> classes = new HashSet<>();
		classes.add(IRingBufferAppender.class);
		return classes;
	}

	@Override
	public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
		Map<Class<? extends IFloodlightService>, IFloodlightService> impls = new HashMap<>();
		impls.put(IRingBufferAppender.class, appender);
		return impls;
	}

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
		ArrayList<Class<? extends IFloodlightService>> deps = new ArrayList<Class<? extends IFloodlightService>>();
		deps.add(IRestApiService.class);
		return deps;
	}

	@Override
	public void init(FloodlightModuleContext context)
			throws FloodlightModuleException {
		restApi = context.getServiceImpl(IRestApiService.class);
		appender.init();
	}

	@Override
	public void startUp(FloodlightModuleContext context)
			throws FloodlightModuleException {
		try {
			appender.startUp();
			restApi.addRestletRoutable(new LogRouter());
			new WebSocketServer(appender);
		} catch (IOException e) {
			throw new FloodlightModuleException("Error initializing module", e);
		}
	}

}
