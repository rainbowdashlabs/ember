/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.twofactor.service;

import com.yubico.webauthn.AssertionRequest;
import com.yubico.webauthn.AssertionResult;
import com.yubico.webauthn.FinishAssertionOptions;
import com.yubico.webauthn.FinishRegistrationOptions;
import com.yubico.webauthn.RegistrationResult;
import com.yubico.webauthn.StartAssertionOptions;
import com.yubico.webauthn.StartRegistrationOptions;
import com.yubico.webauthn.data.AuthenticatorAssertionResponse;
import com.yubico.webauthn.data.AuthenticatorAttestationResponse;
import com.yubico.webauthn.data.AuthenticatorSelectionCriteria;
import com.yubico.webauthn.data.AuthenticatorTransport;
import com.yubico.webauthn.data.ByteArray;
import com.yubico.webauthn.data.ClientAssertionExtensionOutputs;
import com.yubico.webauthn.data.ClientRegistrationExtensionOutputs;
import com.yubico.webauthn.data.PublicKeyCredential;
import com.yubico.webauthn.data.PublicKeyCredentialCreationOptions;
import com.yubico.webauthn.data.UserIdentity;
import com.yubico.webauthn.exception.AssertionFailedException;
import com.yubico.webauthn.exception.RegistrationFailedException;
import dev.chojo.ember.conf.file.elements.WebAuthnSettings;
import dev.chojo.ember.feature.twofactor.entity.ChallengePurpose;
import dev.chojo.ember.feature.twofactor.entity.TwoFactorFactor;
import dev.chojo.ember.feature.twofactor.entity.TwoFactorKind;
import dev.chojo.ember.feature.twofactor.entity.WebAuthnChallenge;
import dev.chojo.ember.feature.twofactor.entity.WebAuthnCredential;
import dev.chojo.ember.feature.twofactor.repository.TwoFactorRepository;
import dev.chojo.ember.feature.twofactor.repository.WebAuthnChallengeRepository;
import dev.chojo.ember.util.RandomTokens;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The WebAuthn ceremonies both credential kinds run: starting and finishing a creation, starting
 * and finishing an assertion, and parking the server challenge in {@code webauthn_challenge} in
 * between, so the verifier is stateless and survives the round trip to the browser.
 *
 * <p>Everything that differs between a second factor and a passkey comes from the
 * {@link CredentialRole}; the services around this class decide which challenge kinds exist,
 * what a success means and what is audited.
 *
 * <p>Creations always run on the full relying-party view, so the exclude list covers every
 * credential the account has. Assertions run on the view the role names.
 */
public final class WebAuthnCeremonies {
    private static final Logger log = LoggerFactory.getLogger(WebAuthnCeremonies.class);
    private static final Duration CHALLENGE_TTL = Duration.ofMinutes(5);

    private final RelyingParties relyingParties;
    private final TwoFactorRepository repository;
    private final WebAuthnChallengeRepository challengeRepository;
    private final WebAuthnSettings settings;

    public WebAuthnCeremonies(
            RelyingParties relyingParties,
            TwoFactorRepository repository,
            WebAuthnChallengeRepository challengeRepository,
            WebAuthnSettings settings) {
        this.relyingParties = relyingParties;
        this.repository = repository;
        this.challengeRepository = challengeRepository;
        this.settings = settings;
    }

    /**
     * Starts a creation ceremony for the account in the given role and parks its options under a
     * challenge of the given kind. The user handle is the account's existing one, or a fresh one
     * for an account without any credential yet.
     */
    public CeremonyStart startRegistration(
            int accountId, String email, String displayName, CredentialRole role, ChallengePurpose purpose) {
        byte[] userHandle = repository.findUserHandleForAccount(accountId).orElseGet(WebAuthnCeremonies::newUserHandle);

        UserIdentity user = UserIdentity.builder()
                .name(String.valueOf(accountId))
                .displayName(displayName != null ? displayName : email)
                .id(new ByteArray(userHandle))
                .build();

        var selection = AuthenticatorSelectionCriteria.builder()
                .residentKey(role.residentKey())
                .userVerification(role.userVerification())
                .build();

        PublicKeyCredentialCreationOptions options = relyingParties
                .passkey()
                .startRegistration(StartRegistrationOptions.builder()
                        .user(user)
                        .authenticatorSelection(selection)
                        .timeout(timeoutMillis())
                        .build());

        String persistJson;
        try {
            persistJson = options.toJson();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize WebAuthn creation options for storage", e);
        }
        String browserJson;
        try {
            browserJson = options.toCredentialsCreateJson();
        } catch (Exception e) {
            log.warn("WebAuthn creation options fell back to the stored shape for account {}", accountId, e);
            browserJson = persistJson;
        }
        return new CeremonyStart(persistChallenge(accountId, purpose, persistJson), browserJson);
    }

