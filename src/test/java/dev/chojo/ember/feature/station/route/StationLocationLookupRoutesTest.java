/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.station.route;

import com.github.benmanes.caffeine.cache.Ticker;
import dev.chojo.ember.api.RouteHarness;
import dev.chojo.ember.api.TestSessions;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.refusal.MapRefusal;
import dev.chojo.ember.api.refusal.StationRefusal;
import dev.chojo.ember.feature.maps.entity.GeocodingProvider;
import dev.chojo.ember.feature.maps.entity.MapsGeocodingConfig;
import dev.chojo.ember.feature.maps.service.GeocodingRateLimiter;
import dev.chojo.ember.feature.maps.service.GeocodingService;
import dev.chojo.ember.feature.maps.service.MapsConfigService;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.feature.station.service.StationLocationService;
import io.javalin.testtools.HttpClient;
import io.javalin.testtools.Request;
import io.javalin.testtools.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static dev.chojo.ember.api.RouteHarness.PREFIX;
import static dev.chojo.ember.api.RouteHarness.body;
import static dev.chojo.ember.api.RouteHarness.header;
import static dev.chojo.ember.api.RouteHarness.json;
import static dev.chojo.ember.api.RouteHarness.refusalOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Looking the station's address up over HTTP: who may, what the answer shows, and how a switched-off,
 * failing or overused address search is refused.
 */
class StationLocationLookupRoutesTest {
    private static final String GEOCODE = PREFIX + "/station/location/geocode";
    private static final int STATION_ID = 7;
    private static final String HOUSE = """
            {"features":[{"geometry":{"coordinates":[13.38886,52.51704],"type":"Point"},\
            "properties":{"type":"house"}}],"type":"FeatureCollection"}""";
    private static final String NOTHING = "{\"features\":[],\"type\":\"FeatureCollection\"}";

    private final List<URI> asked = new ArrayList<>();
    private MapsConfigService config;
    private String answer = HOUSE;
    private IOException failure;
    private RouteHarness harness;

    @BeforeEach
    void serve() {
        config = mock(MapsConfigService.class);
        when(config.geocodingConfig()).thenReturn(new MapsGeocodingConfig(GeocodingProvider.PHOTON, null));
        var geocoding = new GeocodingService(
                config,
                uri -> {
                    asked.add(uri);
                    if (failure != null) throw failure;
                    return answer;
                },
                Ticker.systemTicker());
        var locations = new StationLocationService(mock(StationRepository.class), geocoding);
        var limiter = new GeocodingRateLimiter(Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));
        harness = RouteHarness.serving(new StationLocationLookupRoutes(locations, limiter));
    }

    private Consumer<Request.Builder> manager() {
        return harness.as(TestSessions.member(STATION_ID, StationPermission.STATION_GENERAL));
    }

    private static Response lookUp(HttpClient client, String address, Consumer<Request.Builder> as) {
        return client.post(GEOCODE, body(address), as);
    }

    private static String berlin() {
        return """
                {"addressLine": "Unter den Linden 1", "postalCode": "10117", "city": "Berlin", "country": "DE"}""";
    }

    @Test
    void aManagerGetsThePinAndItsPrecision() {
        harness.run((server, client) -> {
            var found = json(lookUp(client, berlin(), manager())).path("match");
            assertEquals(52.51704, found.path("latitude").asDouble());
            assertEquals(13.38886, found.path("longitude").asDouble());
            assertEquals("EXACT", found.path("precision").asString());
        });
        assertTrue(asked.getFirst().getRawQuery().contains("countrycode=DE"));
    }

    @Test
    void noMatchAnswersWithoutAPin() {
        answer = NOTHING;
        harness.run((server, client) -> {
            var response = lookUp(client, berlin(), manager());
            assertEquals(200, response.code());
            assertTrue(json(response).path("match").isNull());
        });
    }

    @Test
    void aMemberWhoMayNotSetTheLocationIsRefused() {
        harness.run((server, client) -> {
            var response = lookUp(client, berlin(), harness.as(TestSessions.member(STATION_ID)));
            assertEquals(403, response.code());
        });
        assertTrue(asked.isEmpty());
    }

    @Test
    void aSwitchedOffInstanceRefuses() {
        when(config.geocodingConfig()).thenReturn(MapsGeocodingConfig.DEFAULT);
        harness.run((server, client) -> {
            var response = lookUp(client, berlin(), manager());
            assertEquals(409, response.code());
            assertEquals(MapRefusal.GEOCODING_SWITCHED_OFF, refusalOf(response));
        });
    }

    @Test
    void aSearchThatDoesNotAnswerIsRefused() {
        failure = new IOException("HTTP 502");
        harness.run((server, client) -> {
            var response = lookUp(client, berlin(), manager());
            assertEquals(503, response.code());
            assertEquals(MapRefusal.GEOCODING_NOT_ANSWERING, refusalOf(response));
        });
    }

    @Test
    void aCountryThatIsNoCodeIsRefusedBeforeAnybodyIsAsked() {
        harness.run((server, client) -> assertEquals(
                StationRefusal.STATION_COUNTRY_NOT_A_CODE,
                refusalOf(lookUp(client, "{\"city\": \"Berlin\", \"country\": \"Deutschland\"}", manager()))));
        assertTrue(asked.isEmpty());
    }

    @Test
    void lookingUpTooOftenIsRefusedWithAWait() {
        harness.run((server, client) -> {
            for (int i = 0; i < GeocodingRateLimiter.BURST_CAPACITY; i++) {
                assertEquals(200, lookUp(client, berlin(), manager()).code());
            }
            var refused = lookUp(client, berlin(), manager());
            assertEquals(MapRefusal.GEOCODING_TOO_OFTEN, refusalOf(refused));
            assertNotNull(header(refused, "Retry-After"));
        });
        assertEquals(1, asked.size());
    }
}
