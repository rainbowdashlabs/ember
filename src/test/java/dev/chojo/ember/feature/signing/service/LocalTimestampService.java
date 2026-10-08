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
import org.bouncycastle.asn1.ocsp.OCSPObjectIdentifiers;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.AccessDescription;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.asn1.x509.AuthorityInformationAccess;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.CRLDistPoint;
import org.bouncycastle.asn1.x509.CRLNumber;
import org.bouncycastle.asn1.x509.DistributionPoint;
import org.bouncycastle.asn1.x509.DistributionPointName;
import org.bouncycastle.asn1.x509.ExtendedKeyUsage;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.Extensions;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.asn1.x509.KeyPurposeId;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaCertStore;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils;
import org.bouncycastle.cert.jcajce.JcaX509v2CRLBuilder;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.cert.ocsp.CertificateStatus;
import org.bouncycastle.cert.ocsp.OCSPReq;
import org.bouncycastle.cert.ocsp.OCSPRespBuilder;
import org.bouncycastle.cert.ocsp.RespID;
import org.bouncycastle.cert.ocsp.jcajce.JcaBasicOCSPRespBuilder;
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
import java.util.function.IntFunction;

/**
 * An RFC 3161 timestamp service on the loopback address, with the revocation list and the status
 * responder of its certificate, so the sealing tests run the real HTTP paths of the timestamp and
 * revocation clients without reaching the internet.
 *
 * <p>It answers every POST to {@code /tsr} with a timestamp signed by a throwaway timestamp certificate
 * that carries the critical timestamping key purpose and is issued by a throwaway root, {@link #root()},
 * the root a test pins for it ({@link #pinned()}). The token holds both certificates. The timestamp
 * certificate names its revocation list addresses, by default {@code /crl} on the same server, which
 * serves an empty list signed by the root, and may name a status responder (OCSP), {@code /ocsp} on the
 * same server or elsewhere, which answers "good" for every certificate it is asked about, signed by the
 * root. It counts the requests to all three. The addresses for a service that is down and for one that
 * never answers are here as well.
 */
final class LocalTimestampService implements AutoCloseable {
    private static final ASN1ObjectIdentifier POLICY = new ASN1ObjectIdentifier("1.3.6.1.4.1.55555.7.1");
    private static final String TIMESTAMP_PATH = "/tsr";
    private static final String LIST_PATH = "/crl";
    private static final String STATUS_PATH = "/ocsp";
    private static final Duration CERTIFICATE_VALIDITY = Duration.ofDays(30);
    private static final Duration ROOT_VALIDITY = Duration.ofDays(3650);
    private static final X500Name ROOT_NAME = new X500Name("CN=Ember test timestamp root");
    private static final KeyPair ROOT_KEYS = keyPair();
    private static final X509Certificate ROOT = root(ROOT_NAME, ROOT_KEYS);
    private static final X509Certificate OTHER_ROOT = root(new X500Name("CN=Ember test other root"), keyPair());
    private static final KeyPair KEYS = keyPair();

    private final HttpServer server;
    private final Duration delay;
    private final AtomicInteger requests = new AtomicInteger();
    private final AtomicInteger listRequests = new AtomicInteger();
    private final AtomicInteger statusRequests = new AtomicInteger();
    private final AtomicInteger serial = new AtomicInteger();
    private final CountDownLatch released = new CountDownLatch(1);
    private X509Certificate certificate;

    private LocalTimestampService(HttpServer server, Duration delay) {
        this.server = server;
        this.delay = delay;
    }

    /** @return a running service on a free loopback port that serves its own revocation list */
    static LocalTimestampService start() throws IOException {
        return start(Duration.ZERO, port -> new Names(List.of(urlOf(port, LIST_PATH)), null));
    }

    /**
     * A service whose timestamp certificate is valid for the given time from now on instead of 30 days, so
     * its timestamps rest on a certificate that ends later or sooner than those of another service.
     *
     * @param validity how long its certificate stays valid
     * @return a running service on a free loopback port that serves its own revocation list
     */
    static LocalTimestampService lastingFor(Duration validity) throws IOException {
        return start(Duration.ZERO, validity, port -> new Names(List.of(urlOf(port, LIST_PATH)), null));
    }

