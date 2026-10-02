/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.service;

import dev.chojo.ember.api.refusal.Refusal;
import dev.chojo.ember.api.refusal.RefusalResponse;
import dev.chojo.ember.feature.cluster.entity.ClusterBackendReach;
import dev.chojo.ember.feature.storage.audit.StorageAuditAction;
import dev.chojo.ember.feature.storage.audit.StorageAuditEntry;
import dev.chojo.ember.feature.storage.audit.StorageAuditOutcome;
import dev.chojo.ember.feature.storage.core.BackendRedaction;
import dev.chojo.ember.feature.storage.core.BackendSummary;
import dev.chojo.ember.feature.storage.repository.StorageBackendAuditRepository;
import dev.chojo.ember.owner.Owner;
import dev.chojo.ember.util.Json;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Writes every storage event to the history of whoever owns the storage: a station, an association or the
 * instance. Storage is written as its {@link BackendSummary}, so no credential reaches the history, only a
 * fingerprint that tells a rotation apart.
 *
 * <p>A move an association decided for one of its stations is written to both histories. A probe repeated
 * by the same actor for the same owner with the same outcome within {@value #PROBE_DEDUPE_SECONDS} seconds
 * is dropped, so a screen being refreshed does not flood it.
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

    /**
     * A storage set, replaced or given up.
     *
     * @param before the storage before, {@code null} for {@code CREATED}
     * @param after  the storage after, {@code null} for {@code DELETED}
     */
    public void recordConfigChange(
            Actor actor,
            Owner owner,
            StorageAuditAction action,
            @Nullable BackendSummary before,
            @Nullable BackendSummary after) {
        insert(actor, Where.of(owner), action, json(before), json(after), StorageAuditOutcome.OK, null);
    }

    /**
     * A change refused before anything was written, with the reason the person asking was told.
     *
     * @param refused the refusal they were given
     */
    public void recordRejected(Actor actor, Owner owner, RefusalResponse refused) {
        Refusal refusal = refused.refusal();
        insert(
                actor,
                Where.of(owner),
                StorageAuditAction.REJECTED,
                null,
                null,
                StorageAuditOutcome.FAILED,
                refusal.code() + ": " + refusal.message());
    }

    /** @param errorOrNull the reason, as the person asking was told it */
    public void recordProbe(Actor actor, Owner owner, StorageAuditOutcome outcome, @Nullable String errorOrNull) {
        StorageAuditAction action =
                outcome == StorageAuditOutcome.OK ? StorageAuditAction.PROBE_OK : StorageAuditAction.PROBE_FAILED;
        Where where = Where.of(owner);
        Instant cutoff = Instant.now().minus(Duration.ofSeconds(PROBE_DEDUPE_SECONDS));
        Optional<StorageAuditEntry> recent = repository.findRecentMatching(
                actor.accountId(), actor.systemActor(), where.stationId(), where.clusterId(), action, outcome, cutoff);
        if (recent.isPresent()) return;
        insert(actor, where, action, null, null, outcome, errorOrNull);
    }

    /**
     * One step of a station's files moving, written to the station's history and, for a move its association
     * decided, to the association's.
     *
     * @param decidedBy the station itself, or the association that moved it
     * @param before    where the files were, {@code null} for the instance's storage
     * @param after     where they go, {@code null} for the instance's storage
     */
    public void recordMove(
            Actor actor,
            Owner decidedBy,
            int stationId,
            StorageAuditAction action,
            @Nullable BackendSummary before,
            @Nullable BackendSummary after,
            @Nullable String errorOrNull) {
        Integer clusterId = decidedBy instanceof Owner.Association association ? association.clusterId() : null;
        insert(
                actor,
                new Where(Optional.of(stationId), Optional.ofNullable(clusterId)),
                action,
                json(before),
                json(after),
                action == StorageAuditAction.MIGRATION_FAILED ? StorageAuditOutcome.FAILED : StorageAuditOutcome.OK,
                errorOrNull);
    }

    /** One step of the instance's own files moving. */
    public void recordInstanceMigration(
            Actor actor,
            StorageAuditAction action,
            @Nullable BackendSummary before,
            @Nullable BackendSummary after,
            @Nullable String errorOrNull) {
        insert(
                actor,
                Where.of(new Owner.Instance()),
                action,
                json(before),
                json(after),
                action == StorageAuditAction.INSTANCE_MIGRATION_FAILED
                        ? StorageAuditOutcome.FAILED
                        : StorageAuditOutcome.OK,
                errorOrNull);
    }

    /**
     * What an association decided about its storage, before and after.
     *
     * @param clusterId the association
     */
    public void recordPolicyChange(Actor actor, int clusterId, Policy before, Policy after) {
        insert(
                actor,
                Where.of(new Owner.Association(clusterId)),
                StorageAuditAction.POLICY_CHANGED,
                Json.MAPPER.writeValueAsString(before),
                Json.MAPPER.writeValueAsString(after),
                StorageAuditOutcome.OK,
                null);
    }

    private static @Nullable String json(@Nullable BackendSummary summary) {
        return BackendRedaction.json(summary);
    }

    /** Writes one row and mirrors it into the log, so the log carries the same history as the screens. */
    private void insert(
            Actor actor,
            Where where,
            StorageAuditAction action,
            @Nullable String before,
            @Nullable String after,
            StorageAuditOutcome outcome,
            @Nullable String error) {
        repository.insert(new StorageBackendAuditRepository.NewEntry(
                actor.accountId(),
                actor.memberId(),
                actor.systemActor(),
                where.stationId(),
                where.clusterId(),
                action,
                Optional.ofNullable(before),
                Optional.ofNullable(after),
                outcome,
                Optional.ofNullable(error)));
        log.info(
                "Storage backend audit: {}, action={}, outcome={}, actor={}{}",
                where.describe(),
                action,
                outcome,
                actor.systemActor()
                        .orElseGet(() ->
                                actor.accountId().map(id -> "account " + id).orElse("nobody")),
                error == null ? "" : ", error=" + error);
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

    /**
     * What an association decided about its storage, as the history keeps it.
     *
     * @param reach  how far its storage reaches
     * @param locked whether only the association may move a station
     */
    public record Policy(ClusterBackendReach reach, boolean locked) {}

    /** Whose history a row belongs to. */
    private record Where(Optional<Integer> stationId, Optional<Integer> clusterId) {
        static Where of(Owner owner) {
            return switch (owner) {
                case Owner.Station station -> new Where(Optional.of(station.stationId()), Optional.empty());
                case Owner.Association association -> new Where(Optional.empty(), Optional.of(association.clusterId()));
                case Owner.Instance ignored -> new Where(Optional.empty(), Optional.empty());
            };
        }

        String describe() {
            Optional<String> station = stationId.map(id -> "station " + id);
            Optional<String> cluster = clusterId.map(id -> "association " + id);
            if (station.isPresent() && cluster.isPresent()) return station.get() + " of " + cluster.get();
            return station.or(() -> cluster).orElse("instance");
        }
    }
}
