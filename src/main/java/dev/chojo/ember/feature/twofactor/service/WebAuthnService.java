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
import com.yubico.webauthn.RelyingParty;
import com.yubico.webauthn.StartAssertionOptions;
import com.yubico.webauthn.StartRegistrationOptions;
import com.yubico.webauthn.data.AuthenticatorAttestationResponse;
import com.yubico.webauthn.data.AuthenticatorSelectionCriteria;
import com.yubico.webauthn.data.ByteArray;
import com.yubico.webauthn.data.ClientRegistrationExtensionOutputs;
import com.yubico.webauthn.data.PublicKeyCredential;
import com.yubico.webauthn.data.PublicKeyCredentialCreationOptions;
import com.yubico.webauthn.data.ResidentKeyRequirement;
import com.yubico.webauthn.data.UserIdentity;
import com.yubico.webauthn.data.UserVerificationRequirement;
import com.yubico.webauthn.exception.AssertionFailedException;
import com.yubico.webauthn.exception.RegistrationFailedException;
import dev.chojo.ember.conf.file.elements.Auth;
import dev.chojo.ember.conf.file.elements.TwoFactorSettings;
import dev.chojo.ember.feature.account.entity.AccountToken;
import dev.chojo.ember.feature.account.entity.TokenType;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.twofactor.entity.TwoFactorEvent;
import dev.chojo.ember.feature.twofactor.entity.TwoFactorFactor;
import dev.chojo.ember.feature.twofactor.entity.TwoFactorKind;
import dev.chojo.ember.feature.twofactor.entity.WebAuthnCredential;
import dev.chojo.ember.feature.twofactor.repository.TwoFactorRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Orchestrates WebAuthn registration and assertion ceremonies. Pending state (the server
 * challenge) is parked in {@code account_token} so the verifier is stateless and survives
 * the round trip to the browser.
 */
@Singleton
public class WebAuthnService {
    private static final Logger log = LoggerFactory.getLogger(WebAuthnService.class);
    private static final Duration CHALLENGE_TTL = Duration.ofMinutes(5);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final RelyingParty relyingParty;
    private final TwoFactorRepository repository;
    private final TwoFactorAuditService auditService;
    private final AccountRepository accountRepository;
    private final TwoFactorSettings settings;
    private final Auth authConfig;

    @Inject
    public WebAuthnService(
            RelyingParty relyingParty,
            TwoFactorRepository repository,
            TwoFactorAuditService auditService,
            AccountRepository accountRepository,
            TwoFactorSettings settings,
            Auth authConfig) {
        this.relyingParty = relyingParty;
        this.repository = repository;
        this.auditService = auditService;
        this.accountRepository = accountRepository;
        this.settings = settings;
        this.authConfig = authConfig;
    }

    // -- Registration --

    public record RegistrationStart(String challengeToken, String optionsJson) {}