    /**
     * Verifies a creation against the stored options and records the credential with the parent
     * factor row. A role that requires user verification refuses a credential that came back
     * without it. Returns the new factor, or empty on any failure.
     */
    public Optional<TwoFactorFactor> finishRegistration(
            int accountId, String optionsJson, String credentialJson, String label, CredentialRole role) {
        PublicKeyCredentialCreationOptions options;
        try {
            options = PublicKeyCredentialCreationOptions.fromJson(optionsJson);
        } catch (Exception e) {
            log.warn("Failed to parse stored WebAuthn {} creation options for account {}", role, accountId, e);
            return Optional.empty();
        }

        PublicKeyCredential<AuthenticatorAttestationResponse, ClientRegistrationExtensionOutputs> response;
        try {
            response = PublicKeyCredential.parseRegistrationResponseJson(credentialJson);
        } catch (Exception e) {
            log.warn("Invalid WebAuthn {} registration response for account {}", role, accountId, e);
            return Optional.empty();
        }

        RegistrationResult result;
        try {
            result = relyingParties
                    .passkey()
                    .finishRegistration(FinishRegistrationOptions.builder()
                            .request(options)
                            .response(response)
                            .build());
        } catch (RegistrationFailedException e) {
            log.warn("WebAuthn {} registration verification failed for account {}", role, accountId, e);
            return Optional.empty();
        }

        if (role.requiresUserVerification() && !result.isUserVerified()) {
            log.info("WebAuthn {} creation refused for account {}: no user verification", role, accountId);
            return Optional.empty();
        }

        String factorLabel = label == null || label.isBlank() ? role.defaultLabel() : label;
        TwoFactorFactor factor = repository.createFactor(accountId, TwoFactorKind.WEBAUTHN, factorLabel);
        List<String> transports = response.getResponse().getTransports().stream()
                .map(AuthenticatorTransport::getId)
                .toList();
        repository.createWebAuthn(
                factor.id(),
                result.getKeyId().getId().getBytes(),
                result.getPublicKeyCose().getBytes(),
                result.getSignatureCount(),
                aaguidToUuid(result.getAaguid()),
                transports,
                result.getAttestationType().name(),
                options.getUser().getId().getBytes(),
                role.signIn(),
                role.secondFactor(),
                result.isDiscoverable().orElse(null),
                result.isUserVerified());
        return Optional.of(factor);
    }

    /**
     * Starts an assertion in the given role and parks the request under a challenge of the given
     * kind. A second factor names the account through its allow list; a passkey names nobody,
     * which leaves the choice to the browser's own account picker. The challenge carries the
     * account when one is known, and none for a passwordless sign-in.
     */
    public CeremonyStart startAssertion(CredentialRole role, Integer accountId, ChallengePurpose purpose) {
        var options = StartAssertionOptions.builder()
                .userVerification(role.userVerification())
                .timeout(timeoutMillis());
        if (role.namesAccount()) options.username(String.valueOf(accountId));
        AssertionRequest request = role.assertionView(relyingParties).startAssertion(options.build());

        String persistJson;
        try {
            persistJson = request.toJson();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize WebAuthn assertion request for storage", e);
        }
        String browserJson;
        try {
            browserJson = request.toCredentialsGetJson();
        } catch (Exception e) {
            log.warn("WebAuthn assertion request fell back to the stored shape for account {}", accountId, e);
            browserJson = persistJson;
        }
        return new CeremonyStart(persistChallenge(accountId, purpose, persistJson), browserJson);
    }

