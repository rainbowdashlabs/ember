/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.entity;

import dev.chojo.ember.feature.federation.service.LendingService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LendingStatusTest {
    private static final UUID LENDER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID BORROWER = UUID.fromString("00000000-0000-0000-0000-000000000002");

    /** A request walks forward one step at a time and never back out of the gear being out. */
    @Test
    void theTableAllowsOnlyTheStepsOfALoan() {
        assertTrue(LendingStatus.REQUESTED.canMoveTo(LendingStatus.APPROVED));
        assertTrue(LendingStatus.APPROVED.canMoveTo(LendingStatus.DECLINED), "withdrawn before the handover");
        assertTrue(LendingStatus.LENT.canMoveTo(LendingStatus.RETURNED));
        assertTrue(LendingStatus.DECLINED.canMoveTo(LendingStatus.CLOSED));
        assertFalse(LendingStatus.LENT.canMoveTo(LendingStatus.DECLINED), "gear that is out comes back first");
        assertFalse(LendingStatus.LENT.canMoveTo(LendingStatus.CLOSED));
        assertFalse(LendingStatus.LENT.canMoveTo(LendingStatus.APPROVED));
        assertFalse(LendingStatus.REQUESTED.canMoveTo(LendingStatus.LENT), "nothing is handed over unagreed");
        assertFalse(LendingStatus.CLOSED.canMoveTo(LendingStatus.REQUESTED));
    }

    /** The lending station's managers agree, hand over and take back; the borrowing station withdraws and closes. */
    @Test
    void eachStationIsOfferedItsOwnSteps() {
        assertEquals(
                List.of(LendingStatus.APPROVED, LendingStatus.DECLINED),
                LendingService.allowedSteps(request(LendingStatus.REQUESTED), LENDER, true));
        assertEquals(
                List.of(LendingStatus.DECLINED),
                LendingService.allowedSteps(request(LendingStatus.APPROVED), BORROWER, true));
        assertEquals(List.of(), LendingService.allowedSteps(request(LendingStatus.LENT), BORROWER, true));
        assertEquals(
                List.of(LendingStatus.RETURNED),
                LendingService.allowedSteps(request(LendingStatus.LENT), LENDER, true));
        assertEquals(
                List.of(LendingStatus.CLOSED),
                LendingService.allowedSteps(request(LendingStatus.RETURNED), BORROWER, true));
        assertEquals(
                List.of(),
                LendingService.allowedSteps(request(LendingStatus.REQUESTED), LENDER, false),
                "a member who only asks for gear decides nothing the station lends");
        assertEquals(
                List.of(LendingStatus.DECLINED),
                LendingService.allowedSteps(request(LendingStatus.REQUESTED), BORROWER, false));
    }

    private static LendingRequest request(LendingStatus status) {
        return new LendingRequest(
                1,
                BORROWER,
                LENDER,
                status,
                LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 5, 3),
                null,
                Instant.EPOCH,
                Instant.EPOCH,
                null,
                null,
                "");
    }
}
