/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.api.refusal;

import io.javalin.http.HttpStatus;

/**
 * The named refusals of {@link Refusal.Area#INSTALLATION}: installation.
 *
 * <p>A new refusal of this area goes at the end with the next free number, which is one above
 * the highest number here and in {@link RetiredRefusals}.
 */
public enum InstallationRefusal implements Refusal {
    /** Asking to set an instance up far more often than a person could. */
    SETUP_TOO_OFTEN(1, HttpStatus.TOO_MANY_REQUESTS, Sentences.TOO_MANY_ATTEMPTS);

    private final Definition definition;

    InstallationRefusal(int number, HttpStatus status, String message) {
        this.definition = new Definition(Area.INSTALLATION, number, status, message);
    }

    @Override
    public Definition definition() {
        return definition;
    }
}
