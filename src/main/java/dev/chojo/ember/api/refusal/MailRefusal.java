/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.api.refusal;

import io.javalin.http.HttpStatus;

/**
 * The named refusals of {@link Refusal.Area#MAIL}: mail.
 *
 * <p>A new refusal of this area goes at the end with the next free number, which is one above
 * the highest number here and in {@link RetiredRefusals}.
 */
public enum MailRefusal implements Refusal {
    /**
     * A delivery report from a mail provider whose key opens nothing, or whose signature does not
     * match what it carries. One code deliberately: two would tell whoever is trying the addresses
     * which of the two they got right.
     */
    MAIL_REPORT_NOT_TAKEN(1, HttpStatus.NOT_FOUND, "Nothing here takes that report"),

    /**
     * A failed mail named on the instance's routes that is not one of the instance's own failed
     * mails: a station's mail, one that arrived, or none at all. The three are not told apart, so
     * the routes say nothing about a station's post.
     */
    FAILED_MAIL_NOT_HERE(2, HttpStatus.NOT_FOUND, "There is no failed mail of the instance by that number"),

    /** A mail a provider reported as refused, asked to be sent again. */
    FAILED_MAIL_REFUSED_BY_RECIPIENT(
            3,
            HttpStatus.CONFLICT,
            "The receiving side refused this mail, and sending it again would only be refused again"),

    /**
     * A mail that failed before the queue kept why, asked to be sent again. Its files were dropped when
     * it failed, so sending it again could send it without them.
     */
    FAILED_MAIL_REASON_NOT_RECORDED(
            4, HttpStatus.CONFLICT, "This mail failed before its reason was recorded, so it cannot be sent again"),

    /** Taking note of a station's failed mails for a station that does not exist. */
    STATION_NOT_HERE_FOR_FAILED_MAILS(5, HttpStatus.NOT_FOUND, Sentences.STATION_NOT_HERE),

    /** Taking note of a station's failed mails without saying up to when. */
    FAILED_MAILS_UP_TO_MISSING(
            6, HttpStatus.BAD_REQUEST, "Taking note needs the latest failure seen, so nothing was taken note of");

    private final Definition definition;

    MailRefusal(int number, HttpStatus status, String message) {
        this.definition = new Definition(Area.MAIL, number, status, message);
    }

    @Override
    public Definition definition() {
        return definition;
    }
}
