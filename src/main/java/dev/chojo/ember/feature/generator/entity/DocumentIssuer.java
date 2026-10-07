/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.entity;

import org.jspecify.annotations.Nullable;

/**
 * Who issues a document for the station: the member whose official name {@code issuer.fullName} prints
 * and to whom the signature field named {@code issuer} belongs, with what they do there.
 *
 * <p>Every template names one for each station that generates it: a station's template itself, a
 * template of an association in what each station chose for it. A manager may pick another member for
 * one document or one run; self service and documents to bring always take the template's. Whether the
 * issuer is the template's own is kept with every document, since the template's issuer may later sign
 * its documents without signing each by hand.
 *
 * @param memberId the member, or null where nobody is named or the one named was deleted
 * @param function what they do at the station, or null where nothing is said
 * @param fixed    whether this is the issuer the template names, rather than one picked for the occasion
 */
public record DocumentIssuer(
        @Nullable Integer memberId, @Nullable String function, boolean fixed) {

    /** No issuer named. */
    public static final DocumentIssuer NONE = new DocumentIssuer(null, null, true);

    /**
     * @param memberId the member the template names, or null
     * @param function what they do, or null
     * @return the issuer a template names
     */
    public static DocumentIssuer ofTemplate(@Nullable Integer memberId, @Nullable String function) {
        return new DocumentIssuer(memberId, function, true);
    }

    /**
     * @param memberId the member a manager picked
     * @param function what they do, or null
     * @return an issuer picked for one document or one run
     */
    public static DocumentIssuer picked(int memberId, @Nullable String function) {
        return new DocumentIssuer(memberId, function, false);
    }
}
