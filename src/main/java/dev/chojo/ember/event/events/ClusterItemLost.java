/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.event.events;

import dev.chojo.ember.event.DomainEvent;

/**
 * A station has reported that a piece of the cluster's gear cannot be found.
 *
 * <p>Only raised when a cluster on this instance owns it. Gear belonging to a body that does not run
 * here has nobody to tell, and gear the station owns is the station's own business.
 *
 * @param clusterId the cluster that owns the item
 * @param itemName  the item, as the reader knows it
 * @param stationId the station that had it
 */
public record ClusterItemLost(int clusterId, String itemName, int stationId) implements DomainEvent {}
