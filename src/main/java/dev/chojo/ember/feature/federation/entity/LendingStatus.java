/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.entity;

/**
 * Status lifecycle for inventory lending requests.
 */
public enum LendingStatus {
    REQUESTED,
    APPROVED,
    DECLINED,
    LENT,
    RETURNED,
    CLOSED
}
