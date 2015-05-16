package org.eclipse.californium.core.network.tcp;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.californium.core.Utils;
import org.eclipse.californium.core.coap.CoAP.Type;
import org.eclipse.californium.core.coap.EmptyMessage;
import org.eclipse.californium.core.coap.Message;
import org.eclipse.californium.core.coap.Request;
import org.eclipse.californium.core.coap.Response;
import org.eclipse.californium.core.network.Endpoint;
import org.eclipse.californium.core.network.EndpointManager.ClientMessageDeliverer;
import org.eclipse.californium.core.network.EndpointObserver;
import org.eclipse.californium.core.network.Exchange;
import org.eclipse.californium.core.network.Matcher;
import org.eclipse.californium.core.network.Outbox;
import org.eclipse.californium.core.network.config.NetworkConfig;
import org.eclipse.californium.core.network.interceptors.MessageInterceptor;
import org.eclipse.californium.core.network.serialization.DataParser;
import org.eclipse.californium.core.network.serialization.Serializer;
import org.eclipse.californium.core.network.stack.CoapStack;
import org.eclipse.californium.core.server.MessageDeliverer;
import org.eclipse.californium.elements.ConnectorBuilder;
import org.eclipse.californium.elements.ConnectorBuilder.CommunicationRole;
import org.eclipse.californium.elements.ConnectorBuilder.LayerSemantic;
import org.eclipse.californium.elements.RawData;
import org.eclipse.californium.elements.RawDataChannel;
import org.eclipse.californium.elements.StatefulConnector;

public class TCPEndpoint implements Endpoint{

	/** the logger. */
	private final static Logger LOGGER = Logger.getLogger(TCPEndpoint.class.getCanonicalName());
	
	/** The stack of layers that make up the CoAP protocol */
	private final CoapStack coapstack;
	
	/** The connector over which the endpoint connects to the network */
	private final StatefulConnector connector;
	
	/** The configuration of this endpoint */
	private final NetworkConfig config;
	
	/** The executor to run tasks for this endpoint and its layers */
	private ScheduledExecutorService executor;
	
	/** Indicates if the endpoint has been started */
	private boolean started;
	
	/** The list of endpoint observers (has nothing to do with CoAP observe relations) */
	private final List<EndpointObserver> observers = new ArrayList<EndpointObserver>(0);
	
	/** The list of interceptors */
	private final List<MessageInterceptor> interceptors = new ArrayList<MessageInterceptor>(0);

	/** The matcher which matches incoming responses, akcs and rsts an exchange */
	private final Matcher matcher;
	
	/** The serializer to serialize messages to bytes */
	private final Serializer serializer;

	private final CommunicationRole role;
	
	/**
	 * Instantiates a new endpoint with an ephemeral port.
	 */
	public TCPEndpoint(final CommunicationRole role) {
		this(0, role);
	}
	
	/**
	 * Instantiates a new endpoint with the specified port
	 *
	 * @param port the port
	 */
	public TCPEndpoint(final int port, final CommunicationRole role) {
		this(new InetSocketAddress(port), role);
	}

	/**
	 * Instantiates a new endpoint with the specified address.
	 *
	 * @param address the address
	 */
	public TCPEndpoint(final InetSocketAddress address, final CommunicationRole role) {
		this(address, NetworkConfig.getStandard(), role);
	}
	
	public TCPEndpoint(final NetworkConfig config, final CommunicationRole role) {
		this(new InetSocketAddress(0), config, role);
	}
	
	/**
	 * Instantiates a new endpoint with the specified port and configuration.
	 *
	 * @param port the UDP port
	 * @param config the network configuration
	 */
	public TCPEndpoint(final int port, final NetworkConfig config, final CommunicationRole role) {
		this(new InetSocketAddress(port), config, role);
	}
	
	/**
	 * Instantiates a new endpoint with the specified address and configuration.
	 *
	 * @param address the address
	 * @param config the network configuration
	 */
	public TCPEndpoint(final InetSocketAddress address, final NetworkConfig config, final CommunicationRole role) {
		this(createTCPConnector(address, config, role), config, role);
	}
	
	/**
	 * Instantiates a new endpoint with the specified connector and
	 * configuration.
	 *
	 * @param connector the connector
	 * @param config the config
	 */
	public TCPEndpoint(final StatefulConnector connector, final NetworkConfig config, final CommunicationRole role) {
		this.config = config;
		this.connector = connector;
		this.serializer = new Serializer();
		this.matcher = new Matcher(config);		
		this.coapstack = new CoapStack(config, new OutboxImpl());
		this.role = role;
		this.connector.setRawDataReceiver(new InboxImpl());
	}
	
