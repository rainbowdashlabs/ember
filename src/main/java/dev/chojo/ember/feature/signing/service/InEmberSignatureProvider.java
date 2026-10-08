/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.service;

import dev.chojo.ember.api.refusal.DocumentRefusal;
import dev.chojo.ember.feature.legal.service.ConsentService;
import dev.chojo.ember.feature.signing.entity.SealedDocument;
import dev.chojo.ember.feature.signing.entity.SealingKey;
import dev.chojo.ember.feature.signing.entity.SignatureLevel;
import dev.chojo.ember.feature.signing.entity.SignedDocument;
import dev.chojo.ember.feature.signing.entity.SignerConfirmation;
import dev.chojo.ember.feature.signing.entity.SignerConfirmation.StepUpPassed;
import dev.chojo.ember.feature.signing.entity.SignerConfirmation.WebAuthnAssertion;
import dev.chojo.ember.feature.signing.entity.SigningAct;
import dev.chojo.ember.feature.signing.entity.SigningCircumstances;
import dev.chojo.ember.feature.signing.entity.SigningEvidence;
import dev.chojo.ember.feature.signing.entity.SigningRequest;
import dev.chojo.ember.feature.signing.entity.SigningStart;
import dev.chojo.ember.feature.signing.service.SigningAssertions.VerifiedSigningAssertion;
import dev.chojo.ember.feature.twofactor.entity.StepUpProof;
import dev.chojo.ember.feature.twofactor.service.TwoFactorService;
import dev.chojo.ember.util.RandomTokens;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.net.InetAddress;
import java.time.Clock;
import java.util.EnumSet;
import java.util.Set;

/**
 * Ember's own signature provider: the signer confirms in Ember with a step-up, and the station seals the
 * result. It reaches a simple electronic signature and never claims more.
 *
 * <p><b>Proofs.</b> A passkey or a security key, with user verification required, is the one proof bound
 * to the document: it signs the {@link SigningChallenge}. An authenticator app code and, for an account
 * without a second factor, the password are taken as well and recorded as unbound. A backup code is a
 * recovery path and a confirmation on another device happens on a screen that never showed the document,
 * so neither is ever taken. Which proofs an account has comes from {@link TwoFactorService#availableProofs},
 * the same rule step-up follows. The evidence of a passkey or security key carries the timestamp over the
 * credential's public key; a credential that has none yet is stamped during the act
 * ({@link CredentialKeyStamps#forSigning}), and when no timestamp service answers the act still goes through
 * and its evidence records the key as not stamped.
 *
 * <p><b>Sealing.</b> {@link #complete} seals the frozen content as it is, with the station's key, and
 * returns the evidence beside it; the signer's mark and typed values are not drawn into the document. No
 * signing state is ever signed onto the one before: each is a fresh document, built from the frozen
 * content with a signature record page and the evidence attached ({@link SigningStateAssembler}), and
 * sealed on its own, so the latest version carries every act so far and the earlier ones stay valid as
 * they are. {@link #complete} does not seal such a state yet.
 */
@Singleton
public class InEmberSignatureProvider implements SignatureProvider {
    private static final Set<StepUpProof> SIGNING_PROOFS =
            EnumSet.of(StepUpProof.PASSKEY, StepUpProof.SECURITY_KEY, StepUpProof.TOTP, StepUpProof.PASSWORD);
    private static final Set<StepUpProof> UNBOUND_PROOFS = EnumSet.of(StepUpProof.TOTP, StepUpProof.PASSWORD);

    private final TwoFactorService twoFactor;
    private final SigningAssertions assertions;
    private final SignerNames names;
    private final StationSigningKeys keys;
    private final PdfSealer sealer;
    private final CredentialKeyStamps keyStamps;
    private final Clock clock;

    @Inject
    public InEmberSignatureProvider(
            TwoFactorService twoFactor,
            SigningAssertions assertions,
            SignerNames names,
            StationSigningKeys keys,
            PdfSealer sealer,
            CredentialKeyStamps keyStamps) {
        this(twoFactor, assertions, names, keys, sealer, keyStamps, Clock.systemUTC());
    }

