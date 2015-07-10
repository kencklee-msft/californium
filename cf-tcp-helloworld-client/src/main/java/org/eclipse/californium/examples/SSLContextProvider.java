package org.eclipse.californium.examples;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStore.Builder;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SecureRandom;
import java.security.Security;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.KeyStoreBuilderParameters;
import javax.net.ssl.ManagerFactoryParameters;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;

public class SSLContextProvider {

	private static final String NEW_ALGO = "NewSunX509";
	private static final String JAVA_KEYSTORE = "JKS";
	private static final String ALGO_SUN_JSSE = "SunJSSE";

	private char[] password;
	private String protocol;
	private SSLContext sslContext;
	private String[] storeResourcesPath;

	public SSLContextProvider(final String protocol, final String password, final String... storeResourcesPath) {
		this.protocol = protocol;
		this.password = password.toCharArray();
		this.storeResourcesPath = storeResourcesPath;
	}

	public synchronized SSLContext getSingletonSSLContext() {
		if(sslContext == null) {
			try{
				String algorithm = Security.getProperty("ssl.KeyManagerFactory.algorithm");
				if (algorithm == null) {
					algorithm = "SunX509";
				}
				sslContext = initializeSSLContext(algorithm);
			} catch (final Exception e) {
				throw new Error("Failed to initialize the SSLContext", e);
			}
		}
		return sslContext;
	}

	private SSLContext initializeSSLContext(final String algorithm) throws NoSuchAlgorithmException, IOException, KeyManagementException,
	KeyStoreException, CertificateException, NoSuchProviderException,
	InvalidAlgorithmParameterException {
		final SSLContext serverContext =  SSLContext.getInstance(protocol);

		final List<String> allResources = new ArrayList<String>();
		for(final String resource : storeResourcesPath) {
			allResources.add(resource);
		}
		if (allResources.isEmpty()) {
			System.err.println("Keystore not found in classpath for server.");
			throw new RuntimeException("Unable to find any keystore [*.ts or *.ks] from the classpath, are you sure its included in this war?");
		}

		final List<Builder> builders = new ArrayList<Builder>();
		final List<TrustManager> allTrustManagers = new ArrayList<TrustManager>();
		final KeyStore.ProtectionParameter passwordProtection = new KeyStore.PasswordProtection(password);
		for (final String resource : allResources) {
			System.out.println("Loading SSL Cert Resource from '" + resource + "'.");
			final KeyStore keyStoreJava = KeyStore.getInstance(JAVA_KEYSTORE);
			final InputStream inputStream = new FileInputStream(resource);

			keyStoreJava.load(inputStream, password);
			final TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(algorithm, ALGO_SUN_JSSE);
			trustManagerFactory.init(keyStoreJava);
			allTrustManagers.addAll(Arrays.asList(trustManagerFactory.getTrustManagers()));

			final Builder b = KeyStore.Builder.newInstance(keyStoreJava, passwordProtection);

			builders.add(b);
		}

		if (builders.isEmpty()) {
			System.err.println("Unable to build SSL Factory params.");
			throw new RuntimeException("Unable to build SSL Factory params, the SSLCerts might be wrong, please check certs folder.");
		}

		final ManagerFactoryParameters ksParams = new KeyStoreBuilderParameters(builders);

		final KeyManagerFactory factory = KeyManagerFactory.getInstance(NEW_ALGO);
		// Pass builder parameters to factory
		factory.init(ksParams);
		System.out.println("KeyManagerFactory = '" + factory + "'");
		serverContext.init(factory.getKeyManagers(), allTrustManagers.toArray(new TrustManager[allTrustManagers.size()]), new SecureRandom());

		return serverContext;
	}

}
