/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.service;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import dev.chojo.ember.conf.file.elements.Demo;
import dev.chojo.ember.conf.file.elements.Federation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.KeyStore;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.TrustManagerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Outbound requests to addresses somebody else supplied: resolved once, checked, and connected to the
 * checked address, with TLS still verifying the host name.
 *
 * <p>The servers run on two loopback addresses. For the pinning tests, {@code 127.0.0.2} stands in
 * for a public address and {@code 127.0.0.1} for a private one, and a name resolves to whichever the
 * test says. The certificate is made for this run with {@code keytool} and trusted by the test's own
 * TLS context.
 */
class OutboundHttpTest {
    private static final String HOST = "rebind.test";
    private static final String PASSWORD = "changeit";
    private static InetAddress publicAddress;
    private static InetAddress privateAddress;
    private static SSLContext serverContext;
    private static SSLContext clientContext;

    @TempDir
    static Path keys;

    private final List<HttpServer> servers = new ArrayList<>();

    @BeforeAll
    static void makeCertificate() throws Exception {
        publicAddress = InetAddress.getByName("127.0.0.2");
        privateAddress = InetAddress.getByName("127.0.0.1");
        Path store = keys.resolve("server.p12");
        String keytool =
                Path.of(System.getProperty("java.home"), "bin", "keytool").toString();
        var process = new ProcessBuilder(
                        keytool,
                        "-genkeypair",
                        "-alias",
                        "server",
                        "-keyalg",
                        "RSA",
                        "-keysize",
                        "2048",
                        "-validity",
                        "2",
                        "-dname",
                        "CN=" + HOST,
                        "-ext",
                        "SAN=dns:" + HOST,
                        "-storetype",
                        "PKCS12",
                        "-keystore",
                        store.toString(),
                        "-storepass",
                        PASSWORD,
                        "-keypass",
                        PASSWORD)
                .redirectErrorStream(true)
                .start();
        process.getInputStream().readAllBytes();
        assertEquals(0, process.waitFor());

        var keyStore = KeyStore.getInstance("PKCS12");
        try (var in = new FileInputStream(store.toFile())) {
            keyStore.load(in, PASSWORD.toCharArray());
        }
        var keyManagers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keyManagers.init(keyStore, PASSWORD.toCharArray());
        serverContext = SSLContext.getInstance("TLS");
        serverContext.init(keyManagers.getKeyManagers(), null, null);

        var trustStore = KeyStore.getInstance("PKCS12");
        trustStore.load(null, null);
        trustStore.setCertificateEntry("server", keyStore.getCertificate("server"));
        var trustManagers = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustManagers.init(trustStore);
        clientContext = SSLContext.getInstance("TLS");
        clientContext.init(null, trustManagers.getTrustManagers(), null);
    }

    @AfterEach
    void stopServers() {
        servers.forEach(server -> server.stop(0));
    }

    /**
     * A validator that treats {@code 127.0.0.2} as public and everything else as private, so the
     * pinning can be watched on loopback.
     */
    private static RemoteUrlValidator loopbackPolicy() {
        return new RemoteUrlValidator(new Federation(), new Demo()) {
            @Override
            public List<InetAddress> publicAddresses(String host, OutboundHttp.HostResolver resolver)
                    throws RefusedDestinationException {
                InetAddress[] addresses;
                try {
                    addresses = resolver.resolve(host);
                } catch (IOException e) {
                    throw new RefusedDestinationException(RefusedDestinationException.Reason.UNRESOLVABLE, host);
                }
                for (InetAddress address : addresses) {
                    if (!address.equals(publicAddress)) {
                        throw new RefusedDestinationException(RefusedDestinationException.Reason.NOT_PUBLIC, host);
                    }
                }
                return List.of(addresses);
            }
        };
    }

    private HttpServer https(
            InetAddress address, int port, String answer, AtomicInteger hits, AtomicReference<String> host)
            throws IOException {
        var server = HttpsServer.create(new InetSocketAddress(address, port), 0);
        server.setHttpsConfigurator(new HttpsConfigurator(serverContext));
        return serve(server, answer, hits, host);
    }

