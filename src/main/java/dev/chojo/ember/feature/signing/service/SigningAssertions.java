/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.service;

import com.yubico.webauthn.AssertionRequest;
import com.yubico.webauthn.AssertionResult;
import com.yubico.webauthn.FinishAssertionOptions;
import com.yubico.webauthn.RelyingParty;
import com.yubico.webauthn.data.AuthenticatorAssertionResponse;
import com.yubico.webauthn.data.AuthenticatorData;
import com.yubico.webauthn.data.ByteArray;
import com.yubico.webauthn.data.ClientAssertionExtensionOutputs;
import com.yubico.webauthn.data.CollectedClientData;
import com.yubico.webauthn.data.PublicKeyCredential;
import com.yubico.webauthn.data.PublicKeyCredentialRequestOptions;
import com.yubico.webauthn.data.UserVerificationRequirement;
import com.yubico.webauthn.data.exception.Base64UrlException;
import com.yubico.webauthn.exception.AssertionFailedException;
import dev.chojo.ember.api.refusal.DocumentRefusal;
import dev.chojo.ember.feature.signing.entity.SignerConfirmation.WebAuthnAssertion;
import dev.chojo.ember.feature.twofactor.entity.StepUpProof;
import dev.chojo.ember.feature.twofactor.entity.WebAuthnCredential;
import dev.chojo.ember.feature.twofactor.repository.TwoFactorRepository;
import dev.chojo.ember.feature.twofactor.service.RelyingParties;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.Optional;

/**
 * Checks a passkey or security key answer to a signing challenge, on the same relying party and credential
 * store as every other ceremony.
 *
 * <p>The client data and the authenticator data are read first, so that another challenge, another type,
 * another origin and a missing user verification each get a refusal of their own. Then the WebAuthn
 * library verifies the whole answer once more against a request built around the signing challenge: user
 * verification required, the account as the only owner, the signature under the stored public key and the
 * signature counter. Every credential the account holds counts, passkey or security key, and a success
 * advances the counter and the credential's last use as any other assertion does.
 */
@Singleton
public class SigningAssertions {
    private static final Logger log = LoggerFactory.getLogger(SigningAssertions.class);
    private static final String ASSERTION_TYPE = "webauthn.get";

    private final RelyingParties relyingParties;
    private final TwoFactorRepository credentials;

    @Inject
    public SigningAssertions(RelyingParties relyingParties, TwoFactorRepository credentials) {
        this.relyingParties = relyingParties;
        this.credentials = credentials;
    }

    /**
     * Verifies the answer as the account's answer to this challenge.
     *
     * @param accountId the account whose credential must have answered
     * @param challenge the signing challenge the answer must carry
     * @param assertion the answer as the browser passed it on
     * @return what the evidence records about the credential and the check
     */
    public VerifiedSigningAssertion verify(int accountId, byte[] challenge, WebAuthnAssertion assertion) {
        CollectedClientData clientData = clientData(assertion);
        if (!ASSERTION_TYPE.equals(clientData.getType())) throw DocumentRefusal.SIGNING_NOT_AN_ASSERTION.raise();
        if (!MessageDigest.isEqual(challenge, clientData.getChallenge().getBytes())) {
            throw DocumentRefusal.SIGNING_CHALLENGE_MISMATCH.raise();
        }
        if (!fromThisInstallation(clientData.getOrigin())) throw DocumentRefusal.SIGNING_FOREIGN_ORIGIN.raise();
        if (!authenticatorData(assertion).getFlags().UV) throw DocumentRefusal.SIGNING_NOT_USER_VERIFIED.raise();

        AssertionResult result = verified(accountId, challenge, assertion);
        WebAuthnCredential credential = credentials
                .findActiveWebAuthnByCredentialId(
                        result.getCredential().getCredentialId().getBytes())
                .orElseThrow(DocumentRefusal.SIGNING_ASSERTION_INVALID::raise);
        credentials.updateWebAuthnSignatureCounter(credential.factorId(), result.getSignatureCount());
        credentials.touchFactorUsed(credential.factorId());
        return new VerifiedSigningAssertion(
                credential.signIn() ? StepUpProof.PASSKEY : StepUpProof.SECURITY_KEY,
                relyingParty().getIdentity().getId(),
                credential.publicKeyCose(),
                result.isUserVerified(),
                result.getSignatureCount());
    }

