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

package net.floodlightcontroller.uds.flow.internal;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;

import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.linkdiscovery.ILinkDiscoveryListener;
import net.floodlightcontroller.routing.Link;
import net.floodlightcontroller.threadpool.IThreadPoolService;
import net.floodlightcontroller.uds.flow.interfaces.IFlowService;
import net.floodlightcontroller.uds.flow.model.Flow;
import net.floodlightcontroller.uds.routing.interfaces.IRoutingService;

import org.projectfloodlight.openflow.protocol.OFFlowMod;
import org.projectfloodlight.openflow.protocol.OFFlowRemoved;
import org.projectfloodlight.openflow.protocol.OFFlowRemovedReason;
import org.projectfloodlight.openflow.protocol.match.Match;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.U64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ArrayListMultimap;

public class FlowManager implements ILinkDiscoveryListener, IFlowService {

	// flow-mod - for use in the cookie
	public U64 ofcontrolCookieAppId = U64.of((0xbadcab1el & 0xffffffff) << 32);

	/** The flowmod idle timeout. */
	public short flowmodIdleTimeout = 10;

	/** The flowmod hard timeout. */
	public short flowmodHardTimeout = 30;

	protected IFloodlightProviderService floodlightProvider;
	protected IThreadPoolService threadPool;

	protected static final Logger log = LoggerFactory
			.getLogger(FlowManager.class);

	protected IRoutingService routingModule;

	protected final Map<U64, Flow> cookieToFlowMap;
	protected final ArrayListMultimap<DatapathId, Flow> dpidToFlowMap;
	protected final ArrayListMultimap<Link, Flow> linkToFlowMap;

	protected final HashSet<Flow> activeFlowSet;
	protected final Random randomNumberGegerator = new Random(); 

	protected IOFSwitchService switchManager;

	public FlowManager() {
		this.cookieToFlowMap = new HashMap<>();
		this.dpidToFlowMap = ArrayListMultimap.create();
		this.linkToFlowMap = ArrayListMultimap.create();
		this.activeFlowSet = new HashSet<>();
	}

	public Map<U64, Flow> getCookieToFlowMap() {
		return cookieToFlowMap;
	}

	public ArrayListMultimap<DatapathId, Flow> getDpidToFlowMap() {
		return dpidToFlowMap;
	}

	public ArrayListMultimap<Link, Flow> getLinkToFlowMap() {
		return linkToFlowMap;
	}

	/**
	 * Adds the flow.
	 *
	 * @param switches
	 *            the switches
	 * @param links
	 *            the links
	 * @param flowMods
	 *            the flow mods
	 * @param cookie
	 *            the cookie
	 */
	public synchronized Flow addFlow(List<IOFSwitch> switches,
			List<Link> links, List<OFFlowMod> flowMods, U64 cookie,
			Match match, String description) {

		log.trace(
				"Adding flow switches {}, with links {} and flowmods {}, and cookie {}",
				switches, links, flowMods, cookie);
		// Create new flow
		Flow flow = new Flow(switches, links, flowMods, cookie, match,
				description);

		// Enter flow into all maps
		cookieToFlowMap.put(cookie, flow);
		for (IOFSwitch sw : switches) {
			dpidToFlowMap.put(sw.getId(), flow);
		}
		for (Link link : links) {
			if (link != null)
				linkToFlowMap.put(link, flow);
		}
		activeFlowSet.add(flow);
		log.debug("Flow added");
		return flow;
	}

	/**
	 * Callback for a OFFlowRemoved packet. Removes flow from all switches and
	 * internal database
	 *
	 * @param sw
	 *            the switch that sent the OFFlowRemoved packet
	 * @param flowRemoved
	 *            the OFFlowRemoved packet
	 */
	public synchronized void flowRemoved(IOFSwitch sw, OFFlowRemoved flowRemoved) {
		// Get flow from cookieToFlowMap
		U64 cookie = flowRemoved.getCookie();
		if (OFFlowRemovedReason.DELETE.equals(flowRemoved.getReason())) {
			log.trace("Flow {} removed on our request.", cookie);
			return;
		}
		Flow flow = cookieToFlowMap.get(cookie);

		// Remove flow from datastructures and switches
		removeFlow(flow, sw);
		log.trace("flow {} removed from switch {}", flowRemoved, sw);
	}

	@Override
	public synchronized void linkDiscoveryUpdate(LDUpdate update) {
		if (processLinkDiscoveryUpdate(update) && routingModule != null)
			routingModule.invalidateAllCaches();
		log.debug("Link update, invalidating all caches");
	}

