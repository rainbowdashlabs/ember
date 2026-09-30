/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.devicerequest.service;

import dev.chojo.ember.api.TestSessions;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.devicerequest.entity.DeviceRequest;
import dev.chojo.ember.feature.devicerequest.entity.DeviceRequestPurpose;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A code is answered by the account it concerns or its guardian, a step-up by whoever raised it or
 * theirs, and a request that names nobody by nobody.
 */
class DeviceApprovalGuardsTest {
    private static final int READER = 5;
    private static final int CHILD = 6;
    private static final int STRANGER = 7;

    private final AccountRepository accounts = mock(AccountRepository.class);
    private final DeviceApprovalGuards guards = new DeviceApprovalGuards(accounts);

    @Test
    void aSignInIsAnsweredByTheNamedAccountOrItsGuardian() {
        when(accounts.isGuardianOf(READER, CHILD)).thenReturn(true);

        assertTrue(guards.mayConfirm(READER, request(DeviceRequestPurpose.SIGN_IN, null, READER)));
        assertTrue(guards.mayConfirm(READER, request(DeviceRequestPurpose.ENROL_PASSKEY, null, CHILD)));
        assertFalse(guards.mayConfirm(READER, request(DeviceRequestPurpose.SIGN_IN, null, STRANGER)));
        assertFalse(guards.mayConfirm(READER, request(DeviceRequestPurpose.SIGN_IN, READER, null)));
    }

    @Test
    void aStepUpIsAnsweredByWhoeverRaisedItOrTheirGuardian() {
        when(accounts.isGuardianOf(READER, CHILD)).thenReturn(true);

        assertTrue(guards.mayConfirm(READER, request(DeviceRequestPurpose.STEP_UP, READER, null)));
        assertTrue(guards.mayConfirm(READER, request(DeviceRequestPurpose.STEP_UP, CHILD, null)));
        assertFalse(guards.mayConfirm(READER, request(DeviceRequestPurpose.STEP_UP, STRANGER, READER)));
        assertFalse(guards.mayConfirm(READER, request(DeviceRequestPurpose.STEP_UP, null, READER)));
    }

    @Test
    void onlyAStepUpOfSomebodyElseIsNamed() {
        when(accounts.findById(CHILD)).thenReturn(Optional.of(TestSessions.account()));

        assertEquals(
                TestSessions.account().fullName(),
                guards.stepUpSubject(READER, request(DeviceRequestPurpose.STEP_UP, CHILD, null)));
        assertNull(guards.stepUpSubject(READER, request(DeviceRequestPurpose.STEP_UP, READER, null)));
        assertNull(guards.stepUpSubject(READER, request(DeviceRequestPurpose.SIGN_IN, CHILD, null)));
    }

    @Test
    void aGoneAccountHasAnEmptyName() {
        when(accounts.findById(READER)).thenReturn(Optional.empty());

        assertEquals("", guards.nameOf(READER));
    }

    private static DeviceRequest request(DeviceRequestPurpose purpose, Integer requesting, Integer named) {
        Instant now = Instant.now();
        return new DeviceRequest(
                1,
                purpose,
                null,
                null,
                requesting,
                null,
                named,
                null,
                null,
                null,
                now.plusSeconds(300),
                null,
                42,
                List.of(42, 17, 88),
                0,
                "agent",
                null,
                false,
                now);
    }
}
