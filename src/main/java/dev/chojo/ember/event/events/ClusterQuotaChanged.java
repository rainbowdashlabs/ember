/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.event.events;

import dev.chojo.ember.event.DomainEvent;

/**
 * The cluster has changed how much room the station has.
 *
 * @param stationId  the station
 * @param clusterName the cluster that decided, for the reader
 * @param quotaBytes  what it may now use, or {@code null} when it was handed back to the instance default
 */
public record ClusterQuotaChanged(int stationId, String clusterName, Long quotaBytes) implements DomainEvent {}
