/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.maps.repository;

import dev.chojo.ember.feature.maps.entity.GeocodePrecision;
import dev.chojo.ember.feature.maps.entity.StationPlace;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** One pin per station and place key: stored, replaced, found singly and in bulk, gone with its station. */
class StationPlaceRepositoryTest extends RepositoryTestBase {

    private final StationPlaceRepository places = new StationPlaceRepository();

    private static int station() {
        return stationRepo.create("Orte " + UUID.randomUUID()).id();
    }

    private static BigDecimal degrees(String value) {
        return new BigDecimal(value);
    }

    @Test
    void aStoredPinIsFoundByItsKey() {
        int station = station();
        StationPlace saved = places.save(
                station,
                "gerätehaus",
                "Gerätehaus",
                degrees("52.123456"),
                degrees("10.654321"),
                GeocodePrecision.EXACT);

        assertEquals("Gerätehaus", saved.text());
        assertEquals(52.123456, saved.latitude());
        assertEquals(10.654321, saved.longitude());
        assertEquals(GeocodePrecision.EXACT, saved.precision());
        assertEquals(saved, places.find(station, "gerätehaus").orElseThrow());
        assertTrue(places.find(station, "sportplatz").isEmpty());
    }

    @Test
    void savingTheSameKeyAgainReplacesThePin() {
        int station = station();
        places.save(station, "gerätehaus", "Gerätehaus", degrees("52.1"), degrees("10.6"), GeocodePrecision.AREA);

        StationPlace moved = places.save(station, "gerätehaus", "GERÄTEHAUS", degrees("52.2"), degrees("10.7"), null);

        assertEquals("GERÄTEHAUS", moved.text());
        assertEquals(52.2, moved.latitude());
        assertNull(moved.precision());
        assertEquals(moved, places.find(station, "gerätehaus").orElseThrow());
    }

    @Test
    void manyPinsAreFoundAtOnceAndOnlyForTheirStation() {
        int station = station();
        int other = station();
        places.save(station, "a", "A", degrees("1"), degrees("1"), GeocodePrecision.EXACT);
        places.save(station, "b", "B", degrees("2"), degrees("2"), GeocodePrecision.STREET);
        places.save(other, "c", "C", degrees("3"), degrees("3"), GeocodePrecision.EXACT);

        Set<String> found = places.findAll(station, List.of("a", "b", "c", "d")).stream()
                .map(StationPlace::text)
                .collect(Collectors.toSet());

        assertEquals(Set.of("A", "B"), found);
        assertTrue(places.findAll(station, List.of()).isEmpty());
        assertTrue(places.find(other, "a").isEmpty());
    }

    @Test
    void thePinsGoWithTheirStation() {
        int station = station();
        places.save(station, "a", "A", degrees("1"), degrees("1"), GeocodePrecision.EXACT);

        stationRepo.delete(station);

        assertTrue(places.find(station, "a").isEmpty());
    }
}
