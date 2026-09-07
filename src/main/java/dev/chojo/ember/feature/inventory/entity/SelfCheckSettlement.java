/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.entity;

/**
 * What taking one answer would do, so a reviewer reads the consequence rather than the wording.
 */
public enum SelfCheckSettlement {
    /**
     * The piece goes on the check as confirmed.
     */
    CONFIRMS_PIECE,
    /**
     * The piece goes on the check as not held. No loss follows: this answer is only offered about
     * gear a partner owns, and a loss on borrowed gear belongs on the lending request it came in on.
     */
    RECORDS_NOT_HELD,
    /**
     * A piece the station had written off comes back, and the check confirms it.
     */
    MARKS_FOUND,
    /**
     * An empty place goes on the check as not held, exactly as a checker's own walk records one.
     */
    CONFIRMS_GAP,
    /**
     * Nothing can be taken until the record is put right: the member holds something other than
     * what is written against their name.
     */
    NEEDS_RECORD_PUT_RIGHT,
    /**
     * Nothing can be taken until a piece is named: the member holds something nobody wrote down.
     */
    NEEDS_A_PIECE_NAMED,
    /**
     * The piece this answer was about has been deleted since, so there is nothing left to settle.
     */
    ANCHOR_GONE
}
