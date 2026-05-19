/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.entity;

import java.util.Set;

public enum ExchangeStatus {
    ANNOUNCED,
    RECEIVED,
    SHIPPED,
    ARRIVED,
    EXCHANGED;

    public static final Set<ExchangeStatus> INTERNAL_FLOW = Set.of(ANNOUNCED, RECEIVED, EXCHANGED);
    public static final Set<ExchangeStatus> EXTERNAL_FLOW = Set.of(ANNOUNCED, RECEIVED, SHIPPED, ARRIVED, EXCHANGED);
}
