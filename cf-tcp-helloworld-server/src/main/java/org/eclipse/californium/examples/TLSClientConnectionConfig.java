package org.eclipse.californium.examples;

import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;

import org.eclipse.californium.core.network.config.DefaultTCPConnectionConfig;

public class TLSClientConnectionConfig extends DefaultTCPConnectionConfig{

	public TLSClientConnectionConfig(final String address, final int port) {
		super(CommunicationRole.CLIENT, address, port);
	}
	
	public void secure() throws SSLException, NoSuchAlgorithmException, KeyManagementException {
		final SSLContext context = SSLContext.getInstance("TLSv1.2");
		context.init(null, null, null);
		setClientSSL(context);
	}

}
