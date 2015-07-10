/*******************************************************************************
 * Copyright (c) 2014 Institute for Pervasive Computing, ETH Zurich and others.
 * 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Eclipse Distribution License v1.0 which accompany this distribution.
 * 
 * The Eclipse Public License is available at
 *    http://www.eclipse.org/legal/epl-v10.html
 * and the Eclipse Distribution License is available at
 *    http://www.eclipse.org/org/documents/edl-v10.html.
 * 
 * Contributors:
 *    Matthias Kovatsch - creator and main architect
 *    Martin Lanter - architect and re-implementation
 *    Dominique Im Obersteg - parsers and initial implementation
 *    Daniel Pauli - parsers and initial implementation
 *    Kai Hudalla - logging
 ******************************************************************************/
package org.eclipse.californium.core.network.stack;

import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.logging.Logger;

import org.eclipse.californium.core.coap.EmptyMessage;
import org.eclipse.californium.core.coap.Request;
import org.eclipse.californium.core.coap.Response;
import org.eclipse.californium.core.network.Exchange;
import org.eclipse.californium.core.network.Exchange.Origin;
import org.eclipse.californium.core.network.Outbox;
import org.eclipse.californium.core.network.config.NetworkConfig;
import org.eclipse.californium.core.server.MessageDeliverer;
import org.eclipse.californium.elements.Connector;


/**
 * The CoAPStack builds up the stack of CoAP layers that process the CoAP
 * protocol.
 * <p>
 * The complete process for incoming and outgoing messages is visualized below.
 * The class <code>CoapStack</code> builds up the part between the Stack Top and
 * Bottom.
 * <hr><blockquote><pre>
 * +-----------------------+
 * | {@link MessageDeliverer}      |
 * +-------------A---------+
 *               A
 *             * A
 * +-----------+-A---------+
 * |       CoAPEndpoint    |
 * |           v A         |
 * |           v A         |
 * | +---------v-+-------+ |
 * | | Stack Top         | |
 * | +-------------------+ |
 * | | {@link ObserveLayer}      | |
 * | +-------------------+ |
 * | | {@link BlockwiseLayer}    | |
 * | +-------------------+ |
 * | | {@link TokenLayer}        | |
 * | +-------------------+ |
 * | | {@link ReliabilityLayer}  | |
 * | +-------------------+ |
 * | | Stack Bottom      | |
 * | +---------+-A-------+ |
 * |           v A         |
 * |         Matcher       |
 * |           v A         |
 * |       Interceptor     |
 * |           v A         |
 * +-----------v-A---------+
 *             v A 
 *             v A 
 * +-----------v-+---------+
 * | {@link Connector}             |
 * +-----------------------+
 * </pre></blockquote><hr>
 */
public class CoapStack {

	/** The LOGGER. */
	final static Logger LOGGER = Logger.getLogger(CoapStack.class.getCanonicalName());

	private final List<Layer> layers;
	private final Outbox outbox;
	private final StackTopAdapter top;
	private StackBottomAdapter bottom;
	private MessageDeliverer deliverer;
	
	public CoapStack(final NetworkConfig config, final Outbox outbox) {
		this.top = new StackTopAdapter();
		this.outbox = outbox;
		
		ReliabilityLayer reliabilityLayer;
		if (config.getBoolean(NetworkConfig.Keys.USE_CONGESTION_CONTROL) == true) {
			reliabilityLayer = CongestionControlLayer.newImplementation(config);
			LOGGER.config("Enabling congestion control: " + reliabilityLayer.getClass().getSimpleName());
		} else {
			reliabilityLayer = new ReliabilityLayer(config);
		}
		
		this.layers = 
				new Layer.TopDownBuilder()
				.add(top)
				.add(new ObserveLayer(config))
				.add(new BlockwiseLayer(config))
				.add(new TokenLayer(config))
				.add(reliabilityLayer)
				.add(bottom = new StackBottomAdapter())
				.create();
		
		// make sure the endpoint sets a MessageDeliverer
	}
	
	// delegate to top
	public void sendRequest(final Request request) {
		top.sendRequest(request);
	}

	// delegate to top
	public void sendResponse(final Exchange exchange, final Response response) {
		top.sendResponse(exchange, response);
	}

	// delegate to top
	public void sendEmptyMessage(final Exchange exchange, final EmptyMessage message) {
		top.sendEmptyMessage(exchange, message);
	}

	// delegate to bottom
	public void receiveRequest(final Exchange exchange, final Request request) {
		bottom.receiveRequest(exchange, request);
	}

	// delegate to bottom
	public void receiveResponse(final Exchange exchange, final Response response) {
		bottom.receiveResponse(exchange, response);
	}

	// delegate to bottom
	public void receiveEmptyMessage(final Exchange exchange, final EmptyMessage message) {
		bottom.receiveEmptyMessage(exchange, message);
	}

	public void setExecutor(final ScheduledExecutorService executor) {
		for (final Layer layer:layers)
			layer.setExecutor(executor);
	}
	
	public void setDeliverer(final MessageDeliverer deliverer) {
		this.deliverer = deliverer;
	}
	
	private class StackTopAdapter extends AbstractLayer {
		
		public void sendRequest(final Request request) {
			final Exchange exchange = new Exchange(request, Origin.LOCAL);
			sendRequest(exchange, request); // layer method
		}
		
		@Override
		public void sendRequest(final Exchange exchange, final Request request) {
			exchange.setRequest(request);
			super.sendRequest(exchange, request);
		}
		
		@Override
		public void sendResponse(final Exchange exchange, final Response response) {
			exchange.setResponse(response);
			super.sendResponse(exchange, response);
		}
		
		@Override
		public void receiveRequest(final Exchange exchange, final Request request) {
			// if there is no BlockwiseLayer we still have to set it
			if (exchange.getRequest() == null)
				exchange.setRequest(request);
			if (deliverer != null) {
				deliverer.deliverRequest(exchange);
			} else {
				LOGGER.severe("Top of CoAP stack has no deliverer to deliver request");
			}
		}

		@Override
		public void receiveResponse(final Exchange exchange, final Response response) {
			if (!response.getOptions().hasObserve())
				exchange.setComplete();
			if (deliverer != null) {
				deliverer.deliverResponse(exchange, response); // notify request that response has arrived
			} else {
				LOGGER.severe("Top of CoAP stack has no deliverer to deliver response");
			}
		}
		
		@Override
		public void receiveEmptyMessage(final Exchange exchange, final EmptyMessage message) {
			// When empty messages reach the top of the CoAP stack we can ignore them. 
		}
	}
	
	private class StackBottomAdapter extends AbstractLayer {
	
		@Override
		public void sendRequest(final Exchange exchange, final Request request) {
			outbox.sendRequest(exchange, request);
		}

		@Override
		public void sendResponse(final Exchange exchange, final Response response) {
			outbox.sendResponse(exchange, response);
		}

		@Override
		public void sendEmptyMessage(final Exchange exchange, final EmptyMessage message) {
			outbox.sendEmptyMessage(exchange, message);
		}
		
	}
	
	public boolean hasDeliverer() {
		return deliverer != null;
	}
}
