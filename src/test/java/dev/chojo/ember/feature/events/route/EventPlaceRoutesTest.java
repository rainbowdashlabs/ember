/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.route;

import dev.chojo.ember.api.RouteHarness;
import dev.chojo.ember.api.StationSession;
import dev.chojo.ember.api.TestSessions;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.refusal.EventRefusal;
import dev.chojo.ember.api.refusal.MapRefusal;
import dev.chojo.ember.feature.events.entity.AppointmentField;
import dev.chojo.ember.feature.events.entity.EventQuestionSettings;
import dev.chojo.ember.feature.events.service.EventFieldService;
import dev.chojo.ember.feature.events.service.EventPlaceService;
import dev.chojo.ember.feature.events.service.EventPlaceService.PlaceDraft;
import dev.chojo.ember.feature.maps.entity.GeocodePrecision;
import dev.chojo.ember.feature.maps.entity.GeocodeResult;
import dev.chojo.ember.feature.maps.entity.StationPlace;
import dev.chojo.ember.feature.maps.service.GeocodingRateLimiter;
import dev.chojo.ember.feature.question.FieldType;
import dev.chojo.ember.feature.station.entity.StationModule;
import io.javalin.testtools.Request;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import static dev.chojo.ember.api.RouteHarness.PREFIX;
import static dev.chojo.ember.api.RouteHarness.body;
import static dev.chojo.ember.api.RouteHarness.header;
import static dev.chojo.ember.api.RouteHarness.json;
import static dev.chojo.ember.api.RouteHarness.refusalOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Place pins over HTTP: editors of appointments read, store and look up pins of their own station,
 * members read the pins of an appointment they may see, and nobody else gets anywhere.
 */
class EventPlaceRoutesTest {
    private static final int STATION_ID = 5;
    private static final StationPlace HOUSE =
            new StationPlace("Gerätehaus", 52.1, 10.6, GeocodePrecision.EXACT, Instant.parse("2026-11-01T10:00:00Z"));

    private EventPlaceService places;
    private EventFieldService fields;
    private EventVisibility visibility;
    private RouteHarness harness;

