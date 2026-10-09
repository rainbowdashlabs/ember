/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.entity;

import dev.chojo.ember.feature.twofactor.entity.StepUpProof;
import org.jspecify.annotations.Nullable;

/**
 * The signer's confirmation of a started act, as the caller hands it to the provider.
 *
 * <p>The nonce is always the one the provider issued at the start, taken from where the caller kept it
 * on the server, never from the browser. The arrays are handed over as they are, without a copy.
 */
public sealed interface SignerConfirmation
        permits SignerConfirmation.WebAuthnAssertion, SignerConfirmation.StepUpPassed {

    /** @return the nonce issued when the act started */
    byte[] nonce();

    /** @return where the confirmation came from */
    SigningCircumstances circumstances();

    /**
     * A passkey or security key answered the signing challenge. The provider checks it itself, so this is
     * the raw answer as the browser passed it on.
     *
     * <p>Whether the authenticator verified its user is read from the authenticator data, which the
     * signature covers, rather than taken from a flag beside it.
     *
     * @param nonce             the nonce issued when the act started
     * @param credentialId      the id of the credential that answered
     * @param clientDataJson    the client data the browser collected, as sent
     * @param authenticatorData the authenticator data, as sent
     * @param signature         the authenticator's signature over both
     * @param userHandle        the user handle the authenticator returned, or null when it returned none
     * @param circumstances     where the answer came from
     */
    record WebAuthnAssertion(
            byte[] nonce,
            byte[] credentialId,
            byte[] clientDataJson,
            byte[] authenticatorData,
            byte[] signature,
            byte @Nullable [] userHandle,
            SigningCircumstances circumstances)
            implements SignerConfirmation {}

    /**
     * The caller verified a step-up proof that cannot carry the challenge, such as a code from an
     * authenticator app or the password. It says only that the check passed; the code or password itself
     * never reaches the provider.
     *
     * @param nonce         the nonce issued when the act started
     * @param proof         the kind of proof the caller verified
     * @param circumstances where the proof came from
     */
    record StepUpPassed(byte[] nonce, StepUpProof proof, SigningCircumstances circumstances)
            implements SignerConfirmation {}
}
