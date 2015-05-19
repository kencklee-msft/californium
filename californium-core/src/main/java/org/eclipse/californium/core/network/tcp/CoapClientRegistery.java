package org.eclipse.californium.core.network.tcp;

import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.californium.core.CoapClient;
import org.eclipse.californium.elements.tcp.ConnectionInfo;
import org.eclipse.californium.elements.tcp.ConnectionInfo.ConnectionState;
import org.eclipse.californium.elements.tcp.ConnectionStateListener;

/**
 * this class is ment for server tcp connection only
 * 
 * If you build a TCP server that must serve to multiple TcpClient containing the coapServer (hence multiple coap Client from 1 tcp connection)
 * you must use this registry to keep track of you client.  Once a Connection is establish, the call will be make for you to create a CoAP client 
 * if you wish to do so.
 * @author simonlemoy
 *
 */
public abstract class CoapClientRegistery<K> implements ConnectionStateListener{
	
	public final Map<K, CoapClient> coapClientRegistery;
	private final TCPEndpoint endpoint;
	
	
	public CoapClientRegistery(final boolean threadsafe, final TCPEndpoint endpoint) {
		coapClientRegistery = threadsafe ? new HashMap<K, CoapClient>() : new ConcurrentHashMap<K, CoapClient>();
		this.endpoint = endpoint;
		endpoint.addConnectionStateListener(this);
	}

	@Override
	public void stateChange(final ConnectionInfo info) {
		System.out.println("Incoming connection state change from " + info.getRemote() + " to  " + info.getConnectionState());
		if(endpoint == null) {
			System.out.println("No Endpoint setup, will not trigger creation of Client");
		}
		if(info.getConnectionState().equals(ConnectionState.NEW_INCOMING_CONNECT)) {
			final CoapClient client = new CoapClient();
			client.setEndpoint(endpoint);
			final K key = configureCoapClient(client, info.getRemote());
			coapClientRegistery.put(key, client);
		} else if(info.getConnectionState().equals(ConnectionState.NEW_INCOMING_DISCONNECT)) {
			coapClientRegistery.remove(info.getRemote());
		}
	}
	
	public void clearRegistry() {
		coapClientRegistery.clear();
	}
	
	public CoapClient getClient(final K key) {
		return coapClientRegistery.get(key);
	}
	
	public CoapClient remote(final K key) {
		return coapClientRegistery.remove(key);
	}
	
	public boolean containsKey(final K key) {
		return coapClientRegistery.containsKey(key);
	}
	
	public boolean containsCoapClient(final CoapClient client) {
		return coapClientRegistery.containsValue(client);
	}
	
	public Set<Entry<K, CoapClient>> getAllClient() {
		return coapClientRegistery.entrySet();
	}
	
	
	/**
	 * this will be call when a new connection is established.  
	 * you will then receive an empty CoAP client with an endpoint associated with it
	 * @param endpoint
	 * @return
	 */
	public abstract K configureCoapClient(CoapClient client, InetSocketAddress remote);
}
