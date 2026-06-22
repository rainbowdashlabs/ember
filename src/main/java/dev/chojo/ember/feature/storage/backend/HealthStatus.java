/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.backend;

import java.time.Instant;
import java.util.Optional;

/**
 * Outcome of {@link StorageBackend#probe()}. {@link #healthy()} drives the bootstrap probe and
 * the {@code /healthz/storage} endpoint; {@link #error()} carries the operator-readable reason
 * when something is wrong.
 */
public record HealthStatus(boolean healthy, Instant checkedAt, Optional<String> error) {

    /** Returns a healthy status pinned to {@link Instant#now()}. */
    public static HealthStatus ok() {
        return new HealthStatus(true, Instant.now(), Optional.empty());
    }

    /** Returns an unhealthy status carrying the supplied operator-facing message. */
    public static HealthStatus unhealthy(String message) {
        return new HealthStatus(false, Instant.now(), Optional.ofNullable(message));
    }
}
