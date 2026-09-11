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
     * A piece is swapped for another. The outgoing item walks the return path and the incoming one
     * walks the issue path.
     *
     * <p>Usually that is a member's item, and the whole thing starts and ends with them. It can also
     * be a piece the station keeps on its own shelf: gear the body above the station provided and the
     * station holds, which is worn out and goes back for a replacement without anybody having worn
     * it. That is the same swap with the shelf where the member would otherwise stand, which is why
     * it is this purpose rather than a return and a request that happen to be about the same slot.
     */
    EXCHANGE,
    /**
     * A station asks the owner for a piece it does not have. Nothing leaves the station, so there is
     * no outgoing side: the station names what it wants, and the owner either sends a piece or refuses
     * with a reason. It is the only purpose a station raises about gear it does not yet hold.
     */
    REQUEST
}
