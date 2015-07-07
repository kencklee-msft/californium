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
import java.security.NoSuchAlgorithmException;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.eclipse.californium.core.CoapClient;
import org.eclipse.californium.core.CoapHandler;
import org.eclipse.californium.core.CoapResponse;
import org.eclipse.californium.core.Utils;
import org.eclipse.californium.core.network.config.NetworkConfig;
import org.eclipse.californium.core.network.tcp.TCPServerEndpoint;
import org.eclipse.californium.elements.config.TCPConnectionConfig;
import org.eclipse.californium.elements.tcp.server.TcpServerConnector;


/**
 * needs to be redone/cleaned up
 * @author simonlemoy
 *
 */
public class GETClient {

	/*
	 * Application entry point.
	 *
	 */
	public static void main(String args[]) throws NoSuchAlgorithmException, ExecutionException {
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

	private TCPServerEndpoint tcpClientEndpoint;

	public GETClient(final String resource) throws NoSuchAlgorithmException, ExecutionException {

		final String address = "localhost";
		final int port = 5684;

		try {
			final TLSServerConnectionConfig config = new TLSServerConnectionConfig(address, port);
			final String keystore = "/Users/simonlemoy/Workspace_github/tls_tmp/server.ks";
			config.secure("TLS", "password", new String[]{keystore}, "TLSv1.1", "TLSv1.2");
			tcpClientEndpoint = new TcpServerEndpointImpl(config, resource);
			final Future<?> connected  = tcpClientEndpoint.start();
			connected.get();

			while(true) {
				final Set<Entry<InetSocketAddress, CoapClient>> list = tcpClientEndpoint.getAllClient();
				for(final Entry<InetSocketAddress, CoapClient> clientEntry : list) {
					final CoapClient client = clientEntry.getValue();
					System.out.println("requesting resource for " + clientEntry.getKey().toString());

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
				}
				Thread.sleep((long) (Math.random() * 5000));

			}
		} catch (final IOException e) {
			System.err.println("Failed to start the Connector");
			e.printStackTrace();
		} catch (final InterruptedException e) {
			e.printStackTrace();
		}
	}

	private class TcpServerEndpointImpl extends TCPServerEndpoint {

		private final String resource;

		public TcpServerEndpointImpl(final TCPConnectionConfig cfg, final String resource) {
			super(new TcpServerConnector(cfg), NetworkConfig.getStandard());
			this.resource = resource;
		}

		@Override
		public void configureCoapClient(final CoapClient client,  final InetSocketAddress remote) {
			System.out.println("new Client built for " + remote.toString());
			client.setURI(buildURI(remote, resource));
		}
	}

	/**
	 * coap://127.0.0.1:5683/${string_prompt}/
	 * @param address
	 * @return
	 */
	private String buildURI(final InetSocketAddress address, final String resource) {
		final StringBuilder sb = new StringBuilder();
		sb.append("coap://").append(address.getHostName()).append(':').append(address.getPort()).append('/').append(resource).append('/');
		return sb.toString();
	}
}