	@Override
	public synchronized void linkDiscoveryUpdate(List<LDUpdate> updateList) {
		boolean invalidateCaches = false;
		for (LDUpdate update : updateList)
			invalidateCaches |= processLinkDiscoveryUpdate(update);
		if (invalidateCaches && routingModule != null)
			routingModule.invalidateAllCaches();

		log.debug("Link update, invalidating all caches via update list");
	}

	/**
	 * Process single link discovery update if relevant for flows and caches
	 *
	 * @param update
	 *            the link discovery update
	 * @return true, if caches have to be invalidated
	 */
	private boolean processLinkDiscoveryUpdate(LDUpdate update) {
		List<Flow> flowsToRemove;
		switch (update.getOperation()) {
		case LINK_REMOVED:
			log.debug("link removed update");
			// Find out which flows belong to this link (if any)
			flowsToRemove = linkToFlowMap.get(new Link(update.getSrc(), update
					.getSrcPort(), update.getDst(), update.getDstPort()));
			if (flowsToRemove == null || flowsToRemove.isEmpty())
				return true;

			// Remove flows that contain this link
			flowsToRemove = new ArrayList<>(flowsToRemove);
			for (Flow flow : flowsToRemove) {
				removeFlow(flow, null);
			}
			return true;
		case SWITCH_REMOVED:
			log.debug("switch removed update");
			// Find out which flows belong to this switch (if any)
			DatapathId dpid = update.getSrc();
			flowsToRemove = dpidToFlowMap.get(dpid);
			if (flowsToRemove == null || flowsToRemove.isEmpty())
				return true;

			// Remove flows that contain this link
			flowsToRemove = new ArrayList<>(flowsToRemove);
			for (Flow flow : flowsToRemove) {
				removeFlow(flow, switchManager.getSwitch(dpid));

			}
			return true;
		default:
			// ignore
			return false;
		}
	}

	/**
	 * Removes the flow from internal datastructures and switches if it still
	 * exists. If not, returns immediately.
	 *
	 * @param flow
	 *            the flow
	 */
	public void removeFlow(Flow flow, IOFSwitch switchToIgnore) {
		log.trace("removing flow {}, but ignoring switch {}", flow,
				switchToIgnore);
		// Flow not found, probably already removed
		if (flow == null)
			return;

		// Send message to remove flow...
		RemoveFlowRunnable runnable = new RemoveFlowRunnable(
				flow.getFlowMods(), flow.getSwitches(), switchToIgnore);
		threadPool.getScheduledExecutor().submit(runnable);

		// ... and remove flow from all lists
		cookieToFlowMap.remove(flow.getCookie());
		for (IOFSwitch switchToRemove : flow.getSwitches()) {
			dpidToFlowMap.remove(switchToRemove.getId(), flow);
		}
		for (Link link : flow.getLinks()) {
			linkToFlowMap.remove(link, flow);
		}
		activeFlowSet.remove(flow);
		log.trace("flow {} removed", flow);
	}

	public void init(IFloodlightProviderService floodlightProvider,
			IThreadPoolService threadPool, IRoutingService routingModule,
			IOFSwitchService swManager) {
		this.floodlightProvider = floodlightProvider;
		this.threadPool = threadPool;
		this.routingModule = routingModule;
		this.switchManager = swManager;
		log.debug("Cookie App ID is {}", ofcontrolCookieAppId);
	}

	public short getFlowmodIdleTimeout() {
		return flowmodIdleTimeout;
	}

	public void setFlowmodIdleTimeout(short flowmodIdleTimeout) {
		this.flowmodIdleTimeout = flowmodIdleTimeout;
	}

	public short getFlowmodHardTimeout() {
		return flowmodHardTimeout;
	}

	public void setFlowmodHardTimeout(short flowmodHardTimeout) {
		this.flowmodHardTimeout = flowmodHardTimeout;
	}

	public U64 getOfcontrolCookieAppId() {
		return ofcontrolCookieAppId;
	}

	public void setOfcontrolCookieAppId(U64 ofcontrolCookieAppId) {
		this.ofcontrolCookieAppId = ofcontrolCookieAppId;
	}

	@Override
	public U64 getNewRandomCookie() {
		return ofcontrolCookieAppId.or(U64.of(randomNumberGegerator.nextLong() & 0x00000000ffffffffl));
	}
}
