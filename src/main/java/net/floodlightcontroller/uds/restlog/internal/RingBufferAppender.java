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

package net.floodlightcontroller.uds.restlog.internal;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import net.floodlightcontroller.uds.restlog.interfaces.IRingBufferAppender;
import net.floodlightcontroller.uds.restlog.interfaces.ISimpleAppender;

import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import ch.qos.logback.core.filter.Filter;
import ch.qos.logback.core.spi.FilterReply;

public class RingBufferAppender extends AppenderBase<ILoggingEvent> implements
		IRingBufferAppender {

	public static final int RING_BUFFER_DEFAULT_SIZE = 1000;
	public static final boolean LOG_EACH_EVENT = false; 
	
	private List<ISimpleAppender> appenders = new LinkedList<ISimpleAppender>();

	protected static final org.slf4j.Logger log = LoggerFactory.getLogger(RingBufferAppender.class);
	int ringBufferSize;
	PatternLayoutEncoder encoder;
	ByteArrayOutputStream baos;
	LinkedList<ILoggingEvent> ringBuffer;

	public void init() {
		if (this.started)
			this.stop();
		this.setName("RingBufferAppender for REST API");
		ringBufferSize = RING_BUFFER_DEFAULT_SIZE;
		ringBuffer = new LinkedList<ILoggingEvent>();
		encoder = new PatternLayoutEncoder();
		encoder.setPattern("%date{yyyy-MM-dd HH:mm:ss.S} %-5level [%logger{15}] %msg%n");
		baos = new ByteArrayOutputStream();
		log.debug("Init executed");
	}

	public void startUp() throws IOException {
		LoggerContext context = (LoggerContext) LoggerFactory
				.getILoggerFactory();
		encoder.init(baos);
		encoder.setContext(context);
		encoder.start();
		this.setContext(context);
		this.start();
		context.getLogger(Logger.ROOT_LOGGER_NAME).addAppender(this);
		log.info("Start-up completed with ring buffer size of {}", ringBufferSize);
	}

	@Override
	public int getRingBufferSize() {
		return ringBufferSize;
	}

	@Override
	public void setRingBufferSize(int newSize) {
		ringBufferSize = newSize;
		log.debug("New ring buffer size set to {}", ringBufferSize);
	}

	@Override
	synchronized protected void append(ILoggingEvent event) {
		ringBuffer.addLast(event);
		while (ringBuffer.size() > ringBufferSize) {
			ringBuffer.removeFirst();
		}
		if (LOG_EACH_EVENT)
			System.out.println("Event logged, new buffer size is " + ringBuffer.size() + ", event: " + encodeLoggingEvent(event));
		String logString = encodeLoggingEvent(event);
		for (ISimpleAppender appender : appenders) {
			appender.doAppend(logString);
		}
			
	}

	@Override
	synchronized public void setPatternLayoutEncoder(
			PatternLayoutEncoder encoder) throws IOException {
		encoder.setContext(context);
		encoder.init(baos);
		encoder.start();
		this.encoder = encoder;
		log.debug("New layout encoder set");
	}

	@Override
	synchronized public List<ILoggingEvent> getAllEvents() {
		return new ArrayList<ILoggingEvent>(ringBuffer);
	}

	@Override
	public List<ILoggingEvent> getFilteredEvents(
			Set<Filter<ILoggingEvent>> filters) {
		List<ILoggingEvent> allEvents = getAllEvents();
		List<ILoggingEvent> retVal = new ArrayList<>(allEvents.size());
		ArrayList<Filter<ILoggingEvent>> filterList = new ArrayList<Filter<ILoggingEvent>>();

		@SuppressWarnings("unchecked")
		Filter<ILoggingEvent>[] filterArray = (Filter<ILoggingEvent>[]) filterList
				.toArray();

		for (ILoggingEvent event : allEvents) {
			if (applyFilters(filterArray, event))
				retVal.add(event);
		}
		return retVal;
	}

	@Override
	public List<String> getAllEventStrings() {
		List<String> retVal = new ArrayList<>(ringBuffer.size());
		for (ILoggingEvent event : getAllEvents()) {
			retVal.add(encodeLoggingEvent(event));
		}
		return retVal;
	}

	@Override
	public List<String> getFilteredEventStrings(
			Set<Filter<ILoggingEvent>> filters) {
		List<String> retVal = new ArrayList<>(ringBuffer.size());
		ArrayList<Filter<ILoggingEvent>> filterList = new ArrayList<Filter<ILoggingEvent>>();

		@SuppressWarnings("unchecked")
		Filter<ILoggingEvent>[] filterArray = (Filter<ILoggingEvent>[]) filterList
				.toArray();

		for (ILoggingEvent event : getAllEvents()) {
			if (applyFilters(filterArray, event))
				retVal.add(encodeLoggingEvent(event));
		}
		return retVal;
	}

	private boolean applyFilters(Filter<ILoggingEvent>[] filters,
			ILoggingEvent event) {
		for (int i = 0; i < filters.length; i++) {
			if (!filters[i].decide(event).equals(FilterReply.ACCEPT))
				return false;
		}
		return true;
	}

	private String encodeLoggingEvent(ILoggingEvent event) {
		baos.reset();
		try {
			encoder.doEncode(event);
			return baos.toString("UTF-8");
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}
	}

	@Override
	public void registerSimpleAppender(ISimpleAppender appender) {
		appenders.add(appender);
	}

}
