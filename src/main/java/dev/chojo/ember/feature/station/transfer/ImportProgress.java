/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.station.transfer;

import java.util.List;
import java.util.UUID;

/**
 * Tracks the progress of an asynchronous station import. Volatile fields make the progress
 * readable from polling endpoints without locks.
 */
public class ImportProgress {
    private final int stationId;
    private final UUID stationUid;
    private final String stationName;
    private final List<String> phases;
    private final String sourceUrl;
    private final String token;
    private volatile Status status = Status.IN_PROGRESS;
    private volatile String currentPhase;
    private volatile int completedPhases;
    private volatile int subTotal;
    private volatile int subCompleted;
    private volatile String error;

    public ImportProgress(
            int stationId, UUID stationUid, String stationName, List<String> phases, String sourceUrl, String token) {
        this.stationId = stationId;
        this.stationUid = stationUid;
        this.stationName = stationName;
        this.phases = List.copyOf(phases);
        this.sourceUrl = sourceUrl;
        this.token = token;
    }

    public UUID stationUid() {
        return stationUid;
    }

    public String sourceUrl() {
        return sourceUrl;
    }

    public String token() {
        return token;
    }

    public int stationId() {
        return stationId;
    }

    public String stationName() {
        return stationName;
    }

    public Status status() {
        return status;
    }

    public List<String> phases() {
        return phases;
    }

    public int completedPhases() {
        return completedPhases;
    }

    public String currentPhase() {
        return currentPhase;
    }

    public String error() {
        return error;
    }

    public int subTotal() {
        return subTotal;
    }

    public int subCompleted() {
        return subCompleted;
    }

    public void startPhase(String phase) {
        this.currentPhase = phase;
        this.subTotal = 0;
        this.subCompleted = 0;
    }

    public void setSubTotal(int total) {
        this.subTotal = total;
    }

    public synchronized void incrementSub() {
        this.subCompleted++;
    }

    public synchronized void completePhase() {
        this.completedPhases++;
    }

    public void complete() {
        this.status = Status.COMPLETED;
        this.currentPhase = null;
    }

    public void fail(String error) {
        this.status = Status.FAILED;
        this.error = error;
    }

    public enum Status {
        IN_PROGRESS,
        COMPLETED,
        FAILED
    }
}
