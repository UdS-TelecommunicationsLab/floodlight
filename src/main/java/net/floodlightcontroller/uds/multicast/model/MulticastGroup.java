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

package net.floodlightcontroller.uds.multicast.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.projectfloodlight.openflow.types.IPv4Address;

public class MulticastGroup {

	// Address of this multicast group
	private final IPv4Address groupAddress;

	// Client ip -> Timestamp when we last heard of client
	private final Map<IPv4Address, MulticastGroupMember> memberMap;

	// Constructor for any sources

	public MulticastGroup(IPv4Address groupAddress) {
		this.groupAddress = groupAddress;
		this.memberMap = new HashMap<>();
	}

	public IPv4Address getGroupAddress() {
		return groupAddress;
	}

	public Set<IPv4Address> getAllClients() {
		return memberMap.keySet();
	}

	public boolean removeTimedOutClients(long timeoutSeconds) {
		long borderTime = System.currentTimeMillis() - timeoutSeconds * 1000;
		for (Iterator<MulticastGroupMember> iterator = memberMap.values()
				.iterator(); iterator.hasNext();) {
			MulticastGroupMember multicastGroupMember = iterator.next();
			if (multicastGroupMember.getLastContactTime() < borderTime) {
				iterator.remove();
			}
		}
		return memberMap.isEmpty();
	}

	public boolean signalFromClient(IPv4Address client,
			List<IPv4Address> sources, boolean isIncludeMode, boolean changed) {
		MulticastGroupMember member;
		if (memberMap.containsKey(client)) {
			member = memberMap.get(client);
			if (changed) {
				// Check if client is to be removed
				if (isIncludeMode && sources == null) {
					return clientLeaveOrTimeout(client);
				}
				member.setIncludeMode(isIncludeMode);
				member.setSources(sources);
			}
		} else {
			memberMap.put(client, member = new MulticastGroupMember(client,
					isIncludeMode, sources));
		}
		return false;
	}

	public boolean clientLeaveOrTimeout(IPv4Address client) {
		memberMap.remove(client);
		return memberMap.isEmpty();
	}

	public boolean changeClientSources(IPv4Address clientIP,
			Collection<IPv4Address> sources, boolean isAllowNewSources) {
		if (!memberMap.containsKey(clientIP) || sources == null) {
			throw new IllegalArgumentException(
					"!memberMap.containsKey(clientIP) || sources == null");
		}
		MulticastGroupMember member = memberMap.get(clientIP);

		// save sources in List so we can operate on them more easily
		ArrayList<IPv4Address> currentAddresses = new ArrayList<>();
		if (member.getSources() != null)
			currentAddresses.addAll(member.getSources());

		if (isAllowNewSources == member.isIncludeMode()) {
			currentAddresses.addAll(sources);
		} else {
			currentAddresses.removeAll(sources);
		}
		Collections.sort(currentAddresses);
		// Check if there are no more sources left
		if (currentAddresses.size() == 0) {
			if (member.isIncludeMode()) {
				return clientLeaveOrTimeout(clientIP);
			} else {
				member.setSources(null);
				return false;
			}
		}

		// Remove duplicates
		Iterator<IPv4Address> iterator = currentAddresses.iterator();
		IPv4Address previous = iterator.next();
		while (iterator.hasNext()) {
			IPv4Address current = iterator.next();
			if (current.equals(previous)) {
				iterator.remove();
			} else {
				previous = current;
			}
		}

		// Write new sources back
		member.setSources(currentAddresses);
		return false;
	}

	public IPv4Address[] getInterestedTargets(IPv4Address source) {
		// TODO cache this
		ArrayList<IPv4Address> targets = new ArrayList<>(memberMap.size());
		for (MulticastGroupMember member : memberMap.values()) {
			if (!member.getIpAddress().equals(source)
					&& (member.isIncludeMode() == (member.getSources() != null && member
							.getSources().contains(source)))) {
				targets.add(member.getIpAddress());
			}
		}
		return targets.toArray(new IPv4Address[0]);
	}

}
