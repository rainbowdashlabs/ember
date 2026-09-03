/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.passkey.service;

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
import dev.chojo.ember.feature.twofactor.service.RelyingParties;
import dev.chojo.ember.feature.twofactor.service.TwoFactorAuditService;
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
 * Runs the passkey ceremonies: creating a credential that may start a sign-in, and the
 * passwordless sign-in itself. Beside {@code WebAuthnService} rather than inside it, because
 * the two differ in their options and their outcomes: a passkey requires a resident key and
 * user verification where a second factor asks for neither, and its assertion identifies the
 * account instead of confirming one.
 */
@Singleton
public class PasskeyService {
    private static final Logger log = LoggerFactory.getLogger(PasskeyService.class);
    private static final Duration CHALLENGE_TTL = Duration.ofMinutes(5);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final RelyingParties relyingParties;
    private final TwoFactorRepository repository;
    private final TwoFactorAuditService auditService;
    private final WebAuthnChallengeRepository challengeRepository;
    private final WebAuthnSettings settings;

    @Inject
    public PasskeyService(
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

    // -- Creation --

    /**
     * Starts a passkey creation for the account: resident key and user verification required,
     * the exclude list covering every credential the account has, and no hints, so the
     * browser's own cross-device path stays available.
     */
    public CeremonyStart startCreation(int accountId, String email, String displayName) {
        byte[] userHandle = repository.findUserHandleForAccount(accountId).orElseGet(PasskeyService::newUserHandle);

        UserIdentity user = UserIdentity.builder()
                .name(String.valueOf(accountId))
                .displayName(displayName != null ? displayName : email)
                .id(new ByteArray(userHandle))
                .build();

        var selection = AuthenticatorSelectionCriteria.builder()
                .residentKey(ResidentKeyRequirement.REQUIRED)
                .userVerification(UserVerificationRequirement.REQUIRED)
                .build();

        PublicKeyCredentialCreationOptions options = relyingParties
                .passkey()
                .startRegistration(StartRegistrationOptions.builder()
                        .user(user)
                        .authenticatorSelection(selection)
                        .timeout(settings.timeoutSeconds() * 1000L)
                        .build());

        return persistCreationStart(options, accountId);
    }

    private CeremonyStart persistCreationStart(PublicKeyCredentialCreationOptions options, int accountId) {
        String persistJson;
        try {
            persistJson = options.toJson();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize passkey options for storage", e);
        }
        String browserJson;
        try {
            browserJson = options.toCredentialsCreateJson();
        } catch (Exception e) {
            log.warn("Passkey creation options fell back to the stored shape for account {}", accountId, e);
            browserJson = persistJson;
        }
        String token = newChallengeToken();
        challengeRepository.create(
                token,
                ChallengePurpose.REGISTRATION,
                accountId,
                persistJson,
                Instant.now().plus(CHALLENGE_TTL));
        return new CeremonyStart(token, browserJson);
    }

    /**
     * Completes a passkey creation. Refuses a credential that came back without user
     * verification, which is the honest outcome for an authenticator that cannot do it: the
     * flag is what lets the sign-in count as two factors in one gesture (D2), so a credential
     * without it must not become a sign-in credential.
     */
    public Optional<TwoFactorFactor> finishCreation(
            int accountId,
            String challengeToken,
            String credentialJson,
            String label,
            String userAgent,
            String country) {
        Optional<WebAuthnChallenge> challengeOpt = challengeRepository
                .consume(challengeToken)
                .filter(stored -> !stored.isExpired()
                        && stored.purpose() == ChallengePurpose.REGISTRATION
                        && stored.accountId() != null
                        && stored.accountId() == accountId);
        if (challengeOpt.isEmpty()) {
            log.info("Passkey creation failed for account {}: challenge unknown or expired", accountId);
            return Optional.empty();
        }
        return finishCreationCeremony(
                accountId,
                challengeOpt.get().optionsJson(),
                credentialJson,
                label,
                userAgent,
                country,
                TwoFactorEvent.ENROLLED);
    }

    /**
     * The shared tail of every creation door: session, device code, mail link and guardian QR
     * all verify the same way and write the same rows; only the audit event differs.
     */
    Optional<TwoFactorFactor> finishCreationCeremony(
            int accountId,
            String optionsJson,
            String credentialJson,
            String label,
            String userAgent,
            String country,
            TwoFactorEvent auditEvent) {
        PublicKeyCredentialCreationOptions options;
        try {
            options = PublicKeyCredentialCreationOptions.fromJson(optionsJson);
        } catch (Exception e) {
            log.warn("Failed to parse stored passkey options for account {}", accountId, e);
            return Optional.empty();
        }

        PublicKeyCredential<AuthenticatorAttestationResponse, ClientRegistrationExtensionOutputs> response;
        try {
            response = PublicKeyCredential.parseRegistrationResponseJson(credentialJson);
        } catch (Exception e) {
            log.warn("Invalid passkey registration response for account {}", accountId, e);
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
            log.warn("Passkey registration verification failed for account {}", accountId, e);
            return Optional.empty();
        }

        if (!result.isUserVerified()) {
            log.info("Passkey creation refused for account {}: no user verification", accountId);
            return Optional.empty();
        }

        String factorLabel = label == null || label.isBlank() ? "Passkey" : label;
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
                true,
                false,
                result.isDiscoverable().orElse(null),
                true);

        auditService.record(accountId, null, auditEvent, TwoFactorKind.WEBAUTHN, userAgent, country);
        log.info("Passkey enrolled for account {} (factor {})", accountId, factor.id());
        return Optional.of(factor);
    }

