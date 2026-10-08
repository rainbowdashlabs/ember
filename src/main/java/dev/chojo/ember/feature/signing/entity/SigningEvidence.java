/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.entity;

import dev.chojo.ember.feature.twofactor.entity.StepUpProof;

import java.util.Set;

/**
 * The record of one signing act and of the proof the signer confirmed it with, one kind per proof.
 *
 * <p>Only a passkey or security key binds the proof to the document: its signature covers a challenge
 * computed from the content hash and everything else the act names. An authenticator app code or a
 * password proves that the account holder was present at that moment, and nothing ties it to this
 * document beyond Ember's own record; those kinds say so in their name.
 */
public sealed interface SigningEvidence
        permits SigningEvidence.WebAuthnBound, SigningEvidence.TotpUnbound, SigningEvidence.PasswordUnbound {

    /** @return what the act was: who signed what, when and from where */
    SigningAct act();

    /** @return the kind of proof the signer confirmed with */
    StepUpProof proof();

    /** @return whether the proof itself is bound to the document, rather than only recorded next to it */
    boolean boundToDocument();

    /**
     * A passkey or security key signed the challenge. Everything needed to check that again later without
     * Ember is here: the challenge can be recomputed from the act, the client data must carry it with type
     * {@code webauthn.get}, the authenticator data must start with the SHA-256 of the relying party id and
     * have the user-verified flag set, and the signature must verify over the authenticator data followed
     * by the SHA-256 of the client data, under the credential's public key.
     *
     * <p>The arrays are handed over as they are, without a copy.
     *
     * @param act                     what the act was
     * @param proof                   {@link StepUpProof#PASSKEY} or {@link StepUpProof#SECURITY_KEY}
     * @param relyingPartyId          the relying party id the credential is bound to
     * @param challenge               the challenge the authenticator signed
     * @param credentialId            the id of the credential that signed
     * @param credentialPublicKeyCose the credential's public key, COSE encoded, as it was on file
     * @param clientDataJson          the client data the browser collected
     * @param authenticatorData       the authenticator data
     * @param signature               the authenticator's signature
     * @param userVerified            whether the authenticator verified its user, always true for an
     *                                accepted act and recorded all the same
     * @param signatureCount          the authenticator's signature counter at this act
     */
    record WebAuthnBound(
            SigningAct act,
            StepUpProof proof,
            String relyingPartyId,
            byte[] challenge,
            byte[] credentialId,
            byte[] credentialPublicKeyCose,
            byte[] clientDataJson,
            byte[] authenticatorData,
            byte[] signature,
            boolean userVerified,
            long signatureCount)
            implements SigningEvidence {
        private static final Set<StepUpProof> WEBAUTHN = Set.of(StepUpProof.PASSKEY, StepUpProof.SECURITY_KEY);

        /** Refuses a proof that is not a passkey or a security key. */
        public WebAuthnBound {
            if (!WEBAUTHN.contains(proof)) {
                throw new IllegalArgumentException(proof + " is not a passkey or a security key");
            }
        }

        @Override
        public boolean boundToDocument() {
            return true;
        }
    }

    /**
     * A code from an authenticator app confirmed the act. The code does not carry the challenge, so this
     * proves presence of the account holder at that time, not agreement to this content.
     *
     * @param act what the act was
     */
    record TotpUnbound(SigningAct act) implements SigningEvidence {
        @Override
        public StepUpProof proof() {
            return StepUpProof.TOTP;
        }

        @Override
        public boolean boundToDocument() {
            return false;
        }
    }

    /**
     * The password confirmed the act, which is only accepted for an account without a second factor. It
     * does not carry the challenge either, and is the weakest proof there is.
     *
     * @param act what the act was
     */
    record PasswordUnbound(SigningAct act) implements SigningEvidence {
        @Override
        public StepUpProof proof() {
            return StepUpProof.PASSWORD;
        }

        @Override
        public boolean boundToDocument() {
            return false;
        }
    }
}
