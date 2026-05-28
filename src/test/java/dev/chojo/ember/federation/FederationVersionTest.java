/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.federation;

import dev.chojo.ember.feature.federation.version.FederationVersionComputer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ensures the federation API contract hash matches the stored version.
 * <p>
 * If this test fails, the federation API contract has changed. Run
 * {@code ./gradlew generateFederationVersion} to update the stored hash,
 * then verify the change is intentional and compatible with deployed instances.
 */
class FederationVersionTest {

    @Test
    void federationVersionHashMatchesStoredVersion() throws IOException {
        String computed = FederationVersionComputer.computeHash();

        try (var is = getClass().getResourceAsStream("/federation_version")) {
            assertNotNull(is, "federation_version resource not found — run ./gradlew generateFederationVersion");
            String stored = new String(is.readAllBytes(), StandardCharsets.UTF_8).trim();
            assertEquals(
                    stored,
                    computed,
                    "Federation API contract has changed. Run ./gradlew generateFederationVersion to update the hash.");
        }
    }

    @Test
    void federationVersionHashExistsInHistory() throws IOException {
        String computed = FederationVersionComputer.computeHash();

        try (var is = getClass().getResourceAsStream("/federation_versions.json")) {
            assertNotNull(is, "federation_versions.json resource not found — run ./gradlew generateFederationVersion");
            String json = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(
                    json.contains("\"" + computed + "\""),
                    "Current hash not found in federation_versions.json. Run ./gradlew generateFederationVersion.");
        }
    }
}
