/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.service;

import dev.chojo.ember.api.refusal.DocumentRefusal;
import dev.chojo.ember.feature.legal.service.ConsentService;
import dev.chojo.ember.feature.signing.entity.BatchMembership;
import dev.chojo.ember.feature.signing.entity.CompletedSigning;
import dev.chojo.ember.feature.signing.entity.SignatureLevel;
import dev.chojo.ember.feature.signing.entity.SignerConfirmation;
import dev.chojo.ember.feature.signing.entity.SignerConfirmation.StepUpPassed;
import dev.chojo.ember.feature.signing.entity.SignerConfirmation.WebAuthnAssertion;
import dev.chojo.ember.feature.signing.entity.SigningAct;
import dev.chojo.ember.feature.signing.entity.SigningBatch;
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
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
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
 * <p><b>Sealing.</b> {@link #complete} gives the evidence only and seals nothing. Once the act is recorded,
 * {@link SigningStateSealer} seals the request's new state: a fresh document built from the frozen content
 * with a signature record page and the evidence of every act so far attached ({@link SigningStateAssembler}),
 * sealed on its own, so the latest version carries every act so far and the earlier ones stay valid as they
 * are. No state is ever signed onto the one before.
 */
@Singleton
public class InEmberSignatureProvider implements SignatureProvider {
    private static final Set<StepUpProof> SIGNING_PROOFS =
            EnumSet.of(StepUpProof.PASSKEY, StepUpProof.SECURITY_KEY, StepUpProof.TOTP, StepUpProof.PASSWORD);
    private static final Set<StepUpProof> UNBOUND_PROOFS = EnumSet.of(StepUpProof.TOTP, StepUpProof.PASSWORD);

    private final TwoFactorService twoFactor;
    private final SigningAssertions assertions;
    private final SignerNames names;
    private final CredentialKeyStamps keyStamps;
    private final Clock clock;

    @Inject
    public InEmberSignatureProvider(
            TwoFactorService twoFactor,
            SigningAssertions assertions,
            SignerNames names,
            CredentialKeyStamps keyStamps) {
        this(twoFactor, assertions, names, keyStamps, Clock.systemUTC());
    }

    InEmberSignatureProvider(
            TwoFactorService twoFactor,
            SigningAssertions assertions,
            SignerNames names,
            CredentialKeyStamps keyStamps,
            Clock clock) {
        this.twoFactor = twoFactor;
        this.assertions = assertions;
        this.names = names;
        this.keyStamps = keyStamps;
        this.clock = clock;
    }

    @Override
    public SignatureLevel level() {
        return SignatureLevel.SIMPLE;
    }

    @Override
    public SigningStart start(SigningBatch batch) {
        Set<StepUpProof> accepted = acceptedProofs(batch.accountId());
        if (accepted.isEmpty()) throw DocumentRefusal.SIGNING_NO_PROOF.raise();
        byte[] nonce = RandomTokens.bytes(SigningChallenge.NONCE_BYTES);
        return new SigningStart.InEmber(nonce, SigningChallenge.of(nonce, batch), accepted);
    }

    /**
     * Checks the one confirmation of a batch once, then gives each field its own evidence: the act on that
     * field, with the batch it was confirmed in, and the shared proof. A passkey's or security key's answer
     * is the same in every field's evidence, and so is the moment it was accepted.
     */
    @Override
    public List<CompletedSigning> complete(SigningBatch batch, SignerConfirmation confirmation) {
        var acts = acts(batch, confirmation);
        List<SigningEvidence> evidence =
                switch (confirmation) {
                    case WebAuthnAssertion assertion -> bound(batch, acts, assertion);
                    case StepUpPassed passed -> unbound(batch, acts, passed);
                };
        return evidence.stream()
                .map(each -> new CompletedSigning(level(), each))
                .toList();
    }

    private Set<StepUpProof> acceptedProofs(int accountId) {
        Set<StepUpProof> accepted = EnumSet.noneOf(StepUpProof.class);
        accepted.addAll(twoFactor.availableProofs(accountId));
        accepted.retainAll(SIGNING_PROOFS);
        return accepted;
    }

    private List<SigningEvidence> bound(SigningBatch batch, List<SigningAct> acts, WebAuthnAssertion assertion) {
        byte[] challenge = SigningChallenge.of(assertion.nonce(), batch);
        VerifiedSigningAssertion verified = assertions.verify(batch.accountId(), challenge, assertion);
        var keyStamp = keyStamps.forSigning(verified.credential());
        return acts.stream()
                .<SigningEvidence>map(act -> new SigningEvidence.WebAuthnBound(
                        act,
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
                        keyStamp))
                .toList();
    }

    private List<SigningEvidence> unbound(SigningBatch batch, List<SigningAct> acts, StepUpPassed passed) {
        StepUpProof proof = passed.proof();
        if (!UNBOUND_PROOFS.contains(proof)
                || !twoFactor.availableProofs(batch.accountId()).contains(proof)) {
            throw DocumentRefusal.SIGNING_PROOF_NOT_ACCEPTED.raise();
        }
        return acts.stream()
                .<SigningEvidence>map(act -> proof == StepUpProof.TOTP
                        ? new SigningEvidence.TotpUnbound(act)
                        : new SigningEvidence.PasswordUnbound(act))
                .toList();
    }

    /** The act on each field of the batch, each naming the batch where it holds more than one field. */
    private List<SigningAct> acts(SigningBatch batch, SignerConfirmation confirmation) {
        SigningCircumstances circumstances = confirmation.circumstances();
        Instant signedAt = clock.instant();
        @Nullable String truncatedIp = truncated(circumstances.clientIp());
        String accountHolder = names.accountHolder(batch.accountId());
        List<BatchMembership.Item> items = batch.single() ? List.of() : itemsOf(batch);
        var acts = new ArrayList<SigningAct>(batch.requests().size());
        for (int position = 0; position < batch.requests().size(); position++) {
            SigningRequest request = batch.requests().get(position);
            acts.add(new SigningAct(
                    request.requestUid(),
                    request.signer(),
                    accountHolder,
                    names.member(request.signer().memberId()),
                    request.fieldName(),
                    request.statement(),
                    request.contentSha256(),
                    request.entries(),
                    confirmation.nonce(),
                    signedAt,
                    truncatedIp,
                    circumstances.userAgent(),
                    items.isEmpty() ? null : new BatchMembership(batch.uid(), position, items)));
        }
        return acts;
    }

    private static List<BatchMembership.Item> itemsOf(SigningBatch batch) {
        var digests = SigningBatchChallenge.digestsOf(batch);
        var items = new ArrayList<BatchMembership.Item>(digests.size());
        for (int position = 0; position < digests.size(); position++) {
            var request = batch.requests().get(position);
            items.add(new BatchMembership.Item(request.requestUid(), request.fieldName(), digests.get(position)));
        }
        return items;
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
