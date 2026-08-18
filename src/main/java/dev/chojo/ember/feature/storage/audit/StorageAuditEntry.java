/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.audit;

import java.time.Instant;
import java.util.Optional;

/**
 * One row from the {@code storage_backend_audit} table. {@link #oldConfig()} and
 * {@link #newConfig()} are already-redacted JSON strings - credentials are stripped before
 * they reach this record.
 *
 * <p>Exactly one of {@link #actorAccountId()} and {@link #systemActor()} is set per the
 * {@code storage_backend_audit_actor_present} check constraint.
 */
public record StorageAuditEntry(
        long id,
        Instant ts,
        Optional<Integer> actorAccountId,
        Optional<Integer> actorMemberId,
        Optional<String> systemActor,
        Optional<Integer> stationId,
        StorageAuditAction action,
        Optional<String> oldConfig,
        Optional<String> newConfig,
        StorageAuditOutcome outcome,
        Optional<String> error) {}