    @BeforeEach
    void serve() {
        places = mock(EventPlaceService.class);
        fields = mock(EventFieldService.class);
        visibility = mock(EventVisibility.class);
        var limiter = new GeocodingRateLimiter(Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));
        harness = RouteHarness.serving(new EventPlaceRoutes(places, fields, visibility, limiter));
    }

    private Consumer<Request.Builder> editor() {
        return harness.as(TestSessions.member(STATION_ID, StationPermission.EVENT_EDIT));
    }

    private Consumer<Request.Builder> member() {
        return harness.as(TestSessions.member(STATION_ID, StationPermission.USER));
    }

    @Test
    void anEditorReadsTheStoredPinOfAPlace() {
        when(places.find(STATION_ID, "Gerätehaus")).thenReturn(Optional.of(HOUSE));
        harness.run((server, client) -> {
            var found = json(client.get(PREFIX + "/station/places?text=Ger%C3%A4tehaus", editor()));
            assertEquals("Gerätehaus", found.path("place").path("text").asString());
            assertEquals(52.1, found.path("place").path("latitude").asDouble());

            var none = json(client.get(PREFIX + "/station/places?text=Nirgendwo", editor()));
            assertTrue(none.path("place").isNull());
        });
    }

    @Test
    void anEditorStoresAPinForTheirOwnStation() {
        when(places.save(eq(STATION_ID), any())).thenReturn(HOUSE);
        harness.run((server, client) -> {
            var saved = client.put(
                    PREFIX + "/station/places",
                    body("{\"text\": \"Gerätehaus\", \"latitude\": 52.1, \"longitude\": 10.6, \"precision\": null}"),
                    editor());
            assertEquals(200, saved.code());
        });
        verify(places).save(STATION_ID, new PlaceDraft("Gerätehaus", 52.1, 10.6, null));
    }

    @Test
    void aDraftWithoutCoordinatesReachesTheServiceWithoutThem() {
        when(places.save(eq(STATION_ID), any())).thenThrow(EventRefusal.PLACE_OFF_THE_MAP.raise());
        harness.run((server, client) -> assertEquals(
                EventRefusal.PLACE_OFF_THE_MAP,
                refusalOf(client.put(PREFIX + "/station/places", body("{\"text\": \"Gerätehaus\"}"), editor()))));
        verify(places).save(STATION_ID, new PlaceDraft("Gerätehaus", null, null, null));
    }

    @Test
    void anEditorLooksAPlaceUpUntilTheyAskTooOften() {
        when(places.locate(STATION_ID, "Gerätehaus"))
                .thenReturn(Optional.of(new GeocodeResult(52.1, 10.6, GeocodePrecision.STREET)));
        harness.run((server, client) -> {
            for (int i = 0; i < GeocodingRateLimiter.BURST_CAPACITY; i++) {
                var found = json(
                        client.post(PREFIX + "/station/places/geocode", body("{\"text\": \"Gerätehaus\"}"), editor()));
                assertEquals("STREET", found.path("match").path("precision").asString());
            }
            var refused = client.post(PREFIX + "/station/places/geocode", body("{\"text\": \"Gerätehaus\"}"), editor());
            assertEquals(MapRefusal.GEOCODING_TOO_OFTEN, refusalOf(refused));
            assertNotNull(header(refused, "Retry-After"));
        });
    }

    @Test
    void aMemberWhoDoesNotEditAppointmentsTouchesNoPin() {
        harness.run((server, client) -> {
            assertEquals(
                    403, client.get(PREFIX + "/station/places?text=x", member()).code());
            assertEquals(
                    403,
                    client.put(
                                    PREFIX + "/station/places",
                                    body("{\"text\": \"x\", \"latitude\": 1, \"longitude\": 1}"),
                                    member())
                            .code());
            assertEquals(
                    403,
                    client.post(PREFIX + "/station/places/geocode", body("{\"text\": \"x\"}"), member())
                            .code());
        });
        verify(places, never()).save(anyInt(), any());
        verify(places, never()).locate(anyInt(), any());
    }

    @Test
    void aMemberReadsThePinsOfAnAppointmentTheyMaySeeOnItsDate() {
        var location = new AppointmentField(
                3, 9, "Ort", FieldType.LOCATION, EventQuestionSettings.empty(), "Gerätehaus", 0, true, null, false);
        when(fields.findByEvent(9, LocalDate.of(2026, 12, 1))).thenReturn(List.of(location));
        when(places.pinsOf(STATION_ID, List.of(location))).thenReturn(List.of(HOUSE));
        harness.run((server, client) -> {
            var pins = json(client.get(PREFIX + "/events/9/places?date=2026-12-01", member()));
            assertEquals(1, pins.size());
            assertEquals("Gerätehaus", pins.get(0).path("text").asString());
        });
        verify(visibility).requireVisibleEvent(any(StationSession.class), eq(9));
    }

    @Test
    void anAppointmentHiddenFromTheMemberShowsNoPins() {
        when(visibility.requireVisibleEvent(any(StationSession.class), eq(9)))
                .thenThrow(EventRefusal.EVENT_NOT_YOURS_TO_SEE.raise());
        harness.run((server, client) -> assertEquals(
                EventRefusal.EVENT_NOT_YOURS_TO_SEE, refusalOf(client.get(PREFIX + "/events/9/places", member()))));
        verify(places, never()).pinsOf(anyInt(), any());
    }

    @Test
    void pinsAreGoneWhileAppointmentsAreSwitchedOff() {
        harness.withModulesOff(StationModule.EVENTS);
        harness.run((server, client) -> {
            assertTrue(client.get(PREFIX + "/events/9/places", member()).code() >= 400);
            assertTrue(client.get(PREFIX + "/station/places?text=x", editor()).code() >= 400);
        });
        verify(places, never()).pinsOf(anyInt(), any());
        verify(places, never()).find(anyInt(), any());
    }
}
