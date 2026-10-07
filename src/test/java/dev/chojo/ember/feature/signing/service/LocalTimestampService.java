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
import org.bouncycastle.asn1.x509.CRLDistPoint;
import org.bouncycastle.asn1.x509.CRLNumber;
import org.bouncycastle.asn1.x509.DistributionPoint;
import org.bouncycastle.asn1.x509.DistributionPointName;
import org.bouncycastle.asn1.x509.ExtendedKeyUsage;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.asn1.x509.KeyPurposeId;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaCertStore;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils;
import org.bouncycastle.cert.jcajce.JcaX509v2CRLBuilder;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoGeneratorBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;
import org.bouncycastle.tsp.TSPAlgorithms;
import org.bouncycastle.tsp.TimeStampRequest;
import org.bouncycastle.tsp.TimeStampResponseGenerator;
import org.bouncycastle.tsp.TimeStampTokenGenerator;
import org.jspecify.annotations.Nullable;

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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * An RFC 3161 timestamp service on the loopback address, with the revocation list of its certificate,
 * so the sealing tests run the real HTTP paths of the timestamp and revocation clients without reaching
 * the internet.
 *
 * <p>It answers every POST to {@code /tsr} with a timestamp signed by a throwaway timestamp certificate
 * that carries the critical timestamping key purpose and is issued by a throwaway root, {@link #root()}.
 * The token holds both certificates. The timestamp certificate names one revocation list address,
 * by default {@code /crl} on the same server, which serves an empty list signed by the root. It counts
 * the requests to both. The addresses for a service that is down and for one that never answers are
 * here as well.
 */
final class LocalTimestampService implements AutoCloseable {
    private static final ASN1ObjectIdentifier POLICY = new ASN1ObjectIdentifier("1.3.6.1.4.1.55555.7.1");
    private static final String TIMESTAMP_PATH = "/tsr";
    private static final String LIST_PATH = "/crl";
    private static final X500Name ROOT_NAME = new X500Name("CN=Ember test timestamp root");
    private static final KeyPair ROOT_KEYS = keyPair();
    private static final X509Certificate ROOT = root(ROOT_KEYS);
    private static final KeyPair KEYS = keyPair();

    private final HttpServer server;
    private final Duration delay;
    private final AtomicInteger requests = new AtomicInteger();
    private final AtomicInteger listRequests = new AtomicInteger();
    private final AtomicInteger serial = new AtomicInteger();
    private final CountDownLatch released = new CountDownLatch(1);
    private X509Certificate certificate;

    private LocalTimestampService(HttpServer server, Duration delay) {
        this.server = server;
        this.delay = delay;
    }

    /** @return a running service on a free loopback port that serves its own revocation list */
    static LocalTimestampService start() throws IOException {
        return start(Duration.ZERO, null);
    }

    /**
     * A service that waits before each answer, the timestamp and the revocation list alike.
     *
     * @param delay how long it waits
     * @return a running service on a free loopback port
     */
    static LocalTimestampService slow(Duration delay) throws IOException {
        return start(delay, null);
    }

    /**
     * A service whose certificate names its revocation list at another address.
     *
     * @param revocationListUrl the address in the certificate
     * @return a running service on a free loopback port
     */
    static LocalTimestampService listingAt(String revocationListUrl) throws IOException {
        return start(Duration.ZERO, revocationListUrl);
    }

    /**
     * A service that reads every request and counts it, then never answers until it is closed.
     *
     * @return a running service on a free loopback port
     */
    static LocalTimestampService hanging() throws IOException {
        var service = new LocalTimestampService(loopbackServer(), Duration.ZERO);
        service.server.createContext(TIMESTAMP_PATH, service::hang);
        service.server.start();
        return service;
    }

    /** @return the root that issued every service's timestamp certificate and signs its revocation list */
    static X509Certificate root() {
        return ROOT;
    }

    /** @return an address on the loopback interface where nothing listens */
    static String unreachableUrl() throws IOException {
        try (var socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            return urlOf(socket.getLocalPort(), TIMESTAMP_PATH);
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
        return urlOf(socket.getLocalPort(), TIMESTAMP_PATH);
    }

    /** @return the address timestamps are requested at */
    String url() {
        return urlOf(server.getAddress().getPort(), TIMESTAMP_PATH);
    }

    /** @return how many timestamp requests reached the service */
    int requests() {
        return requests.get();
    }

    /** @return how many requests for the revocation list reached the service */
    int listRequests() {
        return listRequests.get();
    }

    @Override
    public void close() {
        released.countDown();
        server.stop(0);
    }

    private static LocalTimestampService start(Duration delay, @Nullable String revocationListUrl) throws IOException {
        var service = new LocalTimestampService(loopbackServer(), delay);
        int port = service.server.getAddress().getPort();
        service.certificate = certificate(revocationListUrl == null ? urlOf(port, LIST_PATH) : revocationListUrl);
        service.server.createContext(TIMESTAMP_PATH, service::answer);
        service.server.createContext(LIST_PATH, service::list);
        service.server.start();
        return service;
    }

    private static HttpServer loopbackServer() throws IOException {
        return HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    }

    private static String urlOf(int port, String path) {
        return "http://127.0.0.1:" + port + path;
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
            waitBeforeAnswering();
            var response =
                    responseGenerator().generate(request, BigInteger.valueOf(serial.incrementAndGet()), new Date());
            send(exchange, "application/timestamp-reply", response.getEncoded());
        } catch (Exception e) {
            throw new IOException("The local timestamp service could not answer", e);
        }
    }

    private void list(HttpExchange exchange) throws IOException {
        listRequests.incrementAndGet();
        try (exchange) {
            waitBeforeAnswering();
            send(exchange, "application/pkix-crl", revocationList());
        } catch (Exception e) {
            throw new IOException("The local timestamp service could not serve its revocation list", e);
        }
    }

    private void waitBeforeAnswering() throws InterruptedException {
        if (!delay.isZero()) released.await(delay.toMillis(), TimeUnit.MILLISECONDS);
    }

    private static void send(HttpExchange exchange, String type, byte[] body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", type);
        exchange.sendResponseHeaders(200, body.length);
        exchange.getResponseBody().write(body);
    }

    private TimeStampResponseGenerator responseGenerator() throws Exception {
        var digests = new JcaDigestCalculatorProviderBuilder().build();
        var signerInfo =
                new JcaSimpleSignerInfoGeneratorBuilder().build("SHA256withRSA", KEYS.getPrivate(), certificate);
        var tokens = new TimeStampTokenGenerator(
                signerInfo, digests.get(new AlgorithmIdentifier(NISTObjectIdentifiers.id_sha256)), POLICY);
        tokens.addCertificates(new JcaCertStore(List.of(certificate, ROOT)));
        return new TimeStampResponseGenerator(tokens, TSPAlgorithms.ALLOWED);
    }

    private static byte[] revocationList() throws Exception {
        var now = Instant.now();
        var holder = new JcaX509v2CRLBuilder(ROOT, Date.from(now.minus(Duration.ofMinutes(1))))
                .setNextUpdate(Date.from(now.plus(Duration.ofDays(1))))
                .addExtension(Extension.cRLNumber, false, new CRLNumber(BigInteger.valueOf(now.toEpochMilli())))
                .addExtension(
                        Extension.authorityKeyIdentifier,
                        false,
                        new JcaX509ExtensionUtils().createAuthorityKeyIdentifier(ROOT))
                .build(new JcaContentSignerBuilder("SHA256withRSA").build(ROOT_KEYS.getPrivate()));
        return holder.getEncoded();
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

    private static X509Certificate root(KeyPair keys) {
        try {
            var extensions = new JcaX509ExtensionUtils();
            var builder = validFromYesterday(ROOT_NAME, ROOT_NAME, keys)
                    .addExtension(Extension.basicConstraints, true, new BasicConstraints(true))
                    .addExtension(Extension.keyUsage, true, new KeyUsage(KeyUsage.keyCertSign | KeyUsage.cRLSign))
                    .addExtension(
                            Extension.subjectKeyIdentifier,
                            false,
                            extensions.createSubjectKeyIdentifier(keys.getPublic()));
            return signed(builder, keys);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static X509Certificate certificate(String revocationListUrl) {
        try {
            var extensions = new JcaX509ExtensionUtils();
            var distributionPoint = new DistributionPoint(
                    new DistributionPointName(new GeneralNames(
                            new GeneralName(GeneralName.uniformResourceIdentifier, revocationListUrl))),
                    null,
                    null);
            var builder = validFromYesterday(new X500Name("CN=Ember test timestamp service"), ROOT_NAME, KEYS)
                    .addExtension(Extension.basicConstraints, true, new BasicConstraints(false))
                    .addExtension(
                            Extension.keyUsage, true, new KeyUsage(KeyUsage.digitalSignature | KeyUsage.nonRepudiation))
                    .addExtension(
                            Extension.extendedKeyUsage, true, new ExtendedKeyUsage(KeyPurposeId.id_kp_timeStamping))
                    .addExtension(
                            Extension.subjectKeyIdentifier,
                            false,
                            extensions.createSubjectKeyIdentifier(KEYS.getPublic()))
                    .addExtension(
                            Extension.authorityKeyIdentifier, false, extensions.createAuthorityKeyIdentifier(ROOT))
                    .addExtension(Extension.cRLDistributionPoints, false, new CRLDistPoint(new DistributionPoint[] {
                        distributionPoint
                    }));
            return signed(builder, ROOT_KEYS);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static X509v3CertificateBuilder validFromYesterday(X500Name subject, X500Name issuer, KeyPair keys) {
        var now = Instant.now();
        return new JcaX509v3CertificateBuilder(
                issuer,
                BigInteger.valueOf(System.nanoTime()).abs(),
                Date.from(now.minus(Duration.ofDays(1))),
                Date.from(now.plus(Duration.ofDays(30))),
                subject,
                keys.getPublic());
    }

    private static X509Certificate signed(X509v3CertificateBuilder builder, KeyPair signer) throws Exception {
        var holder = builder.build(new JcaContentSignerBuilder("SHA256withRSA").build(signer.getPrivate()));
        return new JcaX509CertificateConverter().getCertificate(holder);
    }
}
