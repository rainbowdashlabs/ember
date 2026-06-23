/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.repository;

import de.chojo.sadu.mapper.rowmapper.RowMapping;
import dev.chojo.ember.feature.storage.audit.StorageAuditAction;
import dev.chojo.ember.feature.storage.audit.StorageAuditEntry;
import dev.chojo.ember.feature.storage.audit.StorageAuditOutcome;
import jakarta.inject.Singleton;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * CRUD on the {@code storage_backend_audit} table. Inserts are append-only; reads are
 * cursor-paginated on {@code (ts DESC, id DESC)} so the admin / station audit views can keep
 * scrolling without {@code OFFSET} performance pitfalls.
 */
@Singleton
public class StorageBackendAuditRepository {

    private static final String SELECT_COLS = "id, ts, actor_account_id, actor_member_id, system_actor, station_id, "
            + "action, old_config::text AS old_config_text, new_config::text AS new_config_text, "
            + "outcome, error";
    private static final RowMapping<StorageAuditEntry> MAP = row -> new StorageAuditEntry(
            row.getLong("id"),
            row.get("ts", INSTANT_TIMESTAMP),
            Optional.ofNullable((Integer) row.getObject("actor_account_id")),
            Optional.ofNullable((Integer) row.getObject("actor_member_id")),
            Optional.ofNullable(row.getString("system_actor")),
            Optional.ofNullable((Integer) row.getObject("station_id")),
            row.getEnum("action", StorageAuditAction.class),
            Optional.ofNullable(row.getString("old_config_text")),
            Optional.ofNullable(row.getString("new_config_text")),
            row.getEnum("outcome", StorageAuditOutcome.class),
            Optional.ofNullable(row.getString("error")));

    /**
     * Persists one row. Returns the generated id.
     */
    public long insert(NewEntry entry) {
        return query("""
                INSERT INTO storage_backend_audit (
                    actor_account_id, actor_member_id, system_actor, station_id,
                    action, old_config, new_config, outcome, error
                ) VALUES (
                    :actor_account_id, :actor_member_id, :system_actor, :station_id,
                    :action, :old_config::JSONB, :new_config::JSONB, :outcome, :error
                )
                RETURNING id;
                """)
                .single(call().bind("actor_account_id", entry.actorAccountId().orElse(null))
                        .bind("actor_member_id", entry.actorMemberId().orElse(null))
                        .bind("system_actor", entry.systemActor().orElse(null))
                        .bind("station_id", entry.stationId().orElse(null))
                        .bind("action", entry.action().name())
                        .bind("old_config", entry.oldConfig().orElse(null))
                        .bind("new_config", entry.newConfig().orElse(null))
                        .bind("outcome", entry.outcome().name())
                        .bind("error", entry.error().orElse(null)))
                .map(row -> row.getLong("id"))
                .first()
                .orElseThrow(() -> new IllegalStateException("Audit insert returned no id"));
    }

    /**
     * Returns the most recent audit row for the same actor + station + action + outcome
     * inside the dedupe window. Used by {@code StorageBackendAuditService} to suppress
     * runaway probe-storm rows on admin-panel auto-refresh.
     */
    public Optional<StorageAuditEntry> findRecentMatching(
            Optional<Integer> actorAccountId,
            Optional<String> systemActor,
            Optional<Integer> stationId,
            StorageAuditAction action,
            StorageAuditOutcome outcome,
            Instant cutoff) {
        return query("""
                SELECT %s FROM storage_backend_audit
                WHERE ts >= :cutoff
                  AND action = :action
                  AND outcome = :outcome
                  AND ( (:actor_account_id IS NULL AND actor_account_id IS NULL)
                        OR actor_account_id = :actor_account_id )
                  AND ( (:system_actor IS NULL AND system_actor IS NULL)
                        OR system_actor = :system_actor )
                  AND ( (:station_id IS NULL AND station_id IS NULL)
                        OR station_id = :station_id )
                ORDER BY ts DESC, id DESC
                LIMIT 1;
                """, SELECT_COLS)
                .single(call().bind("cutoff", cutoff, INSTANT_TIMESTAMP)
                        .bind("action", action.name())
                        .bind("outcome", outcome.name())
                        .bind("actor_account_id", actorAccountId.orElse(null))
                        .bind("system_actor", systemActor.orElse(null))
                        .bind("station_id", stationId.orElse(null)))
                .map(MAP)
                .first();
    }

    /**
     * Paginated cursor read of every audit row, newest first. {@code before} bounds the result
     * on {@code ts < :before}; the admin route passes the last seen row's timestamp.
     */
    public List<StorageAuditEntry> findAll(Optional<Instant> before, Optional<Integer> stationId, int limit) {
        return query("""
                SELECT %s FROM storage_backend_audit
                WHERE ( :before IS NULL OR ts < :before )
                  AND ( :station_id IS NULL OR station_id = :station_id )
                ORDER BY ts DESC, id DESC
                LIMIT :limit;
                """, SELECT_COLS)
                .single(call().bind("before", before.orElse(null), INSTANT_TIMESTAMP)
                        .bind("station_id", stationId.orElse(null))
                        .bind("limit", limit))
                .map(MAP)
                .all();
    }

    /**
     * Paginated cursor read scoped to one station. The {@code station_id} filter is enforced
     * server-side regardless of any query string, matching the strict per-station ownership
     * model the rest of the station-scoped routes use.
     */
    public List<StorageAuditEntry> findByStation(int stationId, Optional<Instant> before, int limit) {
        return findAll(before, Optional.of(stationId), limit);
    }

    /**
     * Inputs to {@link #insert(NewEntry)}. {@code oldConfig} / {@code newConfig} are
     * already-redacted JSON strings supplied by the caller; the repository never touches
     * raw cipher material.
     */
    public record NewEntry(
            Optional<Integer> actorAccountId,
            Optional<Integer> actorMemberId,
            Optional<String> systemActor,
            Optional<Integer> stationId,
            StorageAuditAction action,
            Optional<String> oldConfig,
            Optional<String> newConfig,
            StorageAuditOutcome outcome,
            Optional<String> error) {}
}
