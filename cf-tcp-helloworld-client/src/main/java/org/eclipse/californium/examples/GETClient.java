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

import org.eclipse.californium.core.CoapClient;
import org.eclipse.californium.core.CoapHandler;
import org.eclipse.californium.core.CoapResponse;
import org.eclipse.californium.core.Utils;
import org.eclipse.californium.core.network.tcp.CoapClientRegistry;
import org.eclipse.californium.core.network.tcp.TCPEndpoint;


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
	public static void main(String args[]) throws NoSuchAlgorithmException {
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
		
	private TCPEndpoint tcpClientEndpoint;
	
	public GETClient(final String resource) throws NoSuchAlgorithmException {
		
		final String address = "localhost";
		final int port = 5684;
		 
		 try {
			 final TLSServerConnectionConfig config = new TLSServerConnectionConfig(address, port);
			 final String keystore = "/Users/simonlemoy/Workspace_github/tls_tmp/server.ks";
			 config.secure("TLS", "password", new String[]{keystore}, "TLSv1.1", "TLSv1.2");
			tcpClientEndpoint  = new TCPEndpoint(config);
			final ConnectionRegistryImpl regImpl = new ConnectionRegistryImpl(tcpClientEndpoint, resource);
			tcpClientEndpoint.start();
			
			while(true) {
				 final Set<Entry<InetSocketAddress, CoapClient>> list = regImpl.getAllClient();
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
	
	private class ConnectionRegistryImpl extends CoapClientRegistry<InetSocketAddress> {
		
		private final String resource;

		public ConnectionRegistryImpl(final TCPEndpoint endpoint, final String resource) {
			super(true, endpoint);
			this.resource = resource;
		}

		@Override
		public InetSocketAddress configureCoapClient(final CoapClient client,  final InetSocketAddress remote) {
			System.out.println("new Client built for " + remote.toString());
			client.setURI(buildURI(remote, resource));
			return remote;
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
