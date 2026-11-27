/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.maps.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import dev.chojo.ember.api.refusal.MapRefusal;
import dev.chojo.ember.feature.federation.service.OutboundHttp;
import dev.chojo.ember.feature.maps.entity.GeocodePrecision;
import dev.chojo.ember.feature.maps.entity.GeocodeQuery;
import dev.chojo.ember.feature.maps.entity.GeocodeResult;
import dev.chojo.ember.feature.maps.entity.GeocodingProvider;
import dev.chojo.ember.util.Json;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;

/**
 * Finds where an address lies by asking the Photon server the operator configured in Admin → Maps.
 *
 * <p>The browser never asks Photon itself: every search goes through here, so no reader's address
 * reaches a third party and the operator's setting alone decides whether any address leaves the
 * instance. With the provider set to {@link GeocodingProvider#NONE} every search is refused.
 *
 * <p>Photon answers GeoJSON, whose points are {@code [longitude, latitude]}; they are swapped into
 * latitude first here and nowhere else. The {@code type} of the match shows how close it is
 * ({@link GeocodePrecision#ofPhotonType(String)}). A search without a match is an empty answer,
 * not a failure; a server that does not answer within {@value #TIMEOUT_SECONDS} seconds, or answers
 * with something that is not a search result, is refused as
 * {@link MapRefusal#GEOCODING_NOT_ANSWERING}.
 *
 * <p>Answers, empty ones included, are kept for a day per server and normalised search, which
 * keeps the instance within the fair-use policy of the public server when the same address is
 * looked up again and again. Failures are not kept. Concurrent searches for the same thing wait
 * for the one request already under way. A blank search asks nobody and finds nothing.
 */
@Singleton
public class GeocodingService {
    private static final Logger log = LoggerFactory.getLogger(GeocodingService.class);
    private static final long TIMEOUT_SECONDS = 5;
    private static final Duration TIMEOUT = Duration.ofSeconds(TIMEOUT_SECONDS);
    private static final Duration KEEP_ANSWERS = Duration.ofHours(24);
    private static final int MOST_KEPT_ANSWERS = 10_000;

    private final MapsConfigService configService;
    private final PhotonFetcher fetcher;
    private final Cache<String, Optional<GeocodeResult>> answers;

    @Inject
    public GeocodingService(MapsConfigService configService) {
        this(configService, new HttpPhotonFetcher(), Ticker.systemTicker());
    }

    /**
     * A service asking through the given fetcher, with its cache on the given clock.
     *
     * @param configService where the provider and server are read from
     * @param fetcher       what sends a search to the server
     * @param ticker        the clock the cache ages its answers on
     */
    public GeocodingService(MapsConfigService configService, PhotonFetcher fetcher, Ticker ticker) {
        this.configService = configService;
        this.fetcher = fetcher;
        this.answers = Caffeine.newBuilder()
                .expireAfterWrite(KEEP_ANSWERS)
                .maximumSize(MOST_KEPT_ANSWERS)
                .ticker(ticker)
                .build();
    }

    /**
     * Whether this instance looks up addresses at all.
     *
     * @return true when the operator chose a provider
     */
    public boolean enabled() {
        return configService.geocodingConfig().provider() != GeocodingProvider.NONE;
    }

    /**
     * Looks up where a search lies.
     *
     * @param query what to search for
     * @return the best match, or empty when the server knows no match or there is nothing to search for
     * @throws dev.chojo.ember.api.refusal.RefusalResponse {@link MapRefusal#GEOCODING_SWITCHED_OFF} when no
     *     provider is configured, {@link MapRefusal#GEOCODING_NOT_ANSWERING} when the server fails
     */
    public Optional<GeocodeResult> locate(GeocodeQuery query) {
        var config = configService.geocodingConfig();
        if (config.provider() == GeocodingProvider.NONE) throw MapRefusal.GEOCODING_SWITCHED_OFF.raise();
        if (query.isBlank()) return Optional.empty();
        String baseUrl = config.resolvedBaseUrl();
        return answers.get(baseUrl + "|" + query.cacheKey(), key -> firstMatch(ask(baseUrl, query)));
    }

    private String ask(String baseUrl, GeocodeQuery query) {
        URI uri;
        try {
            uri = URI.create(baseUrl + "/api?" + query.photonParameters());
        } catch (IllegalArgumentException e) {
            log.warn("The configured address search server is not a usable address");
            throw MapRefusal.GEOCODING_NOT_ANSWERING.raise();
        }
        try {
            return fetcher.fetch(uri);
        } catch (IOException e) {
            log.warn("The address search at {} did not answer: {}", baseUrl, e.toString());
            throw MapRefusal.GEOCODING_NOT_ANSWERING.raise();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw MapRefusal.GEOCODING_NOT_ANSWERING.raise();
        }
    }

    private static Optional<GeocodeResult> firstMatch(String body) {
        JsonNode features;
        try {
            features = Json.LENIENT.readTree(body).path("features");
        } catch (JacksonException e) {
            log.warn("The address search answered with something that is not a search result");
            throw MapRefusal.GEOCODING_NOT_ANSWERING.raise();
        }
        if (!features.isArray()) throw MapRefusal.GEOCODING_NOT_ANSWERING.raise();
        if (features.isEmpty()) return Optional.empty();
        JsonNode first = features.get(0);
        JsonNode coordinates = first.path("geometry").path("coordinates");
        if (!coordinates.isArray()
                || coordinates.size() < 2
                || !coordinates.get(0).isNumber()
                || !coordinates.get(1).isNumber()) {
            throw MapRefusal.GEOCODING_NOT_ANSWERING.raise();
        }
        double longitude = coordinates.get(0).asDouble();
        double latitude = coordinates.get(1).asDouble();
        var precision = GeocodePrecision.ofPhotonType(typeOf(first));
        return Optional.of(new GeocodeResult(latitude, longitude, precision));
    }

    private static @Nullable String typeOf(JsonNode feature) {
        JsonNode type = feature.path("properties").path("type");
        return type.isString() ? type.asString() : null;
    }

    /** Sends one search to a Photon server. */
    @FunctionalInterface
    public interface PhotonFetcher {
        /**
         * @param uri the complete search address
         * @return the body of a successful answer
         * @throws IOException          when the server cannot be reached, does not answer in time or
         *                              answers with anything but success
         * @throws InterruptedException when the waiting thread was interrupted
         */
        String fetch(URI uri) throws IOException, InterruptedException;
    }

    /** Sends the search over HTTP, naming Ember in the {@code User-Agent} and waiting at most five seconds. */
    private static final class HttpPhotonFetcher implements PhotonFetcher {
        private final HttpClient client = OutboundHttp.trustedClient(TIMEOUT, HttpClient.Redirect.NORMAL);

        @Override
        public String fetch(URI uri) throws IOException, InterruptedException {
            var request = HttpRequest.newBuilder(uri)
                    .timeout(TIMEOUT)
                    .header("User-Agent", MapsUserAgent.value())
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IOException("HTTP " + response.statusCode());
            }
            return response.body();
        }
    }
}
