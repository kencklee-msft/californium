package org.eclipse.californium.core.network.tcp;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.californium.core.CoapClient;
import org.eclipse.californium.core.network.CoAPEndpoint;
import org.eclipse.californium.core.network.config.NetworkConfig;
import org.eclipse.californium.elements.tcp.ConnectionInfo;
import org.eclipse.californium.elements.tcp.ConnectionInfo.ConnectionState;
import org.eclipse.californium.elements.tcp.ConnectionStateListener;
import org.eclipse.californium.elements.tcp.server.TcpServerConnector;

public class TcpServerEndpoint extends CoAPEndpoint implements ConnectionStateListener {

	private final static Logger LOGGER = Logger.getLogger(TcpServerEndpoint.class.getCanonicalName());

	public final Map<InetSocketAddress, CoapClient> coapClientLink;

	/**
	 * Instantiates a new endpoint with the specified connector and
	 * configuration.
	 *
	 * @param connector the connector
	 * @param config the config
	 */
	public TcpServerEndpoint(final TcpServerConnector connector, final NetworkConfig config) {
		super(connector, config);
		coapClientLink = new ConcurrentHashMap<InetSocketAddress, CoapClient>();
		connector.setConnectionStateListener(this);
	}

	@Override
	public void stateChange(final ConnectionInfo info) {
		LOGGER.log(Level.FINE, "Incoming connection state change from " + info.getRemote() + " to  " + info.getConnectionState());
		if(info.getConnectionState().equals(ConnectionState.NEW_INCOMING_CONNECT)) {
			final InetSocketAddress remote = info.getRemote();
			final CoapClient client = createCoapClient(remote);
			client.setEndpoint(this);
			coapClientLink.put(remote, client);
		} else if(info.getConnectionState().equals(ConnectionState.NEW_INCOMING_DISCONNECT)) {
			coapClientLink.remove(info.getRemote());
		}
	}

	public final void clearRegistry() {
		coapClientLink.clear();
	}

	public final CoapClient getClient(final InetSocketAddress key) {
		return coapClientLink.get(key);
	}

	public final CoapClient remove(final InetSocketAddress key) {
		return coapClientLink.remove(key);
	}

	public final boolean containsKey(final InetSocketAddress key) {
		return coapClientLink.containsKey(key);
	}

	public final boolean containsCoapClient(final CoapClient client) {
		return coapClientLink.containsValue(client);
	}

	public final Set<Entry<InetSocketAddress, CoapClient>> getAllClient() {
		return coapClientLink.entrySet();
	}

	/**
	 * This will be called when a new connection is established.
	 * You will then receive an empty CoAP client with an endpoint associated with it
	 * @param endpoint
	 */
	public CoapClient createCoapClient(final InetSocketAddress remote) {
		return new CoapClient("coap", remote.getHostName(), remote.getPort());
	}

}
