/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.repository;

import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.members.entity.ProfileFieldConfig;
import dev.chojo.ember.feature.members.entity.ProfileFieldScope;
import dev.chojo.ember.feature.members.entity.ProfileFieldType;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A profile change carries the old and the new value of a field. Whoever may only see the members
 * they manage must not be handed the rest of the station along the way.
 */
class ProfileFieldChangeScopeTest extends RepositoryTestBase {

    private static Station station;
    private static Account ownAccount;
    private static Account otherAccount;
    private static StationMember own;
    private static StationMember other;
    private static int changeOfOwn;
    private static int changeOfOther;

    @BeforeAll
    static void setup() {
        station = stationRepo.create("Change Scope Station");
        ownAccount = accountRepo.create("scope-own@test.com", "Own", "Child");
        otherAccount = accountRepo.create("scope-other@test.com", "Other", "Member");
        own = stationMemberRepo.create(station.id(), ownAccount.id());
        other = stationMemberRepo.create(station.id(), otherAccount.id());

        var field = profileFieldRepo.create(
                station.id(),
                "Telefon",
                ProfileFieldType.TEXT,
                ProfileFieldConfig.parse("{}"),
                0,
                ProfileFieldScope.MEMBER);

        changeOfOwn = profileFieldChangeRepo
                .create(field.id(), own.id(), "\"alt\"", "\"neu\"", own.id(), true)
                .id();
        changeOfOther = profileFieldChangeRepo
                .create(field.id(), other.id(), "\"alt\"", "\"neu\"", other.id(), true)
                .id();
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
        accountRepo.delete(ownAccount.id());
        accountRepo.delete(otherAccount.id());
    }

    @Test
    void onlyTheChangesOfTheGivenMembersAreListed() {
        var changes = profileFieldChangeRepo.findByMembers(List.of(own.id()), 20, 0);

        assertEquals(1, changes.size());
        assertEquals(own.id(), changes.getFirst().memberId());
        assertEquals(1, profileFieldChangeRepo.countByMembers(List.of(own.id())));
    }

    @Test
    void withoutMembersNothingIsListed() {
        assertTrue(profileFieldChangeRepo.findByMembers(List.of(), 20, 0).isEmpty());
        assertEquals(0, profileFieldChangeRepo.countByMembers(List.of()));
    }

    @Test
    void aChangeNamesTheMemberItBelongsTo() {
        assertEquals(
                own.id(), profileFieldChangeRepo.findMemberOfChange(changeOfOwn).orElseThrow());
        assertEquals(
                other.id(),
                profileFieldChangeRepo.findMemberOfChange(changeOfOther).orElseThrow());
        assertTrue(profileFieldChangeRepo.findMemberOfChange(-1).isEmpty());
    }

    @Test
    void theStationWideListStillSeesEverything() {
        assertTrue(profileFieldChangeRepo.countByStation(station.id()) >= 2);
    }
}