    private AssertionResult verified(int accountId, byte[] challenge, WebAuthnAssertion assertion) {
        var options = PublicKeyCredentialRequestOptions.builder()
                .challenge(new ByteArray(challenge))
                .rpId(relyingParty().getIdentity().getId())
                .userVerification(UserVerificationRequirement.REQUIRED)
                .build();
        var request = AssertionRequest.builder()
                .publicKeyCredentialRequestOptions(options)
                .username(String.valueOf(accountId))
                .build();
        try {
            var response = AuthenticatorAssertionResponse.builder()
                    .authenticatorData(new ByteArray(assertion.authenticatorData()))
                    .clientDataJSON(new ByteArray(assertion.clientDataJson()))
                    .signature(new ByteArray(assertion.signature()))
                    .userHandle(Optional.ofNullable(assertion.userHandle()).map(ByteArray::new))
                    .build();
            var credential =
                    PublicKeyCredential.<AuthenticatorAssertionResponse, ClientAssertionExtensionOutputs>builder()
                            .id(new ByteArray(assertion.credentialId()))
                            .response(response)
                            .clientExtensionResults(
                                    ClientAssertionExtensionOutputs.builder().build())
                            .build();
            AssertionResult result = relyingParty()
                    .finishAssertion(FinishAssertionOptions.builder()
                            .request(request)
                            .response(credential)
                            .build());
            if (!result.isSuccess() || !result.isUserVerified())
                throw DocumentRefusal.SIGNING_ASSERTION_INVALID.raise();
            return result;
        } catch (IOException | Base64UrlException | AssertionFailedException | IllegalArgumentException e) {
            log.info("Signing assertion for account {} did not verify: {}", accountId, e.getMessage());
            throw DocumentRefusal.SIGNING_ASSERTION_INVALID.raise();
        }
    }

    private static CollectedClientData clientData(WebAuthnAssertion assertion) {
        try {
            return new CollectedClientData(new ByteArray(assertion.clientDataJson()));
        } catch (IOException | Base64UrlException | IllegalArgumentException e) {
            throw DocumentRefusal.SIGNING_ASSERTION_INVALID.raise();
        }
    }

    private static AuthenticatorData authenticatorData(WebAuthnAssertion assertion) {
        try {
            return new AuthenticatorData(new ByteArray(assertion.authenticatorData()));
        } catch (RuntimeException e) {
            throw DocumentRefusal.SIGNING_ASSERTION_INVALID.raise();
        }
    }

    /**
     * Whether the origin is one the relying party serves: same scheme and host as one of its origins, and
     * the same port unless the relying party allows any. Subdomains never count.
     */
    private boolean fromThisInstallation(String origin) {
        URI given = uri(origin);
        if (given == null || given.getHost() == null) return false;
        RelyingParty party = relyingParty();
        for (String allowed : party.getOrigins()) {
            URI expected = uri(allowed);
            if (expected != null && sameSite(given, expected, party.isAllowOriginPort())) return true;
        }
        return false;
    }

    private static boolean sameSite(URI given, URI expected, boolean anyPort) {
        return lower(given.getScheme()).equals(lower(expected.getScheme()))
                && lower(given.getHost()).equals(lower(expected.getHost()))
                && (anyPort || given.getPort() == expected.getPort());
    }

    private static String lower(@Nullable String text) {
        return text == null ? "" : text.toLowerCase(Locale.ROOT);
    }

    private static @Nullable URI uri(String text) {
        try {
            return new URI(text);
        } catch (URISyntaxException e) {
            return null;
        }
    }

    private RelyingParty relyingParty() {
        return relyingParties.passkey();
    }

    /**
     * What a verified answer says about the credential that gave it.
     *
     * @param proof          {@link StepUpProof#PASSKEY} for a sign-in passkey, {@link StepUpProof#SECURITY_KEY}
     *                       for a second-factor key
     * @param relyingPartyId the relying party id the credential is bound to
     * @param publicKeyCose  the credential's public key on file, COSE encoded
     * @param userVerified   whether the authenticator verified its user
     * @param signatureCount the authenticator's signature counter at this answer
     */
    public record VerifiedSigningAssertion(
            StepUpProof proof,
            String relyingPartyId,
            byte[] publicKeyCose,
            boolean userVerified,
            long signatureCount) {}
}
