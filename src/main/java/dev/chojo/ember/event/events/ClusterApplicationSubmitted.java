/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.event.events;

import dev.chojo.ember.event.DomainEvent;

/**
 * A standing station has asked to join a cluster, and somebody at the cluster has to decide.
 *
 * @param clusterId   the cluster being asked
 * @param stationId   the station asking
 * @param stationName what that station is called, for the reader
 */
public record ClusterApplicationSubmitted(int clusterId, int stationId, String stationName) implements DomainEvent {}
