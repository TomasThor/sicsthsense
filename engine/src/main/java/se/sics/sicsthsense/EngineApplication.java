/*
 * Copyright (c) 2013, Swedish Institute of Computer Science
 * All rights reserved.
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *	   * Redistributions of source code must retain the above copyright
 *		 notice, this list of conditions and the following disclaimer.
 *	   * Redistributions in binary form must reproduce the above copyright
 *		 notice, this list of conditions and the following disclaimer in the
 *		 documentation and/or other materials provided with the distribution.
 *	   * Neither the name of The Swedish Institute of Computer Science nor the
 *		 names of its contributors may be used to endorse or promote products
 *		 derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE SWEDISH INSTITUTE OF COMPUTER SCIENCE BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE. */
package se.sics.sicsthsense;

import java.util.UUID;
import javax.servlet.ServletRegistration;

import org.skife.jdbi.v2.*; // For DBI
import org.skife.jdbi.v2.exceptions.*; // For lack of connection Exception
import org.eclipse.jetty.server.session.SessionHandler;
import com.google.common.collect.ImmutableMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.dropwizard.Application;
import io.dropwizard.assets.AssetsBundle;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import io.dropwizard.jdbi.*;
import io.dropwizard.db.*;
import io.dropwizard.auth.*;
import io.dropwizard.auth.Authenticator;
import io.dropwizard.auth.AuthenticationException;
import io.dropwizard.auth.basic.BasicCredentials;
import io.dropwizard.auth.basic.BasicAuthProvider;
import io.dropwizard.auth.oauth.*;
import io.dropwizard.views.ViewBundle;
import io.dropwizard.views.ViewMessageBodyWriter;
import io.dropwizard.jdbi.bundles.DBIExceptionsBundle;

import org.atmosphere.cpr.AtmosphereServlet;

import akka.actor.Props;
import scala.concurrent.duration.Duration;
import java.util.concurrent.TimeUnit;
import akka.actor.UntypedActor;
import akka.actor.Cancellable;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.util.logging.Level;
import org.eclipse.californium.core.CaliforniumLogger;

import se.sics.sicsthsense.resources.*;
import se.sics.sicsthsense.jdbi.*;
import se.sics.sicsthsense.core.*;
import se.sics.sicsthsense.auth.*;
import se.sics.sicsthsense.auth.openid.*;
import se.sics.sicsthsense.model.security.*;
import se.sics.sicsthsense.resources.coap.ResourceCoapResource;
import se.sics.sicsthsense.resources.coap.StreamCoapResource;
import se.sics.sicsthsense.resources.coap.UserCoapResource;

import org.eclipse.californium.core.CoapServer;
import org.eclipse.californium.core.network.CoAPEndpoint;
import org.eclipse.californium.core.network.config.NetworkConfig;
import org.eclipse.californium.scandium.DTLSConnector;
import org.eclipse.californium.scandium.ScandiumLogger;
import org.eclipse.californium.core.coap.CoAP;
import org.eclipse.californium.scandium.dtls.pskstore.InMemoryPskStore;

public class EngineApplication extends Application<EngineConfiguration> {
	private final Logger logger = LoggerFactory.getLogger(EngineApplication.class);
	private PollSystem pollSystem;
        private static final String TRUST_STORE_PASSWORD = "rootPass";
	private final static String KEY_STORE_PASSWORD = "endPass";
	private static final String KEY_STORE_LOCATION = "certs/keyStore.jks";
        private static final String TRUST_STORE_LOCATION = "certs/trustStore.jks";
        
        static {
            CaliforniumLogger.initialize();
            CaliforniumLogger.setLevel(Level.CONFIG);
            ScandiumLogger.initialize();
            ScandiumLogger.setLevel(Level.ALL);
        }

	public static void main(String[] args) throws Exception {
		new EngineApplication().run(args);
	}

	@Override
	public String getName() {
		return "SicsthSense-Engine";
	}
	@Override
	public void initialize(Bootstrap<EngineConfiguration> bootstrap) {
		bootstrap.addBundle(new AssetsBundle("/assets/images", "/images"));
		bootstrap.addBundle(new AssetsBundle("/assets/jquery", "/jquery"));
		bootstrap.addBundle(new AssetsBundle("/assets/atmos", "/atmos"));
		bootstrap.addBundle(new AssetsBundle("/assets/", "/"));
		bootstrap.addBundle(new ViewBundle());
		bootstrap.addBundle(new DBIExceptionsBundle());
	}

