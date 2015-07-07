package org.eclipse.californium.core.network.tcp;

import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.californium.core.CoapClient;
import org.eclipse.californium.elements.tcp.ConnectionInfo;
import org.eclipse.californium.elements.tcp.ConnectionInfo.ConnectionState;
import org.eclipse.californium.elements.tcp.ConnectionStateListener;

/**
 * This class is meant for server TCP connection only
 *
 * If you build a TCP server that must serve to multiple TCP Clients containing the coapServer (hence multiple CoAP Client from 1 TCP connection)
 * You must use this registry to keep track of your clients. Once a Connection is established, the call will be made for you to create a CoAP client
 * if you wish to do so.
 */
public abstract class CoapClientRegistry implements ConnectionStateListener {

	private final static Logger LOGGER = Logger.getLogger(CoapClientRegistry.class.getCanonicalName());

	public final Map<InetSocketAddress, CoapClient> coapClientLink;
	private final TCPEndpoint endpoint;


	public CoapClientRegistry(final boolean threadsafe, final TCPEndpoint endpoint) {
		coapClientLink = threadsafe ? new ConcurrentHashMap<InetSocketAddress, CoapClient>() : new HashMap<InetSocketAddress, CoapClient>();
		this.endpoint = endpoint;
		endpoint.addConnectionStateListener(this);
	}

	@Override
	public void stateChange(final ConnectionInfo info) {
		LOGGER.log(Level.FINE, "Incoming connection state change from " + info.getRemote() + " to  " + info.getConnectionState());
		if(endpoint == null) {
			LOGGER.log(Level.FINE, "No Endpoint setup, will not trigger creation of Client");
			return;
		}
		if(info.getConnectionState().equals(ConnectionState.NEW_INCOMING_CONNECT)) {
			final CoapClient client = new CoapClient();
			client.setEndpoint(endpoint);
			final InetSocketAddress key = info.getRemote();
			configureCoapClient(client, key);
			coapClientLink.put(key, client);
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
	public abstract void configureCoapClient(CoapClient client, InetSocketAddress remote);
}
