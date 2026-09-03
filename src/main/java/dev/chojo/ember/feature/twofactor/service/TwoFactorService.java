/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.twofactor.service;

import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.mail.service.EmailService;
import dev.chojo.ember.feature.mail.service.MailLocaleService;
import dev.chojo.ember.feature.twofactor.entity.BackupCode;
import dev.chojo.ember.feature.twofactor.entity.StepUpProof;
import dev.chojo.ember.feature.twofactor.entity.TwoFactorEvent;
import dev.chojo.ember.feature.twofactor.entity.TwoFactorFactor;
import dev.chojo.ember.feature.twofactor.entity.TwoFactorKind;
import dev.chojo.ember.feature.twofactor.repository.TwoFactorRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

@Singleton
public class TwoFactorService {
    private static final Logger log = LoggerFactory.getLogger(TwoFactorService.class);

    private final TwoFactorRepository repository;
    private final TotpService totpService;
    private final BackupCodeService backupCodeService;
    private final TwoFactorAuditService auditService;
    private final AccountRepository accountRepository;
    private final MailLocaleService mailLocaleService;
    private final EmailService emailService;

    @Inject
    public TwoFactorService(
            TwoFactorRepository repository,
            TotpService totpService,
            BackupCodeService backupCodeService,
            TwoFactorAuditService auditService,
            AccountRepository accountRepository,
            MailLocaleService mailLocaleService,
            EmailService emailService) {
        this.repository = repository;
        this.totpService = totpService;
        this.backupCodeService = backupCodeService;
        this.auditService = auditService;
        this.accountRepository = accountRepository;
        this.mailLocaleService = mailLocaleService;
        this.emailService = emailService;
    }

    public boolean isEnrolled(int accountId) {
        return repository.isEnrolled(accountId);
    }

    /**
     * Whether the account satisfies a two-factor mandate: a second factor is enrolled, or a
     * sign-in-capable passkey is held. Deliberately not {@link #isEnrolled(int)}: a passkey
     * makes the account compliant without changing what its password does.
     */
    public boolean satisfiesTwoFactorMandate(int accountId) {
        return repository.satisfiesTwoFactorMandate(accountId);
    }

    /**
     * What this account can prove itself with right now, which is the set the step-up refusal
     * names so the dialog can offer it. The rule is D8's: any proof the account can currently
     * give - the second factor where one is enrolled, the passkey where one is held, and the
     * password only where there is no second factor. The last clause is what keeps a phished
     * password from clearing a gate an authenticator app currently holds shut.
     *
     * <p>"Password sign-in is on" is a two-part test, credential row present and not switched
     * off: an account created without a password has no row at all, and reading a missing row
     * as "not disabled, therefore on" would offer a proof the member cannot give.
     */
    public Set<StepUpProof> availableProofs(int accountId) {
        Set<StepUpProof> proofs = EnumSet.noneOf(StepUpProof.class);
        boolean secondFactor = false;
        for (TwoFactorFactor factor : repository.findActiveSecondFactorFactors(accountId)) {
            switch (factor.kind()) {
                case TOTP -> {
                    proofs.add(StepUpProof.TOTP);
                    secondFactor = true;
                }
                case WEBAUTHN -> {
                    proofs.add(StepUpProof.SECURITY_KEY);
                    secondFactor = true;
                }
                case BACKUP_CODES -> {
                    if (repository.countUnusedBackupCodes(factor.id()) > 0) {
                        proofs.add(StepUpProof.BACKUP_CODE);
                    }
                }
            }
        }
        if (repository.hasSignInPasskey(accountId)) {
            proofs.add(StepUpProof.PASSKEY);
        }
        if (!secondFactor) {
            boolean passwordOn = accountRepository
                    .findCredential(accountId)
                    .map(credential -> credential.passwordLoginDisabledAt() == null)
                    .orElse(false);
            if (passwordOn) {
                proofs.add(StepUpProof.PASSWORD);
            }
        }
        return proofs;
    }

