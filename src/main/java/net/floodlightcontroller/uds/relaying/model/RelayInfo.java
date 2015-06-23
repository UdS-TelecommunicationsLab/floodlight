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

package net.floodlightcontroller.uds.relaying.model;


import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.MacAddress;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.TransportPort;

import net.floodlightcontroller.devicemanager.SwitchPort;

public class RelayInfo {
	private final IPv4Address relayIpAddress;
	private final TransportPort relayTransportPort;
	private final MacAddress relayMacAddress;
	private final DatapathId relaySwitch;
	private final OFPort relaySwitchPort;
	private final SwitchPort attachmentPoint;

	public SwitchPort getAttachmentPoint() {
		return attachmentPoint;
	}

	@Override
	public String toString() {
		return "RelayInfo [relayIpAddress=" + relayIpAddress
				+ ", relayTransportPort=" + relayTransportPort
				+ ", relayMacAddress=" + relayMacAddress + ", relaySwitch="
				+ relaySwitch + ", relaySwitchPort=" + relaySwitchPort
				+ ", attachmentPoint=" + attachmentPoint + "]";
	}

	public RelayInfo(String relayIpAddress, String relayTransportPort,
			String relayMac, String relaySwitch, String relaySwitchPort) {
		this.relayIpAddress = IPv4Address.of(relayIpAddress);
		this.relayTransportPort = TransportPort.of(Integer
				.valueOf(relayTransportPort));
		this.relayMacAddress = MacAddress.of(relayMac);
		this.relaySwitch = DatapathId.of(relaySwitch);
		this.relaySwitchPort = OFPort.of(Integer.valueOf(relaySwitchPort));
		this.attachmentPoint = new SwitchPort(this.relaySwitch,
				this.relaySwitchPort);
	}

	public IPv4Address getRelayIpAddress() {
		return relayIpAddress;
	}

	public TransportPort getRelayTransportPort() {
		return relayTransportPort;
	}

	public MacAddress getRelayMacAddress() {
		return relayMacAddress;
	}

	public DatapathId getRelaySwitch() {
		return relaySwitch;
	}

	public OFPort getRelaySwitchPort() {
		return relaySwitchPort;
	}

}
