/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.service;

import dev.chojo.ember.feature.station.repository.StationRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Single entry point for background writers (schedulers, federation remote handlers, mail
 * delivery) to refuse work on a station that is currently being transferred to another
 * instance. HTTP routes are covered by the centralised before-matched handler in
 * {@code ApiServer}; this helper exists for callers that run outside the request loop.
 *
 * <p>{@link #requireWritable(int)} throws {@link StationReadOnlyForTransferException} when the
 * station carries the read-only flag, which maps to HTTP 503 when surfaced through a Javalin
 * response and otherwise propagates as a runtime exception that the caller can swallow / log.
 */
@Singleton
public class StationReadOnlyGuard {

    private final StationRepository stationRepository;

    @Inject
    public StationReadOnlyGuard(StationRepository stationRepository) {
        this.stationRepository = stationRepository;
    }

    /**
     * Throws {@link StationReadOnlyForTransferException} when {@code stationId} is currently
     * flagged read-only for an in-flight transfer.
     */
    public void requireWritable(int stationId) {
        if (stationRepository.isReadOnlyForTransfer(stationId)) {
            throw new StationReadOnlyForTransferException(stationId);
        }
    }

    /**
     * Returns {@code true} when the station is writable (i.e. not flagged for transfer).
     * Use this when the caller wants to skip work silently rather than raise an exception
     * (e.g. a scheduled job logging "skipped — station transferring" instead of crashing).
     */
    public boolean isWritable(int stationId) {
        return !stationRepository.isReadOnlyForTransfer(stationId);
    }
}