    // -- Passwordless sign-in --

    /**
     * Starts a passwordless sign-in: no username and no user handle, which is what produces
     * the empty allow list and with it the browser's own account picker and cross-device flow.
     * The challenge knows no account, because nobody has said who they are yet.
     */
    public CeremonyStart startSignIn() {
        AssertionRequest request = relyingParties
                .passkey()
                .startAssertion(StartAssertionOptions.builder()
                        .userVerification(UserVerificationRequirement.REQUIRED)
                        .timeout(settings.timeoutSeconds() * 1000L)
                        .build());

        String persistJson;
        try {
            persistJson = request.toJson();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize passkey assertion request for storage", e);
        }
        String browserJson;
        try {
            browserJson = request.toCredentialsGetJson();
        } catch (Exception e) {
            log.warn("Passkey assertion request fell back to the stored shape", e);
            browserJson = persistJson;
        }
        String token = newChallengeToken();
        challengeRepository.create(
                token,
                ChallengePurpose.PASSKEY_SIGN_IN,
                null,
                persistJson,
                Instant.now().plus(CHALLENGE_TTL));
        return new CeremonyStart(token, browserJson);
    }

    /**
     * Verifies a passwordless assertion and answers with the account it belongs to. The
     * refusals are deliberately alike from the outside: an unknown credential, a bad signature
     * and a credential that may not start a sign-in all come back empty.
     */
    public Optional<Integer> finishSignIn(
            String challengeToken, String credentialJson, String userAgent, String country) {
        Optional<WebAuthnChallenge> challengeOpt = challengeRepository
                .consume(challengeToken)
                .filter(stored -> !stored.isExpired() && stored.purpose() == ChallengePurpose.PASSKEY_SIGN_IN);
        if (challengeOpt.isEmpty()) {
            log.info("Passkey sign-in failed: challenge unknown or expired");
            return Optional.empty();
        }

        AssertionResult result = verifySignInAssertion(challengeOpt.get().optionsJson(), credentialJson);
        if (result == null) return Optional.empty();

        Optional<WebAuthnCredential> credentialOpt = repository.findActiveWebAuthnByCredentialId(
                result.getCredential().getCredentialId().getBytes());
        if (credentialOpt.isEmpty()) {
            log.warn("Passkey sign-in failed: the accepted credential is not on file or disabled");
            return Optional.empty();
        }
        WebAuthnCredential credential = credentialOpt.get();
        if (!credential.signIn()) {
            log.info("Passkey sign-in refused: the credential may not start a sign-in");
            return Optional.empty();
        }

        Optional<Integer> accountId = repository.findAccountByUserHandle(
                result.getCredential().getUserHandle().getBytes());
        if (accountId.isEmpty()) {
            log.warn("Passkey sign-in failed: no account for the credential's user handle");
            return Optional.empty();
        }

        repository.updateWebAuthnSignatureCounter(credential.factorId(), result.getSignatureCount());
        repository.touchFactorUsed(credential.factorId());
        auditService.record(
                accountId.get(), null, TwoFactorEvent.PASSKEY_SIGN_IN, TwoFactorKind.WEBAUTHN, userAgent, country);
        return accountId;
    }

    /**
     * Runs the passwordless verification against stored options and insists on user
     * verification (D2: possession plus the unlock is two factors in one gesture, and without
     * the unlock the sign-in is refused rather than downgraded). Returns {@code null} on any
     * failure.
     */
    AssertionResult verifySignInAssertion(String optionsJson, String credentialJson) {
        AssertionRequest request;
        try {
            request = AssertionRequest.fromJson(optionsJson);
        } catch (Exception e) {
            log.warn("Failed to parse stored passkey assertion request", e);
            return null;
        }

        PublicKeyCredential<AuthenticatorAssertionResponse, ClientAssertionExtensionOutputs> response;
        try {
            response = PublicKeyCredential.parseAssertionResponseJson(credentialJson);
        } catch (Exception e) {
            log.warn("Invalid passkey assertion response", e);
            return null;
        }

        AssertionResult result;
        try {
            result = relyingParties
                    .passkey()
                    .finishAssertion(FinishAssertionOptions.builder()
                            .request(request)
                            .response(response)
                            .build());
        } catch (AssertionFailedException e) {
            log.warn("Passkey assertion verification failed", e);
            return null;
        }

        if (!result.isSuccess()) {
            log.info("Passkey assertion was not accepted");
            return null;
        }
        if (!result.isUserVerified()) {
            log.info("Passkey assertion refused: no user verification");
            return null;
        }
        return result;
    }

    public record CeremonyStart(String challengeToken, String optionsJson) {}
}
