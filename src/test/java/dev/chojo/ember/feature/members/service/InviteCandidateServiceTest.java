/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.service;

import dev.chojo.ember.api.StationSession;
import dev.chojo.ember.api.TestSessions;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.refusal.MemberRefusal;
import dev.chojo.ember.api.refusal.RefusalResponse;
import dev.chojo.ember.feature.members.entity.FindableBecause;
import dev.chojo.ember.feature.members.entity.InviteCandidate;
import dev.chojo.ember.feature.members.repository.InviteCandidateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * An invitation by name names an account freely, so before anybody is asked the account is checked to be
 * one the manager may find, not at the station or waited on already, not having said no lately, and not
 * invited by name from the station earlier the same day.
 */
class InviteCandidateServiceTest {
    private static final int STATION = 3;
    private static final UUID FOUND = UUID.fromString("00000000-0000-0000-0000-000000000077");

    private InviteCandidateRepository repository;
    private InviteCandidateService service;
    private StationSession manager;

    @BeforeEach
    void setup() {
        repository = mock(InviteCandidateRepository.class);
        var clock = Clock.fixed(Instant.parse("2026-11-26T12:00:00Z"), ZoneOffset.UTC);
        service = new InviteCandidateService(repository, new InviteCandidateRateLimiter(clock));
        manager = StationSession.of(TestSessions.member(STATION, StationPermission.MEMBER_EDIT));
    }

    private void found(boolean here, boolean asked, boolean declined) {
        when(repository.findReachable(TestSessions.ACCOUNT_ID, STATION, FOUND))
                .thenReturn(Optional.of(new InviteCandidate(
                        77, FOUND, "Anna", "Schmidt", FindableBecause.OPTED_IN, null, here, asked, declined)));
    }

    private static void assertRefused(MemberRefusal refusal, Executable call) {
        var refused = assertThrows(RefusalResponse.class, call);
        assertEquals(refusal, refused.refusal());
    }

    @Test
    void somebodyTheManagerCannotFindIsRefusedAsIfNobodyWereThere() {
        when(repository.findReachable(TestSessions.ACCOUNT_ID, STATION, FOUND)).thenReturn(Optional.empty());

        assertRefused(MemberRefusal.INVITE_ACCOUNT_NOT_FINDABLE, () -> service.invitable(manager, FOUND));
    }

    @Test
    void somebodyHereAskedOrHavingDeclinedLatelyIsNotAskedAgain() {
        found(true, false, false);
        assertRefused(MemberRefusal.INVITE_ACCOUNT_ALREADY_HERE, () -> service.invitable(manager, FOUND));

        found(false, true, false);
        assertRefused(MemberRefusal.INVITE_ACCOUNT_ALREADY_ASKED, () -> service.invitable(manager, FOUND));

        found(false, false, true);
        assertRefused(MemberRefusal.INVITE_ACCOUNT_DECLINED_LATELY, () -> service.invitable(manager, FOUND));
    }

    /** Deleting the member that waits and inviting again would otherwise mail the person on every click. */
    @Test
    void theSamePersonIsInvitedByNameOnceADayAtMost() {
        found(false, false, false);

        assertEquals(77, service.invitable(manager, FOUND).accountId());
        assertRefused(MemberRefusal.INVITE_ACCOUNT_INVITED_LATELY, () -> service.invitable(manager, FOUND));
    }
}
