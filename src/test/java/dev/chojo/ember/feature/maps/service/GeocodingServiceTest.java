/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.maps.service;

import com.github.benmanes.caffeine.cache.Ticker;
import dev.chojo.ember.api.refusal.MapRefusal;
import dev.chojo.ember.api.refusal.RefusalResponse;
import dev.chojo.ember.feature.maps.entity.GeocodePrecision;
import dev.chojo.ember.feature.maps.entity.GeocodeQuery;
import dev.chojo.ember.feature.maps.entity.GeocodingProvider;
import dev.chojo.ember.feature.maps.entity.MapsGeocodingConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/** The address search against answers recorded from a Photon server. */
class GeocodingServiceTest {

    private final List<URI> asked = new ArrayList<>();
    private final AtomicLong nanos = new AtomicLong();
    private MapsConfigService config;
    private String answer;
    private IOException failure;
    private GeocodingService service;

    private static String recorded(String name) throws IOException {
        try (InputStream in = GeocodingServiceTest.class.getResourceAsStream("/maps/photon/" + name + ".json")) {
            if (in == null) throw new IOException("missing recording " + name);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static GeocodeQuery berlin() {
        return GeocodeQuery.address("Unter den Linden 1", "10117", "Berlin", "DE");
    }

    @BeforeEach
    void setUp() {
        config = Mockito.mock(MapsConfigService.class);
        when(config.geocodingConfig()).thenReturn(new MapsGeocodingConfig(GeocodingProvider.PHOTON, ""));
        Ticker ticker = nanos::get;
        service = new GeocodingService(
                config,
                uri -> {
                    asked.add(uri);
                    if (failure != null) throw failure;
                    return answer;
                },
                ticker);
    }

    @Test
    void aHouseIsExactAndTheCoordinatesAreSwapped() throws IOException {
        answer = recorded("house");
        var result = service.locate(berlin()).orElseThrow();
        assertEquals(52.5170365, result.latitude());
        assertEquals(13.3888599, result.longitude());
        assertEquals(GeocodePrecision.EXACT, result.precision());
    }

    @Test
    void aStreetIsStreetPrecision() throws IOException {
        answer = recorded("street");
        assertEquals(
                GeocodePrecision.STREET, service.locate(berlin()).orElseThrow().precision());
    }

    @Test
    void aTownIsAnArea() throws IOException {
        answer = recorded("city");
        var result = service.locate(GeocodeQuery.address(null, null, "Frankfurt am Main", "DE"))
                .orElseThrow();
        assertEquals(GeocodePrecision.AREA, result.precision());
        assertEquals(50.1106444, result.latitude());
    }

    @Test
    void noMatchIsEmptyNotAFailure() throws IOException {
        answer = recorded("empty");
        assertTrue(service.locate(berlin()).isEmpty());
    }

    @Test
    void aTimeoutIsRefusedAsNotAnswering() {
        failure = new HttpTimeoutException("request timed out");
        var refusal = assertThrows(RefusalResponse.class, () -> service.locate(berlin()));
        assertEquals(MapRefusal.GEOCODING_NOT_ANSWERING, refusal.refusal());
    }

    @Test
    void anUnreadableAnswerIsRefusedAsNotAnswering() {
        answer = "<html>busy</html>";
        var refusal = assertThrows(RefusalResponse.class, () -> service.locate(berlin()));
        assertEquals(MapRefusal.GEOCODING_NOT_ANSWERING, refusal.refusal());
    }

    @Test
    void aMatchWithoutCoordinatesIsRefusedAsNotAnswering() {
        answer = "{\"features\":[{\"geometry\":{\"coordinates\":[]},\"properties\":{\"type\":\"house\"}}]}";
        var refusal = assertThrows(RefusalResponse.class, () -> service.locate(berlin()));
        assertEquals(MapRefusal.GEOCODING_NOT_ANSWERING, refusal.refusal());
    }

    @Test
    void anAnswerWithoutFeaturesIsRefusedAsNotAnswering() {
        answer = "{\"message\":\"bad request\"}";
        var refusal = assertThrows(RefusalResponse.class, () -> service.locate(berlin()));
        assertEquals(MapRefusal.GEOCODING_NOT_ANSWERING, refusal.refusal());
    }

    @Test
    void anInterruptedSearchIsRefusedAndKeepsTheInterrupt() {
        Ticker ticker = nanos::get;
        var interrupted = new GeocodingService(
                config,
                uri -> {
                    throw new InterruptedException();
                },
                ticker);
        var refusal = assertThrows(RefusalResponse.class, () -> interrupted.locate(berlin()));
        assertEquals(MapRefusal.GEOCODING_NOT_ANSWERING, refusal.refusal());
        assertTrue(Thread.interrupted());
    }

    @Test
    void aBlankSearchAsksNobodyAndFindsNothing() {
        assertTrue(service.locate(GeocodeQuery.address(" ", null, "", "DE")).isEmpty());
        assertTrue(service.locate(new GeocodeQuery("   ", null, null, null)).isEmpty());
        assertTrue(asked.isEmpty());
    }

    @Test
    void aSwitchedOffInstanceAsksNobody() {
        when(config.geocodingConfig()).thenReturn(MapsGeocodingConfig.DEFAULT);
        assertFalse(service.enabled());
        var refusal = assertThrows(RefusalResponse.class, () -> service.locate(berlin()));
        assertEquals(MapRefusal.GEOCODING_SWITCHED_OFF, refusal.refusal());
        assertTrue(asked.isEmpty());
    }

    @Test
    void theSearchNamesTheAddressCountryAndLanguage() throws IOException {
        answer = recorded("house");
        service.locate(berlin());
        assertTrue(service.enabled());
        URI uri = asked.getFirst();
        assertEquals("https://photon.komoot.io/api", uri.getScheme() + "://" + uri.getHost() + uri.getPath());
        assertEquals("q=Unter+den+Linden+1%2C+10117+Berlin&limit=1&lang=de&countrycode=DE", uri.getRawQuery());
    }

    @Test
    void aBiasedSearchNamesThePointAndAnOwnServerIsAsked() throws IOException {
        when(config.geocodingConfig())
                .thenReturn(new MapsGeocodingConfig(GeocodingProvider.PHOTON, "https://photon.example/ "));
        answer = recorded("house");
        service.locate(new GeocodeQuery("Gerätehaus", null, 50.5, 8.25));
        URI uri = asked.getFirst();
        assertEquals("photon.example", uri.getHost());
        assertEquals("/api", uri.getPath());
        assertTrue(uri.getRawQuery().endsWith("&lat=50.500000&lon=8.250000"));
        assertFalse(uri.getRawQuery().contains("countrycode"));
    }

    @Test
    void anAnswerIsKeptForADayUnderItsNormalisedSearch() throws IOException {
        answer = recorded("house");
        service.locate(berlin());
        service.locate(GeocodeQuery.address("  unter den   LINDEN 1 ", "10117", "berlin", "de"));
        assertEquals(1, asked.size());

        nanos.addAndGet(Duration.ofHours(25).toNanos());
        service.locate(berlin());
        assertEquals(2, asked.size());
    }

    @Test
    void anEmptyAnswerIsKeptButAFailureIsNot() throws IOException {
        answer = recorded("empty");
        service.locate(berlin());
        service.locate(berlin());
        assertEquals(1, asked.size());

        var other = GeocodeQuery.address("Somewhere 2", null, "Berlin", "DE");
        failure = new IOException("HTTP 502");
        assertThrows(RefusalResponse.class, () -> service.locate(other));
        failure = null;
        answer = recorded("street");
        assertEquals(
                GeocodePrecision.STREET, service.locate(other).orElseThrow().precision());
        assertEquals(3, asked.size());
    }

    @Test
    void anotherServerIsAskedAgain() throws IOException {
        answer = recorded("house");
        service.locate(berlin());
        when(config.geocodingConfig())
                .thenReturn(new MapsGeocodingConfig(GeocodingProvider.PHOTON, "https://photon.example"));
        service.locate(berlin());
        assertEquals(2, asked.size());
    }

    @Test
    void theAddressLeavesOutBlankParts() {
        assertEquals("Berlin", GeocodeQuery.address(" ", null, "Berlin", null).text());
        assertEquals(
                "Hauptstr. 3, 12345",
                GeocodeQuery.address("Hauptstr. 3", "12345", "", null).text());
        assertTrue(GeocodeQuery.address(null, null, null, "DE").isBlank());
    }

    @Test
    void precisionFollowsThePhotonType() {
        assertEquals(GeocodePrecision.EXACT, GeocodePrecision.ofPhotonType("house"));
        assertEquals(GeocodePrecision.STREET, GeocodePrecision.ofPhotonType("street"));
        assertEquals(GeocodePrecision.AREA, GeocodePrecision.ofPhotonType("district"));
        assertEquals(GeocodePrecision.AREA, GeocodePrecision.ofPhotonType("locality"));
        assertEquals(GeocodePrecision.AREA, GeocodePrecision.ofPhotonType(null));
    }

    @Test
    void aNoBreakSpaceIsWhitespaceLikeAnyOther() {
        assertEquals("altes gerätehaus", GeocodeQuery.normalise("\u00a0Altes\u00a0\u2009 Gerätehaus\u00a0"));
    }
}
