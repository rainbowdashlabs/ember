/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.twofactor.service;

import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.mail.service.EmailService;
import dev.chojo.ember.feature.twofactor.entity.BackupCode;
import dev.chojo.ember.feature.twofactor.entity.TwoFactorEvent;
import dev.chojo.ember.feature.twofactor.entity.TwoFactorFactor;
import dev.chojo.ember.feature.twofactor.entity.TwoFactorKind;
import dev.chojo.ember.feature.twofactor.repository.TwoFactorRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;

@Singleton
public class TwoFactorService {
    private static final Logger log = LoggerFactory.getLogger(TwoFactorService.class);

    private final TwoFactorRepository repository;
    private final TotpService totpService;
    private final BackupCodeService backupCodeService;
    private final TwoFactorAuditService auditService;
    private final AccountRepository accountRepository;
    private final EmailService emailService;

    @Inject
    public TwoFactorService(
            TwoFactorRepository repository,
            TotpService totpService,
            BackupCodeService backupCodeService,
            TwoFactorAuditService auditService,
            AccountRepository accountRepository,
            EmailService emailService) {
        this.repository = repository;
        this.totpService = totpService;
        this.backupCodeService = backupCodeService;
        this.auditService = auditService;
        this.accountRepository = accountRepository;
        this.emailService = emailService;
    }

    public boolean isEnrolled(int accountId) {
        return repository.isEnrolled(accountId);
    }

    /**
     * Admin-initiated wipe of a target account's 2FA state. Disables every factor row,
     * marks every backup code used, revokes every trusted device and active session, sends
     * a notification email to the target, and records an {@link TwoFactorEvent#ADMIN_RESET}
     * audit row attributed to {@code actorAccountId}.
     *
     * <p>Returns {@code false} when the target account does not exist; callers should treat
     * that as a 404. Reset never fails partially — the audit row is the source of truth even
     * if the email enqueue throws.
     */
    public boolean resetAccount2FA(int targetAccountId, Integer actorAccountId, String userAgent, String country) {
        var target = accountRepository.findById(targetAccountId);
        if (target.isEmpty()) return false;

        repository.disableAllFactors(targetAccountId);
        repository.markAllBackupCodesUsed(targetAccountId);
        repository.revokeAllTrustedDevices(targetAccountId);
        accountRepository.deleteSessionsByAccount(targetAccountId);

        auditService.record(targetAccountId, actorAccountId, TwoFactorEvent.ADMIN_RESET, null, userAgent, country);

        Account account = target.get();
        String actorLabel = actorAccountId == null
                ? null
                : accountRepository.findById(actorAccountId).map(Account::email).orElse(null);
        String displayName = (account.firstName() + " " + account.lastName()).trim();
        try {
            emailService.sendTwoFactorResetNotice(
                    account.email(),
                    displayName.isBlank() ? account.email() : displayName,
                    actorLabel,
                    Instant.now(),
                    accountRepository.findMailLocale(account.id()));
        } catch (Exception e) {
            log.warn("Failed to send 2FA reset notification to account {}", targetAccountId, e);
        }
        log.info("2FA reset for account {} by actor {}", targetAccountId, actorAccountId);
        return true;
    }

    public List<TwoFactorFactor> getActiveFactors(int accountId) {
        return repository.findActiveFactors(accountId);
    }

    public int countUnusedBackupCodes(int accountId) {
        var factor = repository.findActiveFactor(accountId, TwoFactorKind.BACKUP_CODES);
        return factor.map(f -> repository.countUnusedBackupCodes(f.id())).orElse(0);
    }

    // -- TOTP enrollment --

    public TotpEnrollment beginTotpEnrollment(int accountId, String email) {
        String secret = totpService.generateSecret();
        String uri = totpService.buildOtpauthUri(secret, email);
        byte[] qr = totpService.generateQrPng(uri, 256);
        List<String> codes = backupCodeService.generateCodes();
        return new TotpEnrollment(secret, uri, qr, codes);
    }

    public boolean confirmTotpEnrollment(
            int accountId, String secret, String code, List<String> recoveryCodes, String userAgent, String country) {
        if (!totpService.verifyCode(secret, code)) {
            return false;
        }

        byte[] encrypted = totpService.encryptSecret(secret);
        var config = totpService.config();

        var factor = repository.createFactor(accountId, TwoFactorKind.TOTP, "Authenticator");
        repository.createTotp(
                factor.id(),
                encrypted,
                (short) 1,
                (short) config.digits(),
                (short) config.periodSeconds(),
                config.algorithm());

        createBackupCodeFactor(accountId, recoveryCodes);

        auditService.record(accountId, null, TwoFactorEvent.ENROLLED, TwoFactorKind.TOTP, userAgent, country);
        log.info("TOTP enrolled for account {}", accountId);
        return true;
    }

