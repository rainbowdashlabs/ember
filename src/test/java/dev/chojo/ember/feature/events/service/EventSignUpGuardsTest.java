/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.api.StationSession;
import dev.chojo.ember.api.TestSessions;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.refusal.EventRefusal;
import dev.chojo.ember.api.refusal.RefusalResponse;
import dev.chojo.ember.feature.members.service.GuardianPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Whom a reader may sign up: themselves, somebody in their care, or anybody where they run the
 * station's appointments, and nobody else.
 */
class EventSignUpGuardsTest {
    private static final int STATION_ID = 3;
    private static final int WARD = 40;
    private static final int STRANGER = 41;

    private EventSignUpGuards guards;

    @BeforeEach
    void wire() {
        var guardians = mock(GuardianPolicy.class);
        when(guardians.mayActFor(any(), eq(WARD))).thenReturn(true);
        guards = new EventSignUpGuards(guardians);
    }

    private static StationSession session(StationPermission... permissions) {
        return StationSession.of(TestSessions.member(STATION_ID, permissions));
    }

    @Test
    void aReaderNamingNobodyOrThemselvesActsForThemselves() {
        assertEquals(TestSessions.MEMBER_ID, guards.requireTarget(session(), null));
        assertEquals(TestSessions.MEMBER_ID, guards.requireTarget(session(), TestSessions.MEMBER_ID));
    }

    @Test
    void aGuardianActsForTheirWard() {
        assertEquals(WARD, guards.requireTarget(session(), WARD));
    }

    @Test
    void whoeverRunsEventsActsForAnybody() {
        assertEquals(STRANGER, guards.requireTarget(session(StationPermission.EVENT_MANAGER), STRANGER));
    }

    @Test
    void anybodyElseIsRefused() {
        var refused = assertThrows(RefusalResponse.class, () -> guards.requireTarget(session(), STRANGER));

        assertEquals(EventRefusal.MEMBER_NOT_YOURS_TO_REGISTER, refused.refusal());
    }
}
