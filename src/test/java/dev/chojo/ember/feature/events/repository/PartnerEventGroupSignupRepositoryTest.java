/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.repository;

import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.events.entity.RegistrationStatus;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a station remembers of its members' sign-ups to a partner station's event groups: that it signed
 * them up, the status the holder last told it, and the instant that status was decided, which survives a
 * withdrawal so a late notice cannot apply to a fresh sign-up.
 */
class PartnerEventGroupSignupRepositoryTest extends RepositoryTestBase {
    private static final PartnerEventGroupSignupRepository signups = new PartnerEventGroupSignupRepository();
    private static final Instant EARLIER = Instant.parse("2028-03-01T10:00:00Z");
    private static final Instant LATER = Instant.parse("2028-03-01T11:00:00Z");

    private static Station station;
    private static Account account;
    private static StationMember member;

    @BeforeAll
    static void setup() {
        station = stationRepo.create("Partner Event Group Signups");
        account = accountRepo.create("partner-group-signups@test.com", "Partner", "Signups");
        member = stationMemberRepo.create(station.id(), account.id());
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
        accountRepo.delete(account.id());
    }

    @Test
    void aRecordedSignUpIsKnownWithoutAStatusUntilTheHolderSaysOne() {
        UUID holder = UUID.randomUUID();
        assertFalse(signups.signedUp(member.id(), holder, 1));

        signups.record(member.id(), holder, 1);

        assertTrue(signups.signedUp(member.id(), holder, 1));
        assertEquals(Optional.empty(), signups.knownStatus(member.id(), holder, 1));
    }

    @Test
    void theAnsweredStatusIsKeptAndAReRecordDoesNotWipeIt() {
        UUID holder = UUID.randomUUID();
        signups.recordStatus(member.id(), holder, 2, RegistrationStatus.PENDING);
        signups.record(member.id(), holder, 2);

        assertEquals(Optional.of(RegistrationStatus.PENDING), signups.knownStatus(member.id(), holder, 2));

        signups.recordStatus(member.id(), holder, 2, RegistrationStatus.ACCEPTED);
        assertEquals(Optional.of(RegistrationStatus.ACCEPTED), signups.knownStatus(member.id(), holder, 2));
    }

    @Test
    void aNoticeAppliesOnlyWhenItIsNewerThanTheLastOne() {
        UUID holder = UUID.randomUUID();
        signups.recordStatus(member.id(), holder, 3, RegistrationStatus.PENDING);

        assertTrue(signups.learnStatus(member.id(), holder, 3, RegistrationStatus.ACCEPTED, LATER));
        assertFalse(signups.learnStatus(member.id(), holder, 3, RegistrationStatus.DENIED, EARLIER));
        assertFalse(signups.learnStatus(member.id(), holder, 3, RegistrationStatus.DENIED, LATER));

        assertEquals(Optional.of(RegistrationStatus.ACCEPTED), signups.knownStatus(member.id(), holder, 3));
    }

    @Test
    void aNoticeForASignUpNeverRecordedChangesNothing() {
        assertFalse(signups.learnStatus(member.id(), UUID.randomUUID(), 4, RegistrationStatus.ACCEPTED, LATER));
    }

    @Test
    void aWithdrawalKeepsTheRowAndTheLastInstantButIsNoSignUp() {
        UUID holder = UUID.randomUUID();
        signups.record(member.id(), holder, 5);
        signups.learnStatus(member.id(), holder, 5, RegistrationStatus.ACCEPTED, LATER);

        signups.forget(member.id(), holder, 5);

        assertFalse(signups.signedUp(member.id(), holder, 5));
        assertEquals(Optional.of(RegistrationStatus.WITHDRAWN), signups.knownStatus(member.id(), holder, 5));

        signups.record(member.id(), holder, 5);

        assertTrue(signups.signedUp(member.id(), holder, 5));
        assertEquals(Optional.empty(), signups.knownStatus(member.id(), holder, 5));
        assertFalse(signups.learnStatus(member.id(), holder, 5, RegistrationStatus.DENIED, EARLIER));
        assertTrue(signups.learnStatus(member.id(), holder, 5, RegistrationStatus.DENIED, LATER.plusSeconds(60)));
    }
}