    InEmberSignatureProvider(
            TwoFactorService twoFactor,
            SigningAssertions assertions,
            SignerNames names,
            StationSigningKeys keys,
            PdfSealer sealer,
            CredentialKeyStamps keyStamps,
            Clock clock) {
        this.twoFactor = twoFactor;
        this.assertions = assertions;
        this.names = names;
        this.keys = keys;
        this.sealer = sealer;
        this.keyStamps = keyStamps;
        this.clock = clock;
    }

    @Override
    public SignatureLevel level() {
        return SignatureLevel.SIMPLE;
    }

    @Override
    public SigningStart start(SigningRequest request) {
        Set<StepUpProof> accepted = acceptedProofs(request.signer().accountId());
        if (accepted.isEmpty()) throw DocumentRefusal.SIGNING_NO_PROOF.raise();
        byte[] nonce = RandomTokens.bytes(SigningChallenge.NONCE_BYTES);
        return new SigningStart.InEmber(nonce, SigningChallenge.of(nonce, request), accepted);
    }

    @Override
    public SignedDocument complete(SigningRequest request, SignerConfirmation confirmation) {
        SigningEvidence evidence =
                switch (confirmation) {
                    case WebAuthnAssertion assertion -> bound(request, assertion);
                    case StepUpPassed passed -> unbound(request, passed);
                };
        // TODO seal the state SigningStateAssembler builds, mark drawn in, instead of the bare content
        SealingKey key = keys.forStation(request.stationId());
        SealedDocument sealed = sealer.seal(request.contentPdf(), key.privateKey(), key.chain());
        return new SignedDocument(sealed, level(), evidence);
    }

    private Set<StepUpProof> acceptedProofs(int accountId) {
        Set<StepUpProof> accepted = EnumSet.noneOf(StepUpProof.class);
        accepted.addAll(twoFactor.availableProofs(accountId));
        accepted.retainAll(SIGNING_PROOFS);
        return accepted;
    }

    private SigningEvidence bound(SigningRequest request, WebAuthnAssertion assertion) {
        byte[] challenge = SigningChallenge.of(assertion.nonce(), request);
        VerifiedSigningAssertion verified = assertions.verify(request.signer().accountId(), challenge, assertion);
        return new SigningEvidence.WebAuthnBound(
                act(request, assertion),
                verified.proof(),
                verified.relyingPartyId(),
                challenge,
                assertion.credentialId(),
                verified.credential().publicKeyCose(),
                assertion.clientDataJson(),
                assertion.authenticatorData(),
                assertion.signature(),
                verified.userVerified(),
                verified.signatureCount(),
                keyStamps.forSigning(verified.credential()));
    }

    private SigningEvidence unbound(SigningRequest request, StepUpPassed passed) {
        StepUpProof proof = passed.proof();
        if (!UNBOUND_PROOFS.contains(proof)
                || !twoFactor.availableProofs(request.signer().accountId()).contains(proof)) {
            throw DocumentRefusal.SIGNING_PROOF_NOT_ACCEPTED.raise();
        }
        SigningAct act = act(request, passed);
        return proof == StepUpProof.TOTP
                ? new SigningEvidence.TotpUnbound(act)
                : new SigningEvidence.PasswordUnbound(act);
    }

    private SigningAct act(SigningRequest request, SignerConfirmation confirmation) {
        SigningCircumstances circumstances = confirmation.circumstances();
        return new SigningAct(
                request.requestUid(),
                request.signer(),
                names.accountHolder(request.signer().accountId()),
                names.member(request.signer().memberId()),
                request.fieldName(),
                request.statement(),
                request.contentSha256(),
                request.entries(),
                confirmation.nonce(),
                clock.instant(),
                truncated(circumstances.clientIp()),
                circumstances.userAgent());
    }

    private static @Nullable String truncated(@Nullable String clientIp) {
        if (clientIp == null) return null;
        try {
            return ConsentService.anonymizeIp(InetAddress.ofLiteral(clientIp));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
