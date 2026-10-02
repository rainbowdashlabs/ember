/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.service;

import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Whom a reader acts for, which the guardian relation alone decides. */
class GuardianPolicyTest extends RepositoryTestBase {

    private static GuardianPolicy policy;
    private static Station station;
    private static Account account;
    private static StationMember guardian;
    private static StationMember ward;
    private static StationMember stranger;
    private static StationMember otherGuardian;
    private static StationMember otherWard;
    private static StationMember formerWard;

    @BeforeAll
    static void setup() {
        policy = new GuardianPolicy(stationMemberRepo);
        station = stationRepo.create("Guardian Policy Station");
        account = accountRepo.create("guardian-policy@test.com", "Gerda", "Guardian");
        guardian = member("guardian");
        ward = member("ward");
        stranger = member("stranger");
        otherGuardian = member("other-guardian");
        otherWard = member("other-ward");
        formerWard = member("former-ward");
        stationMemberRepo.addManager(guardian.id(), ward.id());
        stationMemberRepo.addManager(guardian.id(), formerWard.id());
        stationMemberRepo.addManager(otherGuardian.id(), otherWard.id());
        stationMemberRepo.setFormer(formerWard.id(), true);
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
        accountRepo.delete(account.id());
    }

    private static StationMember member(String name) {
        var created = accountRepo.create("guardian-policy-" + name + "@test.com", name, "Member");
        return stationMemberRepo.create(station.id(), created.id());
    }

    private static UserSession sessionOf(StationMember member, StationPermission... permissions) {
        return new UserSession(account, 1, station.id(), station.uid(), member, Set.of(permissions), Set.of(), null);
    }

    @Test
    void aReaderActsForThemselves() {
        assertTrue(policy.mayActFor(sessionOf(stranger), stranger.id()));
        assertEquals(List.of(stranger.id()), policy.household(sessionOf(stranger)));
        assertTrue(policy.wards(sessionOf(stranger)).isEmpty());
    }

    /** The relation is enough: nobody has to hold the guardian permission on top of it. */
    @Test
    void aGuardianActsForTheirWardWithoutThePermission() {
        var withoutPermission = sessionOf(guardian);

        assertTrue(policy.mayActFor(withoutPermission, ward.id()));
        assertEquals(List.of(guardian.id(), ward.id()), policy.household(withoutPermission));
        assertEquals(
                List.of(ward.id()),
                policy.wards(withoutPermission).stream().map(StationMember::id).toList());
    }

    @Test
    void nobodyActsForAStranger() {
        assertFalse(policy.mayActFor(sessionOf(guardian, StationPermission.MEMBER_GUARDIAN), stranger.id()));
        assertFalse(policy.mayActFor(sessionOf(stranger), ward.id()));
    }

    /** Being somebody's guardian says nothing about the wards of another guardian. */
    @Test
    void aGuardianDoesNotActForTheWardOfAnother() {
        assertFalse(policy.mayActFor(sessionOf(guardian, StationPermission.MEMBER_GUARDIAN), otherWard.id()));
        assertFalse(policy.household(sessionOf(guardian)).contains(otherWard.id()));
    }

    /** A member who has left the station is nobody's ward any more. */
    @Test
    void aFormerMemberIsNobodysWard() {
        assertFalse(policy.mayActFor(sessionOf(guardian), formerWard.id()));
    }

    @Test
    void aReaderWhoIsNoMemberActsForNobody() {
        var noMember = sessionOf(null, StationPermission.MEMBER_GUARDIAN);

        assertFalse(policy.mayActFor(noMember, ward.id()));
        assertTrue(policy.household(noMember).isEmpty());
        assertTrue(policy.wards(noMember).isEmpty());
    }
}
