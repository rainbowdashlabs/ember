/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.service;

import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.feature.storage.audit.StorageAuditAction;
import dev.chojo.ember.feature.storage.audit.StorageAuditEntry;
import dev.chojo.ember.feature.storage.audit.StorageAuditOutcome;
import dev.chojo.ember.feature.storage.repository.StorageBackendAuditRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;

/**
 * The history of storage changes, newest first, read a page at a time: all of it for the administrator,
 * one station's for that station.
 */
@Singleton
public class StorageAuditLogService {
    /** How many entries a page holds when the caller does not say. */
    public static final int DEFAULT_LIMIT = 50;

    private static final int MAX_LIMIT = 200;

    private final StorageBackendAuditRepository repository;
    private final StationRepository stationRepository;

    @Inject
    public StorageAuditLogService(StorageBackendAuditRepository repository, StationRepository stationRepository) {
        this.repository = repository;
        this.stationRepository = stationRepository;
    }

    /**
     * The history of the whole instance, or of one station when one is named.
     *
     * @param before     the point in time to list from, or null for the newest
     * @param stationUid the station to narrow down to, or null for all of them
     * @param limit      how many entries at most, held between 1 and 200
     * @return the entries
     */
    public List<AuditEntryResponse> list(@Nullable String before, @Nullable String stationUid, int limit) {
        Optional<Integer> stationId = stationUid == null
                ? Optional.empty()
                : stationRepository.resolveId(StorageQuotaAdminService.parseUid(stationUid));
        return repository.findAll(pointBefore(before), stationId, clamp(limit)).stream()
                .map(StorageAuditLogService::toResponse)
                .toList();
    }

    /**
     * The history of one station.
     *
     * @param stationId the station
     * @param before    the point in time to list from, or null for the newest
     * @param limit     how many entries at most, held between 1 and 200
     * @return the entries
     */
    public List<AuditEntryResponse> listForStation(int stationId, @Nullable String before, int limit) {
        return repository.findByStation(stationId, pointBefore(before), clamp(limit)).stream()
                .map(StorageAuditLogService::toResponse)
                .toList();
    }

    private static Optional<Instant> pointBefore(@Nullable String raw) {
        if (raw == null) return Optional.empty();
        try {
            return Optional.of(Instant.parse(raw));
        } catch (DateTimeParseException e) {
            throw Refusal.STORAGE_AUDIT_BEFORE_NOT_A_TIME.raise();
        }
    }

    private static int clamp(int limit) {
        return Math.clamp(limit, 1, MAX_LIMIT);
    }

    private static AuditEntryResponse toResponse(StorageAuditEntry entry) {
        return new AuditEntryResponse(
                entry.id(),
                entry.ts().toString(),
                entry.actorAccountId().orElse(null),
                entry.actorMemberId().orElse(null),
                entry.systemActor().orElse(null),
                entry.stationId().orElse(null),
                entry.action(),
                entry.oldConfig().orElse(null),
                entry.newConfig().orElse(null),
                entry.outcome(),
                entry.error().orElse(null));
    }

    public record AuditEntryResponse(
            long id,
            String ts,
            @Nullable Integer actorAccountId,
            @Nullable Integer actorMemberId,
            @Nullable String systemActor,
            @Nullable Integer stationId,
            StorageAuditAction action,
            @Nullable String oldConfig,
            @Nullable String newConfig,
            StorageAuditOutcome outcome,
            @Nullable String error) {}
}
