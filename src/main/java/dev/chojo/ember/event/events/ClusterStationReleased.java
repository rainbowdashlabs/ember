/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.event.events;

import dev.chojo.ember.event.DomainEvent;

/**
 * A cluster has let a station go. The station keeps everything of its own and loses what the cluster lent it,
 * so its owner needs to hear about it rather than discover it.
 *
 * @param stationId   the station that was released
 * @param clusterName the cluster it belonged to, for the reader
 */
public record ClusterStationReleased(int stationId, String clusterName) implements DomainEvent {}
