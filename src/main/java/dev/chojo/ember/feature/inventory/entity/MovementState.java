/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.entity;

/**
 * Where a movement stands as a whole, as opposed to which step it is on.
 */
public enum MovementState {
    /**
     * Still walking its flow.
     */
    OPEN,
    /**
     * It reached the end of its flow.
     */
    DONE,
    /**
     * Whoever's turn it was refused, and the outgoing item went back to the custody it had before.
     */
    DECLINED,
    /**
     * The side that started it called it off while it was still on their side.
     */
    CANCELLED;

    /**
     * Whether the movement has stopped moving, whichever way it ended.
     *
     * @return {@code true} when nothing further will happen to it
     */
    public boolean closed() {
        return this != OPEN;
    }
}
