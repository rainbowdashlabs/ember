/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.contract;

import dev.chojo.ember.feature.federation.entity.CapabilityType;
import dev.chojo.ember.feature.federation.entity.FederationContract;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the federation contract hashes to the committed resources.
 * <p>
 * If these tests fail, the federation contract has changed. Run
 * {@code ./toolchain.sh be-federation-version} to update the stored hashes, then verify
 * the change is intentional - each changed surface hash pauses federation of that feature
 * with instances still running the previous contract.
 */
class FederationVersionTest {

    @Test
    void contractMatchesStoredVersion() throws IOException {
        var computed = FederationVersionComputer.computeContract();
        var stored = FederationContract.fromJson(resource("/federation_version.json"));
        assertNotNull(stored, "federation_version.json resource not found or unparsable");
        assertEquals(
                stored,
                computed,
                "Federation contract has changed. Run ./toolchain.sh be-federation-version to update the hashes.");
    }

    @Test
    void allCurrentHashesExistInHistory() throws IOException {
        var computed = FederationVersionComputer.computeContract();
        var history = resource("/federation_versions.json");
        Stream.concat(Stream.of(computed.core()), computed.features().values().stream())
                .forEach(hash -> assertTrue(
                        history.contains("\"" + hash + "\""),
                        "Hash " + hash
                                + " not found in federation_versions.json. Run ./toolchain.sh be-federation-version."));
    }

    @Test
    void everyCapabilityHasASurface() {
        var computed = FederationVersionComputer.computeContract();
        for (var capability : CapabilityType.values()) {
            assertNotNull(computed.featureHash(capability), "No contract surface for capability " + capability);
        }
    }

    /**
     * Only the endpoints a diverged partnership needs to recover may skip the version gate:
     * the handshake and ping that carry the vectors, and the host-change announcement without
     * which a partner that moved and upgraded at once could never be reached again.
     */
    @Test
    void onlyRecoveryEndpointsAreVersionExempt() {
        var exempt = FederationContractCatalog.ENDPOINTS.stream()
                .filter(FederationEndpoint::versionExempt)
                .map(FederationEndpoint::path)
                .toList();
        assertEquals(List.of("/remote/handshake", "/remote/announce", "/remote/federation/ping"), exempt);
    }

    private static String resource(String name) throws IOException {
        try (var is = FederationVersionTest.class.getResourceAsStream(name)) {
            assertNotNull(is, name + " resource not found - run ./toolchain.sh be-federation-version");
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
