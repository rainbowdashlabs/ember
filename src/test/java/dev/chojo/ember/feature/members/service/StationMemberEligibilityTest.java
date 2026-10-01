/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.service;

import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.question.MemberEligibility;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Who passes the group, user type and tag a member field is narrowed to, read from the station's own data. */
class StationMemberEligibilityTest extends RepositoryTestBase {
    private static Station station;
    private static int inside;
    private static int outside;
    private static int groupId;
    private static int tagId;
    private static MemberEligibility eligibility;

    @BeforeAll
    static void setup() {
        station = stationRepo.create("Eligibility Station");
        inside = stationMemberRepo
                .create(
                        station.id(),
                        accountRepo
                                .create("eligible-inside@test.com", "Ina", "I")
                                .id())
                .id();
        outside = stationMemberRepo
                .create(
                        station.id(),
                        accountRepo
                                .create("eligible-outside@test.com", "Otto", "O")
                                .id())
                .id();
        groupId = memberGroupRepo.create(station.id(), "Jugend").id();
        memberGroupRepo.addMember(groupId, inside);
        tagId = userTagRepo.create(station.id(), "Sanitäter").id();
        userTagRepo.addMember(tagId, inside);
        stationMemberRepo.setUserType(inside, StationUserType.GUARDIAN);
        eligibility = memberEligibility();
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
    }

    @Test
    void aGroupTakesOnlyItsMembers() {
        assertTrue(eligibility.inGroup(inside, groupId));
        assertFalse(eligibility.inGroup(outside, groupId));
    }

    @Test
    void aUserTypeTakesOnlyMembersOfIt() {
        assertTrue(eligibility.ofType(inside, StationUserType.GUARDIAN));
        assertFalse(eligibility.ofType(outside, StationUserType.GUARDIAN));
        assertFalse(eligibility.ofType(Integer.MAX_VALUE, StationUserType.GUARDIAN));
    }

    @Test
    void aTagTakesOnlyMembersCarryingIt() {
        assertTrue(eligibility.hasTag(inside, tagId));
        assertFalse(eligibility.hasTag(outside, tagId));
    }
}
