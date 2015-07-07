package org.eclipse.californium.core.network.tcp;

import org.eclipse.californium.core.network.CoAPEndpoint;
import org.eclipse.californium.core.network.config.NetworkConfig;
import org.eclipse.californium.elements.StatefulConnector;
import org.eclipse.californium.elements.tcp.ConnectionStateListener;

public class TCPEndpoint extends CoAPEndpoint {

	private final StatefulConnector statefulConnector;

	/**
	 * Instantiates a new endpoint with the specified connector and
	 * configuration.
	 *
	 * @param connector the connector
	 * @param config the config
	 */
	public TCPEndpoint(final StatefulConnector statefulConnector, final NetworkConfig config) {
		super(statefulConnector, config);
		this.statefulConnector = statefulConnector;
	}

	/**
	 * Adds a connection state listener
	 * @param listener
	 */
	public void addConnectionStateListener(final ConnectionStateListener listener) {
		statefulConnector.addConnectionStateListener(listener);
	}

}
