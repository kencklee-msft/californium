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

import org.eclipse.californium.core.CoapResource;
import org.eclipse.californium.core.CoapServer;
import org.eclipse.californium.core.network.config.NetworkConfig;
import org.eclipse.californium.core.network.tcp.TCPEndpoint;
import org.eclipse.californium.core.server.resources.CoapExchange;
import org.eclipse.californium.elements.ConnectorBuilder;
import org.eclipse.californium.elements.ConnectorBuilder.CommunicationRole;
import org.eclipse.californium.elements.ConnectorBuilder.ConnectionSemantic;
import org.eclipse.californium.elements.ConnectorBuilder.LayerSemantic;
import org.eclipse.californium.elements.StatefulConnector;
import org.eclipse.californium.elements.tcp.ConnectionInfo;
import org.eclipse.californium.elements.tcp.ConnectionStateListener;


public class HelloWorldServer extends CoapServer {
    
    /*
     * Application entry point.
     */
    public static void main(String[] args) {
        
        try {
            
        	if(args == null || args.length == 0) {
        		System.out.println("No resource to expose was specifed, exposing default value \"TEMP\"");
        		args = new String[]{"TEMP"};
        	}
        	
        	final String[] list = args;
        	final InetSocketAddress remote = new InetSocketAddress("localhost", 5683);
        	
        	final StatefulConnector conn = ConnectorBuilder.createTransportLayerBuilder(LayerSemantic.TCP)
					 .setAddress(remote.getHostName())
					 .setPort(remote.getPort())
					 .setConnectionSemantics(ConnectionSemantic.NIO)
					 .setCommunicationRole(CommunicationRole.CLIENT)
					 .setConnectionStateListener(new ConnectionStateListener() {
						
						@Override
						public void stateChange(final ConnectionInfo info) {
							System.out.println(info.toString());
						}
					})
					 .buildStatfulConnector();
        	
            // create server
            final HelloWorldServer server = new HelloWorldServer(list);
            final TCPEndpoint tcpClientEndpoint = new TCPEndpoint(conn, NetworkConfig.getStandard());
            tcpClientEndpoint.bindDataReceiver(remote);
            server.addEndpoint(tcpClientEndpoint);
            server.start();
            
        } catch (final SocketException e) {
            
            System.err.println("Failed to initialize server: " + e.getMessage());
        }
    }
    
    /*
     * Constructor for a new Hello-World server. Here, the resources
     * of the server are initialized.
     */
    public HelloWorldServer(final String... name) throws SocketException {
        
        // provide an instance of a Hello-World resource
    	for(final String s : name) {
    		System.out.println("adding resource " + s);
            add(new HelloWorldResource(s));
    	}
    }
    
    /*
     * Definition of the Hello-World Resource
     */
    class HelloWorldResource extends CoapResource {
        
        public HelloWorldResource(final String id) {
            
            // set resource identifier
            super(id);
            
            // set display name
            getAttributes().setTitle("Hello-World Resource");
        }
        
        @Override
        public void handleGET(final CoapExchange exchange) {
            
            // respond to the request
            exchange.respond(getName());
        }
    }
}
