/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.entity;

/**
 * What a movement between two parties is for.
 */
public enum MovementPurpose {
    /**
     * The owner sends gear to a station that will hold it.
     */
    ISSUE,
    /**
     * A station hands gear back to its owner with nothing coming the other way.
     */
    RETURN,
    /**
     * A member's item is swapped for another. The outgoing item walks the return path and the
     * incoming one walks the issue path, and the whole thing starts and ends at a member.
     */
    EXCHANGE,
    /**
     * A station asks the owner for a piece it does not have. Nothing leaves the station, so there is
     * no outgoing side: the station names what it wants, and the owner either sends a piece or refuses
     * with a reason. It is the only purpose a station raises about gear it does not yet hold.
     */
    REQUEST
}
