/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.service;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.nist.NISTObjectIdentifiers;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.ExtendedKeyUsage;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.KeyPurposeId;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.jcajce.JcaCertStore;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoGeneratorBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;
import org.bouncycastle.tsp.TSPAlgorithms;
import org.bouncycastle.tsp.TimeStampRequest;
import org.bouncycastle.tsp.TimeStampResponseGenerator;
import org.bouncycastle.tsp.TimeStampTokenGenerator;

import java.io.IOException;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * An RFC 3161 timestamp service on the loopback address, so the sealing tests run the real HTTP path
 * of the timestamp client without reaching the internet.
 *
 * <p>It answers every POST to {@code /tsr} with a timestamp signed by a throwaway authority whose
 * certificate carries the critical timestamping key purpose, and counts the requests it saw. The
 * addresses for a service that is down and for one that never answers are here as well.
 */
final class LocalTimestampService implements AutoCloseable {
    private static final ASN1ObjectIdentifier POLICY = new ASN1ObjectIdentifier("1.3.6.1.4.1.55555.7.1");
    private static final String PATH = "/tsr";
    private static final KeyPair KEYS = keyPair();
    private static final X509Certificate CERTIFICATE = certificate(KEYS);

    private final HttpServer server;
    private final AtomicInteger requests = new AtomicInteger();
    private final AtomicInteger serial = new AtomicInteger();
    private final CountDownLatch released = new CountDownLatch(1);

    private LocalTimestampService(HttpServer server) {
        this.server = server;
    }

    /** @return a running service on a free loopback port */
    static LocalTimestampService start() throws IOException {
        var server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        var service = new LocalTimestampService(server);
        server.createContext(PATH, service::answer);
        server.start();
        return service;
    }

    /**
     * A service that reads every request and counts it, then never answers until it is closed.
     *
     * @return a running service on a free loopback port
     */
    static LocalTimestampService hanging() throws IOException {
        var server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        var service = new LocalTimestampService(server);
        server.createContext(PATH, service::hang);
        server.start();
        return service;
    }

    /** @return the certificate of the authority that signs this service's timestamps */
    static X509Certificate certificate() {
        return CERTIFICATE;
    }

    /** @return an address on the loopback interface where nothing listens */
    static String unreachableUrl() throws IOException {
        try (var socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            return urlOf(socket.getLocalPort());
        }
    }

    /**
     * Opens a socket that takes connections into its backlog but never reads or answers them, which is
     * what a service that hangs looks like to a client.
     *
     * @return the open socket, to be closed by the caller
     */
    static ServerSocket silent() throws IOException {
        return new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
    }

    /** @return the address of a socket from {@link #silent()} */
    static String urlOf(ServerSocket socket) {
        return urlOf(socket.getLocalPort());
    }

    /** @return the address timestamps are requested at */
    String url() {
        return urlOf(server.getAddress().getPort());
    }

    /** @return how many requests reached the service */
    int requests() {
        return requests.get();
    }

    @Override
    public void close() {
        released.countDown();
        server.stop(0);
    }

    private static String urlOf(int port) {
        return "http://127.0.0.1:" + port + PATH;
    }

    private void hang(HttpExchange exchange) throws IOException {
        requests.incrementAndGet();
        try (exchange) {
            exchange.getRequestBody().readAllBytes();
            released.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void answer(HttpExchange exchange) throws IOException {
        requests.incrementAndGet();
        try (exchange) {
            var request = new TimeStampRequest(exchange.getRequestBody().readAllBytes());
            var response =
                    responseGenerator().generate(request, BigInteger.valueOf(serial.incrementAndGet()), new Date());
            var body = response.getEncoded();
            exchange.getResponseHeaders().set("Content-Type", "application/timestamp-reply");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
        } catch (Exception e) {
            throw new IOException("The local timestamp service could not answer", e);
        }
    }

    private static TimeStampResponseGenerator responseGenerator() throws Exception {
        var digests = new JcaDigestCalculatorProviderBuilder().build();
        var signerInfo =
                new JcaSimpleSignerInfoGeneratorBuilder().build("SHA256withRSA", KEYS.getPrivate(), CERTIFICATE);
        var tokens = new TimeStampTokenGenerator(
                signerInfo, digests.get(new AlgorithmIdentifier(NISTObjectIdentifiers.id_sha256)), POLICY);
        tokens.addCertificates(new JcaCertStore(List.of(CERTIFICATE)));
        return new TimeStampResponseGenerator(tokens, TSPAlgorithms.ALLOWED);
    }

    private static KeyPair keyPair() {
        try {
            var generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private static X509Certificate certificate(KeyPair keys) {
        var subject = new X500Name("CN=Ember test timestamp authority");
        var now = Instant.now();
        try {
            var holder = new JcaX509v3CertificateBuilder(
                            subject,
                            BigInteger.valueOf(now.toEpochMilli()),
                            Date.from(now.minus(Duration.ofDays(1))),
                            Date.from(now.plus(Duration.ofDays(30))),
                            subject,
                            keys.getPublic())
                    .addExtension(Extension.basicConstraints, true, new BasicConstraints(false))
                    .addExtension(
                            Extension.keyUsage, true, new KeyUsage(KeyUsage.digitalSignature | KeyUsage.nonRepudiation))
                    .addExtension(
                            Extension.extendedKeyUsage, true, new ExtendedKeyUsage(KeyPurposeId.id_kp_timeStamping))
                    .build(new JcaContentSignerBuilder("SHA256withRSA").build(keys.getPrivate()));
            return new JcaX509CertificateConverter().getCertificate(holder);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
