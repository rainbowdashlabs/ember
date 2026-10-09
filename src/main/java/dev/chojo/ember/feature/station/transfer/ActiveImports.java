/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.station.transfer;

import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The imports this installation runs or ran since it started, by the station they write into.
 *
 * <p>A failed import stays on file so it can be retried and its error read. Other parts of the
 * installation ask whether an import is still underway for a station, since its rows and its files
 * are incomplete until it ends.
 */
@Singleton
public class ActiveImports {
    private final ConcurrentHashMap<Integer, ImportProgress> byStation = new ConcurrentHashMap<>();

    /**
     * Files the progress of an import that is about to run.
     *
     * @param progress the progress of the import
     */
    public void start(ImportProgress progress) {
        byStation.put(progress.stationId(), progress);
    }

    /**
     * @param stationId the station the import writes into
     * @return the progress of the last import into that station, or null when none ran
     */
    public @Nullable ImportProgress get(int stationId) {
        return byStation.get(stationId);
    }

    /**
     * @param stationUid the identifier of the station the import writes into
     * @return the progress of the last import into that station, or null when none ran
     */
    public @Nullable ImportProgress byUid(UUID stationUid) {
        for (var progress : byStation.values()) {
            if (stationUid.equals(progress.stationUid())) return progress;
        }
        return null;
    }

    /**
     * Drops the progress of an import into the station.
     *
     * @param stationId the station the import wrote into
     */
    public void forget(int stationId) {
        byStation.remove(stationId);
    }

    /**
     * @param stationId the station to check
     * @return whether an import into that station has started and not yet ended
     */
    public boolean isUnderway(int stationId) {
        var progress = byStation.get(stationId);
        return progress != null && progress.status() == ImportProgress.Status.IN_PROGRESS;
    }
}
