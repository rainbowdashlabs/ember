/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.event.events;

import dev.chojo.ember.event.DomainEvent;

/**
 * Somebody at the cluster has filled in an answer about a member.
 *
 * <p>The member hears, because it is their profile and the person who changed it is not at their station.
 * The station's own managers hear through the ordinary change acknowledgement, which the write goes into
 * like any other.
 *
 * @param stationId   the station the member belongs to
 * @param memberId    the member whose profile changed
 * @param clusterName the cluster that changed it, for the reader
 * @param fieldNames  what changed, already joined for reading
 */
public record ClusterFieldValueChanged(int stationId, int memberId, String clusterName, String fieldNames)
        implements DomainEvent {}
