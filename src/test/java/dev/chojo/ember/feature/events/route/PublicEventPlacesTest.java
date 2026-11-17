/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.route;

import dev.chojo.ember.api.RouteHarness;
import dev.chojo.ember.feature.events.entity.AppointmentField;
import dev.chojo.ember.feature.events.entity.EventQuestionSettings;
import dev.chojo.ember.feature.events.entity.StationCalendar;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.service.EventCategoryService;
import dev.chojo.ember.feature.events.service.EventCrudService;
import dev.chojo.ember.feature.events.service.EventFieldService;
import dev.chojo.ember.feature.events.service.EventPlaceService;
import dev.chojo.ember.feature.events.service.OccurrenceCalendar;
import dev.chojo.ember.feature.maps.entity.GeocodePrecision;
import dev.chojo.ember.feature.maps.entity.StationPlace;
import dev.chojo.ember.feature.question.FieldType;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.entity.StationModule;
import dev.chojo.ember.feature.station.service.StationService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static dev.chojo.ember.api.RouteHarness.PREFIX;
import static dev.chojo.ember.api.RouteHarness.json;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** A public appointment page carries the pins of its public place fields and of no other field. */
class PublicEventPlacesTest {
    private static final int STATION_ID = 4;
    private static final int EVENT_ID = 12;
    private static final UUID STATION_UID = UUID.fromString("00000000-0000-0000-0000-000000000004");

    private static AppointmentField location(int id, String value, boolean isPublic) {
        return new AppointmentField(
                id,
                EVENT_ID,
                "Ort",
                FieldType.LOCATION,
                EventQuestionSettings.empty(),
                value,
                id,
                true,
                null,
                isPublic);
    }

    @Test
    void onlyPublicPlaceFieldsBringTheirPin() {
        var station = mock(Station.class);
        when(station.id()).thenReturn(STATION_ID);
        when(station.publicCalendarEnabled()).thenReturn(true);
        var stations = mock(StationService.class);
        when(stations.findByUid(STATION_UID)).thenReturn(Optional.of(station));
        when(stations.isModuleEnabled(STATION_ID, StationModule.EVENTS)).thenReturn(true);

        var event = mock(StationEvent.class);
        when(event.id()).thenReturn(EVENT_ID);
        when(event.stationId()).thenReturn(STATION_ID);
        when(event.isPublic()).thenReturn(true);
        when(event.name()).thenReturn("Übung");
        when(event.eventType()).thenReturn(StationEvent.EventType.ONE_TIME);
        when(event.startTime()).thenReturn(Instant.parse("2026-12-01T18:00:00Z"));
        when(event.endTime()).thenReturn(Instant.parse("2026-12-01T20:00:00Z"));
        var crud = mock(EventCrudService.class);
        when(crud.findById(EVENT_ID)).thenReturn(Optional.of(event));

        var shown = location(1, "Gerätehaus", true);
        var hidden = location(2, "Privatweg", false);
        var fields = mock(EventFieldService.class);
        when(fields.findByEvent(EVENT_ID, null)).thenReturn(List.of(shown, hidden));

        var calendar = mock(OccurrenceCalendar.class);
        when(calendar.dateInView(event)).thenReturn(Optional.empty());
        var stationCalendar = mock(StationCalendar.class);
        when(stationCalendar.today()).thenReturn(LocalDate.of(2026, 11, 17));
        when(calendar.forStation(STATION_ID)).thenReturn(stationCalendar);

        var places = mock(EventPlaceService.class);
        var pin = new StationPlace("Gerätehaus", 52.1, 10.6, GeocodePrecision.EXACT, Instant.EPOCH);
        when(places.pinsOf(STATION_ID, List.of(shown))).thenReturn(List.of(pin));

        var harness = RouteHarness.serving(
                new PublicEventRoutes(crud, mock(EventCategoryService.class), fields, calendar, stations, places));

        var detail =
                json(harness.request(client -> client.get(PREFIX + "/public/events/" + STATION_UID + "/" + EVENT_ID)));

        assertEquals(1, detail.path("places").size());
        assertEquals("Gerätehaus", detail.path("places").get(0).path("text").asString());
        assertEquals(1, detail.path("publicFields").size());
        verify(places).pinsOf(any(Integer.class), any());
    }
}
