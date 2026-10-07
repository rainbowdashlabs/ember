/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.service;

import dev.chojo.ember.conf.file.elements.Signing;
import eu.europa.esig.dss.enumerations.DigestAlgorithm;
import eu.europa.esig.dss.model.TimestampBinary;
import eu.europa.esig.dss.service.SecureRandomNonceSource;
import eu.europa.esig.dss.service.http.commons.TimestampDataLoader;
import eu.europa.esig.dss.service.tsp.OnlineTSPSource;
import eu.europa.esig.dss.spi.exception.DSSExternalResourceException;
import eu.europa.esig.dss.spi.x509.tsp.TSPSource;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.apache.hc.client5.http.impl.DefaultHttpRequestRetryStrategy;
import org.apache.hc.core5.util.TimeValue;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serial;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * The RFC 3161 timestamp services seals are stamped by, in the order the operator listed them.
 *
 * <p>This is the one place the signing feature reaches the network. A service receives the hash DSS
 * asks to have stamped, never the document. Each service gets {@link #TIMEOUT} to connect and again
 * to answer, with no retry; a service that fails is logged once and the next one is asked. All
 * services together get {@link #BUDGET} per seal: each timeout is cut to what is left of it, and once
 * it is spent no further service is asked. When the operator switched timestamps off or listed no
 * service, there is nothing to ask and no request is ever made.
 */
@Singleton
public class TimestampServices {
    /** How long one service may take to accept the connection, and again to answer. */
    public static final Duration TIMEOUT = Duration.ofSeconds(5);

    /**
     * How long one seal may spend on timestamp services altogether, so a seal never waits for every
     * listed service to time out. A service still being asked when it runs out may overrun it by the
     * time it takes to accept the connection.
     */
    public static final Duration BUDGET = Duration.ofSeconds(15);

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
     * @param timeout how long one service may take to accept the connection, and again to answer
     * @param budget  how long one seal may spend on all services together
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
     * A source for one seal, which asks the services in order and remembers which one answered.
     *
     * @return the source, or empty when timestamps are off
     */
    Optional<Round> round() {
        return urls.isEmpty() ? Optional.empty() : Optional.of(new Round(urls, timeout, budget));
    }

    /**
     * One pass over the services for one seal. Not shared between seals, since it remembers which
     * service answered.
     */
    static final class Round implements TSPSource {
        @Serial
        private static final long serialVersionUID = 1L;

        private static final Logger log = LoggerFactory.getLogger(Round.class);

        private final List<String> urls;
        private final Duration timeout;
        private final Duration budget;
        private @Nullable String answeredBy;

        private Round(List<String> urls, Duration timeout, Duration budget) {
            this.urls = urls;
            this.timeout = timeout;
            this.budget = budget;
        }

        @Override
        public TimestampBinary getTimeStampResponse(DigestAlgorithm digestAlgorithm, byte[] digest) {
            long deadline = System.nanoTime() + budget.toNanos();
            for (String url : urls) {
                var left = Duration.ofNanos(deadline - System.nanoTime());
                if (left.toMillis() <= 0) {
                    log.warn("The time for timestamp services ran out before {} was asked", url);
                    throw new DSSExternalResourceException("The time for timestamp services ran out");
                }
                try {
                    var timestamp = source(url, min(timeout, left)).getTimeStampResponse(digestAlgorithm, digest);
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

        private static Duration min(Duration a, Duration b) {
            return a.compareTo(b) <= 0 ? a : b;
        }

        private static OnlineTSPSource source(String url, Duration timeout) {
            var source = new OnlineTSPSource(url, loader(timeout));
            source.setNonceSource(new SecureRandomNonceSource());
            return source;
        }

        private static TimestampDataLoader loader(Duration timeout) {
            var loader = new TimestampDataLoader();
            int millis = Math.toIntExact(timeout.toMillis());
            loader.setTimeoutConnection(millis);
            loader.setTimeoutConnectionRequest(millis);
            loader.setTimeoutResponse(millis);
            loader.setTimeoutSocket(millis);
            loader.setRetryStrategy(new DefaultHttpRequestRetryStrategy(0, TimeValue.ZERO_MILLISECONDS));
            return loader;
        }
    }
}
