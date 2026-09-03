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
import com.yubico.webauthn.data.ResidentKeyRequirement;
import com.yubico.webauthn.data.UserIdentity;
import com.yubico.webauthn.data.UserVerificationRequirement;
import com.yubico.webauthn.exception.AssertionFailedException;
import com.yubico.webauthn.exception.RegistrationFailedException;
import dev.chojo.ember.conf.file.elements.WebAuthnSettings;
import dev.chojo.ember.feature.twofactor.entity.ChallengePurpose;
import dev.chojo.ember.feature.twofactor.entity.TwoFactorEvent;
import dev.chojo.ember.feature.twofactor.entity.TwoFactorFactor;
import dev.chojo.ember.feature.twofactor.entity.TwoFactorKind;
import dev.chojo.ember.feature.twofactor.entity.WebAuthnChallenge;
import dev.chojo.ember.feature.twofactor.entity.WebAuthnCredential;
import dev.chojo.ember.feature.twofactor.repository.TwoFactorRepository;
import dev.chojo.ember.feature.twofactor.repository.WebAuthnChallengeRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Orchestrates WebAuthn registration and assertion ceremonies. Pending state (the server
 * challenge) is parked in {@code webauthn_challenge} so the verifier is stateless and survives
 * the round trip to the browser.
 */
@Singleton
public class WebAuthnService {
    private static final Logger log = LoggerFactory.getLogger(WebAuthnService.class);
    private static final Duration CHALLENGE_TTL = Duration.ofMinutes(5);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final RelyingParties relyingParties;
    private final TwoFactorRepository repository;
    private final TwoFactorAuditService auditService;
    private final WebAuthnChallengeRepository challengeRepository;
    private final WebAuthnSettings settings;

    @Inject
    public WebAuthnService(
            RelyingParties relyingParties,
            TwoFactorRepository repository,
            TwoFactorAuditService auditService,
            WebAuthnChallengeRepository challengeRepository,
            WebAuthnSettings settings) {
        this.relyingParties = relyingParties;
        this.repository = repository;
        this.auditService = auditService;
        this.challengeRepository = challengeRepository;
        this.settings = settings;
    }

    // -- Registration --

    private static String newChallengeToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private static byte[] newUserHandle() {
        byte[] bytes = new byte[64];
        RANDOM.nextBytes(bytes);
        return bytes;
    }

    /**
     * Converts a 16-byte AAGUID to {@link UUID}. Returns {@code null} when the
     * authenticator omits the AAGUID (e.g. U2F-only fallback).
     */
    private static UUID aaguidToUuid(ByteArray aaguid) {
        if (aaguid == null) return null;
        byte[] bytes = aaguid.getBytes();
        if (bytes.length != 16) return null;
        var buf = ByteBuffer.wrap(bytes);
        long msb = buf.getLong();
        long lsb = buf.getLong();
        if (msb == 0L && lsb == 0L) return null;
        return new UUID(msb, lsb);
    }

    // -- Assertion --

    public RegistrationStart startRegistration(int accountId, String email, String displayName) {
        byte[] userHandle = repository.findUserHandleForAccount(accountId).orElseGet(WebAuthnService::newUserHandle);

        UserIdentity user = UserIdentity.builder()
                .name(String.valueOf(accountId))
                .displayName(displayName != null ? displayName : email)
                .id(new ByteArray(userHandle))
                .build();

        // A second factor never needs to be discoverable: it is always named by an allow list.
        // The passkey ceremony is the one that requires a resident key, and it has its own service.
        var selection = AuthenticatorSelectionCriteria.builder()
                .residentKey(ResidentKeyRequirement.DISCOURAGED)
                .userVerification(UserVerificationRequirement.PREFERRED)
                .build();

        PublicKeyCredentialCreationOptions options = relyingParties
                .passkey()
                .startRegistration(StartRegistrationOptions.builder()
                        .user(user)
                        .authenticatorSelection(selection)
                        .timeout(settings.timeoutSeconds() * 1000L)
                        .build());

        String persistJson;
        try {
            persistJson = options.toJson();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize WebAuthn options for storage", e);
        }
        String browserJson;
        try {
            browserJson = options.toCredentialsCreateJson();
        } catch (Exception e) {
            log.warn("WebAuthn registration options fell back to the stored shape for account {}", accountId, e);
            browserJson = persistJson;
        }
        String token = persistChallenge(accountId, ChallengePurpose.REGISTRATION, persistJson);
        return new RegistrationStart(token, browserJson);
    }

