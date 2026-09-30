/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.twofactor.service;

import dev.chojo.ember.conf.file.elements.WebAuthnSettings;
import dev.chojo.ember.feature.twofactor.entity.ChallengePurpose;
import dev.chojo.ember.feature.twofactor.entity.TwoFactorEvent;
import dev.chojo.ember.feature.twofactor.entity.TwoFactorFactor;
import dev.chojo.ember.feature.twofactor.entity.TwoFactorKind;
import dev.chojo.ember.feature.twofactor.entity.WebAuthnChallenge;
import dev.chojo.ember.feature.twofactor.repository.TwoFactorRepository;
import dev.chojo.ember.feature.twofactor.repository.WebAuthnChallengeRepository;
import dev.chojo.ember.feature.twofactor.service.WebAuthnCeremonies.CeremonyStart;
import dev.chojo.ember.feature.twofactor.service.WebAuthnCeremonies.VerifiedAssertion;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * Runs the WebAuthn ceremonies for a security key used as a second factor: enrolling one, and
 * asserting it after a password. The ceremonies themselves are the shared
 * {@link WebAuthnCeremonies} in the {@link CredentialRole#SECOND_FACTOR} role; this service
 * decides the challenge kinds and what a success writes.
 */
@Singleton
public class WebAuthnService {
    private static final Logger log = LoggerFactory.getLogger(WebAuthnService.class);

    private final TwoFactorRepository repository;
    private final TwoFactorAuditService auditService;
    private final WebAuthnCeremonies ceremonies;

    @Inject
    public WebAuthnService(
            RelyingParties relyingParties,
            TwoFactorRepository repository,
            TwoFactorAuditService auditService,
            WebAuthnChallengeRepository challengeRepository,
            WebAuthnSettings settings) {
        this.repository = repository;
        this.auditService = auditService;
        this.ceremonies = new WebAuthnCeremonies(relyingParties, repository, challengeRepository, settings);
    }

    public CeremonyStart startRegistration(int accountId, String email, String displayName) {
        return ceremonies.startRegistration(
                accountId, email, displayName, CredentialRole.SECOND_FACTOR, ChallengePurpose.REGISTRATION);
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
        Optional<WebAuthnChallenge> challenge =
                ceremonies.consumeChallenge(challengeToken, ChallengePurpose.REGISTRATION, accountId);
        if (challenge.isEmpty()) {
            log.info("WebAuthn registration failed for account {}: challenge unknown or expired", accountId);
            return Optional.empty();
        }

        Optional<TwoFactorFactor> factor = ceremonies.finishRegistration(
                accountId, challenge.get().optionsJson(), credentialJson, label, CredentialRole.SECOND_FACTOR);
        factor.ifPresent(created -> {
            auditService.record(accountId, null, TwoFactorEvent.ENROLLED, TwoFactorKind.WEBAUTHN, userAgent, country);
            log.info("WebAuthn credential enrolled for account {} (factor {})", accountId, created.id());
        });
        return factor;
    }

    public CeremonyStart startAssertion(int accountId) {
        return ceremonies.startAssertion(
                CredentialRole.SECOND_FACTOR, accountId, ChallengePurpose.SECOND_FACTOR_ASSERTION);
    }

    /**
     * Verifies a second-factor assertion for the account. Only a credential flagged as a second
     * factor passes, which the role checks on the verified result itself: splitting the allow
     * list alone is not enough, because it comes back empty for an account whose only
     * credentials are passkeys.
     */
    public boolean finishAssertion(int accountId, String challengeToken, String credentialJson) {
        Optional<WebAuthnChallenge> challenge =
                ceremonies.consumeChallenge(challengeToken, ChallengePurpose.SECOND_FACTOR_ASSERTION, accountId);
        if (challenge.isEmpty()) {
            log.info("WebAuthn assertion failed for account {}: challenge unknown or expired", accountId);
            return false;
        }

        Optional<VerifiedAssertion> verified = ceremonies.finishAssertion(
                CredentialRole.SECOND_FACTOR, challenge.get().optionsJson(), credentialJson);
        if (verified.isEmpty()) return false;

        int factorId = verified.get().credential().factorId();
        repository.updateWebAuthnSignatureCounter(
                factorId, verified.get().result().getSignatureCount());
        repository.touchFactorUsed(factorId);
        return true;
    }
}