	public void addServlet(Environment environment) {
		AtmosphereServlet atmosphereServlet = new AtmosphereServlet();
		atmosphereServlet.framework().addInitParameter( "com.sun.jersey.config.property.packages", "se.sics.sicsthsense.resources.atmosphere");
		atmosphereServlet.framework().addInitParameter( "org.atmosphere.cpr.broadcasterCacheClass", "org.atmosphere.cache.UUIDBroadcasterCache");
		atmosphereServlet.framework().addInitParameter( "org.atmosphere.cpr.broadcastFilterClasses", "org.atmosphere.client.TrackMessageSizeFilter");
		atmosphereServlet.framework().addInitParameter( "org.atmosphere.client.TrackMessageSizeFilter", "org.atmosphere.container.Tomcat7Servlet30SupportWithWebSocket");
		atmosphereServlet.framework().addInitParameter( "org.atmosphere.websocket.messageContentType", "application/json");
		final ServletRegistration.Dynamic websocket = environment.servlets().addServlet("atmosphere", atmosphereServlet);
		websocket.setAsyncSupported(true);
		websocket.addMapping("/users/*");

		//@formatter:off
		websocket.setInitParameters(ImmutableMap.<String, String> of( "com.sun.jersey.config.property.packages","se.sics.sicsthsense.resources.atmosphere"));
		websocket.setInitParameters(ImmutableMap.<String, String> of( "org.atmosphere.websocket.messageContentType", "application/json"));
	}

	// ClassNotFoundException thrown when missing DBI driver
	@Override
	public void run(EngineConfiguration configuration, Environment environment) throws ClassNotFoundException {
		DAOFactory.build(configuration, environment);
		StorageDAO storage = DAOFactory.getInstance();
	// register each resource type accessible through the API
		pollSystem = PollSystem.build(storage);
		try {
			pollSystem.createPollers();
		} catch (UnableToObtainConnectionException e) {
			System.out.println("Error: Unable to obtain connection to SQL Server!\nExiting...");
			System.exit(1);
		}

		// Configure authenticator
		User publicUser = new User();
		publicUser.setUsername("__publicUser");
		publicUser.getAuthorities().add(Authority.ROLE_PUBLIC);
		OpenIDAuthenticator authenticator = new OpenIDAuthenticator(publicUser);

		environment.jersey().register(new PublicHomeResource());

		// Attach Atmosphere servlet
		addServlet(environment);
                
                // CoAP server startup
                CoapServer server = new CoapServer();
                server.add(new ResourceCoapResource());
                server.add(new StreamCoapResource());
		server.add(new UserCoapResource());
                server.addEndpoint(new CoAPEndpoint(CoAP.DEFAULT_COAP_PORT));
                
                // Add DTLS CoAP server endpoint
                try {
                    // Pre-shared secrets
                    InMemoryPskStore pskStore = new InMemoryPskStore();
                    pskStore.setKey("Client_identity", "secretPSK".getBytes());
                    
                    // load the trust store
                    KeyStore trustStore = KeyStore.getInstance("JKS");
                    InputStream inTrust = new FileInputStream(TRUST_STORE_LOCATION);
                    trustStore.load(inTrust, TRUST_STORE_PASSWORD.toCharArray());

                    // You can load multiple certificates if needed
                    Certificate[] trustedCertificates = new Certificate[1];
                    trustedCertificates[0] = trustStore.getCertificate("root");
                    
                    // load the key store
                    KeyStore keyStore = KeyStore.getInstance("JKS");
                    InputStream in = new FileInputStream(KEY_STORE_LOCATION);
                    keyStore.load(in, KEY_STORE_PASSWORD.toCharArray());
                    
                    DTLSConnector connector = new DTLSConnector(new InetSocketAddress(CoAP.DEFAULT_COAP_SECURE_PORT), trustedCertificates);
                    connector.getConfig().setPskStore(pskStore);
                    connector.getConfig().setPrivateKey((PrivateKey)keyStore.getKey("server", KEY_STORE_PASSWORD.toCharArray()), keyStore.getCertificateChain("server"), true);
                    server.addEndpoint(new CoAPEndpoint(connector, NetworkConfig.getStandard()));
                    
                } catch (Exception e) {
                    System.err.println("Could not load the keystore or add dtls endpoint to coap server");
                    e.printStackTrace();
                }
                server.start();
	}
}
