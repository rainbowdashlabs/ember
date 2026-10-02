/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.station.entity;

import dev.chojo.ember.api.ApiJsonMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The token that confirms an applicant's address reaches them only through the mail sent to it. An
 * answer that carried it would let anybody confirm an address they never received mail at.
 */
class StationApplicationJsonTest {

    @Test
    void anApplicationIsWrittenWithoutItsVerificationToken() {
        var application = new StationApplication(
                1,
                "Erika",
                "Mustermann",
                "erika@example.org",
                "Musterwache",
                "",
                "secret-token",
                ApplicationStatus.UNVERIFIED,
                null,
                Instant.EPOCH,
                null);

        String json = ApiJsonMapper.create().writeValueAsString(application);

        assertFalse(json.contains("secret-token"), json);
        assertFalse(json.contains("verificationToken"), json);
        assertTrue(json.contains("Musterwache"), json);
    }
}
