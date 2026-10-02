/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.backend;

import java.time.Instant;
import java.util.Optional;

/** Outcome of {@link StorageBackend#probe()}, with an operator-readable reason when it failed. */
public record HealthStatus(boolean healthy, Instant checkedAt, Optional<String> error) {

    public static HealthStatus ok() {
        return new HealthStatus(true, Instant.now(), Optional.empty());
    }

    public static HealthStatus unhealthy(String message) {
        return new HealthStatus(false, Instant.now(), Optional.ofNullable(message));
    }
}
