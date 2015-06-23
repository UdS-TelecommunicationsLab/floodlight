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

package net.floodlightcontroller.uds.flow.interfaces;

import java.util.List;
import java.util.Map;

import org.projectfloodlight.openflow.protocol.OFFlowMod;
import org.projectfloodlight.openflow.protocol.match.Match;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.U64;

import com.google.common.collect.ArrayListMultimap;

import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.routing.Link;
import net.floodlightcontroller.uds.flow.model.Flow;

/**
 * The Interface IFlowService. It represents the FlowService, which keeps track
 * of flows that are set by our routing module. It makes them accessible via
 * this and the REST API and takes care, that no partial flows stay active,
 * meaning that if one part of a flow times out, the whole flow (across all
 * switches) also times out and is removed. This ought to prevent the switches
 * from running into odd behavior, where some parts of a global flow are still
 * active, while others are not, which would lead to packet loss at inter-switch
 * links. This is not desirable due to the way we handle flow creation (reactive
 * global switch).
 * 
 * This interface therefore has several ways to access the flows, sorted by
 * cookie id, switch and link.
 * 
 * It also allows for adding and removing (static) flows into/from bookkeeping.
 * Please read the hints for that at the respective functions.
 * 
 * This modules also allows for controlling flow timeouts and the cookies.
 * 
 * @author Tobias Theobald <theobald@intel-vci.uni-saarland.de>
 */
public interface IFlowService extends IFloodlightService {

	/**
	 * Gets the cookie to flow map.
	 *
	 * @return the cookie to flow map
	 */
	public Map<U64, Flow> getCookieToFlowMap();

	/**
	 * Gets the dpid to flow map.
	 *
	 * @return the dpid to flow map
	 */
	public ArrayListMultimap<DatapathId, Flow> getDpidToFlowMap();

	/**
	 * Gets the link to flow map.
	 *
	 * @return the link to flow map
	 */
	public ArrayListMultimap<Link, Flow> getLinkToFlowMap();

	/**
	 * Adds a flow into bookkeeping. Please note, that this function does NOT
	 * set the flow on the corresponding switches, it only makes sure, anyone
	 * using our API knows about them and they are removed as soon as one
	 * component times out. In order for timeouts to work, the cookie has to
	 * start (first 32 bits) with our cookie app id
	 *
	 * @param switches
	 *            the switches involved in the flow
	 * @param links
	 *            the links that are part of the route
	 * @param flowMods
	 *            the flow mods that were used to set the flow
	 * @param cookie
	 *            the cookie of the flow, which is global to all involved
	 *            flowMods
	 * @param match
	 *            the match of the packet
	 * @param description
	 *            the description string, denoting what the flow is for
	 *            (optional)
	 * @return the new flow object containing all information
	 * @see getOfcontrolCookieAppId
	 */
	public Flow addFlow(List<IOFSwitch> switches, List<Link> links,
			List<OFFlowMod> flowMods, U64 cookie, Match match,
			String description);

	/**
	 * Removes a flow from bookkeeping while also sending a flowMod remove
	 * message to all switches except the given one. For performance reason,
	 * this method call is asynchronous and the flows will be removed as soon as
	 * a thread becomes available.
	 *
	 * @param flow
	 *            the flow to delete
	 * @param switchToIgnore
	 *            the switch to ignore (may be null)
	 */
	public void removeFlow(Flow flow, IOFSwitch switchToIgnore);

	/**
	 * Gets the flowmod idle timeout.
	 *
	 * @return the flowmod idle timeout
	 */
	public short getFlowmodIdleTimeout();

	/**
	 * Sets the flowmod idle timeout. This denotes the time in seconds after
	 * which a flow is removed if no packet matches it. Defaults to 10s, can be
	 * modified in the configuration file.
	 *
	 * @param flowmodIdleTimeout
	 *            the new flowmod idle timeout
	 */
	public void setFlowmodIdleTimeout(short flowmodIdleTimeout);

	/**
	 * Gets the flowmod hard timeout.
	 *
	 * @return the flowmod hard timeout
	 */
	public short getFlowmodHardTimeout();

	/**
	 * Sets the flowmod hard timeout. This denotes the time in seconds after
	 * which a flow is removed, no matter how many packets run over it. Defaults
	 * to 30s for debugging reasons, can be modified in the configuration file.
	 *
	 * @param flowmodHardTimeout
	 *            the new flowmod hard timeout
	 */
	public void setFlowmodHardTimeout(short flowmodHardTimeout);

	/**
	 * Gets the ofcontrol cookie app id.
	 *
	 * @return the ofcontrol cookie app id
	 */
	public U64 getOfcontrolCookieAppId();

	/**
	 * Sets the ofcontrol cookie app id. This should be a U64 in which the first
	 * 32 bit are fixed. Defaults to 0xbadcab1e00000000l
	 *
	 * @param ofcontrolCookieAppId
	 *            the new ofcontrol cookie app id
	 */
	public void setOfcontrolCookieAppId(U64 ofcontrolCookieAppId);

	/**
	 * Generates a new random cookie with the set app id 
	 *
	 * @return the new random cookie
	 */
	public U64 getNewRandomCookie();

}
