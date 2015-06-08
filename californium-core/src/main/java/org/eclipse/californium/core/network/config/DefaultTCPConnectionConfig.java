package org.eclipse.californium.core.network.config;

import org.eclipse.californium.elements.config.TCPConnectionConfig;

public class DefaultTCPConnectionConfig extends TCPConnectionConfig{

	private final String address;
	private final int port;

	public DefaultTCPConnectionConfig(final CommunicationRole role, final String address, final int port) {
		super(role);
		this.address = address;
		this.port = port;
	}

	@Override
	public String getRemoteAddress() {
		return address;
	}

	@Override
	public int getRemotePort() {
		return port;
	}

}
