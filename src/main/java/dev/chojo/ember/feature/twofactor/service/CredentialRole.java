/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.twofactor.service;

import com.yubico.webauthn.RelyingParty;
import com.yubico.webauthn.data.ResidentKeyRequirement;
import com.yubico.webauthn.data.UserVerificationRequirement;
import dev.chojo.ember.feature.twofactor.entity.WebAuthnCredential;

/**
 * What a WebAuthn credential is for, and with it every way the ceremonies for the two kinds
 * differ.
 *
 * <p>A second factor is always named by an allow list after a password, so it never needs to be
 * discoverable and user verification is only preferred. Its assertion runs on the narrow
 * second-factor view, and the accepted credential must carry the second-factor flag, because for
 * an account whose only credentials are passkeys the allow list comes back empty and the library
 * then accepts any credential the account owns.
 *
 * <p>A passkey starts a sign-in on its own: a resident key is required, and user verification is
 * required and insisted on in the result, because possession plus the unlock is what lets one
 * gesture count as two factors. Without the unlock the ceremony is refused rather than
 * downgraded. Its assertion runs on the full view without naming an account, and the accepted
 * credential must carry the sign-in flag.
 */
public enum CredentialRole {
    SECOND_FACTOR(ResidentKeyRequirement.DISCOURAGED, UserVerificationRequirement.PREFERRED, "Security Key"),
    PASSKEY(ResidentKeyRequirement.REQUIRED, UserVerificationRequirement.REQUIRED, "Passkey");

    private final ResidentKeyRequirement residentKey;
    private final UserVerificationRequirement userVerification;
    private final String defaultLabel;

    CredentialRole(
            ResidentKeyRequirement residentKey, UserVerificationRequirement userVerification, String defaultLabel) {
        this.residentKey = residentKey;
        this.userVerification = userVerification;
        this.defaultLabel = defaultLabel;
    }

    /** The resident key requirement a creation ceremony asks for. */
    public ResidentKeyRequirement residentKey() {
        return residentKey;
    }

    /** The user verification requirement both ceremonies ask the browser for. */
    public UserVerificationRequirement userVerification() {
        return userVerification;
    }

    /** Whether a ceremony result without user verification is refused. */
    public boolean requiresUserVerification() {
        return userVerification == UserVerificationRequirement.REQUIRED;
    }

    /** Whether the assertion names the account through an allow list instead of leaving the choice to the browser. */
    public boolean namesAccount() {
        return this == SECOND_FACTOR;
    }

    /** The label a new credential gets when the user gave none. */
    public String defaultLabel() {
        return defaultLabel;
    }

    /** Whether a credential created in this role may start a sign-in on its own. */
    public boolean signIn() {
        return this == PASSKEY;
    }

    /** Whether a credential created in this role is asked for after a password. */
    public boolean secondFactor() {
        return this == SECOND_FACTOR;
    }

    /** The relying-party view an assertion in this role runs on. */
    public RelyingParty assertionView(RelyingParties parties) {
        return this == SECOND_FACTOR ? parties.secondFactor() : parties.passkey();
    }

    /** Whether a stored credential may answer an assertion in this role. */
    public boolean admits(WebAuthnCredential credential) {
        return this == SECOND_FACTOR ? credential.secondFactor() : credential.signIn();
    }
}
