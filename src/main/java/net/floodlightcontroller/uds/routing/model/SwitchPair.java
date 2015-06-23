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

package net.floodlightcontroller.uds.routing.model;

import org.projectfloodlight.openflow.types.DatapathId;

public class SwitchPair {
	final DatapathId sourceDpid;
	final DatapathId destinationDpid;

	public SwitchPair(DatapathId datapathId, DatapathId datapathId2) {
		super();
		this.sourceDpid = datapathId;
		this.destinationDpid = datapathId2;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime
				* result
				+ (int) (destinationDpid.getLong() ^ (destinationDpid.getLong() >>> 32));
		result = prime * result
				+ (int) (sourceDpid.getLong() ^ (sourceDpid.getLong() >>> 32));
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		SwitchPair other = (SwitchPair) obj;
		if (destinationDpid.equals(other.destinationDpid))
			return false;
		if (sourceDpid.equals(other.sourceDpid))
			return false;
		return true;
	}

}