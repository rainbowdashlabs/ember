/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.event.events;

import dev.chojo.ember.event.DomainEvent;

/**
 * The cluster is sending gear to one of its stations.
 *
 * <p>Its own notification rather than the movement's, because the station is not being asked to
 * answer a step yet: it is being told that something is coming, which is a different sentence and
 * arrives before anybody has to do anything.
 *
 * @param stationId   the station it is going to
 * @param movementId  the movement carrying it
 * @param clusterName the cluster sending it
 * @param itemName    what is on its way
 */
public record ClusterItemIssued(int stationId, int movementId, String clusterName, String itemName)
        implements DomainEvent {}
