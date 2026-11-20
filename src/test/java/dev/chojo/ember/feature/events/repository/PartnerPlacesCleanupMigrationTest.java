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
import dev.chojo.ember.feature.federation.entity.ShareScope;
import dev.chojo.ember.feature.federation.repository.FederationRepository;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The one-off cleanup that drops the arrangements left behind by shares that no longer cover their
 * partner, run once more over arrangements written the way the old code left them.
 */
class PartnerPlacesCleanupMigrationTest extends RepositoryTestBase {
    private static final String PATCH = "database/postgresql/1/patch_91.sql";
    private static final String STATEMENT = "DELETE FROM ember_schema.event_partner_places p";

    private static final PartnerPlacesRepository places = new PartnerPlacesRepository();
    private static final EventFederationRepository shares = new EventFederationRepository();

    private static Station station;
    private static int first;
    private static int second;
    private static int unshared;
    private static int namingFirst;
    private static int withEverybody;

    @BeforeAll
    static void leaveArrangementsBehindAndCleanUp() throws IOException, SQLException {
        station = stationRepo.create("Places Cleanup " + UUID.randomUUID());
        var partners = new FederationRepository();
        first = partners.createPartner(station.id(), UUID.randomUUID(), null, null, null)
                .id();
        second = partners.createPartner(station.id(), UUID.randomUUID(), null, null, null)
                .id();

        unshared = appointment();
        namingFirst = appointment();
        shares.setShareTargets(shares.setShare(namingFirst, ShareScope.SPECIFIC).id(), List.of(first));
        withEverybody = appointment();
        shares.setShare(withEverybody, ShareScope.ALL_PARTNERS);
        for (int owner : List.of(unshared, namingFirst, withEverybody)) {
            for (int partner : List.of(first, second)) {
                places.set(PlacesKind.APPOINTMENT, new PartnerPlaces(owner, partner, 2, true));
            }
        }

        try (var connection = dataSource.getConnection();
                var statement = connection.createStatement()) {
            statement.execute(cleanup().replace("ember_schema", schemaName));
        }
    }

    @AfterAll
    static void cleanupStation() {
        stationRepo.delete(station.id());
    }

    /** An appointment that is not shared keeps no arrangement. */
    @Test
    void anUnsharedAppointmentKeepsNoArrangement() {
        assertTrue(places.list(PlacesKind.APPOINTMENT, unshared).isEmpty());
    }

    /** A share naming partners keeps only their arrangements. */
    @Test
    void aShareNamingPartnersKeepsOnlyTheirs() {
        assertEquals(
                List.of(new PartnerPlaces(namingFirst, first, 2, true)),
                places.list(PlacesKind.APPOINTMENT, namingFirst));
    }

    /** A share with every partner keeps every arrangement. */
    @Test
    void aShareWithEveryPartnerKeepsThemAll() {
        assertEquals(2, places.list(PlacesKind.APPOINTMENT, withEverybody).size());
    }

    private static int appointment() {
        Instant start = Instant.now().plus(2, ChronoUnit.DAYS);
        return eventRepo
                .create(
                        station.id(),
                        "Aufräumen",
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

    private static String cleanup() throws IOException {
        String patch;
        try (var in = Objects.requireNonNull(
                PartnerPlacesCleanupMigrationTest.class.getClassLoader().getResourceAsStream(PATCH))) {
            patch = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        int from = patch.indexOf(STATEMENT);
        return patch.substring(from, patch.indexOf(';', from) + 1);
    }
}