    /**
     * Admin-initiated wipe of a target account's 2FA state. Disables every factor row,
     * marks every backup code used, revokes every trusted device and active session, sends
     * a notification email to the target, and records an {@link TwoFactorEvent#ADMIN_RESET}
     * audit row attributed to {@code actorAccountId}.
     *
     * <p>Returns {@code false} when the target account does not exist; callers should treat
     * that as a 404. Reset never fails partially - the audit row is the source of truth even
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
                    mailLocaleService.forAccount(account.id()));
        } catch (Exception e) {
            log.warn("Failed to send 2FA reset notification to account {}", targetAccountId, e);
        }
        log.info("2FA reset for account {} by actor {}", targetAccountId, actorAccountId);
        return true;
    }

    /**
     * The factors the two-factor status screen lists. Sign-in-only passkeys are filtered out:
     * they have a section and a listing of their own, and appearing here would present them as
     * something asked for after a password, which they are not.
     */
    public List<TwoFactorFactor> getActiveFactors(int accountId) {
        return repository.findActiveSecondFactorFactors(accountId);
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
     * Removes any second factor (TOTP or security key) owned by the account, identified by row
     * id. If this leaves the account with no non-backup factors, the backup-code factor is also
     * disabled so the user is fully unenrolled. A sign-in-only passkey is not reachable here:
     * it is not a second factor, and its removal has rules of its own.
     */
    public boolean removeFactor(int accountId, int factorId, String userAgent, String country) {
        var factors = repository.findActiveSecondFactorFactors(accountId);
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
        // A trusted device bypasses the 2FA challenge; removing a factor is a security event, so
        // revoke every remembered device to force fresh verification with what remains.
        repository.revokeAllTrustedDevices(accountId);
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
        boolean renamed = repository.renameFactor(factorId, accountId, label);
        if (renamed) {
            log.info("Renamed 2FA factor {} for account {}", factorId, accountId);
        } else {
            log.warn("2FA factor rename missed: factor {} for account {}", factorId, accountId);
        }
        return renamed;
    }

    public boolean removeTotpFactor(int accountId, String userAgent, String country) {
        var factor = repository.findActiveFactor(accountId, TwoFactorKind.TOTP);
        if (factor.isEmpty()) return false;

        repository.disableFactor(factor.get().id());

        var backupFactor = repository.findActiveFactor(accountId, TwoFactorKind.BACKUP_CODES);
        backupFactor.ifPresent(f -> repository.disableFactor(f.id()));

        repository.revokeAllTrustedDevices(accountId);
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
        log.info("Backup codes regenerated for account {} ({} codes)", accountId, codes.size());
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
        log.info("Initial backup codes issued for account {} ({} codes)", accountId, codes.size());
        return codes;
    }

    public boolean verifyTotp(int accountId, String code) {
        var factor = repository.findActiveFactor(accountId, TwoFactorKind.TOTP);
        if (factor.isEmpty()) {
            log.info("TOTP verification failed for account {}: no active factor", accountId);
            return false;
        }

        var totp = repository.findTotp(factor.get().id());
        if (totp.isEmpty()) {
            log.warn(
                    "TOTP verification failed for account {}: factor {} has no secret",
                    accountId,
                    factor.get().id());
            return false;
        }

        String secret = totpService.decryptSecret(totp.get().secretEncrypted());
        var step = totpService.matchStep(secret, code);
        if (step.isEmpty()) {
            log.info("TOTP verification failed for account {}: code does not match", accountId);
            return false;
        }
        if (step.getAsLong() <= totp.get().lastUsedStep()) {
            log.warn("TOTP verification failed for account {}: code was already used", accountId);
            return false;
        }

        repository.updateLastUsedStep(factor.get().id(), step.getAsLong());
        repository.touchFactorUsed(factor.get().id());
        return true;
    }

    public VerifyBackupCodeResult verifyBackupCode(int accountId, String code, String ip) {
        var factor = repository.findActiveFactor(accountId, TwoFactorKind.BACKUP_CODES);
        if (factor.isEmpty()) {
            log.info("Backup code verification failed for account {}: no active factor", accountId);
            return new VerifyBackupCodeResult(false, 0);
        }

        List<BackupCode> unused = repository.findUnusedBackupCodes(factor.get().id());
        for (BackupCode bc : unused) {
            if (backupCodeService.verifyCode(code, bc.codeHash())) {
                repository.markBackupCodeUsed(bc.id(), ip);
                repository.touchFactorUsed(factor.get().id());
                int remaining = unused.size() - 1;
                log.info("Backup code used by account {}, {} left", accountId, remaining);
                return new VerifyBackupCodeResult(true, remaining);
            }
        }
        log.info(
                "Backup code verification failed for account {}: no match among {} unused code(s)",
                accountId,
                unused.size());
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
