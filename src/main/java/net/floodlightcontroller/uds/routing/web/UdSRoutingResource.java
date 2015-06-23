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

package net.floodlightcontroller.uds.routing.web;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.floodlightcontroller.uds.routing.interfaces.IRoutingService;
import net.floodlightcontroller.uds.routing.model.CostFunctionType;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.restlet.data.Status;
import org.restlet.resource.Get;
import org.restlet.resource.Post;
import org.restlet.resource.ServerResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UdSRoutingResource extends ServerResource {
	protected static final Logger log = LoggerFactory.getLogger(UdSRoutingResource.class);
	protected IRoutingService routingService;
	
	@Get("json")
    public Map<String, Object> retrieve() {
		IRoutingService routingService = (IRoutingService)getContext().getAttributes().get(IRoutingService.class.getCanonicalName()); 
		
		List<CostFunctionType> available = routingService.getAvailableMetrics();
		CostFunctionType defaultMetric = routingService.getCurrentDefaultMetric();
		Map<String, Object> out = new LinkedHashMap<String, Object>();
		out.put("availablemetrics", available);
		out.put("currentdefault", defaultMetric);
		return out;
	}
	
	@Post
	public Map<String, Object> post(String fmJson) {
		JSONTokener tokener = new JSONTokener(fmJson);

		Map<String, Object> m = new LinkedHashMap<String, Object>();
		try{
			JSONObject root = new JSONObject(tokener);
			String metric = root.getString("defaultmetric");
		
			IRoutingService routingService = (IRoutingService)getContext().getAttributes().get(IRoutingService.class.getCanonicalName());
			m.put("result", routingService.setDefaultMetric(metric));
			
			return m;
		} catch (JSONException e) {
			setStatus(Status.CLIENT_ERROR_BAD_REQUEST, "RelayManager exception");
			m.put("error", true);
			m.put("errormsg", "malformed json or something's missing..");
		}
		return m;
	}
}