    /**
     * A service that waits before each answer, the timestamp and the revocation list alike.
     *
     * @param delay how long it waits
     * @return a running service on a free loopback port
     */
    static LocalTimestampService slow(Duration delay) throws IOException {
        return start(delay, port -> new Names(List.of(urlOf(port, LIST_PATH)), null));
    }

    /**
     * A service whose certificate names its revocation lists at other addresses and no status responder.
     *
     * @param revocationListUrls the addresses in the certificate, in order
     * @return a running service on a free loopback port
     */
    static LocalTimestampService listingAt(String... revocationListUrls) throws IOException {
        return start(Duration.ZERO, port -> new Names(List.of(revocationListUrls), null));
    }

    /**
     * A service whose certificate names its own status responder and its own revocation list.
     *
     * @return a running service on a free loopback port
     */
    static LocalTimestampService withStatusResponder() throws IOException {
        return start(Duration.ZERO, port -> new Names(List.of(urlOf(port, LIST_PATH)), urlOf(port, STATUS_PATH)));
    }

    /**
     * A service whose certificate names a status responder at another address, and its own revocation
     * list.
     *
     * @param statusUrl the status responder's address in the certificate
     * @return a running service on a free loopback port
     */
    static LocalTimestampService withStatusResponderAt(String statusUrl) throws IOException {
        return start(Duration.ZERO, port -> new Names(List.of(urlOf(port, LIST_PATH)), statusUrl));
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

    /** @return the root that issued every service's timestamp certificate and signs its revocation data */
    static X509Certificate root() {
        return ROOT;
    }

    /** @return a self-signed root that issued nothing these services hand out */
    static X509Certificate otherRoot() {
        return OTHER_ROOT;
    }

    /**
     * @param url a timestamp service's address
     * @return the service paired with {@link #root()}
     */
    static TimestampServices.Service pinned(String url) {
        return new TimestampServices.Service(url, ROOT);
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

    /** @return an empty revocation list signed by {@link #root()}, DER encoded */
    static byte[] revocationList() throws Exception {
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

    /**
     * A signing certificate for somebody else, issued by {@link #root()} and naming this service's
     * revocation list, so a seal made with it reaches {@code BASELINE-LT} and its chain ends in a pinned
     * timestamp root.
     *
     * @param commonName the certificate's common name
     * @return its private key and certificate
     */
    SigningCertificates.Issued signingCertificate(String commonName) {
        try {
            var keys = keyPair();
            var extensions = new JcaX509ExtensionUtils();
            var list = new DistributionPoint(
                    new DistributionPointName(new GeneralNames(new GeneralName(
                            GeneralName.uniformResourceIdentifier,
                            urlOf(server.getAddress().getPort(), LIST_PATH)))),
                    null,
                    null);
            var builder = validFromYesterday(new X500Name("CN=" + commonName), ROOT_NAME, keys, CERTIFICATE_VALIDITY)
                    .addExtension(Extension.basicConstraints, true, new BasicConstraints(false))
                    .addExtension(
                            Extension.keyUsage, true, new KeyUsage(KeyUsage.digitalSignature | KeyUsage.nonRepudiation))
                    .addExtension(
                            Extension.subjectKeyIdentifier,
                            false,
                            extensions.createSubjectKeyIdentifier(keys.getPublic()))
                    .addExtension(
                            Extension.authorityKeyIdentifier, false, extensions.createAuthorityKeyIdentifier(ROOT))
                    .addExtension(
                            Extension.cRLDistributionPoints, false, new CRLDistPoint(new DistributionPoint[] {list}));
            return new SigningCertificates.Issued(keys.getPrivate(), signed(builder, ROOT_KEYS));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** @return the address timestamps are requested at */
    String url() {
        return urlOf(server.getAddress().getPort(), TIMESTAMP_PATH);
    }

    /** @return this service paired with {@link #root()} */
    TimestampServices.Service pinned() {
        return pinned(url());
    }

    /** @return how many timestamp requests reached the service */
    int requests() {
        return requests.get();
    }

    /** @return how many requests for the revocation list reached the service */
    int listRequests() {
        return listRequests.get();
    }

    /** @return how many status requests reached the service */
    int statusRequests() {
        return statusRequests.get();
    }

    @Override
    public void close() {
        released.countDown();
        server.stop(0);
    }

    private static LocalTimestampService start(Duration delay, IntFunction<Names> names) throws IOException {
        return start(delay, CERTIFICATE_VALIDITY, names);
    }

    private static LocalTimestampService start(Duration delay, Duration validity, IntFunction<Names> names)
            throws IOException {
        var service = new LocalTimestampService(loopbackServer(), delay);
        service.certificate =
                certificate(names.apply(service.server.getAddress().getPort()), validity);
        service.server.createContext(TIMESTAMP_PATH, service::answer);
        service.server.createContext(LIST_PATH, service::list);
        service.server.createContext(STATUS_PATH, service::status);
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

    private void status(HttpExchange exchange) throws IOException {
        statusRequests.incrementAndGet();
        try (exchange) {
            var request = new OCSPReq(exchange.getRequestBody().readAllBytes());
            var digests = new JcaDigestCalculatorProviderBuilder().build();
            var builder = new JcaBasicOCSPRespBuilder(ROOT.getPublicKey(), digests.get(RespID.HASH_SHA1));
            var now = Instant.now();
            for (var single : request.getRequestList()) {
                builder.addResponse(
                        single.getCertID(),
                        CertificateStatus.GOOD,
                        Date.from(now.minus(Duration.ofMinutes(1))),
                        Date.from(now.plus(Duration.ofDays(1))),
                        null);
            }
            var nonce = request.getExtension(OCSPObjectIdentifiers.id_pkix_ocsp_nonce);
            if (nonce != null) builder.setResponseExtensions(new Extensions(nonce));
            var answer = builder.build(
                    new JcaContentSignerBuilder("SHA256withRSA").build(ROOT_KEYS.getPrivate()),
                    new X509CertificateHolder[] {new JcaX509CertificateHolder(ROOT)},
                    Date.from(now));
            send(
                    exchange,
                    "application/ocsp-response",
                    new OCSPRespBuilder()
                            .build(OCSPRespBuilder.SUCCESSFUL, answer)
                            .getEncoded());
        } catch (Exception e) {
            throw new IOException("The local status responder could not answer", e);
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

    private static KeyPair keyPair() {
        try {
            var generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private static X509Certificate root(X500Name name, KeyPair keys) {
        try {
            var extensions = new JcaX509ExtensionUtils();
            var builder = validFromYesterday(name, name, keys, ROOT_VALIDITY)
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

    private static X509Certificate certificate(Names names, Duration validity) {
        try {
            var extensions = new JcaX509ExtensionUtils();
            var points = names.lists().stream()
                    .map(url -> new DistributionPoint(
                            new DistributionPointName(
                                    new GeneralNames(new GeneralName(GeneralName.uniformResourceIdentifier, url))),
                            null,
                            null))
                    .toArray(DistributionPoint[]::new);
            var builder = validFromYesterday(new X500Name("CN=Ember test timestamp service"), ROOT_NAME, KEYS, validity)
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
                    .addExtension(Extension.cRLDistributionPoints, false, new CRLDistPoint(points));
            if (names.status() != null) {
                builder.addExtension(
                        Extension.authorityInfoAccess,
                        false,
                        new AuthorityInformationAccess(new AccessDescription(
                                AccessDescription.id_ad_ocsp,
                                new GeneralName(GeneralName.uniformResourceIdentifier, names.status()))));
            }
            return signed(builder, ROOT_KEYS);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static X509v3CertificateBuilder validFromYesterday(
            X500Name subject, X500Name issuer, KeyPair keys, Duration validity) {
        var now = Instant.now();
        return new JcaX509v3CertificateBuilder(
                issuer,
                BigInteger.valueOf(System.nanoTime()).abs(),
                Date.from(now.minus(Duration.ofDays(1))),
                Date.from(now.plus(validity)),
                subject,
                keys.getPublic());
    }

    private static X509Certificate signed(X509v3CertificateBuilder builder, KeyPair signer) throws Exception {
        var holder = builder.build(new JcaContentSignerBuilder("SHA256withRSA").build(signer.getPrivate()));
        return new JcaX509CertificateConverter().getCertificate(holder);
    }

    /**
     * The revocation addresses a timestamp certificate names.
     *
     * @param lists  its revocation list addresses, in order
     * @param status its status responder's address, or null for none
     */
    private record Names(List<String> lists, @Nullable String status) {}
}
