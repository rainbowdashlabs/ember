/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.passkey.service;

import dev.chojo.ember.conf.Conf;
import dev.chojo.ember.conf.file.elements.PasskeySettings;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.mail.repository.EmailQueueRepository;
import dev.chojo.ember.feature.passkey.repository.PasskeyRepository;
import dev.chojo.ember.feature.twofactor.entity.TwoFactorEvent;
import dev.chojo.ember.feature.twofactor.service.RelyingParties;
import dev.chojo.ember.feature.twofactor.service.TwoFactorAuditService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Optional;

/**
 * What the operator sees and decides about passkeys: the mode, the readiness the instance can
 * check about itself, the adoption figures, and the report before the passwordless switch. The
 * two refusals live here so no screen has to remember them: passwordless needs proven mail, and
 * going below ENCOURAGED is refused while any account depends on a passkey.
 */
@Singleton
public class PasskeyAdminService {
    private static final Logger log = LoggerFactory.getLogger(PasskeyAdminService.class);

    private final Conf conf;
    private final PasskeyModeService modeService;
    private final PasskeyRepository passkeyRepository;
    private final EmailQueueRepository emailQueueRepository;
    private final RelyingParties relyingParties;
    private final AccountRepository accountRepository;
    private final TwoFactorAuditService auditService;

    @Inject
    public PasskeyAdminService(
            Conf conf,
            PasskeyModeService modeService,
            PasskeyRepository passkeyRepository,
            EmailQueueRepository emailQueueRepository,
            RelyingParties relyingParties,
            AccountRepository accountRepository,
            TwoFactorAuditService auditService) {
        this.conf = conf;
        this.modeService = modeService;
        this.passkeyRepository = passkeyRepository;
        this.emailQueueRepository = emailQueueRepository;
        this.relyingParties = relyingParties;
        this.accountRepository = accountRepository;
        this.auditService = auditService;
    }

    /**
     * Everything the mode panel shows. The readiness half can only compare the instance's own
     * configuration, never what a browser sees; the screen says so in its own words.
     */
    public ModeStatus status() {
        return new ModeStatus(
                conf.main().auth().passkeys().mode(),
                modeService.effectiveMode(),
                relyingParties.localhostFallback(),
                relyingParties.passkey().getIdentity().getId(),
                emailQueueRepository.findLastSentAt().orElse(null),
                passkeyRepository.countAccountsDependingOnPasskey(),
                passkeyRepository.adoptionFigures());
    }

    public PasskeyRepository.PasswordlessReport passwordlessReport() {
        return passkeyRepository.passwordlessReport();
    }

    public java.util.List<PasskeyRepository.ResidueEntry> residue() {
        return passkeyRepository.listResidue();
    }

    /**
     * Retires one account's password: the credential row is deleted, which is what makes "how
     * many accounts still hold a password" a count of rows rather than of opinions. Offered only
     * where a passkey has completed a sign-in ceremony; ends no sessions and revokes nothing
     * else, so it is a small step rather than a cliff.
     */
    public RetireOutcome retirePassword(int accountId, Integer actorAccountId, String userAgent, String country) {
        if (accountRepository.findCredential(accountId).isEmpty()) return RetireOutcome.NO_PASSWORD;
        if (!passkeyRepository.hasTriedSignInPasskey(accountId)) return RetireOutcome.NO_TRIED_PASSKEY;
        accountRepository.deleteCredential(accountId);
        auditService.record(accountId, actorAccountId, TwoFactorEvent.PASSWORD_RETIRED, null, userAgent, country);
        log.info("Password retired for account {} by {}", accountId, actorAccountId);
        return RetireOutcome.RETIRED;
    }

    /**
     * The bulk form, because sixty accounts one at a time is how this gets abandoned halfway.
     * Acts only on the accounts that pass the same test as the single action, and says how many
     * it passed over: an operator who ends sixty sessions on a Tuesday has a Tuesday nobody
     * forgets, which is why this one ends none at all.
     */
    public BulkRetireResult retireAllEligible(Integer actorAccountId, String userAgent, String country) {
        int total = passkeyRepository.adoptionFigures().accountsWithPassword();
        int retired = 0;
        for (int accountId : passkeyRepository.listRetireEligibleAccounts()) {
            if (retirePassword(accountId, actorAccountId, userAgent, country) == RetireOutcome.RETIRED) {
                retired++;
            }
        }
        return new BulkRetireResult(retired, total - retired);
    }

    public enum RetireOutcome {
        RETIRED,
        NO_PASSWORD,
        NO_TRIED_PASSKEY
    }

    /**
     * @param passedOver password holders left untouched because no exercised passkey stands
     *         beside their password
     */
    public record BulkRetireResult(int retired, int passedOver) {}

    /**
     * Changes the mode, with the two refusals that keep the change honest. Going back to
     * PREFERRED is free by design; going below ENCOURAGED is where a passkey-only account would
     * lose its way in, so that step is refused while any account depends on one.
     */
    public SetModeResult setMode(PasskeySettings.Mode requested) {
        if (requested == PasskeySettings.Mode.PASSWORDLESS) {
            Optional<Instant> lastMail = emailQueueRepository.findLastSentAt();
            if (lastMail.isEmpty()) {
                return SetModeResult.noMailProof();
            }
        }
        if (!requested.atLeast(PasskeySettings.Mode.ENCOURAGED)) {
            int depending = passkeyRepository.countAccountsDependingOnPasskey();
            if (depending > 0) {
                return SetModeResult.accountsDepend(depending);
            }
        }
        try {
            Field field = PasskeySettings.class.getDeclaredField("mode");
            field.setAccessible(true);
            field.set(conf.main().auth().passkeys(), requested.name());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to update the passkey mode", e);
        }
        conf.save();
        log.info("Passkey mode set to {}", requested);
        return SetModeResult.ok();
    }

    /**
     * @param lastMailSentAt when a mail last went out successfully, or {@code null} when none
     *         ever has
     * @param dependentAccounts how many accounts have no way in without a passkey
     */
    public record ModeStatus(
            PasskeySettings.Mode configured,
            PasskeySettings.Mode effective,
            boolean localhostFallback,
            String rpId,
            Instant lastMailSentAt,
            int dependentAccounts,
            PasskeyRepository.AdoptionFigures figures) {}

    public record SetModeResult(Outcome outcome, int dependentAccounts) {
        public enum Outcome {
            OK,
            NO_MAIL_PROOF,
            ACCOUNTS_DEPEND
        }

        static SetModeResult ok() {
            return new SetModeResult(Outcome.OK, 0);
        }

        static SetModeResult noMailProof() {
            return new SetModeResult(Outcome.NO_MAIL_PROOF, 0);
        }

        static SetModeResult accountsDepend(int count) {
            return new SetModeResult(Outcome.ACCOUNTS_DEPEND, count);
        }
    }
}
