/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.api.refusal.EventRefusal;
import dev.chojo.ember.api.refusal.RefusalResponse;
import dev.chojo.ember.feature.events.entity.AppointmentField;
import dev.chojo.ember.feature.events.entity.EventQuestionSettings;
import dev.chojo.ember.feature.events.service.EventPlaceService.PlaceDraft;
import dev.chojo.ember.feature.maps.entity.GeocodePrecision;
import dev.chojo.ember.feature.maps.entity.GeocodeQuery;
import dev.chojo.ember.feature.maps.entity.GeocodeResult;
import dev.chojo.ember.feature.maps.entity.StationPlace;
import dev.chojo.ember.feature.maps.repository.StationPlaceRepository;
import dev.chojo.ember.feature.maps.service.GeocodingService;
import dev.chojo.ember.feature.question.FieldType;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Place pins per station and place text: one pin for every spelling of a place, held to the map and
 * a sensible length, looked up near the station, and handed out only for place fields.
 */
class EventPlaceServiceTest extends RepositoryTestBase {

    private GeocodingService geocoding;
    private EventPlaceService service;
    private int stationId;

    private static AppointmentField field(FieldType type, String value) {
        return new AppointmentField(1, 1, "Ort", type, EventQuestionSettings.empty(), value, 0, true, null, true);
    }

    private static RefusalResponse refusalOf(Runnable action) {
        return assertThrows(RefusalResponse.class, action::run);
    }

    @BeforeEach
    void setUp() {
        geocoding = mock(GeocodingService.class);
        service = new EventPlaceService(new StationPlaceRepository(), stationRepo, geocoding);
        stationId = stationRepo.create("Orte " + UUID.randomUUID()).id();
    }

    @Test
    void everySpellingOfAPlaceSharesOnePin() {
        StationPlace saved = service.save(
                stationId, new PlaceDraft("  Gerätehaus  ", 52.1234567, 10.7654321, GeocodePrecision.STREET));

        assertEquals("Gerätehaus", saved.text());
        assertEquals(52.123457, saved.latitude());
        assertEquals(10.765432, saved.longitude());
        assertEquals(saved, service.find(stationId, "GERÄTEHAUS").orElseThrow());
        assertEquals(saved, service.find(stationId, "gerätehaus ").orElseThrow());

        service.save(stationId, new PlaceDraft("gerätehaus", 52.2, 10.8, null));
        assertEquals(52.2, service.find(stationId, "Gerätehaus").orElseThrow().latitude());
    }

    @Test
    void aBlankTextHasNoPin() {
        assertTrue(service.find(stationId, null).isEmpty());
        assertTrue(service.find(stationId, "  ").isEmpty());
    }

    @Test
    void aPinNeedsAShortNameAndAPointOnTheMap() {
        assertEquals(
                EventRefusal.PLACE_TEXT_MISSING,
                refusalOf(() -> service.save(stationId, new PlaceDraft(" \u00a0", 1.0, 1.0, null)))
                        .refusal());
        assertEquals(
                EventRefusal.PLACE_TEXT_TOO_LONG,
                refusalOf(() -> service.save(stationId, new PlaceDraft("x".repeat(201), 1.0, 1.0, null)))
                        .refusal());
        assertEquals(
                EventRefusal.PLACE_OFF_THE_MAP,
                refusalOf(() -> service.save(stationId, new PlaceDraft("Ort", 90.5, 1.0, null)))
                        .refusal());
        assertEquals(
                EventRefusal.PLACE_OFF_THE_MAP,
                refusalOf(() -> service.save(stationId, new PlaceDraft("Ort", 1.0, -180.5, null)))
                        .refusal());
        assertEquals(
                EventRefusal.PLACE_OFF_THE_MAP,
                refusalOf(() -> service.save(stationId, new PlaceDraft("Ort", Double.NaN, 1.0, null)))
                        .refusal());
    }

    @Test
    void aPlaceIsLookedUpInTheStationsCountryNearTheStation() {
        stationRepo.updateLocation(
                stationId, null, null, "Braunschweig", "DE", new BigDecimal("52.26"), new BigDecimal("10.52"));
        var pin = new GeocodeResult(52.27, 10.53, GeocodePrecision.EXACT);
        when(geocoding.locate(new GeocodeQuery("Gerätehaus", "DE", 52.26, 10.52)))
                .thenReturn(Optional.of(pin));

        assertEquals(Optional.of(pin), service.locate(stationId, " Gerätehaus "));
    }

    @Test
    void aStationWithoutAPinLooksAPlaceUpAnywhereInItsCountry() {
        stationRepo.updateLocation(stationId, null, null, null, "AT", null, null);
        when(geocoding.locate(new GeocodeQuery("Sportplatz", "AT", null, null))).thenReturn(Optional.empty());

        assertTrue(service.locate(stationId, "Sportplatz").isEmpty());
        verify(geocoding).locate(new GeocodeQuery("Sportplatz", "AT", null, null));
    }

    @Test
    void aBlankPlaceIsNotLookedUp() {
        assertEquals(
                EventRefusal.PLACE_TEXT_MISSING,
                refusalOf(() -> service.locate(stationId, "")).refusal());
        verify(geocoding, never()).locate(any());
    }

    @Test
    void onlyPlaceFieldsWithAPinGetOne() {
        service.save(stationId, new PlaceDraft("Gerätehaus", 52.1, 10.6, GeocodePrecision.EXACT));
        service.save(stationId, new PlaceDraft("Thema", 1.0, 1.0, null));

        List<StationPlace> pins = service.pinsOf(
                stationId,
                List.of(
                        field(FieldType.LOCATION, "gerätehaus"),
                        field(FieldType.LOCATION, "Gerätehaus"),
                        field(FieldType.LOCATION, ""),
                        field(FieldType.LOCATION, "Nirgendwo"),
                        field(FieldType.TEXT, "Thema")));

        Set<String> texts = pins.stream().map(StationPlace::text).collect(Collectors.toSet());
        assertEquals(Set.of("Gerätehaus"), texts);
        assertEquals(1, pins.size());
        assertTrue(service.pinsOf(stationId, List.of()).isEmpty());
    }
}
