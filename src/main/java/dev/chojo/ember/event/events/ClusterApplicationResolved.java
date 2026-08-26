/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.event.events;

import dev.chojo.ember.event.DomainEvent;

/**
 * The cluster has answered a station's request to join.
 *
 * <p>One event for both answers rather than two, because the recipient and the route are the same either way
 * and only the wording differs. The station owner asked a question and gets an answer.
 *
 * @param stationId   the station that asked
 * @param clusterName the cluster that answered, for the reader
 * @param approved    whether it was let in
 * @param reason      what the cluster said when it refused, or {@code null} when it agreed
 */
public record ClusterApplicationResolved(int stationId, String clusterName, boolean approved, String reason)
        implements DomainEvent {}
