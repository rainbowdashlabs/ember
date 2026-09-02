/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.entity;

/**
 * Whether a report a member raised beside their answers has actually gone out.
 *
 * <p>Almost every one has, the moment it was given. The exception is a report about a piece whose
 * record the same member has just called wrong: raising it there would send it against a size they
 * have disowned and against a piece the correction is about to take off their name, so it is held
 * until the correction lands.
 */
public enum SelfCheckRaisedState {
    /**
     * It has gone out. The loss is on the piece and the exchange is on a movement, and this row is
     * only the note that it happened during this task.
     */
    RAISED,
    /**
     * It is written down and nothing has happened yet, because the answer it hangs on says the
     * record is wrong and no reviewer has put that right.
     */
    WAITING,
    /**
     * It will never go out, because the answer it hung on came to nothing: it was refused, it was
     * settled without the record being put right, or the member answered it again differently.
     */
    DROPPED
}
