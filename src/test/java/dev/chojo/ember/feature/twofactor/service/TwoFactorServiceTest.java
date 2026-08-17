/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.twofactor.service;

import dev.chojo.ember.conf.file.elements.Demo;
import dev.chojo.ember.conf.file.elements.TwoFactorSettings;
import dev.chojo.ember.feature.mail.service.EmailService;
import dev.chojo.ember.feature.mail.service.MailLocaleService;
import dev.chojo.ember.feature.system.repository.ApplicationSettingRepository;
import dev.chojo.ember.feature.twofactor.entity.TwoFactorEvent;
import dev.chojo.ember.feature.twofactor.entity.TwoFactorKind;
import dev.chojo.ember.repository.RepositoryTestBase;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.code.HashingAlgorithm;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class TwoFactorServiceTest extends RepositoryTestBase {

    private static TwoFactorService service;

    @BeforeAll
    static void initService() throws Exception {
        var settings = new TwoFactorSettings();
        setField(settings, "enabled", true);
        setField(settings, "secretKey", validKey());
        var demo = new Demo();
        TotpService totpService = new TotpService(settings, demo);
        BackupCodeService backupCodeService = new BackupCodeService(settings);
        var auditService = new TwoFactorAuditService(twoFactorRepo);
        var emailService = mock(EmailService.class);
        service = new TwoFactorService(
                twoFactorRepo,
                totpService,
                backupCodeService,
                auditService,
                accountRepo,
                new MailLocaleService(accountRepo, new ApplicationSettingRepository()),
                emailService);
    }

    private int newAccount() {
        return accountRepo
                .create("tfs-" + UUID.randomUUID() + "@test.com", "TF", "Svc", true)
                .id();
    }

    private static String validKey() {
        byte[] key = new byte[32];
        for (int i = 0; i < key.length; i++) key[i] = (byte) (i * 7);
        return Base64.getEncoder().encodeToString(key);
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }

    @Test
    void enrollmentFlow() {
        int accountId = newAccount();
        assertFalse(service.isEnrolled(accountId));

        var enrollment = service.beginTotpEnrollment(accountId, "user@test.com");
        assertNotNull(enrollment.secret());
        assertNotNull(enrollment.otpauthUri());
        assertNotNull(enrollment.qrPng());
        assertEquals(10, enrollment.recoveryCodes().size());

        // Wrong code rejected, factor not yet created
        assertFalse(service.confirmTotpEnrollment(
                accountId, enrollment.secret(), "000000", enrollment.recoveryCodes(), "ua", "DE"));
        assertFalse(service.isEnrolled(accountId));

        // Generate a real code via the service's own algorithm and confirm
        String code = generateCurrentTotp(enrollment.secret());
        assertTrue(service.confirmTotpEnrollment(
                accountId, enrollment.secret(), code, enrollment.recoveryCodes(), "ua", "DE"));
        assertTrue(service.isEnrolled(accountId));
        assertEquals(10, service.countUnusedBackupCodes(accountId));
        assertEquals(2, service.getActiveFactors(accountId).size()); // TOTP + BACKUP_CODES
    }

    @Test
    void totpCodeCannotBeReplayedWithinWindow() {
        int accountId = newAccount();
        var enrollment = service.beginTotpEnrollment(accountId, "replay@test.com");
        String enrollCode = generateCurrentTotp(enrollment.secret());
        assertTrue(service.confirmTotpEnrollment(
                accountId, enrollment.secret(), enrollCode, enrollment.recoveryCodes(), "ua", null));

        String loginCode = generateCurrentTotp(enrollment.secret());
        assertTrue(service.verifyTotp(accountId, loginCode), "first use of a fresh code should succeed");
        assertFalse(
                service.verifyTotp(accountId, loginCode),
                "the same code must be rejected as a replay within its window");
    }

    @Test
    void totpVerifyAndBackupCode() {
        int accountId = newAccount();
        var enrollment = service.beginTotpEnrollment(accountId, "verify@test.com");
        String firstCode = generateCurrentTotp(enrollment.secret());
        assertTrue(service.confirmTotpEnrollment(
                accountId, enrollment.secret(), firstCode, enrollment.recoveryCodes(), "ua", null));

        // verifyTotp with a valid code succeeds
        assertTrue(service.verifyTotp(accountId, generateCurrentTotp(enrollment.secret())));
        // wrong code fails
        assertFalse(service.verifyTotp(accountId, "000000"));

        // verifyTotp on account without TOTP returns false
        assertFalse(service.verifyTotp(newAccount(), "123456"));

        // Backup code verify: a valid code passes once, then fails
        String backup = enrollment.recoveryCodes().getFirst();
        var result = service.verifyBackupCode(accountId, backup, "203.0.113.1");
        assertTrue(result.valid());
        assertEquals(9, result.remainingCodes());
        var second = service.verifyBackupCode(accountId, backup, "203.0.113.1");
        assertFalse(second.valid());

        // Backup code verify on an account without any backup codes
        var none = service.verifyBackupCode(newAccount(), "ABCD-1234-EFGH", "203.0.113.1");
        assertFalse(none.valid());
        assertEquals(0, none.remainingCodes());

        // regenerateBackupCodes wipes the old set and creates fresh ones
        var fresh = service.regenerateBackupCodes(accountId, "ua", "DE");
        assertEquals(10, fresh.size());
        assertEquals(10, service.countUnusedBackupCodes(accountId));
    }

    @Test
    void markSessionVerifiedTouchesRow() {
        int accountId = newAccount();
        accountRepo.createSession(accountId, "tfs-bearer", Instant.now().plusSeconds(60), "ua", null);
        var session = accountRepo.findSession("tfs-bearer").orElseThrow();
        service.markSessionTwoFactorVerified(session.id());
        assertNotNull(accountRepo.findSession("tfs-bearer").orElseThrow().twoFactorVerifiedAt());
    }

    @Test
    void removeFactorAndRename() {
        int accountId = newAccount();
        var enrollment = service.beginTotpEnrollment(accountId, "rm@test.com");
        String code = generateCurrentTotp(enrollment.secret());
        service.confirmTotpEnrollment(accountId, enrollment.secret(), code, enrollment.recoveryCodes(), "ua", null);
        int totpFactorId = service.getActiveFactors(accountId).stream()
                .filter(f -> f.kind() == TwoFactorKind.TOTP)
                .findFirst()
                .orElseThrow()
                .id();

        assertTrue(service.renameFactor(accountId, totpFactorId, "MyAuth"));
        assertFalse(service.renameFactor(accountId, totpFactorId, ""), "blank label rejected");
        assertFalse(service.renameFactor(accountId, 99_999, "X"), "missing factor rejected");

        // removeFactor for TOTP also wipes backup codes (last primary factor)
        assertTrue(service.removeFactor(accountId, totpFactorId, "ua", null));
        assertFalse(service.isEnrolled(accountId));
        assertFalse(service.removeFactor(accountId, totpFactorId, "ua", null), "already removed");
    }

    @Test
    void removeTotpFactorHelper() {
        int accountId = newAccount();
        var enrollment = service.beginTotpEnrollment(accountId, "rm2@test.com");
        service.confirmTotpEnrollment(
                accountId,
                enrollment.secret(),
                generateCurrentTotp(enrollment.secret()),
                enrollment.recoveryCodes(),
                "ua",
                null);
        twoFactorRepo.createTrustedDevice(
                accountId, "rm-trust-hash", "ua", Instant.now().plusSeconds(3600));
        assertEquals(1, twoFactorRepo.findActiveTrustedDevices(accountId).size());
        assertTrue(service.removeTotpFactor(accountId, "ua", null));
        assertEquals(
                0,
                twoFactorRepo.findActiveTrustedDevices(accountId).size(),
                "removing a factor must revoke trusted devices that bypass 2FA");
        assertFalse(service.removeTotpFactor(accountId, "ua", null));
    }

    @Test
    void issueInitialBackupCodesIfMissing() {
        int accountId = newAccount();
        // First call seeds a fresh set of 10 codes
        var initial = service.issueInitialBackupCodesIfMissing(accountId, "ua", null);
        assertEquals(10, initial.size());
        // Second call is a no-op because the factor already exists
        assertTrue(
                service.issueInitialBackupCodesIfMissing(accountId, "ua", null).isEmpty());
    }

    @Test
    void resetAccount2faClearsEverything() {
        int accountId = newAccount();
        var enrollment = service.beginTotpEnrollment(accountId, "reset@test.com");
        service.confirmTotpEnrollment(
                accountId,
                enrollment.secret(),
                generateCurrentTotp(enrollment.secret()),
                enrollment.recoveryCodes(),
                "ua",
                null);
        accountRepo.createSession(accountId, "reset-bearer", Instant.now().plusSeconds(60), "ua", null);
        twoFactorRepo.createTrustedDevice(
                accountId, "reset-trust-hash", "ua", Instant.now().plusSeconds(3600));

        assertTrue(service.resetAccount2FA(accountId, null, "ua", "DE"));
        assertFalse(service.isEnrolled(accountId));
        assertTrue(accountRepo.findSession("reset-bearer").isEmpty());
        assertEquals(0, twoFactorRepo.findActiveTrustedDevices(accountId).size());
        // Audit row exists
        assertTrue(twoFactorRepo.findAuditLog(accountId, 5, 0).stream()
                .anyMatch(e -> e.event() == TwoFactorEvent.ADMIN_RESET));

        assertFalse(service.resetAccount2FA(999_999, null, "ua", null), "unknown account is a no-op");
    }

    /**
     * Generates a TOTP code for the given Base32 secret using the {@link TotpService}'s own
     * verifier configuration. We need this so {@code confirmTotpEnrollment} sees a code
     * that the verifier accepts in the same thread.
     */
    private String generateCurrentTotp(String secret) {
        try {
            var algorithm = HashingAlgorithm.SHA1;
            var codeGenerator = new DefaultCodeGenerator(algorithm, 6);
            long timeBucket = Instant.now().getEpochSecond() / 30;
            return codeGenerator.generate(secret, timeBucket);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
