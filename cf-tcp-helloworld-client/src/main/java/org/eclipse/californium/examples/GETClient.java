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

import java.io.IOException;
import java.net.InetSocketAddress;

import org.eclipse.californium.core.CoapClient;
import org.eclipse.californium.core.CoapHandler;
import org.eclipse.californium.core.CoapResponse;
import org.eclipse.californium.core.Utils;
import org.eclipse.californium.core.network.config.NetworkConfig;
import org.eclipse.californium.core.network.tcp.TCPEndpoint;
import org.eclipse.californium.elements.ConnectorBuilder;
import org.eclipse.californium.elements.ConnectorBuilder.CommunicationRole;
import org.eclipse.californium.elements.ConnectorBuilder.ConnectionSemantic;
import org.eclipse.californium.elements.ConnectorBuilder.LayerSemantic;
import org.eclipse.californium.elements.StatefulConnector;
import org.eclipse.californium.elements.tcp.ConnectionInfo;
import org.eclipse.californium.elements.tcp.ConnectionInfo.ConnectionState;
import org.eclipse.californium.elements.tcp.ConnectionStateListener;


public class GETClient {

	/*
	 * Application entry point.
	 * 
	 */	
	public static void main(String args[]) {
		if (args.length == 0) {
			// display help
			System.out.println("Californium (Cf) GET Client");
			System.out.println("(c) 2014, Institute for Pervasive Computing, ETH Zurich");
			System.out.println();
			System.out.println("Usage: " + GETClient.class.getSimpleName() + " URI");
			System.out.println("  Resource: The CoAP Resource to get when device connect");
						
			System.out.println("No URI was specified: using default \"TEMP\" ");
			args = new String[]{"TEMP"};
		} 		
		
		final GETClient client = new GETClient(args[0]);

	}
	
	private final StatefulConnector conn;
	
	private TCPEndpoint tcpClientEndpoint;
	
	public GETClient(final String resource) {
		
		final InetSocketAddress bind = new InetSocketAddress("localhost", 5683);

		 conn = ConnectorBuilder
				.createTransportLayerBuilder(LayerSemantic.TCP)
				.setAddress(bind.getHostName()).setPort(bind.getPort())
				.makeSharable()
				.setConnectionSemantics(ConnectionSemantic.NIO)
				.setCommunicationRole(CommunicationRole.SERVER)
				.setConnectionStateListener(new ConnectionListener(resource))
				.buildStatfulConnector();
		 
		 try {
			tcpClientEndpoint  = new TCPEndpoint(conn, NetworkConfig.getStandard(), CommunicationRole.SERVER);
			tcpClientEndpoint.start();
		} catch (final IOException e) {
			System.err.println("Failed to start the Connector");
			e.printStackTrace();
		}
		 
	}
	
	private class ConnectionListener implements ConnectionStateListener {
		
		private final String resource;

		public ConnectionListener(final String resource) {
			this.resource = resource;
		}
		
		@Override
		public void stateChange(final ConnectionInfo info) {
			if(info.getConnectionState().equals(ConnectionState.NEW_INCOMING_CONNECT)) {

				System.out.println("New Connection from " + info.toString());
				final CoapClient client = new CoapClient();

				client.setEndpoint(tcpClientEndpoint);
				client.setURI(buildURI(info.getRemote(), resource));

				for (int i = 0; i < 15; i++) {
					 client.get(new CoapHandler() {
						
						@Override
						public void onLoad(final CoapResponse response) {
							if (response != null) {

								System.out.println(response.getCode());
								System.out.println(response.getOptions());
								System.out.println(response.getResponseText());

								System.out.println("\nADVANCED\n");
								// access advanced API with access to more details through
								// .advanced()
								System.out.println(Utils.prettyPrint(response));
							} else {
								System.out.println("No response received.");
							}
							
						}
						
						@Override
						public void onError() {
							System.out.println("ERROR processing the request");
						}
					});
					try {
						Thread.sleep((long) (Math.random() * 5000));
					} catch (final InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			}
		}
		
		
		/**
		 * coap://127.0.0.1:5683/${string_prompt}/
		 * @param address
		 * @return
		 */
		private String buildURI(final InetSocketAddress address, final String resource) {
			final StringBuilder sb = new StringBuilder();
			sb.append("coap://").append(address.getHostString()).append(':').append(address.getPort()).append('/').append(resource).append('/');
			return sb.toString();
		}
	}
}
