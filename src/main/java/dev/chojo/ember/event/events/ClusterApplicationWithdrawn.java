/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.event.events;

import dev.chojo.ember.event.DomainEvent;

/**
 * A station took its request back before the cluster decided. Worth telling the cluster, because somebody
 * there may have been about to answer it.
 *
 * @param stationId   the station that changed its mind
 * @param clusterId   the cluster that was asked
 * @param stationName that station's name, for the reader
 */
public record ClusterApplicationWithdrawn(int stationId, int clusterId, String stationName) implements DomainEvent {}