	/**
	 * Creates a new UDP connector.
	 *
	 * @param address the address
	 * @param config the configuration
	 * @return the connector
	 */
	private static StatefulConnector createTCPConnector(final InetSocketAddress address, final NetworkConfig config, final CommunicationRole role) {
		switch (role) {
		case CLIENT:
			return getNewTCPClientConnector(address.getHostString(), address.getPort());
		case SERVER:
			return getNewTCPServerConnector(address.getHostString(), address.getPort());
		default:
			throw new IllegalArgumentException("Cannot create a TCP connection of type " + role);
		}
	}
	
	public static StatefulConnector getNewTCPClientConnector(final String address, final int port) {
		return ConnectorBuilder.createTransportLayerBuilder(LayerSemantic.TCP)
												.setCommunicationRole(CommunicationRole.CLIENT)
												.setAddress(address)
												.setPort(port)
												.buildStatfulConnector();
	}
	
	public static StatefulConnector getNewTCPServerConnector(final String address, final int port) {
		return ConnectorBuilder.createTransportLayerBuilder(LayerSemantic.TCP)
												.setCommunicationRole(CommunicationRole.SERVER)
												.makeSharable()
												.setAddress(address)
												.setPort(port)
												.buildStatfulConnector();
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.californium.core.network.Endpoint#start()
	 */
	@Override
	public synchronized void start() throws IOException {
		if (started) {
			LOGGER.log(Level.FINE, "Endpoint at " + getAddress().toString() + " is already started");
			return;
		}
		
		if (!this.coapstack.hasDeliverer())
			this.coapstack.setDeliverer(new ClientMessageDeliverer());
		
		if (this.executor == null) {
			LOGGER.config("Endpoint "+toString()+" requires an executor to start. Using default single-threaded daemon executor.");
			
			final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(new Utils.DaemonThreadFactory());
			setExecutor(executor);
			addObserver(new EndpointObserver() {
				@Override
				public void started(final Endpoint endpoint) { }
				@Override
				public void stopped(final Endpoint endpoint) { }
				@Override
				public void destroyed(final Endpoint endpoint) {
					executor.shutdown();
				}
			});
		}
		
		try {
			LOGGER.log(Level.INFO, "Starting endpoint at " + getAddress());
			
			started = true;
			matcher.start();
			connector.start();
			for (final EndpointObserver obs:observers)
				obs.started(this);
			startExecutor();
		} catch (final IOException e) {
			// free partially acquired resources
			stop();
			throw e;
		}
	}
	
	/**
	 * Makes sure that the executor has started, i.e., a thread has been
	 * created. This is necessary for the server because it makes sure a
	 * non-daemon thread is running. Otherwise the program might find that only
	 * daemon threads are running and exit.
	 */
	private void startExecutor() {
		// Run a task that does nothing but make sure at least one thread of
		// the executor has started.
		executeTask(new Runnable() {
			@Override
			public void run() { /* do nothing */ }
		});
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.californium.core.network.Endpoint#stop()
	 */
	@Override
	public synchronized void stop() {
		if (!started) {
			LOGGER.log(Level.INFO, "Endpoint at " + getAddress() + " is already stopped");
		} else {
			LOGGER.log(Level.INFO, "Stopping endpoint at address " + getAddress());
			started = false;
			connector.stop();
			matcher.stop();
			for (final EndpointObserver obs:observers)
				obs.stopped(this);
			matcher.clear();
		}
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.californium.core.network.Endpoint#destroy()
	 */
	@Override
	public synchronized void destroy() {
		LOGGER.log(Level.INFO, "Destroying endpoint at address " + getAddress());
		if (started)
			stop();
		connector.destroy();
		for (final EndpointObserver obs:observers)
			obs.destroyed(this);
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.californium.core.network.Endpoint#clear()
	 */
	@Override
	public void clear() {
		matcher.clear();
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.californium.core.network.Endpoint#isStarted()
	 */
	@Override
	public boolean isStarted() {
		return started;
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.californium.core.network.Endpoint#setExecutor(java.util.concurrent.ScheduledExecutorService)
	 */
	@Override
	public synchronized void setExecutor(final ScheduledExecutorService executor) {
		this.executor = executor;
		this.coapstack.setExecutor(executor);
		this.matcher.setExecutor(executor);
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.californium.core.network.Endpoint#addObserver(org.eclipse.californium.core.network.EndpointObserver)
	 */
	@Override
	public void addObserver(final EndpointObserver obs) {
		observers.add(obs);
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.californium.core.network.Endpoint#removeObserver(org.eclipse.californium.core.network.EndpointObserver)
	 */
	@Override
	public void removeObserver(final EndpointObserver obs) {
		observers.remove(obs);
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.californium.core.network.Endpoint#addInterceptor(org.eclipse.californium.core.network.MessageIntercepter)
	 */
	@Override
	public void addInterceptor(final MessageInterceptor interceptor) {
		interceptors.add(interceptor);
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.californium.core.network.Endpoint#removeInterceptor(org.eclipse.californium.core.network.MessageIntercepter)
	 */
	@Override
	public void removeInterceptor(final MessageInterceptor interceptor) {
		interceptors.remove(interceptor);
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.californium.core.network.Endpoint#getInterceptors()
	 */
	@Override
	public List<MessageInterceptor> getInterceptors() {
		return new ArrayList<MessageInterceptor>(interceptors);
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.californium.core.network.Endpoint#sendRequest(org.eclipse.californium.core.coap.Request)
	 */
	@Override
	public void sendRequest(final Request request) {
		executor.execute(new Runnable() {
			@Override
			public void run() {
				try {
					coapstack.sendRequest(request);
				} catch (final Throwable t) {
					t.printStackTrace();
				}
			}
		});
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.californium.core.network.Endpoint#sendResponse(org.eclipse.californium.core.network.Exchange, org.eclipse.californium.core.coap.Response)
	 */
	@Override
	public void sendResponse(final Exchange exchange, final Response response) {
		// TODO: If the currently executing thread is not a thread of the
		// executor, a new task on the executor should be created to send the
		// response. (Just uncomment this code)
//		executor.execute(new Runnable() {
//			public void run() {
//				try {
//					coapstack.sendResponse(exchange, response);
//				} catch (Exception e) {
//					e.printStackTrace();
//				}
//			}
//		});
		coapstack.sendResponse(exchange, response);
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.californium.core.network.Endpoint#sendEmptyMessage(org.eclipse.californium.core.network.Exchange, org.eclipse.californium.core.coap.EmptyMessage)
	 */
	@Override
	public void sendEmptyMessage(final Exchange exchange, final EmptyMessage message) {
		executor.execute(new Runnable() {
			@Override
			public void run() {
				try {
					coapstack.sendEmptyMessage(exchange, message);
				} catch (final Exception e) {
					e.printStackTrace();
				}
			}
		});
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.californium.core.network.Endpoint#setMessageDeliverer(org.eclipse.californium.core.server.MessageDeliverer)
	 */
	@Override
	public void setMessageDeliverer(final MessageDeliverer deliverer) {
		coapstack.setDeliverer(deliverer);
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.californium.core.network.Endpoint#getAddress()
	 */
	@Override
	public InetSocketAddress getAddress() {
		return connector.getAddress();
	}

	/* (non-Javadoc)
	 * @see org.eclipse.californium.core.network.Endpoint#getConfig()
	 */
	@Override
	public NetworkConfig getConfig() {
		return config;
	}

	/**
	 * The stack of layers uses this Outbox to send messages. The OutboxImpl
	 * will then give them to the matcher, the interceptors, and finally send
	 * them over the connector.
	 */
	private class OutboxImpl implements Outbox {
		
		@Override
		public void sendRequest(final Exchange exchange, final Request request) {
			matcher.sendRequest(exchange, request);
			
			/* 
			 * Logging here causes significant performance loss.
			 * If necessary, add an interceptor that logs the messages,
			 * e.g., the MessageTracer.
			 */
			
			for (final MessageInterceptor interceptor:interceptors)
				interceptor.sendRequest(request);

			// MessageInterceptor might have canceled
			if (!request.isCanceled())
				connector.send(serializer.serialize(request));
		}

		@Override
		public void sendResponse(final Exchange exchange, final Response response) {
			matcher.sendResponse(exchange, response);
			
			/* 
			 * Logging here causes significant performance loss.
			 * If necessary, add an interceptor that logs the messages,
			 * e.g., the MessageTracer.
			 */
			
			for (final MessageInterceptor interceptor:interceptors)
				interceptor.sendResponse(response);

			// MessageInterceptor might have canceled
			if (!response.isCanceled())
				connector.send(serializer.serialize(response));
		}

		@Override
		public void sendEmptyMessage(final Exchange exchange, final EmptyMessage message) {
			matcher.sendEmptyMessage(exchange, message);
			
			/* 
			 * Logging here causes significant performance loss.
			 * If necessary, add an interceptor that logs the messages,
			 * e.g., the MessageTracer.
			 */
			
			for (final MessageInterceptor interceptor:interceptors)
				interceptor.sendEmptyMessage(message);

			// MessageInterceptor might have canceled
			if (!message.isCanceled())
				connector.send(serializer.serialize(message));
		}
	}
	
	/**
	 * The connector uses this channel to forward messages (in form of
	 * {@link RawData}) to the endpoint. The endpoint creates a new task to
	 * process the message. The task consists of invoking the matcher to look
	 * for an associated exchange and then forwards the message with the
	 * exchange to the stack of layers.
	 */
	private class InboxImpl implements RawDataChannel {

		@Override
		public void receiveData(final RawData raw) {
			if (raw.getAddress() == null)
				throw new NullPointerException();
			if (raw.getPort() == 0)
				throw new NullPointerException();
			
			// Create a new task to process this message
			final Runnable task = new Runnable() {
				@Override
				public void run() {
					receiveMessage(raw);
				}
			};
			executeTask(task);
		}
		
		/*
		 * The endpoint's executor executes this method to convert the raw bytes
		 * into a message, look for an associated exchange and forward it to
		 * the stack of layers.
		 */
		private void receiveMessage(final RawData raw) {
			final DataParser parser = new DataParser(raw.getBytes());
			
			if (parser.isRequest()) {
				// This is a request
				Request request;
				try {
					request = parser.parseRequest();
				} catch (final IllegalStateException e) {
					String log = "message format error caused by " + raw.getInetSocketAddress();
					if (!parser.isReply()) {
						// manually build RST from raw information
						final EmptyMessage rst = new EmptyMessage(Type.RST);
						rst.setDestination(raw.getAddress());
						rst.setDestinationPort(raw.getPort());
						rst.setMID(parser.getMID());
						for (final MessageInterceptor interceptor:interceptors)
							interceptor.sendEmptyMessage(rst);
						connector.send(serializer.serialize(rst));
						log += " and reseted";
					}
					LOGGER.info(log);
					return;
				}
				request.setSource(raw.getAddress());
				request.setSourcePort(raw.getPort());
				
				/* 
				 * Logging here causes significant performance loss.
				 * If necessary, add an interceptor that logs the messages,
				 * e.g., the MessageTracer.
				 */
				
				for (final MessageInterceptor interceptor:interceptors)
					interceptor.receiveRequest(request);

				// MessageInterceptor might have canceled
				if (!request.isCanceled()) {
					final Exchange exchange = matcher.receiveRequest(request);
					if (exchange != null) {
						exchange.setEndpoint(TCPEndpoint.this);
						coapstack.receiveRequest(exchange, request);
					}
				}
				
			} else if (parser.isResponse()) {
				// This is a response
				final Response response = parser.parseResponse();
				response.setSource(raw.getAddress());
				response.setSourcePort(raw.getPort());
				
				/* 
				 * Logging here causes significant performance loss.
				 * If necessary, add an interceptor that logs the messages,
				 * e.g., the MessageTracer.
				 */
				
				for (final MessageInterceptor interceptor:interceptors)
					interceptor.receiveResponse(response);

				// MessageInterceptor might have canceled
				if (!response.isCanceled()) {
					final Exchange exchange = matcher.receiveResponse(response);
					if (exchange != null) {
						exchange.setEndpoint(TCPEndpoint.this);
						response.setRTT(System.currentTimeMillis() - exchange.getTimestamp());
						coapstack.receiveResponse(exchange, response);
					} else if (response.getType() != Type.ACK) {
						LOGGER.fine("Rejecting unmatchable response from " + raw.getInetSocketAddress());
						reject(response);
					}
				}
				
			} else if (parser.isEmpty()) {
				// This is an empty message
				final EmptyMessage message = parser.parseEmptyMessage();
				message.setSource(raw.getAddress());
				message.setSourcePort(raw.getPort());
				
				/* 
				 * Logging here causes significant performance loss.
				 * If necessary, add an interceptor that logs the messages,
				 * e.g., the MessageTracer.
				 */
				
				for (final MessageInterceptor interceptor:interceptors)
					interceptor.receiveEmptyMessage(message);

				// MessageInterceptor might have canceled
				if (!message.isCanceled()) {
					// CoAP Ping
					if (message.getType() == Type.CON || message.getType() == Type.NON) {
						LOGGER.info("Responding to ping by " + raw.getInetSocketAddress());
						reject(message);
					} else {
						final Exchange exchange = matcher.receiveEmptyMessage(message);
						if (exchange != null) {
							exchange.setEndpoint(TCPEndpoint.this);
							coapstack.receiveEmptyMessage(exchange, message);
						}
					}
				}
			} else {
				LOGGER.finest("Silently ignoring non-CoAP message from " + raw.getInetSocketAddress());
			}
		}
		
		private void reject(final Message message) {
			final EmptyMessage rst = EmptyMessage.newRST(message);
			for (final MessageInterceptor interceptor:interceptors)
				interceptor.sendEmptyMessage(rst);
			connector.send(serializer.serialize(rst));
		}

	}
	
	/**
	 * Execute the specified task on the endpoint's executor.
	 *
	 * @param task the task
	 */
	private void executeTask(final Runnable task) {
		executor.execute(new Runnable() {
			@Override
			public void run() {
				try {
					task.run();
				} catch (final Throwable t) {
					t.printStackTrace();
				}
			}
		});
	}

}
