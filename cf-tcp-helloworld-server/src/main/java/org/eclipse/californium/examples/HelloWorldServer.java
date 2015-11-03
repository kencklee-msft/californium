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
 ******************************************************************************/
package org.eclipse.californium.examples;

import java.net.InetSocketAddress;
import java.net.SocketException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;

import org.eclipse.californium.core.CoapResource;
import org.eclipse.californium.core.CoapServer;
import org.eclipse.californium.core.network.CoAPEndpoint;
import org.eclipse.californium.core.network.config.NetworkConfig;
import org.eclipse.californium.core.server.resources.CoapExchange;
import org.eclipse.californium.elements.tcp.client.TlsClientConnector;


public class HelloWorldServer extends CoapServer {

	/*
	 * Application entry point.
	 */
	public static void main(String[] args) throws KeyManagementException, SSLException, NoSuchAlgorithmException {

		try {

			if(args == null || args.length == 0) {
				System.out.println("No resource to expose was specified, exposing default value \"TEMP\"");
				args = new String[]{"TEMP"};
			}

			final String[] list = args;
			final String address = "localhost";
			final int port = 5684;

			// create server
			final HelloWorldServer server = new HelloWorldServer(list);
			final SSLContext sslContext = SSLContext.getInstance("TLSv1.2");
			sslContext.init(null, null, null);
			final CoAPEndpoint tcpClientEndpoint = new CoAPEndpoint(new TlsClientConnector(address, port, sslContext), NetworkConfig.getStandard());
			//            tcpClientEndpoint.addConnectionStateListener(new ConnectionStateListener() {
			//
			//				@Override
			//				public void stateChange(final ConnectionInfo info) {
			//					System.out.println(info.toString());
			//				}
			//			});
			server.addEndpoint(tcpClientEndpoint);
			final Map<InetSocketAddress, Future<?>> connected = server.start();
			for(final Entry<InetSocketAddress, Future<?>> epState : connected.entrySet()) {
				try {
					epState.getValue().get();
				} catch (final InterruptedException e) {
					e.printStackTrace();
				} catch (final ExecutionException e) {
					e.printStackTrace();
				}
			}

		} catch (final SocketException e) {

			System.err.println("Failed to initialize server: " + e.getMessage());
		}
	}

	/*
	 * Constructor for a new Hello-World server. Here, the resources
	 * of the server are initialized.
	 */
	public HelloWorldServer(final String... resourceNames) throws SocketException {

		// provide an instance of a Hello-World resource
		for(final String resourceName : resourceNames) {
			System.out.println("adding resource " + resourceName);
			add(new HelloWorldResource(resourceName));
		}
	}

	/*
	 * Definition of the Hello-World Resource
	 */
	class HelloWorldResource extends CoapResource {
		String internalID = " ID:" +  String.valueOf(Math.abs(Math.random()*100));

		public HelloWorldResource(final String id) {

			// set resource identifier
			super(id);

			// set display name
			getAttributes().setTitle("Hello-World Resource");
		}

		@Override
		public void handleGET(final CoapExchange exchange) {
			System.out.println("GET Received: " + exchange.toString());
			// respond to the request
			exchange.respond(String.valueOf(getName()) + internalID + " --> value: " + Math.random()*31);
		}
	}
}
