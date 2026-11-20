/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.repository;

import dev.chojo.ember.feature.events.entity.PartnerPlaces;
import dev.chojo.ember.feature.events.entity.PlacesKind;
import dev.chojo.ember.feature.events.entity.RegistrationDeadline;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.federation.repository.FederationRepository;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import dev.chojo.ember.util.sql.Transactions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The arrangements a host keeps with its partners, read and written per kind. */
class PartnerPlacesRepositoryTest extends RepositoryTestBase {
    private static final PartnerPlacesRepository places = new PartnerPlacesRepository();

    private static Station station;
    private static int first;
    private static int second;

    @BeforeAll
    static void setup() {
        station = stationRepo.create("Partner Places Station " + UUID.randomUUID());
        var partners = new FederationRepository();
        first = partners.createPartner(station.id(), UUID.randomUUID(), null, null, null)
                .id();
        second = partners.createPartner(station.id(), UUID.randomUUID(), null, null, null)
                .id();
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
    }

    private static int appointment() {
        Instant start = Instant.now().plus(2, ChronoUnit.DAYS);
        return eventRepo
                .create(
                        station.id(),
                        "Plätze",
                        "desc",
                        StationEvent.EventType.ONE_TIME,
                        null,
                        start,
                        start.plus(2, ChronoUnit.HOURS),
                        null,
                        true,
                        RegistrationDeadline.none(),
                        false,
                        null,
                        null,
                        null,
                        null)
                .id();
    }

    /** Nothing arranged reads as the host deciding without a cap, and taking an arrangement back leaves no row. */
    @Test
    void anArrangementIsKeptUpdatedAndTakenBack() {
        int owner = appointment();
        assertEquals(PartnerPlaces.hostDecides(owner, first), places.find(PlacesKind.APPOINTMENT, owner, first));

        places.set(PlacesKind.APPOINTMENT, new PartnerPlaces(owner, first, 2, true));
        places.set(PlacesKind.APPOINTMENT, new PartnerPlaces(owner, first, 4, true));
        places.set(PlacesKind.APPOINTMENT, new PartnerPlaces(owner, second, null, true));
        assertEquals(new PartnerPlaces(owner, first, 4, true), places.find(PlacesKind.APPOINTMENT, owner, first));
        assertEquals(
                List.of(new PartnerPlaces(owner, first, 4, true), new PartnerPlaces(owner, second, null, true)),
                places.list(PlacesKind.APPOINTMENT, owner));

        places.set(PlacesKind.APPOINTMENT, PartnerPlaces.hostDecides(owner, first));
        assertEquals(List.of(new PartnerPlaces(owner, second, null, true)), places.list(PlacesKind.APPOINTMENT, owner));
    }

    /** Only the arrangements with the partners named stay; naming nobody drops them all. */
    @Test
    void onlyTheNamedPartnersKeepTheirArrangements() {
        int owner = appointment();
        int other = appointment();
        places.set(PlacesKind.APPOINTMENT, new PartnerPlaces(owner, first, 1, true));
        places.set(PlacesKind.APPOINTMENT, new PartnerPlaces(owner, second, 2, true));
        places.set(PlacesKind.APPOINTMENT, new PartnerPlaces(other, first, 3, true));

        places.retainOnly(PlacesKind.APPOINTMENT, owner, List.of(second));
        assertEquals(List.of(new PartnerPlaces(owner, second, 2, true)), places.list(PlacesKind.APPOINTMENT, owner));

        places.retainOnly(PlacesKind.APPOINTMENT, owner, List.of());
        assertTrue(places.list(PlacesKind.APPOINTMENT, owner).isEmpty());
        assertEquals(1, places.list(PlacesKind.APPOINTMENT, other).size(), "another appointment keeps its own");
    }

    /**
     * An arrangement and its owner are held for the running transaction: another transaction asking for
     * them without waiting is turned away. Holding an arrangement nobody made is harmless.
     */
    @Test
    void anArrangementAndItsOwnerAreHeldUntilTheTransactionEnds() {
        int owner = appointment();
        places.set(PlacesKind.APPOINTMENT, new PartnerPlaces(owner, first, 1, true));
        Transactions.run(() -> {
            places.lock(PlacesKind.APPOINTMENT, owner, first);
            places.lock(PlacesKind.APPOINTMENT, owner, second);
            places.lockOwner(PlacesKind.APPOINTMENT, owner);
            assertThrows(
                    SQLException.class,
                    () -> takeWithoutWaiting(
                            "SELECT 1 FROM %s.event_partner_places WHERE event_id = %d AND partner_id = %d FOR UPDATE NOWAIT"
                                    .formatted(schemaName, owner, first)));
            assertThrows(
                    SQLException.class,
                    () -> takeWithoutWaiting("SELECT 1 FROM %s.station_event WHERE id = %d FOR UPDATE NOWAIT"
                            .formatted(schemaName, owner)));
        });
        assertDoesNotThrow(() -> takeWithoutWaiting(
                "SELECT 1 FROM %s.station_event WHERE id = %d FOR UPDATE NOWAIT".formatted(schemaName, owner)));
    }

    private static void takeWithoutWaiting(String sql) throws SQLException {
        try (var connection = dataSource.getConnection();
                var statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }
}
