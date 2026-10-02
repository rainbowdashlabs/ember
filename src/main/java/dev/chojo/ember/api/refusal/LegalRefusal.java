/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.api.refusal;

import io.javalin.http.HttpStatus;

/**
 * The named refusals of {@link Refusal.Area#LEGAL}: consent and terms.
 *
 * <p>A new refusal of this area goes at the end with the next free number, which is one above
 * the highest number here and in {@link RetiredRefusals}.
 */
public enum LegalRefusal implements Refusal {
    /** Consent written down without saying which version of the terms was agreed to. */
    CONSENT_VERSION_MISSING(
            1, HttpStatus.BAD_REQUEST, "Say which version of the terms was agreed to, so nothing was written down"),

    /** Consent given without saying which version of the consent text was clicked through. */
    LEGAL_CONSENT_VERSION_MISSING(
            2, HttpStatus.BAD_REQUEST, "Say which version of the consent text was agreed to, so nothing was saved"),

    /** Consent given without saying which version of the privacy policy was clicked through. */
    LEGAL_PRIVACY_VERSION_MISSING(
            3, HttpStatus.BAD_REQUEST, "Say which version of the privacy policy was agreed to, so nothing was saved"),

    /** Consent given without saying which version of the terms of service was clicked through. */
    LEGAL_TERMS_VERSION_MISSING(
            4, HttpStatus.BAD_REQUEST, "Say which version of the terms of service was agreed to, so nothing was saved"),

    /** Consent given to legal documents that have changed since the form was loaded. */
    LEGAL_DOCUMENTS_CHANGED(
            5,
            HttpStatus.CONFLICT,
            "The legal documents have changed since the form was loaded, so nothing was saved. "
                    + "Reload the page and agree again");

    private final Definition definition;

    LegalRefusal(int number, HttpStatus status, String message) {
        this.definition = new Definition(Area.LEGAL, number, status, message);
    }

    @Override
    public Definition definition() {
        return definition;
    }
}
