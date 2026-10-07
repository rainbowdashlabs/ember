/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.service;

import dev.chojo.ember.conf.file.elements.Signing;
import eu.europa.esig.dss.enumerations.DigestAlgorithm;
import eu.europa.esig.dss.model.TimestampBinary;
import eu.europa.esig.dss.model.x509.revocation.crl.CRL;
import eu.europa.esig.dss.model.x509.revocation.ocsp.OCSP;
import eu.europa.esig.dss.service.SecureRandomNonceSource;
import eu.europa.esig.dss.service.crl.OnlineCRLSource;
import eu.europa.esig.dss.service.http.commons.CommonsDataLoader;
import eu.europa.esig.dss.service.ocsp.OnlineOCSPSource;
import eu.europa.esig.dss.service.tsp.OnlineTSPSource;
import eu.europa.esig.dss.spi.client.http.Protocol;
import eu.europa.esig.dss.spi.exception.DSSExternalResourceException;
import eu.europa.esig.dss.spi.x509.revocation.RevocationSource;
import eu.europa.esig.dss.spi.x509.tsp.TSPSource;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.apache.hc.client5.http.impl.DefaultHttpRequestRetryStrategy;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.core5.util.TimeValue;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serial;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * The RFC 3161 timestamp services seals are stamped by, in the order the operator listed them, and
 * the public revocation data of their certificates.
 *
 * <p>This is the one place the signing feature reaches the network. A service receives the hash DSS
 * asks to have stamped, never the document. Each service gets {@link #TIMEOUT} to connect and again
 * to answer, with no retry; a service that fails is logged once and the next one is asked. After the
 * timestamp, the revocation lists and status answers its certificates name are fetched from the
 * public addresses in those certificates, which receives at most a certificate's serial number.
 * Everything one seal asks outside shares {@link #BUDGET}: each timeout is cut to what is left of it,
 * and once it is spent nothing more is asked. When the operator switched timestamps off or listed no
 * service, there is nothing to ask and no request is ever made.
 */
@Singleton
public class TimestampServices {
    /** How long one outside address may take to accept the connection, and again to answer. */
    public static final Duration TIMEOUT = Duration.ofSeconds(5);

    /**
     * How long one seal may spend on outside calls altogether, timestamp services and revocation data
     * together, so a seal never waits for every listed service to time out. A call still running when
     * it runs out may overrun it by the time it takes to accept the connection.
     */
    public static final Duration BUDGET = Duration.ofSeconds(15);

    private static final String TIMESTAMP_QUERY = "application/timestamp-query";
    private static final String OCSP_REQUEST = "application/ocsp-request";

    private final List<String> urls;
    private final Duration timeout;
    private final Duration budget;

    /**
     * Reads the services from the configuration.
     *
     * @param config the signing configuration
     */
    @Inject
    public TimestampServices(Signing config) {
        this(config.timestamps() ? config.timestampUrls() : List.of(), TIMEOUT, BUDGET);
    }

    /**
     * Lets a test name its own services, a shorter timeout and a shorter budget.
     *
     * @param urls    the services, asked in this order; empty for no timestamps
     * @param timeout how long one outside address may take to accept the connection, and again to answer
     * @param budget  how long one seal may spend on all outside calls together
     */
    TimestampServices(List<String> urls, Duration timeout, Duration budget) {
        this.urls = List.copyOf(urls);
        this.timeout = timeout;
        this.budget = budget;
    }

    /** @return services that are never asked, so every seal stays without a timestamp */
    static TimestampServices none() {
        return new TimestampServices(List.of(), TIMEOUT, BUDGET);
    }

    /**
     * The outside calls of one seal, whose budget starts now.
     *
     * @return the round, or empty when timestamps are off
     */
    Optional<Round> round() {
        return urls.isEmpty() ? Optional.empty() : Optional.of(new Round(urls, timeout, budget));
    }

    /**
     * One pass over the services for one seal, and the revocation data its timestamp needs, within one
     * budget that starts when the round is created. Not shared between seals, since it remembers which
     * service answered and how much of its budget is left.
     */
    static final class Round implements TSPSource {
        @Serial
        private static final long serialVersionUID = 1L;

        private static final Logger log = LoggerFactory.getLogger(Round.class);

        private final List<String> urls;
        private final Duration timeout;
        private final long deadline;
        private @Nullable String answeredBy;

        private Round(List<String> urls, Duration timeout, Duration budget) {
            this.urls = urls;
            this.timeout = timeout;
            this.deadline = System.nanoTime() + budget.toNanos();
        }

        @Override
        public TimestampBinary getTimeStampResponse(DigestAlgorithm digestAlgorithm, byte[] digest) {
            for (String url : urls) {
                if (spent()) {
                    log.warn("The time for timestamp services ran out before {} was asked", url);
                    throw new DSSExternalResourceException("The time for timestamp services ran out");
                }
                try {
                    var timestamp = source(url).getTimeStampResponse(digestAlgorithm, digest);
                    answeredBy = url;
                    log.info("Timestamp service {} stamped a seal", url);
                    return timestamp;
                } catch (RuntimeException e) {
                    log.warn("Timestamp service {} gave no timestamp: {}", url, e.getMessage());
                }
            }
            throw new DSSExternalResourceException("No timestamp service gave a timestamp");
        }

        /** @return the address of the service that gave the last timestamp, or null when none did */
        @Nullable
        String answeredBy() {
            return answeredBy;
        }

        /** @return a source that fetches the revocation lists a certificate names, within this round's budget */
        RevocationSource<CRL> revocationLists() {
            return new OnlineCRLSource(new BudgetedLoader(this, null));
        }

        /** @return a source that asks the status responder a certificate names, within this round's budget */
        RevocationSource<OCSP> revocationStatus() {
            var source = new OnlineOCSPSource(new BudgetedLoader(this, OCSP_REQUEST));
            source.setNonceSource(new SecureRandomNonceSource());
            return source;
        }

        private OnlineTSPSource source(String url) {
            var source = new OnlineTSPSource(url, new BudgetedLoader(this, TIMESTAMP_QUERY));
            source.setNonceSource(new SecureRandomNonceSource());
            return source;
        }

        private Duration left() {
            return Duration.ofNanos(deadline - System.nanoTime());
        }

        private boolean spent() {
            return left().toMillis() <= 0;
        }

        /**
         * Reaches one outside address over HTTP, with no retry, and with each timeout cut to what is
         * left of the round's budget at the moment the request starts. Refuses once the budget is spent,
         * and refuses addresses that are not HTTP, so a certificate cannot point it at a local file or a
         * directory service.
         */
        private static final class BudgetedLoader extends CommonsDataLoader {
            @Serial
            private static final long serialVersionUID = 1L;

            private final Round round;

            private BudgetedLoader(Round round, @Nullable String contentType) {
                super(contentType);
                this.round = round;
                setRetryStrategy(new DefaultHttpRequestRetryStrategy(0, TimeValue.ZERO_MILLISECONDS));
            }

            @Override
            public byte[] get(String url) {
                if (!Protocol.isHttpUrl(url)) {
                    throw new DSSExternalResourceException("Only HTTP addresses are asked, not " + url);
                }
                return super.get(url);
            }

            @Override
            protected synchronized HttpClientBuilder getHttpClientBuilder(String url) {
                var left = round.left();
                if (left.toMillis() <= 0) {
                    throw new DSSExternalResourceException(
                            "The time for this seal ran out before " + url + " was asked");
                }
                var timeout = round.timeout;
                int millis = Math.toIntExact((timeout.compareTo(left) <= 0 ? timeout : left).toMillis());
                setTimeoutConnection(millis);
                setTimeoutConnectionRequest(millis);
                setTimeoutResponse(millis);
                setTimeoutSocket(millis);
                return super.getHttpClientBuilder(url);
            }
        }
    }
}
