/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.passkey.service;

import dev.chojo.ember.conf.file.elements.WebAuthnSettings;
import dev.chojo.ember.feature.twofactor.entity.ChallengePurpose;
import dev.chojo.ember.feature.twofactor.entity.TwoFactorEvent;
import dev.chojo.ember.feature.twofactor.entity.TwoFactorFactor;
import dev.chojo.ember.feature.twofactor.entity.TwoFactorKind;
import dev.chojo.ember.feature.twofactor.entity.WebAuthnChallenge;
import dev.chojo.ember.feature.twofactor.repository.TwoFactorRepository;
import dev.chojo.ember.feature.twofactor.repository.WebAuthnChallengeRepository;
import dev.chojo.ember.feature.twofactor.service.CredentialRole;
import dev.chojo.ember.feature.twofactor.service.RelyingParties;
import dev.chojo.ember.feature.twofactor.service.TwoFactorAuditService;
import dev.chojo.ember.feature.twofactor.service.WebAuthnCeremonies;
import dev.chojo.ember.feature.twofactor.service.WebAuthnCeremonies.CeremonyStart;
import dev.chojo.ember.feature.twofactor.service.WebAuthnCeremonies.VerifiedAssertion;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * Runs the passkey ceremonies: creating a credential that may start a sign-in, and the
 * passwordless sign-in itself. The ceremonies are the shared {@link WebAuthnCeremonies} in the
 * {@link CredentialRole#PASSKEY} role, which requires a resident key and user verification; this
 * service owns the challenge kinds and the outcomes, because a passkey assertion identifies the
 * account instead of confirming one.
 */
@Singleton
public class PasskeyService {
    private static final Logger log = LoggerFactory.getLogger(PasskeyService.class);

    private final TwoFactorRepository repository;
    private final TwoFactorAuditService auditService;
    private final WebAuthnCeremonies ceremonies;

    @Inject
    public PasskeyService(
            RelyingParties relyingParties,
            TwoFactorRepository repository,
            TwoFactorAuditService auditService,
            WebAuthnChallengeRepository challengeRepository,
            WebAuthnSettings settings) {
        this.repository = repository;
        this.auditService = auditService;
        this.ceremonies = new WebAuthnCeremonies(relyingParties, repository, challengeRepository, settings);
    }

    /**
     * Starts a passkey creation for the account: resident key and user verification required,
     * the exclude list covering every credential the account has, and no hints, so the
     * browser's own cross-device path stays available.
     */
    public CeremonyStart startCreation(int accountId, String email, String displayName) {
        return ceremonies.startRegistration(
                accountId, email, displayName, CredentialRole.PASSKEY, ChallengePurpose.REGISTRATION);
    }

    /**
     * Starts a passkey creation opened by a device-enrolment token rather than a session: the
     * same options as {@link #startCreation}, on the enrolment's own challenge kind.
     */
    public CeremonyStart startDeviceEnrollment(int accountId, String email, String displayName) {
        return ceremonies.startRegistration(
                accountId, email, displayName, CredentialRole.PASSKEY, ChallengePurpose.DEVICE_ENROLLMENT);
    }

    /**
     * Completes a device enrolment: the creation ceremony verified as always, recorded as the
     * enrolment it is.
     */
    public Optional<TwoFactorFactor> finishDeviceEnrollment(
            int accountId,
            String challengeToken,
            String credentialJson,
            @Nullable String userAgent,
            @Nullable String country) {
        Optional<WebAuthnChallenge> challenge =
                ceremonies.consumeChallenge(challengeToken, ChallengePurpose.DEVICE_ENROLLMENT, accountId);
        if (challenge.isEmpty()) {
            log.info("Device enrolment failed for account {}: challenge unknown or expired", accountId);
            return Optional.empty();
        }
        return finishCreationCeremony(
                accountId,
                challenge.get(),
                credentialJson,
                null,
                userAgent,
                country,
                TwoFactorEvent.PASSKEY_ENROLLED_VIA_DEVICE_CODE);
    }

    /**
     * Completes an enrolment opened by a mail link, a QR code or the console: the same ceremony
     * as the device enrolment, recorded as the ordinary enrolment it is.
     */
    public Optional<TwoFactorFactor> finishTokenEnrollment(
            int accountId, String challengeToken, String credentialJson, @Nullable String country) {
        Optional<WebAuthnChallenge> challenge =
                ceremonies.consumeChallenge(challengeToken, ChallengePurpose.DEVICE_ENROLLMENT, accountId);
        if (challenge.isEmpty()) {
            log.info("Token enrolment failed for account {}: challenge unknown or expired", accountId);
            return Optional.empty();
        }
        return finishCreationCeremony(
                accountId, challenge.get(), credentialJson, null, null, country, TwoFactorEvent.ENROLLED);
    }

    /**
     * Completes a passkey creation. Refuses a credential that came back without user
     * verification, which is the honest outcome for an authenticator that cannot do it: the
     * flag is what lets the sign-in count as two factors in one gesture, so a credential
     * without it must not become a sign-in credential.
     */
    public Optional<TwoFactorFactor> finishCreation(
            int accountId,
            String challengeToken,
            String credentialJson,
            @Nullable String label,
            @Nullable String userAgent,
            @Nullable String country) {
        Optional<WebAuthnChallenge> challenge =
                ceremonies.consumeChallenge(challengeToken, ChallengePurpose.REGISTRATION, accountId);
        if (challenge.isEmpty()) {
            log.info("Passkey creation failed for account {}: challenge unknown or expired", accountId);
            return Optional.empty();
        }
        return finishCreationCeremony(
                accountId, challenge.get(), credentialJson, label, userAgent, country, TwoFactorEvent.ENROLLED);
    }

    /**
     * The shared tail of every creation door: session, device code, mail link and guardian QR
     * all verify the same way and write the same rows; only the audit event differs.
     */
    private Optional<TwoFactorFactor> finishCreationCeremony(
            int accountId,
            WebAuthnChallenge challenge,
            String credentialJson,
            @Nullable String label,
            @Nullable String userAgent,
            @Nullable String country,
            TwoFactorEvent auditEvent) {
        Optional<TwoFactorFactor> factor = ceremonies.finishRegistration(
                accountId, challenge.optionsJson(), credentialJson, label, CredentialRole.PASSKEY);
        factor.ifPresent(created -> {
            auditService.record(accountId, null, auditEvent, TwoFactorKind.WEBAUTHN, userAgent, country);
            log.info("Passkey enrolled for account {} (factor {})", accountId, created.id());
        });
        return factor;
    }

    /**
     * Starts a passwordless sign-in: no username and no user handle, which is what produces
     * the empty allow list and with it the browser's own account picker and cross-device flow.
     * The challenge knows no account, because nobody has said who they are yet.
     */
    public CeremonyStart startSignIn() {
        return ceremonies.startAssertion(CredentialRole.PASSKEY, null, ChallengePurpose.PASSKEY_SIGN_IN);
    }

    /**
     * Verifies a passwordless assertion and answers with the account it belongs to. The
     * refusals are deliberately alike from the outside: an unknown credential, a bad signature,
     * a missing user verification and a credential that may not start a sign-in all come back
     * empty.
     */
    public Optional<Integer> finishSignIn(
            String challengeToken, String credentialJson, @Nullable String userAgent, @Nullable String country) {
        Optional<WebAuthnChallenge> challenge =
                ceremonies.consumeChallenge(challengeToken, ChallengePurpose.PASSKEY_SIGN_IN);
        if (challenge.isEmpty()) {
            log.info("Passkey sign-in failed: challenge unknown or expired");
            return Optional.empty();
        }

        Optional<VerifiedAssertion> verified = ceremonies.finishAssertion(
                CredentialRole.PASSKEY, challenge.get().optionsJson(), credentialJson);
        if (verified.isEmpty()) return Optional.empty();

        Optional<Integer> accountId = repository.findAccountByUserHandle(
                verified.get().result().getCredential().getUserHandle().getBytes());
        if (accountId.isEmpty()) {
            log.warn("Passkey sign-in failed: no account for the credential's user handle");
            return Optional.empty();
        }

        int factorId = verified.get().credential().factorId();
        repository.updateWebAuthnSignatureCounter(
                factorId, verified.get().result().getSignatureCount());
        repository.touchFactorUsed(factorId);
        auditService.record(
                accountId.get(), null, TwoFactorEvent.PASSKEY_SIGN_IN, TwoFactorKind.WEBAUTHN, userAgent, country);
        return accountId;
    }

    /**
     * Starts the test drive: cryptographically the sign-in ceremony, discoverable and user
     * verification required, but on a challenge kind of its own. Borrowing the sign-in's kind
     * would make the same assertion just as valid at the sign-in finish, and the difference
     * between a trial and a sign-in would come down to which URL the browser felt like calling.
     */
    public CeremonyStart startTrial(int accountId) {
        return ceremonies.startAssertion(CredentialRole.PASSKEY, accountId, ChallengePurpose.PASSKEY_TRIAL);
    }

    /**
     * Starts a passkey step-up: the same discoverable, user-verified ceremony as the trial, on
     * a challenge kind of its own so neither is spendable at the other's finish or at the
     * sign-in's.
     */
    public CeremonyStart startStepUp(int accountId) {
        return ceremonies.startAssertion(CredentialRole.PASSKEY, accountId, ChallengePurpose.STEPUP_ASSERTION);
    }

    /**
     * Finishes a passkey step-up: the trial's verification with the trial's own-account check,
     * on the step-up's challenge kind. What the caller does with a success differs, which is
     * why the purposes differ: this one stamps the session as freshly proved.
     */
    public boolean finishStepUp(int accountId, String challengeToken, String credentialJson) {
        return finishOwnAssertion(accountId, ChallengePurpose.STEPUP_ASSERTION, challengeToken, credentialJson)
                == TrialOutcome.OK;
    }

    /**
     * Finishes the trial. Mirrors the sign-in finish exactly, user verification and the
     * sign-in flag included, and differs from it in three things: the credential must belong
     * to the session's own account, no session is minted, and no sign-in audit row is written.
     * The factor's last-used stamp is the whole outcome; it is what a later password retirement
     * reads as evidence.
     *
     * <p>A second-factor key that happens to be discoverable fails here as it would at the next
     * sign-in. A credential of another account gets its own outcome, because on a shared family
     * device the account picker shows every passkey the device holds, siblings included, and
     * picking one is a single mistap away.
     */
    public TrialOutcome finishTrial(int accountId, String challengeToken, String credentialJson) {
        return finishOwnAssertion(accountId, ChallengePurpose.PASSKEY_TRIAL, challengeToken, credentialJson);
    }

    private TrialOutcome finishOwnAssertion(
            int accountId, ChallengePurpose purpose, String challengeToken, String credentialJson) {
        Optional<WebAuthnChallenge> challenge = ceremonies.consumeChallenge(challengeToken, purpose, accountId);
        if (challenge.isEmpty()) {
            log.info("Passkey {} failed for account {}: challenge unknown or expired", purpose, accountId);
            return TrialOutcome.FAILED;
        }

        Optional<VerifiedAssertion> verified = ceremonies.finishAssertion(
                CredentialRole.PASSKEY, challenge.get().optionsJson(), credentialJson);
        if (verified.isEmpty()) return TrialOutcome.FAILED;

        Optional<Integer> owner = repository.findAccountByUserHandle(
                verified.get().result().getCredential().getUserHandle().getBytes());
        if (owner.isEmpty() || owner.get() != accountId) {
            log.info(
                    "Passkey {} refused for account {}: the credential belongs to another account", purpose, accountId);
            return TrialOutcome.FOREIGN_CREDENTIAL;
        }

        repository.touchFactorUsed(verified.get().credential().factorId());
        return TrialOutcome.OK;
    }

    /** How a trial ended. Nothing is minted either way; only the last-used stamp changes. */
    public enum TrialOutcome {
        OK,
        FOREIGN_CREDENTIAL,
        FAILED
    }
}
