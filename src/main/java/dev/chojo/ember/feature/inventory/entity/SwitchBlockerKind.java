/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.entity;

/**
 * What sort of thing stands in the way of an inventory changing what kind of thing it holds.
 *
 * <p>Each of these is live rather than historical, because history must never make an inventory
 * permanently unswitchable: a procurement fulfilled two years ago and an exchange that finished are
 * both past and neither counts. A requirement is the one exception, and not really an exception at
 * all: it is a standing profile rather than an event, so it has no status to have finished.
 */
public enum SwitchBlockerKind {
    /**
     * A requirement pointing at the inventory. Always counts, since it never ends by itself.
     */
    REQUIREMENT,
    /**
     * An order on the inventory that nothing has arrived for yet.
     */
    PROCUREMENT,
    /**
     * An exchange on the inventory that is still walking its flow.
     */
    EXCHANGE,
    /**
     * A size the inventory offers. The size list belongs to the homogeneous half, so leaving it
     * behind would strand the sizes the items already carry.
     */
    SIZE,
    /**
     * A kind of thing the inventory has been given. The one thing that blocks the move back to an
     * inventory of one thing in many copies: kinds live only in a drawer of different things, so
     * going the other way would leave every piece pointing at a level that no longer exists there.
     */
    ART
}
