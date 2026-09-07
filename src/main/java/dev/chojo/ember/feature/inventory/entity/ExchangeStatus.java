/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.entity;

/**
 * Represents the lifecycle status of an equipment exchange request.
 * Internal exchanges follow a shorter flow, while external exchanges include shipping steps.
 *
 * <p>The first five are the stations an exchange passes through, in the order it passes them, and the
 * walk that advances one compares them by that order. The two after them are ends rather than
 * stations: an exchange that was called off or refused is standing on neither, which is why they come
 * last and why nothing ever walks towards them.
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
    DONE,
    /**
     * The exchange was called off by the side that raised it, and nothing further will happen to it.
     */
    CANCELLED,
    /**
     * Whoever's turn it was refused the exchange, and nothing further will happen to it.
     */
    DECLINED;

    /**
     * Whether this is one of the stations an exchange walks through, as opposed to an end it stopped at.
     *
     * @return {@code true} when an exchange can be asked to walk to this status
     */
    public boolean walkable() {
        return this != CANCELLED && this != DECLINED;
    }
}
