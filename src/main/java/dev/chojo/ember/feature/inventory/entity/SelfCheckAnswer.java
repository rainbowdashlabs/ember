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
     *
     * <p>Where the gear comes in sizes, the member may say which size that is, and the screen offers
     * it as the size of the piece rather than behind this answer: somebody looking at a shirt
     * recorded as 128 while holding a 134 changes the number, and that is what says the record is
     * wrong. Choosing an answer first and then saying the thing they came to say is a detour past
     * the only fact they have.
     *
     * <p>It does not narrow what this answer means. A member who holds an entirely different piece
     * still gives it on its own, with no size and a note, and a size beside it is one thing that can
     * be wrong about a record rather than the only one.
     */
    WRONG_RECORD,
    /**
     * An empty place the member never had anything for.
     */
    NEVER_HAD,
    /**
     * An empty place the member is in fact holding something for, which nobody wrote down. They may
     * give the number on the piece and its size where the inventory keeps sizes, and they may leave
     * both blank: a member who cannot read a label off a piece is still telling the station it
     * exists.
     */
    HAVE_ONE;

    /**
     * Whether this answer is one about a piece on the record rather than about an empty place.
     */
    public boolean aboutAPiece() {
        return this != NEVER_HAD && this != HAVE_ONE;
    }
}
