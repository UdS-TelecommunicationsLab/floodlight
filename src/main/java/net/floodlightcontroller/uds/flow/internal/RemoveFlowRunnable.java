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

import java.util.Iterator;
import java.util.List;

import net.floodlightcontroller.core.IOFSwitch;

import org.projectfloodlight.openflow.protocol.OFFlowMod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RemoveFlowRunnable implements Runnable {

	protected static final Logger log = LoggerFactory
			.getLogger(RemoveFlowRunnable.class);

	private final List<OFFlowMod> flowMods;
	private final List<IOFSwitch> switches;
	private final IOFSwitch switchToIgnore;

	public RemoveFlowRunnable(List<OFFlowMod> flowMods,
			List<IOFSwitch> switches, IOFSwitch switchToIgnore) {
		super();
		this.flowMods = flowMods;
		this.switches = switches;
		this.switchToIgnore = switchToIgnore;
	}

	@Override
	public void run() {
		// Iterate over all switches that are part of this flow
		Iterator<OFFlowMod> flowModIterator = flowMods.iterator();
		Iterator<IOFSwitch> switchIterator = switches.iterator();
		while (flowModIterator.hasNext()) {
			OFFlowMod flowMod = flowModIterator.next();
			IOFSwitch sw = switchIterator.next();

			// Ignore switch that was removed / already sent FlowRemoved
			if (switchToIgnore != null && sw.equals(switchToIgnore))
				continue;

			// Build Flow Delete for FlowMod
			OFFlowMod.Builder fmb;
			fmb = sw.getOFFactory().buildFlowDelete();
			fmb.setMatch(flowMod.getMatch());
			fmb.setCookie(flowMod.getCookie());
			fmb.setIdleTimeout(flowMod.getIdleTimeout());
			fmb.setHardTimeout(flowMod.getHardTimeout());
			fmb.setOutPort(flowMod.getOutPort());
			fmb.setBufferId(flowMod.getBufferId());

			// Write to switch
			sw.write(fmb.build());
			sw.flush();
			log.debug("Removed flow {} from switch {}", flowMod, sw);
		}

	}

}