    private HttpServer serve(HttpServer server, String answer, AtomicInteger hits, AtomicReference<String> host) {
        server.createContext("/", exchange -> {
            hits.incrementAndGet();
            host.set(exchange.getRequestHeaders().getFirst("Host"));
            byte[] body = answer.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        servers.add(server);
        return server;
    }

    private static HttpRequest get(String url) {
        return HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();
    }

    @Test
    void theConnectionGoesToTheCheckedAddressEvenWhenTheNameAnswersDifferentlyLater() throws Exception {
        var publicHits = new AtomicInteger();
        var privateHits = new AtomicInteger();
        var seenHost = new AtomicReference<String>();
        int port = https(publicAddress, 0, "public", publicHits, seenHost)
                .getAddress()
                .getPort();
        https(privateAddress, port, "private", privateHits, new AtomicReference<>());
        var lookups = new AtomicInteger();
        OutboundHttp.HostResolver rebinding =
                host -> new InetAddress[] {lookups.getAndIncrement() == 0 ? publicAddress : privateAddress};
        var outbound = new OutboundHttp(loopbackPolicy(), rebinding, clientContext);

        var response =
                outbound.send(get("https://" + HOST + ":" + port + "/ping"), HttpResponse.BodyHandlers.ofString());

        assertEquals("public", response.body());
        assertEquals(1, publicHits.get());
        assertEquals(0, privateHits.get());
        assertEquals(1, lookups.get());
        assertEquals(HOST + ":" + port, seenHost.get());
    }

    @Test
    void aNameThatResolvesToAPrivateAddressIsNeverConnectedTo() throws Exception {
        var privateHits = new AtomicInteger();
        int port = https(privateAddress, 0, "private", privateHits, new AtomicReference<>())
                .getAddress()
                .getPort();
        var outbound = new OutboundHttp(loopbackPolicy(), host -> new InetAddress[] {privateAddress}, clientContext);

        var refusal = assertThrows(
                RefusedDestinationException.class,
                () -> outbound.send(get("https://" + HOST + ":" + port + "/"), HttpResponse.BodyHandlers.ofString()));

        assertEquals(RefusedDestinationException.Reason.NOT_PUBLIC, refusal.reason());
        assertEquals(0, privateHits.get());
    }

    @Test
    void aNameMixingAPublicAndAPrivateAddressIsRefused() {
        var outbound = new OutboundHttp(
                loopbackPolicy(), host -> new InetAddress[] {publicAddress, privateAddress}, clientContext);

        assertThrows(
                RefusedDestinationException.class,
                () -> outbound.send(get("https://" + HOST + "/"), HttpResponse.BodyHandlers.ofString()));
    }

    @Test
    void theCertificateIsStillCheckedAgainstTheHostName() throws Exception {
        int port = https(publicAddress, 0, "public", new AtomicInteger(), new AtomicReference<>())
                .getAddress()
                .getPort();
        var outbound = new OutboundHttp(loopbackPolicy(), host -> new InetAddress[] {publicAddress}, clientContext);

        assertThrows(
                SSLHandshakeException.class,
                () -> outbound.send(get("https://another.test:" + port + "/"), HttpResponse.BodyHandlers.ofString()));
    }

    @Test
    void plainHttpIsRefusedWhilePrivateHostsAreNotAllowed() {
        var outbound = new OutboundHttp(loopbackPolicy(), host -> new InetAddress[] {publicAddress}, clientContext);

        var refusal = assertThrows(
                RefusedDestinationException.class,
                () -> outbound.send(get("http://" + HOST + "/"), HttpResponse.BodyHandlers.ofString()));
        assertEquals(RefusedDestinationException.Reason.MALFORMED, refusal.reason());
    }

    @Test
    void theRealPolicyRefusesANameThatResolvesIntoPrivateSpace() {
        var outbound = new OutboundHttp(
                new RemoteUrlValidator(new Federation(), new Demo()),
                host -> new InetAddress[] {InetAddress.getByName("10.1.2.3")},
                clientContext);

        assertThrows(
                RefusedDestinationException.class,
                () -> outbound.send(get("https://partner.example/"), HttpResponse.BodyHandlers.ofString()));
    }

    @Test
    void allowingPrivateHostsLetsPlainHttpReachLoopbackAsBefore() throws Exception {
        var hits = new AtomicInteger();
        var server = serve(
                HttpServer.create(new InetSocketAddress(privateAddress, 0), 0), "dev", hits, new AtomicReference<>());
        var federation = new Federation() {
            @Override
            public boolean allowPrivateHosts() {
                return true;
            }
        };
        var outbound = new OutboundHttp(new RemoteUrlValidator(federation, new Demo()));

        var response = outbound.send(
                get("http://127.0.0.1:" + server.getAddress().getPort() + "/"), HttpResponse.BodyHandlers.ofString());

        assertEquals("dev", response.body());
        assertEquals(1, hits.get());
    }
}
