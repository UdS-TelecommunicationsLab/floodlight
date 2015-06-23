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

import java.util.Collections;
import java.util.List;

import org.projectfloodlight.openflow.types.IPv4Address;

public class MulticastGroupMember {
	
	private final IPv4Address ipAddress;
	private long lastContactTime;
	private boolean isIncludeMode;
	private List<IPv4Address> sources;
	
	
	public MulticastGroupMember(IPv4Address client, boolean isIncludeMode,
			List<IPv4Address> sources) {
		super();
		this.ipAddress = client;
		this.isIncludeMode = isIncludeMode;
		this.sources = sources;
		this.lastContactTime = System.currentTimeMillis();
	}


	public boolean isIncludeMode() {
		return isIncludeMode;
	}


	public void setIncludeMode(boolean isIncludeMode) {
		this.isIncludeMode = isIncludeMode;
	}


	public List<IPv4Address> getSources() {
		return sources;
	}


	public void setSources(List<IPv4Address> sources) {
		Collections.sort(sources);
		this.sources = sources;
	}


	public IPv4Address getIpAddress() {
		return ipAddress;
	}
	
	public void heardFromClient() {
		lastContactTime = System.currentTimeMillis();
	}
	
	public long getLastContactTime() {
		return lastContactTime;
	}
	
}
