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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Funnels every backend-config event into the {@code storage_backend_audit} table.
 *
 * <p>Config snapshots are redacted with {@link RedactedStationConfig#toJson} before they reach
 * the repository, so cipher material never crosses this boundary. User-triggered probes are
 * deduped on a {@value #PROBE_DEDUPE_SECONDS}-second window per actor + station + outcome to
 * keep the admin panel's auto-refresh from flooding the table.
 */
@Singleton
public class StorageBackendAuditService {
    private static final Logger log = LoggerFactory.getLogger(StorageBackendAuditService.class);

    /**
     * Probe rows for the same actor/station/outcome inside this window are dropped.
     */
    static final int PROBE_DEDUPE_SECONDS = 60;

    private final StorageBackendAuditRepository repository;

    @Inject
    public StorageBackendAuditService(StorageBackendAuditRepository repository) {
        this.repository = repository;
    }

    /**
     * Records a successful config-row mutation. Pass {@code null} for {@code oldConfig} on
     * CREATED events and {@code null} for {@code newConfig} on DELETED events.
     */
    public void recordConfigChange(
            Actor actor,
            int stationId,
            StorageAuditAction action,
            StationStorageBackendConfig oldConfig,
            StationStorageBackendConfig newConfig) {
        insert(new StorageBackendAuditRepository.NewEntry(
                actor.accountId(),
                actor.memberId(),
                actor.systemActor(),
                Optional.of(stationId),
                action,
                Optional.ofNullable(oldConfig).map(RedactedStationConfig::toJson),
                Optional.ofNullable(newConfig).map(RedactedStationConfig::toJson),
                StorageAuditOutcome.OK,
                Optional.empty()));
    }

    /**
     * Records a refused mutation. {@code error} is the user-facing reason the request was
     * rejected (e.g. probe failed, swap-with-bytes-pinned).
     */
    public void recordRejected(
            Actor actor, int stationId, Optional<StationStorageBackendConfig> attempted, String error) {
        insert(new StorageBackendAuditRepository.NewEntry(
                actor.accountId(),
                actor.memberId(),
                actor.systemActor(),
                Optional.of(stationId),
                StorageAuditAction.REJECTED,
                Optional.empty(),
                attempted.map(RedactedStationConfig::toJson),
                StorageAuditOutcome.FAILED,
                Optional.of(error)));
    }

    /**
     * Records a user-triggered probe with the dedupe rule. Returns {@code true} when a row was
     * inserted, {@code false} when an equivalent row inside the dedupe window suppressed it.
     */
    public void recordProbe(Actor actor, int stationId, StorageAuditOutcome outcome, String errorOrNull) {
        StorageAuditAction action =
                outcome == StorageAuditOutcome.OK ? StorageAuditAction.PROBE_OK : StorageAuditAction.PROBE_FAILED;
        Instant cutoff = Instant.now().minus(Duration.ofSeconds(PROBE_DEDUPE_SECONDS));
        Optional<StorageAuditEntry> recent = repository.findRecentMatching(
                actor.accountId(), actor.systemActor(), Optional.of(stationId), action, outcome, cutoff);
        if (recent.isPresent()) return;
        insert(new StorageBackendAuditRepository.NewEntry(
                actor.accountId(),
                actor.memberId(),
                actor.systemActor(),
                Optional.of(stationId),
                action,
                Optional.empty(),
                Optional.empty(),
                outcome,
                Optional.ofNullable(errorOrNull)));
    }

    /**
     * Records a migration lifecycle event. {@code action} is one of {@link
     * StorageAuditAction#MIGRATION_STARTED} / {@link StorageAuditAction#MIGRATION_COMPLETED} /
     * {@link StorageAuditAction#MIGRATION_FAILED}. {@code newConfig} is the migration target;
     * {@code oldConfig} the override the migration is replacing (when one existed).
     */
    public void recordMigration(
            Actor actor,
            int stationId,
            StorageAuditAction action,
            StationStorageBackendConfig oldConfig,
            StationStorageBackendConfig newConfig,
            String errorOrNull) {
        StorageAuditOutcome outcome =
                action == StorageAuditAction.MIGRATION_FAILED ? StorageAuditOutcome.FAILED : StorageAuditOutcome.OK;
        insert(new StorageBackendAuditRepository.NewEntry(
                actor.accountId(),
                actor.memberId(),
                actor.systemActor(),
                Optional.of(stationId),
                action,
                Optional.ofNullable(oldConfig).map(RedactedStationConfig::toJson),
                Optional.ofNullable(newConfig).map(RedactedStationConfig::toJson),
                outcome,
                Optional.ofNullable(errorOrNull)));
    }

    /**
     * Records a non-migrating instance-default-backend mutation. {@code oldRedactedJson} and
     * {@code newRedactedJson} are pre-redacted JSON summaries built by the caller (the route
     * layer owns the {@link dev.chojo.ember.conf.file.elements.StorageBackendSettings} shape;
     * a generic redactor here would couple the audit service to it unnecessarily).
     */
    public void recordInstanceConfigUpdate(Actor actor, String oldRedactedJson, String newRedactedJson) {
        insert(new StorageBackendAuditRepository.NewEntry(
                actor.accountId(),
                actor.memberId(),
                actor.systemActor(),
                Optional.empty(),
                StorageAuditAction.INSTANCE_DEFAULT_UPDATED,
                Optional.ofNullable(oldRedactedJson),
                Optional.ofNullable(newRedactedJson),
                StorageAuditOutcome.OK,
                Optional.empty()));
    }

    /**
     * Records an instance-wide backend migration lifecycle event. {@code action} is one of
     * {@link StorageAuditAction#INSTANCE_MIGRATION_STARTED},
     * {@link StorageAuditAction#INSTANCE_MIGRATION_COMPLETED},
     * {@link StorageAuditAction#INSTANCE_MIGRATION_FAILED}.
     */
    public void recordInstanceMigration(
            Actor actor,
            StorageAuditAction action,
            String oldRedactedJson,
            String newRedactedJson,
            String errorOrNull) {
        StorageAuditOutcome outcome = action == StorageAuditAction.INSTANCE_MIGRATION_FAILED
                ? StorageAuditOutcome.FAILED
                : StorageAuditOutcome.OK;
        insert(new StorageBackendAuditRepository.NewEntry(
                actor.accountId(),
                actor.memberId(),
                actor.systemActor(),
                Optional.empty(),
                action,
                Optional.ofNullable(oldRedactedJson),
                Optional.ofNullable(newRedactedJson),
                outcome,
                Optional.ofNullable(errorOrNull)));
    }

    /**
     * Writes one audit row and mirrors it into the log, so an operator reading the log stream sees
     * the same history the admin panel shows without having to query for it.
     */
    private void insert(StorageBackendAuditRepository.NewEntry entry) {
        repository.insert(entry);
        log.info(
                "Storage backend audit: {}, action={}, outcome={}, actor={}{}",
                entry.stationId().map(id -> "station " + id).orElse("instance"),
                entry.action(),
                entry.outcome(),
                entry.systemActor().orElseGet(() -> entry.actorAccountId()
                        .map(id -> "account " + id)
                        .orElse("nobody")),
                entry.error().map(error -> ", error=" + error).orElse(""));
    }

    /**
     * Captures the actor for an audit row. Exactly one of {@code accountId} / {@code systemActor}
     * is set; {@code memberId} is populated only for human actors who are also station members.
     */
    public record Actor(Optional<Integer> accountId, Optional<Integer> memberId, Optional<String> systemActor) {

        public static Actor human(int accountId, Integer memberId) {
            return new Actor(Optional.of(accountId), Optional.ofNullable(memberId), Optional.empty());
        }

        public static Actor system(String name) {
            return new Actor(Optional.empty(), Optional.empty(), Optional.of(name));
        }
    }
}
