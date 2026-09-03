/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.passkey.service;

import dev.chojo.ember.conf.file.elements.PasskeySettings;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.account.entity.AccountCredential;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.passkey.entity.PasskeyListEntry;
import dev.chojo.ember.feature.passkey.repository.PasskeyRepository;
import dev.chojo.ember.feature.twofactor.entity.TwoFactorEvent;
import dev.chojo.ember.feature.twofactor.entity.TwoFactorKind;
import dev.chojo.ember.feature.twofactor.repository.TwoFactorRepository;
import dev.chojo.ember.feature.twofactor.service.TwoFactorAuditService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * What the member's own security screen does with passkeys: the list, removal with its safety
 * valve, the two switches, and the one-time offer. The ceremonies live in
 * {@link PasskeyService}; this class owns the decisions around them.
 */
@Singleton
public class PasskeyAccountService {
    private static final Logger log = LoggerFactory.getLogger(PasskeyAccountService.class);

    /** How long a "later" answer keeps the offer away. */
    private static final Duration OFFER_SNOOZE = Duration.ofDays(30);

    private final PasskeyRepository passkeyRepository;
    private final TwoFactorRepository twoFactorRepository;
    private final AccountRepository accountRepository;
    private final TwoFactorAuditService auditService;
    private final PasskeyModeService modeService;

    @Inject
    public PasskeyAccountService(
            PasskeyRepository passkeyRepository,
            TwoFactorRepository twoFactorRepository,
            AccountRepository accountRepository,
            TwoFactorAuditService auditService,
            PasskeyModeService modeService) {
        this.passkeyRepository = passkeyRepository;
        this.twoFactorRepository = twoFactorRepository;
        this.accountRepository = accountRepository;
        this.auditService = auditService;
        this.modeService = modeService;
    }

    public List<PasskeyListEntry> list(int accountId) {
        return passkeyRepository.listForAccount(accountId);
    }

    public boolean rename(int accountId, int factorId, String label) {
        if (passkeyRepository.findForAccount(accountId, factorId).isEmpty()) return false;
        if (label == null || label.isBlank() || label.length() > 64) return false;
        return twoFactorRepository.renameFactor(factorId, accountId, label);
    }

    /**
     * Removes a passkey, with the two rules that keep removal from locking anybody out.
     * Removing the last passkey while password sign-in is off switches it back on, visibly:
     * the valve must not be silent. Where the account holds no password at all there is
     * nothing to switch on, so the removal is refused instead; a valve that opens onto
     * nothing is worse than a locked door, because it looks like it worked.
     */
    public RemovalOutcome remove(int accountId, int factorId, String userAgent, String country) {
        Optional<PasskeyListEntry> entry = passkeyRepository.findForAccount(accountId, factorId);
        if (entry.isEmpty()) return RemovalOutcome.NOT_FOUND;

        boolean last = passkeyRepository.countActiveForAccount(accountId) == 1;
        Optional<AccountCredential> credential = accountRepository.findCredential(accountId);
        if (last && credential.isEmpty()) {
            log.info("Refused removing the last passkey of account {}: no password to fall back on", accountId);
            return RemovalOutcome.REFUSED_NO_PASSWORD;
        }

        twoFactorRepository.disableFactor(factorId);
        auditService.record(accountId, null, TwoFactorEvent.REMOVED, TwoFactorKind.WEBAUTHN, userAgent, country);
        log.info("Removed passkey {} for account {}", factorId, accountId);

        if (last && credential.get().passwordLoginDisabledAt() != null) {
            accountRepository.setPasswordLoginDisabled(accountId, false);
            auditService.record(accountId, null, TwoFactorEvent.PASSWORD_LOGIN_ENABLED, null, userAgent, country);
            log.info("Password sign-in switched back on for account {}: the last passkey is gone", accountId);
            return RemovalOutcome.REMOVED_PASSWORD_REENABLED;
        }
        return RemovalOutcome.REMOVED;
    }