    /**
     * Removes any factor (TOTP or WebAuthn) owned by the account, identified by row id.
     * If this leaves the account with no non-backup factors, the backup-code factor is also
     * disabled so the user is fully unenrolled.
     */
    public boolean removeFactor(int accountId, int factorId, String userAgent, String country) {
        var factors = repository.findActiveFactors(accountId);
        var target = factors.stream().filter(f -> f.id() == factorId).findFirst();
        if (target.isEmpty()) return false;
        if (target.get().kind() == TwoFactorKind.BACKUP_CODES) return false;

        repository.disableFactor(factorId);

        long remainingPrimary = factors.stream()
                .filter(f -> f.id() != factorId && f.kind() != TwoFactorKind.BACKUP_CODES)
                .count();
        if (remainingPrimary == 0) {
            repository
                    .findActiveFactor(accountId, TwoFactorKind.BACKUP_CODES)
                    .ifPresent(f -> repository.disableFactor(f.id()));
        }
        auditService.record(
                accountId, null, TwoFactorEvent.REMOVED, target.get().kind(), userAgent, country);
        log.info(
                "Removed 2FA factor {} ({}) for account {}",
                factorId,
                target.get().kind(),
                accountId);
        return true;
    }

    public boolean renameFactor(int accountId, int factorId, String label) {
        if (label == null || label.isBlank() || label.length() > 64) return false;
        return repository.renameFactor(factorId, accountId, label);
    }

    public boolean removeTotpFactor(int accountId, String userAgent, String country) {
        var factor = repository.findActiveFactor(accountId, TwoFactorKind.TOTP);
        if (factor.isEmpty()) return false;

        repository.disableFactor(factor.get().id());

        var backupFactor = repository.findActiveFactor(accountId, TwoFactorKind.BACKUP_CODES);
        backupFactor.ifPresent(f -> repository.disableFactor(f.id()));

        auditService.record(accountId, null, TwoFactorEvent.REMOVED, TwoFactorKind.TOTP, userAgent, country);
        log.info("TOTP removed for account {}", accountId);
        return true;
    }

    public List<String> regenerateBackupCodes(int accountId, String userAgent, String country) {
        var existing = repository.findActiveFactor(accountId, TwoFactorKind.BACKUP_CODES);
        existing.ifPresent(f -> repository.disableFactor(f.id()));

        List<String> codes = backupCodeService.generateCodes();
        createBackupCodeFactor(accountId, codes);

        auditService.record(
                accountId,
                null,
                TwoFactorEvent.BACKUP_CODE_REGENERATED,
                TwoFactorKind.BACKUP_CODES,
                userAgent,
                country);
        return codes;
    }

    // -- Backup codes --

    /**
     * Generates a fresh set of backup codes for the account when none are active yet, returns
     * them in plaintext (caller shows them once). Returns empty when the account already has a
     * backup-code factor.
     */
    public List<String> issueInitialBackupCodesIfMissing(int accountId, String userAgent, String country) {
        if (repository.findActiveFactor(accountId, TwoFactorKind.BACKUP_CODES).isPresent()) {
            return List.of();
        }
        List<String> codes = backupCodeService.generateCodes();
        createBackupCodeFactor(accountId, codes);
        auditService.record(
                accountId,
                null,
                TwoFactorEvent.BACKUP_CODE_REGENERATED,
                TwoFactorKind.BACKUP_CODES,
                userAgent,
                country);
        return codes;
    }

    public boolean verifyTotp(int accountId, String code) {
        var factor = repository.findActiveFactor(accountId, TwoFactorKind.TOTP);
        if (factor.isEmpty()) return false;

        var totp = repository.findTotp(factor.get().id());
        if (totp.isEmpty()) return false;

        String secret = totpService.decryptSecret(totp.get().secretEncrypted());
        if (!totpService.verifyCode(secret, code)) return false;

        repository.touchFactorUsed(factor.get().id());
        return true;
    }

    public VerifyBackupCodeResult verifyBackupCode(int accountId, String code, String ip) {
        var factor = repository.findActiveFactor(accountId, TwoFactorKind.BACKUP_CODES);
        if (factor.isEmpty()) return new VerifyBackupCodeResult(false, 0);

        List<BackupCode> unused = repository.findUnusedBackupCodes(factor.get().id());
        for (BackupCode bc : unused) {
            if (backupCodeService.verifyCode(code, bc.codeHash())) {
                repository.markBackupCodeUsed(bc.id(), ip);
                repository.touchFactorUsed(factor.get().id());
                int remaining = unused.size() - 1;
                return new VerifyBackupCodeResult(true, remaining);
            }
        }
        return new VerifyBackupCodeResult(false, unused.size());
    }

    // -- Verification --

    public void markSessionTwoFactorVerified(int sessionId) {
        repository.setTwoFactorVerified(sessionId);
    }

    private void createBackupCodeFactor(int accountId, List<String> plaintextCodes) {
        var factor = repository.createFactor(accountId, TwoFactorKind.BACKUP_CODES, "Backup Codes");
        for (String code : plaintextCodes) {
            repository.createBackupCode(factor.id(), backupCodeService.hashCode(code));
        }
    }

    public record TotpEnrollment(String secret, String otpauthUri, byte[] qrPng, List<String> recoveryCodes) {}

    public record VerifyBackupCodeResult(boolean valid, int remainingCodes) {}
}
