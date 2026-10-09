/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.entity;

import java.util.List;
import java.util.UUID;

/**
 * What a station states about the signing authorities of the installation it runs on, signed with its
 * federation key, so that a partner can pin them to the partnership.
 *
 * <p>The answer travels over the federation channel, but an answer is not signed the way a request is.
 * The signature here is what binds the authorities to the key the partner holds from the pairing: a
 * statement somebody in between changed or made up does not fit that key and is refused. It is made
 * against the asking station's challenge, so an old statement cannot be played back either.
 *
 * @param stationUid   the station making the statement
 * @param recipientUid the station it is made to
 * @param challenge    the asking station's challenge, 32 bytes in lower-case hexadecimal
 * @param issuedAt     when it was made, ISO 8601, exactly as signed
 * @param authorities  every signing authority of the installation, active, retired and given up, newest
 *                     first; empty while no station there has sealed anything
 * @param signature    the station's Base64 signature over the statement's text
 */
public record SigningAuthorityStatement(
        UUID stationUid,
        UUID recipientUid,
        String challenge,
        String issuedAt,
        List<StatedAuthority> authorities,
        String signature) {

    /** Copies the authorities, so the statement cannot change after the fact. */
    public SigningAuthorityStatement {
        authorities = List.copyOf(authorities);
    }
}
