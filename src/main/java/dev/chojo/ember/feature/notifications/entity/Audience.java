/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.notifications.entity;

/**
 * Who a notification goes to, resolved in the database when it is written.
 *
 * <p>Station members and cluster members are different people as far as a notification is
 * concerned: a row belongs to exactly one of them, so an audience is always one kind or the other.
 */
public sealed interface Audience permits StationAudience, ClusterAudience {}