    /**
     * Switches password sign-in off or back on. Switching off is guarded server side by
     * everything D6 and the review demand: the instance mode, a reachable address for the way
     * back, and at least one passkey that has completed a sign-in ceremony.
     */
    public SwitchOutcome setPasswordLogin(int accountId, boolean enabled, String userAgent, String country) {
        Optional<AccountCredential> credential = accountRepository.findCredential(accountId);
        if (credential.isEmpty()) return SwitchOutcome.NO_PASSWORD;

        if (enabled) {
            if (credential.get().passwordLoginEnabled()) return SwitchOutcome.OK;
            accountRepository.setPasswordLoginDisabled(accountId, false);
            auditService.record(accountId, null, TwoFactorEvent.PASSWORD_LOGIN_ENABLED, null, userAgent, country);
            return SwitchOutcome.OK;
        }

        if (!modeService.effectiveMode().atLeast(PasskeySettings.Mode.PREFERRED)) {
            return SwitchOutcome.MODE_FORBIDS;
        }
        boolean reachable =
                accountRepository.findById(accountId).map(Account::hasRealEmail).orElse(false);
        if (!reachable) {
            // The way back in for a member without a passkey is a mail. An address that cannot
            // receive one is no way back, so the switch is never offered to that account.
            return SwitchOutcome.NO_REACHABLE_ADDRESS;
        }
        if (!passkeyRepository.hasTriedSignInPasskey(accountId)) {
            return SwitchOutcome.NO_TRIED_PASSKEY;
        }
        if (!credential.get().passwordLoginEnabled()) return SwitchOutcome.OK;
        accountRepository.setPasswordLoginDisabled(accountId, true);
        auditService.record(accountId, null, TwoFactorEvent.PASSWORD_LOGIN_DISABLED, null, userAgent, country);
        log.info("Password sign-in switched off for account {}", accountId);
        return SwitchOutcome.OK;
    }

    /**
     * Whether the switch that turns password sign-in off may be offered to this account. The
     * same conditions the switch itself enforces, so the screen never shows a control the
     * server would refuse.
     */
    public boolean mayDisablePasswordLogin(int accountId) {
        if (!modeService.effectiveMode().atLeast(PasskeySettings.Mode.PREFERRED)) return false;
        if (accountRepository.findCredential(accountId).isEmpty()) return false;
        boolean reachable =
                accountRepository.findById(accountId).map(Account::hasRealEmail).orElse(false);
        return reachable && passkeyRepository.hasTriedSignInPasskey(accountId);
    }

    /**
     * The member's other switch (D3): asking for the passkey after the password as well. Flips
     * the second-factor role on the account's sign-in passkeys, which is exactly what makes
     * {@code isEnrolled} start or stop counting them.
     */
    public boolean setAskWithPassword(int accountId, boolean enabled) {
        return passkeyRepository.setSecondFactorForSignInPasskeys(accountId, enabled);
    }

    /**
     * Whether the offer screen should appear for this account after a sign-in. The browser's
     * own capability probe is the caller's half of the decision; this is the account's half.
     */
    public boolean shouldOffer(int accountId) {
        if (!modeService.effectiveMode().atLeast(PasskeySettings.Mode.ENCOURAGED)) return false;
        boolean reachable =
                accountRepository.findById(accountId).map(Account::hasRealEmail).orElse(false);
        if (!reachable) return false;
        if (passkeyRepository.countActiveForAccount(accountId) > 0) return false;
        return passkeyRepository
                .findOfferAnswer(accountId)
                .map(answer -> !answer.declined()
                        && answer.answeredAt().isBefore(Instant.now().minus(OFFER_SNOOZE)))
                .orElse(true);
    }

    public void answerOffer(int accountId, boolean declined) {
        passkeyRepository.answerOffer(accountId, declined);
    }

    public enum RemovalOutcome {
        NOT_FOUND,
        REMOVED,
        REMOVED_PASSWORD_REENABLED,
        REFUSED_NO_PASSWORD
    }

    public enum SwitchOutcome {
        OK,
        MODE_FORBIDS,
        NO_REACHABLE_ADDRESS,
        NO_TRIED_PASSKEY,
        NO_PASSWORD
    }
}
