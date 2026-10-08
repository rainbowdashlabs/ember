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

    @Test
    void aWithdrawnGrantIsGone() {
        grants.grant(station.id(), 5);

        grants.withdraw(station.id());

        assertTrue(grants.find(station.id()).isEmpty());
    }
}
