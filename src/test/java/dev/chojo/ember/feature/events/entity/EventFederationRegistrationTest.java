/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.entity;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventFederationRegistrationTest {

    private static EventFederationRegistration with(RegistrationStatus status) {
        return new EventFederationRegistration(
                1, 2, 3, UUID.randomUUID(), LocalDate.of(2027, 1, 1), status, Instant.EPOCH, null);
    }

    @Test
    void whoeverWaitsStandsButIsNeitherComingNorAskedForAnything() {
        assertTrue(with(RegistrationStatus.QUEUED).isStanding(), "a partner sees its member waiting");
        assertFalse(with(RegistrationStatus.QUEUED).isComingOrAsking());
        assertTrue(with(RegistrationStatus.ACCEPTED).isComingOrAsking());
        assertTrue(with(RegistrationStatus.PENDING).isComingOrAsking());
        assertFalse(with(RegistrationStatus.WITHDRAWN).isComingOrAsking());
        assertFalse(with(RegistrationStatus.DECLINED).isComingOrAsking());
    }
}
