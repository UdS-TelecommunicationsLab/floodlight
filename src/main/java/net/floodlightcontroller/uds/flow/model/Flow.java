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

package net.floodlightcontroller.uds.flow.model;

import java.util.List;

import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.routing.Link;

import org.projectfloodlight.openflow.protocol.OFFlowMod;
import org.projectfloodlight.openflow.protocol.match.Match;
import org.projectfloodlight.openflow.types.U64;

public class Flow {
	private final List<IOFSwitch> switches;
	private final List<Link> links;
	private final List<OFFlowMod> flowMods;
	private final U64 cookie;
	private final Match match;
	private final String description;

	public Flow(List<IOFSwitch> switches, List<Link> links,
			List<OFFlowMod> flowMods, U64 cookie, Match match, String description) {
		super();
		this.switches = switches;
		this.links = links;
		this.flowMods = flowMods;
		this.cookie = cookie;
		this.match = match;
		this.description = description;
	}

	public List<IOFSwitch> getSwitches() {
		return switches;
	}

	public List<Link> getLinks() {
		return links;
	}

	public List<OFFlowMod> getFlowMods() {
		return flowMods;
	}

	public U64 getCookie() {
		return cookie;
	}
	
	public Match getMatch() {
		return match;
	}

	public String getDescription() {
		return description;
	}

	@Override
	public int hashCode() {
		return (int) (cookie.getValue() & 0xffffffff);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Flow other = (Flow) obj;
		if (!cookie.equals(other.cookie))
			return false;
		if (flowMods == null) {
			if (other.flowMods != null)
				return false;
		} else if (!flowMods.equals(other.flowMods))
			return false;
		if (links == null) {
			if (other.links != null)
				return false;
		} else if (!links.equals(other.links))
			return false;
		if (switches == null) {
			if (other.switches != null)
				return false;
		} else if (!switches.equals(other.switches))
			return false;
		return true;
	}
	
	

}