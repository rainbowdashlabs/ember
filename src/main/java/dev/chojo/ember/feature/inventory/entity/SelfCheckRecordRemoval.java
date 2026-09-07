/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.entity;

/**
 * What putting the record right would do with the piece that comes off it.
 *
 * <p>Three different things follow from who owns that piece, and one of them ends it. The reviewer
 * is told which before they take the row, because a correction that quietly deletes a row of the
 * inventory is not something to discover afterwards.
 */
public enum SelfCheckRecordRemoval {
    /**
     * There is no piece to take off: the answer is about a place the record has empty.
     */
    NOTHING,
    /**
     * The piece goes back onto the station's own shelf, which is where its owner keeps it. Gear a
     * partner owns rests there too, because the station that borrowed it is the one holding it.
     */
    BACK_TO_STORE,
    /**
     * The piece goes back to the body above the station, which runs here and has a store of its own.
     */
    RETURNED_TO_OWNER,
    /**
     * The piece ends. Its owner has no store on this instance, so there is nowhere to send it and
     * nobody who could ever tidy the row up, and the correction says it was never there.
     */
    DELETED
}
