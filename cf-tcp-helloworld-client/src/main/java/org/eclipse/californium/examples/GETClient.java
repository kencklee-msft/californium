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

import javax.net.ssl.SSLContext;

import org.eclipse.californium.core.CoapClient;
import org.eclipse.californium.core.CoapHandler;
import org.eclipse.californium.core.CoapResponse;
import org.eclipse.californium.core.Utils;
import org.eclipse.californium.core.network.config.NetworkConfig;
import org.eclipse.californium.core.network.tcp.TcpServerEndpoint;
import org.eclipse.californium.elements.tcp.server.TlsServerConnector;
import org.eclipse.californium.elements.tcp.server.TlsServerConnector.SSLClientCertReq;


/**
 * needs to be redone/cleaned up
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

	private TcpServerEndpoint tcpServerEndpoint;

	public GETClient(final String resource) throws NoSuchAlgorithmException, ExecutionException {

		final String address = "localhost";
		final int port = 5684;

		try {
			final String keystore = "/Users/simonlemoy/Workspace_github/tls_tmp/server.ks";
			final SSLContextProvider contextProvider = new SSLContextProvider("TLS", "password", new String[]{keystore});
			final String[] tlsVersions = new String[] {"TLS", "TLSv1.1", "TLSv1.2"};
			tcpServerEndpoint = new TcpServerEndpointImpl(address, port, contextProvider.getSingletonSSLContext(), SSLClientCertReq.NONE, tlsVersions, resource);
			final Future<?> connected  = tcpServerEndpoint.start();
			connected.get();

			while(true) {
				final Set<Entry<InetSocketAddress, CoapClient>> list = tcpServerEndpoint.getAllClient();
				for(final Entry<InetSocketAddress, CoapClient> clientEntry : list) {
					final CoapClient client = clientEntry.getValue();
					System.out.println("requesting resource for " + clientEntry.getKey());

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

	private class TcpServerEndpointImpl extends TcpServerEndpoint {

		private final String resource;

		public TcpServerEndpointImpl(final String remoteAddress, final int remotePort, final SSLContext sslContext, final SSLClientCertReq clientCertRequest, final String[] supportedTLSVersions, final String resource) {
			super(new TlsServerConnector(remoteAddress, remotePort, sslContext, clientCertRequest, supportedTLSVersions), NetworkConfig.getStandard());
			this.resource = resource;
		}

		@Override
		public CoapClient createCoapClient(final InetSocketAddress remote) {
			final CoapClient client = super.createCoapClient(remote);
			client.setURI(client.getURI() + "/" + resource);
			System.out.println("new Client built for " + remote);
			return client;
		}
	}

}
