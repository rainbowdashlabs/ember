/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.service;

import dev.chojo.ember.feature.storage.audit.StorageAuditAction;
import dev.chojo.ember.feature.storage.audit.StorageAuditEntry;
import dev.chojo.ember.feature.storage.audit.StorageAuditOutcome;
import dev.chojo.ember.feature.storage.entity.RedactedStationConfig;
import dev.chojo.ember.feature.storage.entity.StationStorageBackendConfig;
import dev.chojo.ember.feature.storage.repository.StorageBackendAuditRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Writes every storage backend event to the audit, with configurations redacted so no cipher material
 * reaches it. A probe repeated by the same actor on the same station with the same outcome within
 * {@value #PROBE_DEDUPE_SECONDS} seconds is dropped, so the admin panel's refresh does not flood it.
 */
@Singleton
public class StorageBackendAuditService {
    private static final Logger log = LoggerFactory.getLogger(StorageBackendAuditService.class);

    static final int PROBE_DEDUPE_SECONDS = 60;

    private final StorageBackendAuditRepository repository;

    @Inject
    public StorageBackendAuditService(StorageBackendAuditRepository repository) {
        this.repository = repository;
    }

    /** @param oldConfig null for {@code CREATED}; {@code newConfig} is null for {@code DELETED} */
    public void recordConfigChange(
            Actor actor,
            int stationId,
            StorageAuditAction action,
            StationStorageBackendConfig oldConfig,
            StationStorageBackendConfig newConfig) {
        insert(
                actor,
                Optional.of(stationId),
                action,
                redacted(oldConfig),
                redacted(newConfig),
                StorageAuditOutcome.OK,
                Optional.empty());
    }

    /** @param error the reason the change was refused, as the user saw it */
    public void recordRejected(
            Actor actor, int stationId, Optional<StationStorageBackendConfig> attempted, String error) {
        insert(
                actor,
                Optional.of(stationId),
                StorageAuditAction.REJECTED,
                Optional.empty(),
                attempted.map(RedactedStationConfig::toJson),
                StorageAuditOutcome.FAILED,
                Optional.of(error));
    }

    public void recordProbe(Actor actor, int stationId, StorageAuditOutcome outcome, @Nullable String errorOrNull) {
        StorageAuditAction action =
                outcome == StorageAuditOutcome.OK ? StorageAuditAction.PROBE_OK : StorageAuditAction.PROBE_FAILED;
        Instant cutoff = Instant.now().minus(Duration.ofSeconds(PROBE_DEDUPE_SECONDS));
        Optional<StorageAuditEntry> recent = repository.findRecentMatching(
                actor.accountId(), actor.systemActor(), Optional.of(stationId), action, outcome, cutoff);
        if (recent.isPresent()) return;
        insert(
                actor,
                Optional.of(stationId),
                action,
                Optional.empty(),
                Optional.empty(),
                outcome,
                Optional.ofNullable(errorOrNull));
    }

    /** @param oldConfig the station's own backend the move replaces, if it had one */
    public void recordMigration(
            Actor actor,
            int stationId,
            StorageAuditAction action,
            @Nullable StationStorageBackendConfig oldConfig,
            @Nullable StationStorageBackendConfig newConfig,
            @Nullable String errorOrNull) {
        insert(
                actor,
                Optional.of(stationId),
                action,
                redacted(oldConfig),
                redacted(newConfig),
                action == StorageAuditAction.MIGRATION_FAILED ? StorageAuditOutcome.FAILED : StorageAuditOutcome.OK,
                Optional.ofNullable(errorOrNull));
    }

    /** Takes JSON the caller redacted, since the route owns the shape of the instance settings. */
    public void recordInstanceConfigUpdate(
            Actor actor, @Nullable String oldRedactedJson, @Nullable String newRedactedJson) {
        insert(
                actor,
                Optional.empty(),
                StorageAuditAction.INSTANCE_DEFAULT_UPDATED,
                Optional.ofNullable(oldRedactedJson),
                Optional.ofNullable(newRedactedJson),
                StorageAuditOutcome.OK,
                Optional.empty());
    }

    public void recordInstanceMigration(
            Actor actor,
            StorageAuditAction action,
            @Nullable String oldRedactedJson,
            @Nullable String newRedactedJson,
            @Nullable String errorOrNull) {
        insert(
                actor,
                Optional.empty(),
                action,
                Optional.ofNullable(oldRedactedJson),
                Optional.ofNullable(newRedactedJson),
                action == StorageAuditAction.INSTANCE_MIGRATION_FAILED
                        ? StorageAuditOutcome.FAILED
                        : StorageAuditOutcome.OK,
                Optional.ofNullable(errorOrNull));
    }

    private static Optional<String> redacted(@Nullable StationStorageBackendConfig config) {
        return Optional.ofNullable(config).map(RedactedStationConfig::toJson);
    }

    /** Writes one audit row and mirrors it into the log, so the log carries the same history as the panel. */
    private void insert(
            Actor actor,
            Optional<Integer> stationId,
            StorageAuditAction action,
            Optional<String> oldJson,
            Optional<String> newJson,
            StorageAuditOutcome outcome,
            Optional<String> error) {
        repository.insert(new StorageBackendAuditRepository.NewEntry(
                actor.accountId(),
                actor.memberId(),
                actor.systemActor(),
                stationId,
                action,
                oldJson,
                newJson,
                outcome,
                error));
        log.info(
                "Storage backend audit: {}, action={}, outcome={}, actor={}{}",
                stationId.map(id -> "station " + id).orElse("instance"),
                action,
                outcome,
                actor.systemActor()
                        .orElseGet(() ->
                                actor.accountId().map(id -> "account " + id).orElse("nobody")),
                error.map(message -> ", error=" + message).orElse(""));
    }

    /**
     * Who acted: exactly one of an account and a system actor, with the member only for an account that
     * is one.
     */
    public record Actor(Optional<Integer> accountId, Optional<Integer> memberId, Optional<String> systemActor) {

        public static Actor human(int accountId, @Nullable Integer memberId) {
            return new Actor(Optional.of(accountId), Optional.ofNullable(memberId), Optional.empty());
        }

        public static Actor system(String name) {
            return new Actor(Optional.empty(), Optional.empty(), Optional.of(name));
        }
    }
}
