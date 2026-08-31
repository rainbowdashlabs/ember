/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.entity;

/**
 * What a member may say about one piece of their gear or one empty place in it.
 *
 * <p>Two answers a member can give are not here, and deliberately so. Saying a piece cannot be
 * found and asking for a different size both take effect the moment they are given, through the
 * screens that already accept them, so neither is a row of a submission waiting on anybody.
 */
public enum SelfCheckAnswer {
    /**
     * The ordinary answer about a piece the station says they hold.
     */
    HAVE_IT,
    /**
     * They do not hold it, without that being a loss. Borrowed gear is the case: a piece a partner
     * owns cannot be marked missing here, because the loss belongs on the lending request it came
     * in on.
     */
    DO_NOT_HAVE_IT,
    /**
     * A piece recorded as missing has come back.
     */
    TURNED_UP,
    /**
     * The record is wrong and the member knows what they actually hold. Raised, never performed.
     */
    WRONG_RECORD,
    /**
     * An empty place the member never had anything for.
     */
    NEVER_HAD,
    /**
     * An empty place the member is in fact holding something for, which nobody wrote down. They may
     * give the number on the piece and they may leave it blank.
     */
    HAVE_ONE;

    /**
     * Whether this answer is one about a piece on the record rather than about an empty place.
     */
    public boolean aboutAPiece() {
        return this != NEVER_HAD && this != HAVE_ONE;
    }
}
