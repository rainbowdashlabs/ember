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
import static org.junit.jupiter.api.Assertions.assertTrue;

class StationMailSenderRepositoryTest extends RepositoryTestBase {
    private final StationMailSenderRepository senders = new StationMailSenderRepository();
    private Station station;

    @BeforeEach
    void setup() {
        station = stationRepo.create("Sender Station");
    }

    @AfterEach
    void cleanup() {
        stationRepo.delete(station.id());
    }

    @Test
    void aStationSendsUnderItsNameWithRepliesToTheSenderUntilItNamesAnAddress() {
        var before = senders.find(station.id()).orElseThrow();

        senders.updateReplyTo(station.id(), "kontakt@wache.test");
        var after = senders.find(station.id()).orElseThrow();

        assertEquals("Sender Station", before.name());
        assertEquals("", before.replyTo());
        assertEquals("kontakt@wache.test", after.replyTo());
    }

    @Test
    void thereIsNoSenderForAStationThatDoesNotExist() {
        assertTrue(senders.find(Integer.MAX_VALUE).isEmpty());
    }
}
