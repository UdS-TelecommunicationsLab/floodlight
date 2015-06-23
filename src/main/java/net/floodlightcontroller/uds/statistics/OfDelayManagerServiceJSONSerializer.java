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

package net.floodlightcontroller.uds.statistics;
import java.io.IOException;
import java.util.Map;

import net.floodlightcontroller.routing.Link;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

public class OfDelayManagerServiceJSONSerializer extends JsonSerializer<OfDelayManagerService> {

    /**
     * Handles serialization for OfDelayManagerService
     */
    @Override
    public void serialize(OfDelayManagerService delayms, JsonGenerator jGen,
                          SerializerProvider arg2) throws IOException,
                                                  JsonProcessingException {
        jGen.writeStartObject();
        
        Map<Link, Double> delays = delayms.getDelay();
        jGen.writeStartArray();
        for(Link link : delays.keySet()){
        	// link
        	// source
        	jGen.writeStringField("SrcSwitchDpid", link.getSrc().toString());
        	jGen.writeStringField("SrcSwitchPort", link.getSrcPort().toString());
        	// dst
        	jGen.writeStringField("DstSwitchDpid", link.getDst().toString());
        	jGen.writeStringField("DstSwitchPort", link.getDstPort().toString());
        	// /link
        	
        	// delay
        	double delay = delays.get(link)==null ? null : ((double)(delays.get(link)/1000000.0));
        	jGen.writeNumberField("DelayMs", delay);
        }
        jGen.writeEndArray();
        
        jGen.writeEndObject();
    }

    /**
     * Tells SimpleModule that we are the serializer for OfDelayManagerService
     */
    @Override
    public Class<OfDelayManagerService> handledType() {
        return OfDelayManagerService.class;
    }
}