    public RegistrationStart startRegistration(int accountId, String email, String displayName) {
        byte[] userHandle = repository.findUserHandleForAccount(accountId).orElseGet(WebAuthnService::newUserHandle);

        UserIdentity user = UserIdentity.builder()
                .name(String.valueOf(accountId))
                .displayName(displayName != null ? displayName : email)
                .id(new ByteArray(userHandle))
                .build();

        var selection = AuthenticatorSelectionCriteria.builder()
                .residentKey(
                        settings.webauthn().requireResidentKey()
                                ? ResidentKeyRequirement.REQUIRED
                                : ResidentKeyRequirement.DISCOURAGED)
                .userVerification(UserVerificationRequirement.PREFERRED)
                .build();

        PublicKeyCredentialCreationOptions options = relyingParty.startRegistration(StartRegistrationOptions.builder()
                .user(user)
                .authenticatorSelection(selection)
                .timeout(settings.webauthn().timeoutSeconds() * 1000L)
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
            browserJson = persistJson;
        }
        String token = persistChallenge(accountId, TokenType.TWO_FACTOR_WEBAUTHN_REG, persistJson);
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
        Optional<AccountToken> tokenOpt =
                consumeChallenge(challengeToken, TokenType.TWO_FACTOR_WEBAUTHN_REG, accountId);
        if (tokenOpt.isEmpty()) return Optional.empty();

        PublicKeyCredentialCreationOptions options;
        try {
            options = PublicKeyCredentialCreationOptions.fromJson(tokenOpt.get().metadata());
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
            result = relyingParty.finishRegistration(FinishRegistrationOptions.builder()
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
                .map(com.yubico.webauthn.data.AuthenticatorTransport::getId)
                .toList();
        String attestationFormat = result.getAttestationType() == null
                ? null
                : result.getAttestationType().name();
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

    // -- Assertion --

    public record AssertionStart(String challengeToken, String optionsJson) {}

    public AssertionStart startAssertion(int accountId) {
        AssertionRequest request = relyingParty.startAssertion(StartAssertionOptions.builder()
                .username(String.valueOf(accountId))
                .userVerification(UserVerificationRequirement.PREFERRED)
                .timeout(settings.webauthn().timeoutSeconds() * 1000L)
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
            browserJson = persistJson;
        }
        String token = persistChallenge(accountId, TokenType.TWO_FACTOR_WEBAUTHN_ASSERT, persistJson);
        return new AssertionStart(token, browserJson);
    }

    public boolean finishAssertion(int accountId, String challengeToken, String credentialJson) {
        Optional<AccountToken> tokenOpt =
                consumeChallenge(challengeToken, TokenType.TWO_FACTOR_WEBAUTHN_ASSERT, accountId);
        if (tokenOpt.isEmpty()) return false;

        AssertionRequest request;
        try {
            request = AssertionRequest.fromJson(tokenOpt.get().metadata());
        } catch (Exception e) {
            log.warn("Failed to parse stored WebAuthn assertion request for account {}", accountId, e);
            return false;
        }

        PublicKeyCredential<
                        com.yubico.webauthn.data.AuthenticatorAssertionResponse,
                        com.yubico.webauthn.data.ClientAssertionExtensionOutputs>
                response;
        try {
            response = PublicKeyCredential.parseAssertionResponseJson(credentialJson);
        } catch (Exception e) {
            log.warn("Invalid WebAuthn assertion response for account {}", accountId, e);
            return false;
        }

        AssertionResult result;
        try {
            result = relyingParty.finishAssertion(FinishAssertionOptions.builder()
                    .request(request)
                    .response(response)
                    .build());
        } catch (AssertionFailedException e) {
            log.warn("WebAuthn assertion verification failed for account {}", accountId, e);
            return false;
        }

        if (!result.isSuccess()) return false;

        Optional<WebAuthnCredential> credential =
                repository.findWebAuthnByCredentialId(result.getCredentialId().getBytes());
        if (credential.isEmpty()) return false;
        repository.updateWebAuthnSignatureCounter(credential.get().factorId(), result.getSignatureCount());
        repository.touchFactorUsed(credential.get().factorId());
        return true;
    }

    // -- Helpers --

    private String persistChallenge(int accountId, TokenType type, String metadata) {
        String token = newChallengeToken();
        accountRepository.createToken(
                accountId, token, type, metadata, Instant.now().plus(CHALLENGE_TTL));
        return token;
    }

    private Optional<AccountToken> consumeChallenge(String token, TokenType type, int accountId) {
        Optional<AccountToken> tokenOpt = accountRepository.findToken(token);
        if (tokenOpt.isEmpty()) return Optional.empty();
        AccountToken stored = tokenOpt.get();
        if (stored.isExpired() || stored.tokenType() != type || stored.accountId() != accountId) {
            accountRepository.deleteToken(token);
            return Optional.empty();
        }
        accountRepository.deleteToken(token);
        return tokenOpt;
    }

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
        var buf = java.nio.ByteBuffer.wrap(bytes);
        long msb = buf.getLong();
        long lsb = buf.getLong();
        if (msb == 0L && lsb == 0L) return null;
        return new UUID(msb, lsb);
    }
}
