/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.question;

/**
 * The kinds of question anything in Ember can ask.
 *
 * <p>Six features ask questions of people, and each of them wrote its own list of types, its own
 * settings beside them and its own checks, where it had any. The lists differ in wording rather than
 * in meaning: a choice is a choice on a profile, on an appointment and on a waiting list. This is
 * that list, written once.
 *
 * <p>A feature declares which of these it offers rather than offering all of them. Nothing here
 * appears on a screen because the enum grew: a profile does not ask for a member, and a waiting list
 * knows nothing of groups.
 */
public enum QuestionKind {
    /** A line of text. */
    TEXT,
    /** Several lines of text. */
    LONG_TEXT,
    /** A whole number, which may be bounded. */
    NUMBER,
    /**
     * A number that may carry a fraction, which may be bounded.
     *
     * <p>Two kinds rather than one because the features differ and always have: an appointment asks
     * for a count of guests and refuses one and a half, while a piece of equipment carries a length
     * in metres. Reading both as one kind would either start refusing lengths that are already
     * stored or start accepting half a guest.
     */
    DECIMAL,
    /** A calendar date. */
    DATE,
    /** A time of day. */
    TIME,
    /** Yes or no. */
    BOOLEAN,
    /** One of a written-down set of answers. */
    CHOICE,
    /** A web address. */
    URL,
    /** One member. */
    MEMBER,
    /** Any number of members. */
    MEMBER_LIST;

    /** Whether an answer to this names members rather than holding a value of its own. */
    public boolean namesMembers() {
        return this == MEMBER || this == MEMBER_LIST;
    }

    /** Whether an answer to this may name more than one member. */
    public boolean namesSeveralMembers() {
        return this == MEMBER_LIST;
    }
}
