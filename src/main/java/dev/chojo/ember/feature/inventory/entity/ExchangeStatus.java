/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.entity;

import java.util.Set;

/**
 * Represents the lifecycle status of an equipment exchange request.
 * Internal exchanges follow a shorter flow, while external exchanges include shipping steps.
 */
public enum ExchangeStatus {
    /**
     * The exchange has been announced by the member.
     */
    ANNOUNCED,
    /**
     * The old item has been received from the member.
     */
    RECEIVED,
    /**
     * The replacement item has been shipped (external flow only).
     */
    SHIPPED,
    /**
     * The replacement item has arrived (external flow only).
     */
    ARRIVED,
    /**
     * The exchange is complete and the new item has been handed over.
     */
    EXCHANGED;

    /**
     * The valid status transitions for internal inventory exchanges.
     */
    public static final Set<ExchangeStatus> INTERNAL_FLOW = Set.of(ANNOUNCED, RECEIVED, EXCHANGED);
    /**
     * The valid status transitions for external inventory exchanges, including shipping steps.
     */
    public static final Set<ExchangeStatus> EXTERNAL_FLOW = Set.of(ANNOUNCED, RECEIVED, SHIPPED, ARRIVED, EXCHANGED);
}
