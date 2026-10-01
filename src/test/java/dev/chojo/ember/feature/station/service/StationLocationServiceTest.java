/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.station.service;

import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.api.RefusalResponse;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class StationLocationServiceTest extends RepositoryTestBase {

    private static StationLocationService service;
    private static Station station;

    @BeforeAll
    static void init() {
        service = new StationLocationService(stationRepo);
        station = stationRepo.create("Loc Test");
    }

    @Test
    void findInitiallyEmpty() {
        var fresh = stationRepo.create("Loc Empty");
        var view = service.find(fresh.id());
        assertNull(view.addressLine());
        assertNull(view.latitude());
        assertNull(view.longitude());
    }

    @Test
    void findThrowsForUnknownStation() {
        assertThrows(RefusalResponse.class, () -> service.find(999_999));
    }

    @Test
    void updateAndRead() {
        service.update(
                station.id(),
                new StationLocationService.LocationUpdate(
                        "Hauptstr. 1",
                        "80331",
                        "Munich",
                        "DE",
                        new BigDecimal("48.137154"),
                        new BigDecimal("11.576124")));
        var after = service.find(station.id());
        assertEquals("Hauptstr. 1", after.addressLine());
        assertEquals("Munich", after.city());
        assertEquals("DE", after.country());
        assertEquals(0, new BigDecimal("48.137154").compareTo(after.latitude()));
    }

    @Test
    void clearResets() {
        service.update(
                station.id(),
                new StationLocationService.LocationUpdate(
                        "x", "y", "z", "DE", new BigDecimal("1.0"), new BigDecimal("2.0")));
        service.clear(station.id());
        var view = service.find(station.id());
        assertNull(view.addressLine());
        assertNull(view.latitude());
    }

    @Test
    void updateRejectsNullBody() {
        assertThrows(RefusalResponse.class, () -> service.update(station.id(), null));
    }

    @Test
    void updateRejectsHalfCoordinate() {
        assertThrows(
                RefusalResponse.class,
                () -> service.update(
                        station.id(),
                        new StationLocationService.LocationUpdate(
                                null, null, null, null, new BigDecimal("48.0"), null)));
        assertThrows(
                RefusalResponse.class,
                () -> service.update(
                        station.id(),
                        new StationLocationService.LocationUpdate(
                                null, null, null, null, null, new BigDecimal("11.0"))));
    }

    @Test
    void updateRejectsOutOfRangeCoordinates() {
        assertThrows(
                RefusalResponse.class,
                () -> service.update(
                        station.id(),
                        new StationLocationService.LocationUpdate(
                                null, null, null, null, new BigDecimal("100.0"), new BigDecimal("0.0"))));
        assertThrows(
                RefusalResponse.class,
                () -> service.update(
                        station.id(),
                        new StationLocationService.LocationUpdate(
                                null, null, null, null, new BigDecimal("0.0"), new BigDecimal("181.0"))));
        assertThrows(
                RefusalResponse.class,
                () -> service.update(
                        station.id(),
                        new StationLocationService.LocationUpdate(
                                null, null, null, null, new BigDecimal("-90.1"), new BigDecimal("0"))));
        assertThrows(
                RefusalResponse.class,
                () -> service.update(
                        station.id(),
                        new StationLocationService.LocationUpdate(
                                null, null, null, null, new BigDecimal("0"), new BigDecimal("-180.5"))));
    }

    @Test
    void updateRejectsMalformedCountry() {
        assertThrows(
                RefusalResponse.class,
                () -> service.update(
                        station.id(), new StationLocationService.LocationUpdate(null, null, null, "DEU", null, null)));
    }

    @Test
    void updateRejectsOversizedAddressFields() {
        String tooLong = "x".repeat(201);
        var addressLine = assertThrows(
                RefusalResponse.class,
                () -> service.update(
                        station.id(),
                        new StationLocationService.LocationUpdate(tooLong, null, null, null, null, null)));
        var postalCode = assertThrows(
                RefusalResponse.class,
                () -> service.update(
                        station.id(),
                        new StationLocationService.LocationUpdate(null, tooLong, null, null, null, null)));
        var city = assertThrows(
                RefusalResponse.class,
                () -> service.update(
                        station.id(),
                        new StationLocationService.LocationUpdate(null, null, tooLong, null, null, null)));
        assertEquals(Refusal.STATION_ADDRESS_LINE_TOO_LONG, addressLine.refusal());
        assertEquals(Refusal.STATION_POSTAL_CODE_TOO_LONG, postalCode.refusal());
        assertEquals(Refusal.STATION_CITY_TOO_LONG, city.refusal());
    }

    @Test
    void updateTrimsAndNullsBlankStrings() {
        service.update(station.id(), new StationLocationService.LocationUpdate("   ", "  PLZ  ", "", null, null, null));
        var view = service.find(station.id());
        assertNull(view.addressLine());
        assertEquals("PLZ", view.postalCode());
        assertNull(view.city());
    }

    @Test
    void updateAcceptsBlankCountryAsNull() {
        service.update(station.id(), new StationLocationService.LocationUpdate(null, null, null, "  ", null, null));
    }

    @Test
    void distanceKmStaticHelper() {
        double munichToBerlin = StationLocationService.distanceKm(48.137154, 11.576124, 52.520008, 13.404954);
        assertEquals(504, munichToBerlin, 5);

        assertEquals(0, StationLocationService.distanceKm(0, 0, 0, 0), 0.001);
    }

    @Test
    void findStationsWithinRadiusReturnsEmptyWithoutOrigin() {
        assertTrue(
                service.findStationsWithinRadius(null, new BigDecimal("0"), 100).isEmpty());
        assertTrue(
                service.findStationsWithinRadius(new BigDecimal("0"), null, 100).isEmpty());
        assertTrue(service.findStationsWithinRadius(new BigDecimal("0"), new BigDecimal("0"), 0)
                .isEmpty());
    }

    @Test
    void findStationsWithinRadiusSorts() {
        service.update(
                station.id(),
                new StationLocationService.LocationUpdate(
                        null, null, "Munich", "DE", new BigDecimal("48.137154"), new BigDecimal("11.576124")));
        var results = service.findStationsWithinRadius(new BigDecimal("52.520008"), new BigDecimal("13.404954"), 1000);
        assertFalse(results.isEmpty());
        assertEquals(station.id(), results.getFirst().stationId());
        assertTrue(results.getFirst().distanceKm() > 400);
        assertTrue(results.getFirst().distanceKm() < 600);
    }
}
