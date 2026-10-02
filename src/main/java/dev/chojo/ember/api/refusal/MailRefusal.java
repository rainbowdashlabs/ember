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
    MAIL_REPORT_NOT_TAKEN(1, HttpStatus.NOT_FOUND, "Nothing here takes that report");

    private final Definition definition;

    MailRefusal(int number, HttpStatus status, String message) {
        this.definition = new Definition(Area.MAIL, number, status, message);
    }

    @Override
    public Definition definition() {
        return definition;
    }
}
