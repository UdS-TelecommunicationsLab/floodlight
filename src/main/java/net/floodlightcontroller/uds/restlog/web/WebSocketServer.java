/**
 *    Copyright 2015, Saarland University
 *    Copyright 2010 Red Hat, Inc. (Netty Websocket code from 
 *      http://docs.jboss.org/netty/3.2/xref/org/jboss/netty/example/http/websocket/package-summary.html)
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

package net.floodlightcontroller.uds.restlog.web;

import static org.jboss.netty.channel.Channels.pipeline;
import static org.jboss.netty.handler.codec.http.HttpHeaders.isKeepAlive;
import static org.jboss.netty.handler.codec.http.HttpHeaders.setContentLength;
import static org.jboss.netty.handler.codec.http.HttpHeaders.Names.HOST;
import static org.jboss.netty.handler.codec.http.HttpMethod.GET;
import static org.jboss.netty.handler.codec.http.HttpResponseStatus.FORBIDDEN;
import static org.jboss.netty.handler.codec.http.HttpVersion.HTTP_1_1;

import java.net.InetSocketAddress;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Executors;

import net.floodlightcontroller.uds.restlog.RestLogModule;
import net.floodlightcontroller.uds.restlog.interfaces.IRingBufferAppender;
import net.floodlightcontroller.uds.restlog.interfaces.ISimpleAppender;
import net.floodlightcontroller.uds.restlog.internal.RingBufferAppender;

import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpChunkAggregator;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpRequestDecoder;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseEncoder;
import org.jboss.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import org.jboss.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import org.jboss.netty.handler.codec.http.websocketx.PongWebSocketFrame;
import org.jboss.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import org.jboss.netty.handler.codec.http.websocketx.WebSocketFrame;
import org.jboss.netty.handler.codec.http.websocketx.WebSocketServerHandshaker;
import org.jboss.netty.handler.codec.http.websocketx.WebSocketServerHandshakerFactory;
import org.jboss.netty.util.CharsetUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WebSocketServer extends SimpleChannelUpstreamHandler implements
		ISimpleAppender, ChannelPipelineFactory {

	protected static final Logger log = LoggerFactory
			.getLogger(WebSocketServer.class);

	private List<ChannelHandlerContext> openChannels;

	private WebSocketServerHandshaker handshaker;
	private final IRingBufferAppender appender;

	public WebSocketServer(IRingBufferAppender appender) {
		openChannels = new LinkedList<ChannelHandlerContext>();
		// Configure the server.
		ServerBootstrap bootstrap = new ServerBootstrap(
				new NioServerSocketChannelFactory(
						Executors.newCachedThreadPool(),
						Executors.newCachedThreadPool()));

		// Set up the event pipeline factory.
		bootstrap.setPipelineFactory(this);

		// Bind and start to accept incoming connections.
		bootstrap.bind(new InetSocketAddress(8081));

		log.info("WebSocket Server Logger listening on port 8081");

		this.appender = appender;
		appender.registerSimpleAppender(this);
	}

	// ISimpleAppender

	@Override
	public void doAppend(String event) {
		try {
			for (Iterator<ChannelHandlerContext> iterator = openChannels
					.iterator(); iterator.hasNext();) {
				ChannelHandlerContext ctx = iterator.next();
				if (RingBufferAppender.LOG_EACH_EVENT)
					System.out.println("Sending live log event to "
							+ ctx.getChannel().getRemoteAddress());
				synchronized (ctx) {
					if (!ctx.getChannel().isConnected()) {
						iterator.remove();
						continue;
					}
					ctx.getChannel().write(new TextWebSocketFrame(event));
				}
			}
		} catch (ConcurrentModificationException e) {
			// This might happen sometimes :/
		}
	}

	// ChannelPipelineFactory

	@Override
	public ChannelPipeline getPipeline() throws Exception {
		ChannelPipeline pipeline = pipeline();
		pipeline.addLast("decoder", new HttpRequestDecoder());
		pipeline.addLast("aggregator", new HttpChunkAggregator(65536));
		pipeline.addLast("encoder", new HttpResponseEncoder());
		pipeline.addLast("handler", this);
		return pipeline;
	}

	// SimpleChannelUpstreamHandler

	@Override
	public void messageReceived(ChannelHandlerContext ctx, MessageEvent e)
			throws Exception {
		Object msg = e.getMessage();
		if (msg instanceof HttpRequest) {
			handleHttpRequest(ctx, (HttpRequest) msg);
		} else if (msg instanceof WebSocketFrame) {
			handleWebSocketFrame(ctx, (WebSocketFrame) msg);
		}
	}

	private void handleWebSocketFrame(ChannelHandlerContext ctx,
			WebSocketFrame frame) {
		log.debug("Received Web Socket Frame");
		// Check for closing frame
		if (frame instanceof CloseWebSocketFrame) {
			openChannels.remove(ctx.getChannel());
			log.debug("WS Channel was closed {}", ctx.getChannel()
					.getRemoteAddress());
			handshaker.close(ctx.getChannel(), (CloseWebSocketFrame) frame);
			return;
		}
		if (frame instanceof PingWebSocketFrame) {
			log.trace("WS Channel was pinged by {}", ctx.getChannel()
					.getRemoteAddress());
			ctx.getChannel().write(
					new PongWebSocketFrame(frame.getBinaryData()));
			return;
		}
		if (!(frame instanceof TextWebSocketFrame)) {
			throw new UnsupportedOperationException(String.format(
					"%s frame types not supported", frame.getClass().getName()));
		}
		log.debug("WS channel received text command {}", ctx.getChannel()
				.getRemoteAddress());

		// Send the uppercase string back.
		String request = ((TextWebSocketFrame) frame).getText();
		synchronized (ctx) {
			switch (request) {
			case "get":
				log.debug("WS Channel {} asked for previous logs", ctx
						.getChannel().getRemoteAddress());
				for (String logString : appender.getAllEventStrings())
					ctx.getChannel().write(new TextWebSocketFrame(logString));
				break;
			case "register":
				log.debug("WS Channel {} asked to be registered for live logs",
						ctx.getChannel().getRemoteAddress());
				if (openChannels.contains(ctx)) {
					ctx.getChannel().write(
							new TextWebSocketFrame(
									"NOK: You are already registered."));
					break;
				}
				openChannels.add(ctx);
				ctx.getChannel().write(
						new TextWebSocketFrame("OK: You are now registered."));
				break;
			case "unregister":
				log.debug(
						"WS Channel {} asked to be unregistered for live logs",
						ctx.getChannel().getRemoteAddress());
				if (!openChannels.contains(ctx)) {
					ctx.getChannel().write(
							new TextWebSocketFrame(
									"NOK: You are not registered."));
					break;
				}
				openChannels.remove(ctx);
				ctx.getChannel()
						.write(new TextWebSocketFrame(
								"OK: You are now unregistered."));
				break;
			default:
				ctx.getChannel().write(
						new TextWebSocketFrame("NOK: Unknown command."));
			}
		}
	}

	private void handleHttpRequest(ChannelHandlerContext ctx, HttpRequest req) {
		// Allow only GET methods.
		if (req.getMethod() != GET) {
			sendHttpResponse(ctx, req, new DefaultHttpResponse(HTTP_1_1,
					FORBIDDEN));
			return;
		}

		// Websocket handshake
		WebSocketServerHandshakerFactory wsFactory = new WebSocketServerHandshakerFactory(
				getWebSocketLocation(req), null, false);
		handshaker = wsFactory.newHandshaker(req);
		if (handshaker == null) {
			wsFactory.sendUnsupportedWebSocketVersionResponse(ctx.getChannel());
		} else {
			log.debug("WS channel opened for {}", ctx.getChannel()
					.getRemoteAddress());
			handshaker.handshake(ctx.getChannel(), req).addListener(
					WebSocketServerHandshaker.HANDSHAKE_LISTENER);
			ctx.getChannel()
					.write(new TextWebSocketFrame(
							"Welcome to the Floodlight Log WebSocket. "
									+ "You have 3 commands at your disposal:\r\n"
									+ "\t\"get\" will give you all past logged messages\r\n"
									+ "\t\"register\" will register you for new messages\r\n"
									+ "\t\"unregister\" will remove that registration again\r\n"
									+ "Please note that it is recommended not to request past "
									+ "messages while you are still registered to new ones.\r\n"
									+ "The usual workflow will be to first call \"get\" and"
									+ "then \"register\" for all subsequent messages"));
		}

		// Send an error page otherwise.
		// sendHttpResponse(ctx, req, new DefaultHttpResponse(HTTP_1_1,
		// FORBIDDEN));
	}

	private void sendHttpResponse(ChannelHandlerContext ctx, HttpRequest req,
			HttpResponse res) {
		// Generate an error page if response status code is not OK (200).
		if (res.getStatus().getCode() != 200) {
			res.setContent(ChannelBuffers.copiedBuffer(res.getStatus()
					.toString(), CharsetUtil.UTF_8));
			setContentLength(res, res.getContent().readableBytes());
		}

		// Send the response and close the connection if necessary.
		ChannelFuture f = ctx.getChannel().write(res);
		if (!isKeepAlive(req) || res.getStatus().getCode() != 200) {
			f.addListener(ChannelFutureListener.CLOSE);
		}
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e)
			throws Exception {
		log.error("Exception in WS Server, closing channel", e.getCause());
		e.getChannel().close();
	}

	private static String getWebSocketLocation(HttpRequest req) {
		String location = req.headers().get(HOST) + "/";
		return "ws://" + location;
	}
}
