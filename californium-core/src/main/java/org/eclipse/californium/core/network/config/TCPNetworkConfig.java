package org.eclipse.californium.core.network.config;

import org.eclipse.californium.elements.config.TCPConnectionConfig;

public class TCPNetworkConfig extends TCPConnectionConfig{

	public TCPNetworkConfig(final CommunicationRole role) {
		super(role);
	}

	@Override
	public String getRemoteAddress() {
		return "localhost";
	}

	@Override
	public int getRemotePort() {
		return 5683;
	}

}
