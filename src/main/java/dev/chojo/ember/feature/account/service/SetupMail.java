/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.account.service;

/**
 * Whether the mail that lets somebody claim a new account leaves with the account.
 *
 * <p>Entering people and telling them about it are two acts that do not always belong to the same
 * afternoon. A whole year group is written down before the term starts and told in September; a
 * single member is entered while they stand at the desk and told once their address has been checked.
 * The mail is sent by hand afterwards from the member list, which mints a fresh link at that moment,
 * so nothing runs out in between.
 *
 * <p>Sending at once is what the product has always done and what whoever enters somebody expects, so
 * it stays the answer wherever nobody was asked.
 */
public enum SetupMail {
    /** The mail leaves as the account is made. */
    SEND_NOW,
    /** No mail and no link yet; both are made when somebody sends it from the member list. */
    LATER;

    /**
     * What somebody asked for, reading an unanswered question as sending at once.
     *
     * @param sendNow what was asked for, or {@code null} where nothing was
     */
    public static SetupMail of(Boolean sendNow) {
        return sendNow == null || sendNow ? SEND_NOW : LATER;
    }

    /** Whether a mail is owed right now. */
    public boolean sendsNow() {
        return this == SEND_NOW;
    }
}