    /**
     * Verifies an assertion against the stored request on the role's view, and answers with the
     * accepted credential when the role admits it. A role that requires user verification refuses
     * an assertion without it rather than downgrading it. Returns empty on any failure, and the
     * refusals are deliberately alike from the outside.
     */
    public Optional<VerifiedAssertion> finishAssertion(CredentialRole role, String requestJson, String credentialJson) {
        AssertionRequest request;
        try {
            request = AssertionRequest.fromJson(requestJson);
        } catch (Exception e) {
            log.warn("Failed to parse stored WebAuthn {} assertion request", role, e);
            return Optional.empty();
        }

        PublicKeyCredential<AuthenticatorAssertionResponse, ClientAssertionExtensionOutputs> response;
        try {
            response = PublicKeyCredential.parseAssertionResponseJson(credentialJson);
        } catch (Exception e) {
            log.warn("Invalid WebAuthn {} assertion response", role, e);
            return Optional.empty();
        }

        AssertionResult result;
        try {
            result = role.assertionView(relyingParties)
                    .finishAssertion(FinishAssertionOptions.builder()
                            .request(request)
                            .response(response)
                            .build());
        } catch (AssertionFailedException e) {
            log.warn("WebAuthn {} assertion verification failed", role, e);
            return Optional.empty();
        }

        if (!result.isSuccess()) {
            log.info("WebAuthn {} assertion was not accepted", role);
            return Optional.empty();
        }
        if (role.requiresUserVerification() && !result.isUserVerified()) {
            log.info("WebAuthn {} assertion refused: no user verification", role);
            return Optional.empty();
        }

        Optional<WebAuthnCredential> credential = repository.findActiveWebAuthnByCredentialId(
                result.getCredential().getCredentialId().getBytes());
        if (credential.isEmpty()) {
            log.warn("WebAuthn {} assertion failed: the accepted credential is not on file or disabled", role);
            return Optional.empty();
        }
        if (!role.admits(credential.get())) {
            log.info("WebAuthn {} assertion refused: the credential does not serve this role", role);
            return Optional.empty();
        }
        return Optional.of(new VerifiedAssertion(result, credential.get()));
    }

    /**
     * Spends a challenge of the given kind that was issued to the given account. Unknown, expired,
     * foreign and differently purposed challenges all come back empty; any of them is gone after
     * this call either way.
     */
    public Optional<WebAuthnChallenge> consumeChallenge(String token, ChallengePurpose purpose, int accountId) {
        return consumeChallenge(token, purpose)
                .filter(stored -> stored.accountId() != null && stored.accountId() == accountId);
    }

    /**
     * Spends a challenge of the given kind without asking whom it was issued to, which is what a
     * passwordless sign-in needs: nobody has said who they are yet.
     */
    public Optional<WebAuthnChallenge> consumeChallenge(String token, ChallengePurpose purpose) {
        return challengeRepository.consume(token).filter(stored -> !stored.isExpired() && stored.purpose() == purpose);
    }

    private String persistChallenge(Integer accountId, ChallengePurpose purpose, String optionsJson) {
        String token = newChallengeToken();
        challengeRepository.create(
                token, purpose, accountId, optionsJson, Instant.now().plus(CHALLENGE_TTL));
        return token;
    }

    private long timeoutMillis() {
        return settings.timeoutSeconds() * 1000L;
    }

    static String newChallengeToken() {
        return RandomTokens.hex(32);
    }

    private static byte[] newUserHandle() {
        return RandomTokens.bytes(64);
    }

    /**
     * Converts a 16-byte AAGUID to {@link UUID}. Returns {@code null} when the authenticator omits
     * the AAGUID or reports the all-zero one (e.g. a U2F-only fallback or a {@code none}
     * attestation).
     */
    static UUID aaguidToUuid(ByteArray aaguid) {
        if (aaguid == null) return null;
        byte[] bytes = aaguid.getBytes();
        if (bytes.length != 16) return null;
        var buf = ByteBuffer.wrap(bytes);
        long msb = buf.getLong();
        long lsb = buf.getLong();
        if (msb == 0L && lsb == 0L) return null;
        return new UUID(msb, lsb);
    }

    /**
     * What the browser needs to run a ceremony: the token that names the parked challenge, and
     * the options in the shape {@code navigator.credentials} takes.
     */
    public record CeremonyStart(String challengeToken, String optionsJson) {}

    /**
     * A verified assertion together with the stored credential that answered it.
     *
     * @param result the library's verification result, carrying the new signature count and the
     *         user handle
     * @param credential the active stored credential the assertion was made with
     */
    public record VerifiedAssertion(AssertionResult result, WebAuthnCredential credential) {}
}