    /**
     * Completes a registration ceremony. Inserts the new credential and the parent factor row.
     * Returns the new factor on success.
     */
    public Optional<TwoFactorFactor> finishRegistration(
            int accountId,
            String challengeToken,
            String credentialJson,
            String label,
            String userAgent,
            String country) {
        Optional<WebAuthnChallenge> challengeOpt =
                consumeChallenge(challengeToken, ChallengePurpose.REGISTRATION, accountId);
        if (challengeOpt.isEmpty()) {
            log.info("WebAuthn registration failed for account {}: challenge unknown or expired", accountId);
            return Optional.empty();
        }

        PublicKeyCredentialCreationOptions options;
        try {
            options = PublicKeyCredentialCreationOptions.fromJson(
                    challengeOpt.get().optionsJson());
        } catch (Exception e) {
            log.warn("Failed to parse stored WebAuthn options for account {}", accountId, e);
            return Optional.empty();
        }

        PublicKeyCredential<AuthenticatorAttestationResponse, ClientRegistrationExtensionOutputs> response;
        try {
            response = PublicKeyCredential.parseRegistrationResponseJson(credentialJson);
        } catch (Exception e) {
            log.warn("Invalid WebAuthn registration response for account {}", accountId, e);
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
            log.warn("WebAuthn registration verification failed for account {}", accountId, e);
            return Optional.empty();
        }

        String factorLabel = label == null || label.isBlank() ? "Security Key" : label;
        TwoFactorFactor factor = repository.createFactor(accountId, TwoFactorKind.WEBAUTHN, factorLabel);
        UUID aaguid = aaguidToUuid(result.getAaguid());
        List<String> transports = response.getResponse().getTransports().stream()
                .map(AuthenticatorTransport::getId)
                .toList();
        String attestationFormat = result.getAttestationType().name();
        repository.createWebAuthn(
                factor.id(),
                result.getKeyId().getId().getBytes(),
                result.getPublicKeyCose().getBytes(),
                result.getSignatureCount(),
                aaguid,
                transports,
                attestationFormat,
                options.getUser().getId().getBytes());

        auditService.record(accountId, null, TwoFactorEvent.ENROLLED, TwoFactorKind.WEBAUTHN, userAgent, country);
        log.info("WebAuthn credential enrolled for account {} (factor {})", accountId, factor.id());
        return Optional.of(factor);
    }

    public AssertionStart startAssertion(int accountId) {
        AssertionRequest request = relyingParties
                .secondFactor()
                .startAssertion(StartAssertionOptions.builder()
                        .username(String.valueOf(accountId))
                        .userVerification(UserVerificationRequirement.PREFERRED)
                        .timeout(settings.timeoutSeconds() * 1000L)
                        .build());

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
        String token = persistChallenge(accountId, ChallengePurpose.SECOND_FACTOR_ASSERTION, persistJson);
        return new AssertionStart(token, browserJson);
    }

    // -- Helpers --

    public boolean finishAssertion(int accountId, String challengeToken, String credentialJson) {
        Optional<WebAuthnChallenge> challengeOpt =
                consumeChallenge(challengeToken, ChallengePurpose.SECOND_FACTOR_ASSERTION, accountId);
        if (challengeOpt.isEmpty()) {
            log.info("WebAuthn assertion failed for account {}: challenge unknown or expired", accountId);
            return false;
        }

        AssertionRequest request;
        try {
            request = AssertionRequest.fromJson(challengeOpt.get().optionsJson());
        } catch (Exception e) {
            log.warn("Failed to parse stored WebAuthn assertion request for account {}", accountId, e);
            return false;
        }

        PublicKeyCredential<AuthenticatorAssertionResponse, ClientAssertionExtensionOutputs> response;
        try {
            response = PublicKeyCredential.parseAssertionResponseJson(credentialJson);
        } catch (Exception e) {
            log.warn("Invalid WebAuthn assertion response for account {}", accountId, e);
            return false;
        }

        AssertionResult result;
        try {
            result = relyingParties
                    .secondFactor()
                    .finishAssertion(FinishAssertionOptions.builder()
                            .request(request)
                            .response(response)
                            .build());
        } catch (AssertionFailedException e) {
            log.warn("WebAuthn assertion verification failed for account {}", accountId, e);
            return false;
        }

        if (!result.isSuccess()) {
            log.info("WebAuthn assertion failed for account {}: the authenticator was not accepted", accountId);
            return false;
        }

        Optional<WebAuthnCredential> credential = repository.findWebAuthnByCredentialId(
                result.getCredential().getCredentialId().getBytes());
        if (credential.isEmpty()) {
            log.warn("WebAuthn assertion failed for account {}: the accepted credential is not on file", accountId);
            return false;
        }
        repository.updateWebAuthnSignatureCounter(credential.get().factorId(), result.getSignatureCount());
        repository.touchFactorUsed(credential.get().factorId());
        return true;
    }

    private String persistChallenge(int accountId, ChallengePurpose purpose, String optionsJson) {
        String token = newChallengeToken();
        challengeRepository.create(
                token, purpose, accountId, optionsJson, Instant.now().plus(CHALLENGE_TTL));
        return token;
    }

    private Optional<WebAuthnChallenge> consumeChallenge(String token, ChallengePurpose purpose, int accountId) {
        return challengeRepository
                .consume(token)
                .filter(stored -> !stored.isExpired()
                        && stored.purpose() == purpose
                        && stored.accountId() != null
                        && stored.accountId() == accountId);
    }

    public record RegistrationStart(String challengeToken, String optionsJson) {}

    public record AssertionStart(String challengeToken, String optionsJson) {}
}
