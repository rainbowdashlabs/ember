/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.mailimport.service;

import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The names a subject line might contain.
 */
class StationMemberNamingTest extends RepositoryTestBase {

    private static StationMemberNaming naming;
    private static Station station;
    private static Account anna;
    private static int annaMember;

    @BeforeAll
    static void setup() {
        naming = new StationMemberNaming(stationMemberRepo, accountRepo);
        station = stationRepo.create("Naming Station");
        anna = accountRepo.create("naming-anna@test.com", "Anna", "Weber");
        annaMember = stationMemberRepo.create(station.id(), anna.id()).id();
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
        accountRepo.delete(anna.id());
    }

    @Test
    void aMemberIsOfferedUnderTheirFullName() {
        var candidates = naming.candidates(station.id());

        assertTrue(candidates.stream()
                .anyMatch(
                        candidate -> candidate.memberId() == annaMember && "Anna Weber".equals(candidate.fullName())));
    }

    /**
     * Somebody who has left is not offered. A rule reading a name out of a subject is filing a document
     * about somebody who is in the station now, and offering people who are gone would make an ambiguity
     * out of a name only shared with one of them.
     */
    @Test
    void somebodyWhoHasLeftIsNotOffered() {
        var gone = accountRepo.create("naming-gone@test.com", "Bernd", "Weber");
        int goneMember = stationMemberRepo.create(station.id(), gone.id()).id();
        stationMemberRepo.setFormer(goneMember, true);

        var candidates = naming.candidates(station.id());

        assertFalse(candidates.stream().anyMatch(candidate -> candidate.memberId() == goneMember));
        assertTrue(candidates.stream().anyMatch(candidate -> candidate.memberId() == annaMember));

        accountRepo.delete(gone.id());
    }

    @Test
    void aStationWithNobodyInItOffersNobody() {
        var empty = stationRepo.create("Nobody Station");

        assertEquals(0, naming.candidates(empty.id()).size());

        stationRepo.delete(empty.id());
    }
}
