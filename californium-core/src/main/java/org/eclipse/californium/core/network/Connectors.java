package org.eclipse.californium.core.network;

import org.eclipse.californium.core.network.config.NetworkConfig;
import org.eclipse.californium.elements.Connector;
import org.eclipse.californium.elements.ConnectorBuilder;
import org.eclipse.californium.elements.ConnectorBuilder.CommunicationRole;
import org.eclipse.californium.elements.ConnectorBuilder.LayerSemantic;
import org.eclipse.californium.elements.StatefulConnector;

public class Connectors {
	
	public static Connector getNewDefaultUDPEndpoint() {
		return ConnectorBuilder.createTransportLayerBuilder(LayerSemantic.UDP).setPort(0).buildConnector();
	}
	
	public static Connector getNewUDPEndpoint(final int port) {
		return ConnectorBuilder.createTransportLayerBuilder(LayerSemantic.UDP).setPort(port).buildConnector();
	}
	
	public static CoAPEndpoint getNewUDPEndpoint(final int port, final NetworkConfig config) {
		return new CoAPEndpoint(ConnectorBuilder.createTransportLayerBuilder(LayerSemantic.UDP).setPort(port).buildConnector(), config);
	}
	
	public static StatefulConnector getNewTCPClientEndpoint(final String address, final int port) {
		return ConnectorBuilder.createTransportLayerBuilder(LayerSemantic.TCP)
												.setCommunicationRole(CommunicationRole.CLIENT)
												.setAddress(address)
												.setPort(port)
												.buildStatfulConnector();
	}
	
	public static StatefulConnector getNewTCPServerEndpoint(final String address, final int port) {
		return ConnectorBuilder.createTransportLayerBuilder(LayerSemantic.TCP)
												.setCommunicationRole(CommunicationRole.SERVER)
												.setAddress(address)
												.setPort(port)
												.buildStatfulConnector();
	}
}
