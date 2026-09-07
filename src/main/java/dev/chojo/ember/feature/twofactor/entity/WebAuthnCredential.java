/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.twofactor.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;
import de.chojo.sadu.queries.converter.StandardValueConverter;

import java.util.List;
import java.util.UUID;

/**
 * Persisted WebAuthn credential, one row per registered authenticator.
 *
 * @param factorId          parent {@code account_2fa_factor.id} (also the primary key)
 * @param credentialId      authenticator-issued credential ID, looked up during assertion
 * @param publicKeyCose     COSE-encoded public key used to verify signatures
 * @param signatureCounter  monotonic counter; persisted on every successful assertion
 * @param aaguid            authenticator model identifier, or {@code null} when omitted
 * @param transports        client-reported transports (e.g. usb, nfc, internal)
 * @param attestationFormat attestation statement format used at registration
 * @param userHandle        64-byte stable handle for the account; shared across the account's credentials
 * @param signIn            whether this credential may start a sign-in on its own
 * @param secondFactor      whether this credential is asked for after a password
 * @param discoverable      what the {@code credProps} extension reported at creation, or
 *                          {@code null} when the authenticator did not say
 * @param userVerified      whether user verification was performed at creation
 */
public record WebAuthnCredential(
        int factorId,
        byte[] credentialId,
        byte[] publicKeyCose,
        long signatureCounter,
        UUID aaguid,
        List<String> transports,
        String attestationFormat,
        byte[] userHandle,
        boolean signIn,
        boolean secondFactor,
        Boolean discoverable,
        boolean userVerified) {

    public static RowMapping<WebAuthnCredential> map() {
        return row -> new WebAuthnCredential(
                row.getInt("factor_id"),
                row.getBytes("credential_id"),
                row.getBytes("public_key_cose"),
                row.getLong("signature_counter"),
                row.get("aaguid", StandardValueConverter.UUID_STRING),
                row.getList("transports"),
                row.getString("attestation_format"),
                row.getBytes("user_handle"),
                row.getBoolean("sign_in"),
                row.getBoolean("second_factor"),
                row.getObject("discoverable", Boolean.class),
                row.getBoolean("user_verified"));
    }
}
