/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.event.events;

import dev.chojo.ember.event.DomainEvent;

/**
 * Published when a station crosses the storage warning threshold.
 *
 * @param stationId    the station that crossed the threshold
 * @param usedPercent  the current usage percentage
 * @param usedBytes    the total bytes used
 * @param quotaBytes   the total quota in bytes
 */
public record StorageWarningEvent(int stationId, int usedPercent, long usedBytes, long quotaBytes)
        implements DomainEvent {}
