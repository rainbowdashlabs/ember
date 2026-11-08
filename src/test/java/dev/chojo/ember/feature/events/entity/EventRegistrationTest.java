/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.entity;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Which withdrawals are a place given up: the same rule the reminders that registration is closing
 * apply when they leave out whoever already answered.
 */
class EventRegistrationTest {
    private static final Instant AT = Instant.parse("2026-08-01T10:00:00Z");

    private static EventRegistration registration(RegistrationStatus status, @Nullable RegistrationStatus previous) {
        return new EventRegistration(1, 2, 3, LocalDate.of(2026, 8, 10), status, AT, null, AT, previous, false);
    }

    @Test
    void aWithdrawnPlaceHeldOrAskedForIsGivenUp() {
        assertTrue(registration(RegistrationStatus.WITHDRAWN, RegistrationStatus.ACCEPTED)
                .gaveUpPlace());
        assertTrue(registration(RegistrationStatus.WITHDRAWN, RegistrationStatus.PENDING)
                .gaveUpPlace());
    }

    @Test
    void aRefusalTakenBackOrADroppedDateIsNot() {
        assertFalse(registration(RegistrationStatus.WITHDRAWN, RegistrationStatus.DECLINED)
                .gaveUpPlace());
        assertFalse(registration(RegistrationStatus.WITHDRAWN, null).gaveUpPlace());
    }

    @Test
    void noOtherStatusIs() {
        for (RegistrationStatus status : RegistrationStatus.values()) {
            if (status == RegistrationStatus.WITHDRAWN) continue;
            assertFalse(registration(status, RegistrationStatus.ACCEPTED).gaveUpPlace(), status.name());
        }
    }
}
