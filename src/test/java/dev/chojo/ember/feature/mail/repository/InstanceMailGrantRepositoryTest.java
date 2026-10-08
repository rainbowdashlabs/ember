/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.mail.repository;

import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InstanceMailGrantRepositoryTest extends RepositoryTestBase {
    private final InstanceMailGrantRepository grants = new InstanceMailGrantRepository();
    private Station station;

    @BeforeEach
    void setup() {
        station = stationRepo.create("Grant Station");
    }

    @AfterEach
    void cleanup() {
        stationRepo.delete(station.id());
    }

    @Test
    void aStationHasNoGrantUntilOneIsGiven() {
        assertTrue(grants.find(station.id()).isEmpty());
    }

    @Test
    void aGrantCarriesItsLimitAndKeepsWhenItWasFirstGiven() {
        grants.grant(station.id(), 30);
        var first = grants.find(station.id()).orElseThrow();

        grants.grant(station.id(), null);
        var changed = grants.find(station.id()).orElseThrow();

        assertEquals(30, first.dailyLimit());
        assertNull(changed.dailyLimit(), "the limit is taken off");
        assertEquals(first.grantedAt(), changed.grantedAt(), "changing the limit is not granting anew");
    }

    /**
     * The listing counts what the instance's providers sent for each station today, and only that:
     * mail a station's own provider carried is its own business.
     */
    @Test
    void theListingCountsOnlyWhatTheInstanceSentForTheStationToday() {
        int grantedBefore = grants.countGranted();
        grants.grant(station.id(), 12);
        emailQueueRepo.enqueue("lent@grant.test", "Lent", "Body", station.id());
        emailQueueRepo.enqueue("own@grant.test", "Own", "Body", station.id());
        for (var mail : emailQueueRepo.fetchPending(1_000, true)) {
            if (mail.recipient().equals("lent@grant.test")) emailQueueRepo.markSent(mail.id(), 0);
            if (mail.recipient().equals("own@grant.test")) emailQueueRepo.markSent(mail.id());
        }
        LocalDate today = LocalDate.now();

        var listed = grants.stations(today).stream()
                .filter(entry -> entry.stationUid().equals(station.uid()))
                .findFirst()
                .orElseThrow();
        var single = grants.station(station.id(), today).orElseThrow();

        assertTrue(listed.granted());
        assertEquals(12, listed.dailyLimit());
        assertEquals(1, listed.sentToday());
        assertEquals(listed, single);
        assertEquals(
                0, grants.station(station.id(), today.plusDays(1)).orElseThrow().sentToday(), "another day");
        assertEquals(grantedBefore + 1, grants.countGranted());
        assertTrue(grants.station(Integer.MAX_VALUE, today).isEmpty());
    }

    @Test
    void aWithdrawnGrantIsGone() {
        grants.grant(station.id(), 5);

        grants.withdraw(station.id());

        assertTrue(grants.find(station.id()).isEmpty());
    }
}
