/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.account.service;

import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.auth.BreachCheckWorker;
import dev.chojo.ember.auth.HibpClient;
import dev.chojo.ember.auth.PasswordHasher;
import dev.chojo.ember.auth.TokenHasher;
import dev.chojo.ember.conf.file.elements.Auth;
import dev.chojo.ember.conf.file.elements.Demo;
import dev.chojo.ember.conf.file.elements.TwoFactorSettings;
import dev.chojo.ember.feature.account.entity.TokenType;
import dev.chojo.ember.feature.mail.service.EmailService;
import dev.chojo.ember.feature.twofactor.entity.TwoFactorKind;
import dev.chojo.ember.feature.twofactor.repository.TwoFactorRepository;
import dev.chojo.ember.feature.twofactor.service.TrustedDeviceService;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AuthServiceTest extends RepositoryTestBase {
    private static AuthService service;
    private static final String EMAIL = "auth-test@test.com";
    private static final String PASSWORD = "TestPassword123!";
    private static int accountId;
    private static String verifyToken;
    private static String sessionToken;

    private static TwoFactorRepository twoFactorRepoLocal;
    private static TrustedDeviceService trustedDeviceService;

    @BeforeAll
    static void setup() {
        var passwordHasher = new PasswordHasher();
        var emailService = mock(EmailService.class);
        var authConfig = new Auth();
        var demo = new Demo();
        var hibpClient = mock(HibpClient.class);
        when(hibpClient.isPwned(anyString())).thenReturn(false);
        var breachCheckWorker = mock(BreachCheckWorker.class);

        twoFactorRepoLocal = new TwoFactorRepository();
        trustedDeviceService = new TrustedDeviceService(
                twoFactorRepoLocal, TokenHasher.forTesting("test-pepper"), new TwoFactorSettings());
        service = new AuthService(
                accountRepo,
                registrationCodeRepo,
                stationMemberRepo,
                memberGroupRepo,
                passwordHasher,
                emailService,
                authConfig,
                demo,
                hibpClient,
                breachCheckWorker,
                twoFactorRepoLocal,
                trustedDeviceService);
    }

    @Test
    @Order(1)
    void registerSelfSuccess() {
        var result = service.registerSelf(EMAIL, "Auth", "Tester", PASSWORD, null);
        assertTrue(result.success());
        assertNotNull(result.account());
        assertEquals(EMAIL, result.account().email());
        accountId = result.account().id();
    }

    @Test
    @Order(2)
    void registerSelfDuplicateEmailMaskedAsSuccess() {
        var result = service.registerSelf(EMAIL, "Auth", "Tester2", PASSWORD, null);
        assertTrue(result.success(), "Duplicate email should not leak via failure response");
        assertNotNull(result.account());
        assertEquals(EMAIL, result.account().email());
        assertEquals(0, result.account().id(), "Masked-success account must not expose a real id");
    }

    @Test
    @Order(3)
    void loginWithoutVerificationAndCorrectPassword() {
        var result = service.login(EMAIL, PASSWORD, "TestAgent", "DE");
        assertFalse(result.success());
        assertEquals(
                "Email not verified", result.message(), "Unverified branch is reachable only after a password verify");
    }

    @Test
    @Order(4)
    void loginNonExistentEmail() {
        var result = service.login("nobody@test.com", PASSWORD, "agent", "DE");
        assertFalse(result.success());
        assertEquals("Invalid email or password", result.message());
    }

    @Test
    @Order(5)
    void loginWrongPassword() {
        accountRepo.setEmailVerified(accountId);

        var result = service.login(EMAIL, "WrongPassword!", "agent", "DE");
        assertFalse(result.success());
        assertEquals("Invalid email or password", result.message());
    }

    @Test
    @Order(6)
    void loginWithoutStationMembership() {
        var result = service.login(EMAIL, PASSWORD, "agent", "DE");
        assertTrue(
                result.success(),
                "An account without any station membership must still be allowed to log in to access account data");
        assertNotNull(result.token());
        service.logout(result.token());
    }

    @Test
    @Order(7)
    void loginWithLoginRole() {
        // Grant LOGIN role and try again
        var loginRole = stationMemberRepo.findPermissionByName(StationPermission.LOGIN);
        if (loginRole.isPresent()) {
            // Create a station and membership
            var station = stationRepo.create("AuthSvc Station");
            var member = stationMemberRepo.create(station.id(), accountId);
            stationMemberRepo.grantPermission(member.id(), loginRole.get().id());

            var result = service.login(EMAIL, PASSWORD, "agent", "DE");
            assertTrue(result.success());
            assertNotNull(result.token());
            sessionToken = result.token();

            // Anchor accountId in a separate station that survives the rest of the class so the
            // orphan-account sweep on station-delete (StationRepository.delete) does not destroy
            // it when the test station below gets removed.
            var keepalive = stationRepo.create("AuthSvc Keepalive");
            stationMemberRepo.create(keepalive.id(), accountId);

            stationRepo.delete(station.id());
        }
    }

    @Test
    @Order(8)
    void logout() {
        if (sessionToken != null) {
            assertTrue(service.logout(sessionToken));
        }
    }

    @Test
    @Order(9)
    void logoutInvalidToken() {
        assertFalse(service.logout("invalid-session-token-xyz"));
    }

    @Test
    @Order(12)
    void verifyEmailInvalidToken() {
        assertFalse(service.verifyEmail("nonexistent-token-xyz"));
    }

    @Test
    @Order(13)
    void verifyEmailExpiredToken() {
        // Create an expired token
        var account2 = accountRepo.create("verify-expired@test.com", "V", "User");
        accountRepo.createToken(
                account2.id(),
                "expired-token",
                TokenType.VERIFY_EMAIL,
                Instant.now().minus(1, ChronoUnit.HOURS));
        assertFalse(service.verifyEmail("expired-token"));
        accountRepo.delete(account2.id());
    }

    @Test
    @Order(14)
    void verifyEmailSuccess() {
        var account2 = accountRepo.create("verify-ok@test.com", "V2", "User");
        accountRepo.createToken(
                account2.id(),
                "valid-verify-token",
                TokenType.VERIFY_EMAIL,
                Instant.now().plus(24, ChronoUnit.HOURS));
        assertTrue(service.verifyEmail("valid-verify-token"));
        assertTrue(accountRepo.findById(account2.id()).orElseThrow().emailVerified());
        accountRepo.delete(account2.id());
    }

    @Test
    @Order(15)
    void setPasswordInvalidToken() {
        // Password is long enough so the failure is unambiguously a token problem rather than a
        // password-policy rejection.
        assertEquals(
                AuthService.SetPasswordOutcome.TOKEN_INVALID,
                service.setPassword("nonexistent-token", "LongEnoughPassword!"));
    }

    @Test
    @Order(16)
    void setPasswordSuccess() {
        var account2 = accountRepo.create("setpass@test.com", "SP", "User");
        accountRepo.createToken(
                account2.id(),
                "set-pass-token",
                TokenType.SET_PASSWORD,
                Instant.now().plus(24, ChronoUnit.HOURS));
        assertEquals(AuthService.SetPasswordOutcome.OK, service.setPassword("set-pass-token", "NewSecurePass123!"));
        assertTrue(accountRepo.findCredential(account2.id()).isPresent());
        accountRepo.delete(account2.id());
    }

    @Test
    @Order(50)
    void verifyPasswordChecksStoredCredential() {
        var acc = accountRepo.create("verifypw@test.com", "VP", "User");
        accountRepo.createCredential(acc.id(), new PasswordHasher().hash("CorrectHorse123!"));
        assertTrue(service.verifyPassword(acc.id(), "CorrectHorse123!"));
        assertFalse(service.verifyPassword(acc.id(), "wrong-password"));
        assertFalse(service.verifyPassword(acc.id(), null));
        assertFalse(service.verifyPassword(acc.id(), ""));
        accountRepo.delete(acc.id());
    }

    @Test
    @Order(51)
    void verifyPasswordFalseWithoutCredential() {
        var acc = accountRepo.create("nocred@test.com", "NC", "User");
        assertFalse(service.verifyPassword(acc.id(), "anything"));
        accountRepo.delete(acc.id());
    }

    @Test
    @Order(17)
    void setPasswordUpdatesExisting() {
        var account2 = accountRepo.create("setpass2@test.com", "SP2", "User");
        accountRepo.createCredential(account2.id(), new PasswordHasher().hash("OldPassword123!"));
        accountRepo.createToken(
                account2.id(),
                "set-pass-token-2",
                TokenType.SET_PASSWORD,
                Instant.now().plus(24, ChronoUnit.HOURS));
        assertEquals(AuthService.SetPasswordOutcome.OK, service.setPassword("set-pass-token-2", "NewPassword456!"));
        accountRepo.delete(account2.id());
    }

    @Test
    @Order(17)
    void setPasswordRejectsTooShort() {
        assertEquals(AuthService.SetPasswordOutcome.PASSWORD_TOO_SHORT, service.setPassword("any-token", "short"));
    }

    @Test
    @Order(18)
    void requestPasswordReset() {
        // Silent no-op for non-existent email
        assertDoesNotThrow(() -> service.requestPasswordReset("nonexistent@test.com"));
        // Should call email service for existing email
        assertDoesNotThrow(() -> service.requestPasswordReset(EMAIL));
    }

    @Test
    @Order(19)
    void adminResetPasswordNotFound() {
        assertFalse(service.adminResetPassword(99999, false));
    }

    @Test
    @Order(20)
    void adminResetPasswordSuccess() {
        assertTrue(service.adminResetPassword(accountId, false));
    }

    @Test
    @Order(21)
    void adminResetPasswordForceChange() {
        assertTrue(service.adminResetPassword(accountId, true));
    }

    @Test
    @Order(22)
    void resendVerificationAlreadyVerified() {
        // EMAIL was verified in order 5
        assertFalse(service.resendVerification(EMAIL));
    }

    @Test
    @Order(23)
    void resendVerificationNonExistent() {
        assertFalse(service.resendVerification("nobody@test.com"));
    }

    @Test
    @Order(24)
    void resendVerificationUnverified() {
        var account2 = accountRepo.create("unverified-resend@test.com", "U", "User");
        assertTrue(service.resendVerification("unverified-resend@test.com"));
        accountRepo.delete(account2.id());
    }

    @Test
    @Order(25)
    void changePasswordWrongCurrent() {
        // Set a known password first
        var account2 = accountRepo.create("changepw@test.com", "CP", "User");
        accountRepo.createCredential(account2.id(), new PasswordHasher().hash("correct-password"));
        assertFalse(service.changePassword(account2.id(), null, "wrong-password", "NewPassword123!"));
        accountRepo.delete(account2.id());
    }

    @Test
    @Order(26)
    void changePasswordSuccess() {
        var account2 = accountRepo.create("changepw2@test.com", "CP2", "User");
        accountRepo.createCredential(account2.id(), new PasswordHasher().hash("OldPassword123!"));
        assertTrue(service.changePassword(account2.id(), null, "OldPassword123!", "NewPassword456!"));
        accountRepo.delete(account2.id());
    }

    @Test
    @Order(27)
    void changePasswordNoCredential() {
        var account2 = accountRepo.create("changepw3@test.com", "CP3", "User");
        assertFalse(service.changePassword(account2.id(), null, "OldPassword12", "NewPassword12"));
        accountRepo.delete(account2.id());
    }

    @Test
    @Order(27)
    void changePasswordRejectsShortNewPassword() {
        var account2 = accountRepo.create("changepwshort@test.com", "CP4", "User");
        accountRepo.createCredential(account2.id(), new PasswordHasher().hash("CurrentPassword123!"));
        assertFalse(service.changePassword(account2.id(), null, "CurrentPassword123!", "tooshort"));
        accountRepo.delete(account2.id());
    }

    @Test
    @Order(28)
    void requestEmailChange() {
        assertDoesNotThrow(() -> service.requestEmailChange(accountId, "new-email@test.com"));
    }

    @Test
    @Order(29)
    void confirmEmailChangeInvalidToken() {
        assertEquals(AuthService.EmailChangeResult.INVALID, service.confirmEmailChange("nonexistent-change-token"));
    }

    @Test
    @Order(30)
    void confirmEmailChangeTwoStepSuccess() {
        var account2 = accountRepo.create("emailchange@test.com", "EC", "User");
        String newEmail = "new-email-2@test.com";
        String requestId = UUID.randomUUID().toString();
        String metadata = requestId + "|" + newEmail;
        Instant exp = Instant.now().plus(24, ChronoUnit.HOURS);
        accountRepo.createToken(account2.id(), "ec-release", TokenType.EMAIL_CHANGE_RELEASE, metadata, exp);
        accountRepo.createToken(account2.id(), "ec-claim", TokenType.EMAIL_CHANGE_CLAIM, metadata, exp);

        assertEquals(
                AuthService.EmailChangeResult.WAITING,
                service.confirmEmailChange("ec-release"),
                "First click should mark the token as awaiting partner");
        assertEquals(
                "emailchange@test.com",
                accountRepo.findById(account2.id()).orElseThrow().email(),
                "Email must not change after a single confirmation");

        assertEquals(
                AuthService.EmailChangeResult.COMMITTED,
                service.confirmEmailChange("ec-claim"),
                "Second click should commit the change");
        assertEquals(newEmail, accountRepo.findById(account2.id()).orElseThrow().email());

        accountRepo.delete(account2.id());
    }

    @Test
    @Order(31)
    void findSessionsByAccount() {
        var sessions = service.findSessionsByAccount(accountId);
        assertNotNull(sessions);
    }

    @Test
    @Order(32)
    void invalidateAllSessions() {
        // Should return false if no sessions
        assertDoesNotThrow(() -> service.invalidateAllSessions(accountId));
    }

    @Test
    @Order(33)
    void requestStationDeletion() {
        var station = stationRepo.create("Del Station");
        assertDoesNotThrow(() -> service.requestStationDeletion(accountId, station.id()));
        stationRepo.delete(station.id());
    }

    @Test
    @Order(34)
    void confirmStationDeletionInvalidToken() {
        assertTrue(service.confirmStationDeletion("invalid-delete-token").isEmpty());
    }

    @Test
    @Order(35)
    void confirmStationDeletionSuccess() {
        accountRepo.createToken(
                accountId,
                "station-del-token",
                TokenType.STATION_DELETE,
                "999",
                Instant.now().plus(1, ChronoUnit.HOURS));
        var result = service.confirmStationDeletion("station-del-token");
        assertTrue(result.isPresent());
        assertEquals(999, result.get());
    }

    @Test
    @Order(36)
    void sendPasswordSetup() {
        assertDoesNotThrow(() -> service.sendPasswordSetup(accountId, EMAIL, "Auth"));
    }

    @Test
    @Order(37)
    void refreshSessionInvalid() {
        var result = service.refreshSession("invalid-session-token", "agent", "DE");
        assertFalse(result.success());
    }

    @Test
    @Order(38)
    void refreshSessionExpired() {
        // Create a session that has already expired
        var account2 = accountRepo.create("refresh-expired@test.com", "Refresh", "Expired");
        var expiredTime = Instant.now().minus(1, ChronoUnit.HOURS);
        accountRepo.createSession(account2.id(), "expired-session-token", expiredTime, "agent", "DE");

        var result = service.refreshSession("expired-session-token", "agent", "DE");
        assertFalse(result.success());
        assertEquals("Session expired", result.message());
        accountRepo.delete(account2.id());
    }

    @Test
    @Order(39)
    void refreshSessionSuccess() {
        // Create a valid session
        var account2 = accountRepo.create("refresh-ok@test.com", "Refresh", "Ok");
        var expiresAt = Instant.now().plus(60, ChronoUnit.MINUTES);
        accountRepo.createSession(account2.id(), "valid-session-for-refresh", expiresAt, "agent", "DE");

        // Grant LOGIN role so the refreshed session works
        var station2 = stationRepo.create("Refresh Station");
        var member2 = stationMemberRepo.create(station2.id(), account2.id());
        stationMemberRepo
                .findPermissionByName(StationPermission.LOGIN)
                .ifPresent(r -> stationMemberRepo.grantPermission(member2.id(), r.id()));

        var result = service.refreshSession("valid-session-for-refresh", "agent", "DE");
        assertTrue(result.success());
        assertNotNull(result.token());

        stationRepo.delete(station2.id());
        accountRepo.delete(account2.id());
    }

    @Test
    @Order(40)
    void setPasswordExpiredToken() {
        var account2 = accountRepo.create("setpass-expired@test.com", "SP", "Expired");
        accountRepo.createToken(
                account2.id(),
                "expired-setpass-token",
                TokenType.SET_PASSWORD,
                Instant.now().minus(1, ChronoUnit.HOURS));
        assertEquals(
                AuthService.SetPasswordOutcome.TOKEN_INVALID,
                service.setPassword("expired-setpass-token", "NewPassword123!"));
        accountRepo.delete(account2.id());
    }

    @Test
    @Order(41)
    void setPasswordWrongTokenType() {
        var account2 = accountRepo.create("setpass-wrongtype@test.com", "SP", "WrongType");
        accountRepo.createToken(
                account2.id(),
                "wrong-type-token",
                TokenType.VERIFY_EMAIL,
                Instant.now().plus(24, ChronoUnit.HOURS));
        assertEquals(
                AuthService.SetPasswordOutcome.TOKEN_INVALID,
                service.setPassword("wrong-type-token", "NewPassword123!"));
        accountRepo.delete(account2.id());
    }

    @Test
    @Order(42)
    void confirmEmailChangeExpiredToken() {
        var account2 = accountRepo.create("emailchange-expired@test.com", "EC", "Expired");
        accountRepo.createToken(
                account2.id(),
                "ec-expired-token",
                TokenType.EMAIL_CHANGE_RELEASE,
                "req|new@test.com",
                Instant.now().minus(1, ChronoUnit.HOURS));
        assertEquals(AuthService.EmailChangeResult.INVALID, service.confirmEmailChange("ec-expired-token"));
        accountRepo.delete(account2.id());
    }

    @Test
    @Order(43)
    void confirmEmailChangeWrongTokenType() {
        var account2 = accountRepo.create("emailchange-wrongtype@test.com", "EC", "WrongType");
        accountRepo.createToken(
                account2.id(),
                "ec-wrong-type-token",
                TokenType.VERIFY_EMAIL,
                Instant.now().plus(24, ChronoUnit.HOURS));
        assertEquals(AuthService.EmailChangeResult.INVALID, service.confirmEmailChange("ec-wrong-type-token"));
        accountRepo.delete(account2.id());
    }

    @Test
    @Order(44)
    void confirmEmailChangeRejectsLegacyEmailChange() {
        var account2 = accountRepo.create("emailchange-legacy@test.com", "EC", "Legacy");
        accountRepo.createToken(
                account2.id(),
                "ec-legacy-token",
                TokenType.EMAIL_CHANGE,
                "legacy-new@test.com",
                Instant.now().plus(24, ChronoUnit.HOURS));
        assertEquals(
                AuthService.EmailChangeResult.INVALID,
                service.confirmEmailChange("ec-legacy-token"),
                "Legacy single-step EMAIL_CHANGE rows must be rejected by the new two-step flow");
        accountRepo.delete(account2.id());
    }

    @Test
    @Order(45)
    void confirmEmailChangeDuplicateAtCommitFails() {
        var account2 = accountRepo.create("emailchange-dup-src@test.com", "EC", "Dup");
        var taken = accountRepo.create("emailchange-dup-taken@test.com", "EC", "Taken");
        String requestId = UUID.randomUUID().toString();
        String metadata = requestId + "|emailchange-dup-taken@test.com";
        Instant exp = Instant.now().plus(24, ChronoUnit.HOURS);
        accountRepo.createToken(account2.id(), "ec-dup-release", TokenType.EMAIL_CHANGE_RELEASE, metadata, exp);
        accountRepo.createToken(account2.id(), "ec-dup-claim", TokenType.EMAIL_CHANGE_CLAIM, metadata, exp);

        assertEquals(AuthService.EmailChangeResult.WAITING, service.confirmEmailChange("ec-dup-release"));
        assertEquals(AuthService.EmailChangeResult.DUPLICATE, service.confirmEmailChange("ec-dup-claim"));
        assertEquals(
                "emailchange-dup-src@test.com",
                accountRepo.findById(account2.id()).orElseThrow().email(),
                "Email must not change when the new address is already taken");

        accountRepo.delete(account2.id());
        accountRepo.delete(taken.id());
    }

    @Test
    @Order(44)
    void confirmStationDeletionExpiredToken() {
        accountRepo.createToken(
                accountId,
                "expired-station-del-token",
                TokenType.STATION_DELETE,
                "42",
                Instant.now().minus(1, ChronoUnit.HOURS));
        assertTrue(service.confirmStationDeletion("expired-station-del-token").isEmpty());
    }

    @Test
    @Order(45)
    void loginWithForcePasswordChange() {
        // Create account with credentials and force_password_change flag
        var account2 = accountRepo.create("force-pw@test.com", "Force", "Pw");
        accountRepo.setEmailVerified(account2.id());
        var hasher = new PasswordHasher();
        accountRepo.createCredential(account2.id(), hasher.hash("TestPass123!"));
        accountRepo.setForcePasswordChange(account2.id(), true);

        // Grant LOGIN role
        var station2 = stationRepo.create("Force PW Station");
        var member2 = stationMemberRepo.create(station2.id(), account2.id());
        stationMemberRepo
                .findPermissionByName(StationPermission.LOGIN)
                .ifPresent(r -> stationMemberRepo.grantPermission(member2.id(), r.id()));

        var result = service.login("force-pw@test.com", "TestPass123!", "agent", "DE");
        assertTrue(result.passwordChangeRequired());

        stationRepo.delete(station2.id());
        accountRepo.delete(account2.id());
    }

    @Test
    @Order(46)
    void registerSelfWithRegistrationCode() {
        var station2 = stationRepo.create("Reg Code Station");
        var code = registrationCodeRepo.create(station2.id(), "TEST-CODE-123", 5);

        var result = service.registerSelf("regcode@test.com", "Reg", "Code", "PassWord123!", "TEST-CODE-123");
        assertTrue(result.success());

        // Cleanup
        accountRepo.findByEmail("regcode@test.com").ifPresent(a -> accountRepo.delete(a.id()));
        stationRepo.delete(station2.id());
    }

    @Test
    @Order(47)
    void registerSelfWithExhaustedCode() {
        var station2 = stationRepo.create("Exhausted Code Station");
        var code = registrationCodeRepo.create(station2.id(), "EXHAUSTED-CODE", 0);

        var result = service.registerSelf("exhausted@test.com", "Ex", "Hausted", "PassWord123!", "EXHAUSTED-CODE");
        assertFalse(result.success());
        assertEquals("Registration code has been exhausted", result.message());

        stationRepo.delete(station2.id());
    }

    @Test
    @Order(48)
    void registerSelfWithInvalidCode() {
        var result = service.registerSelf("invalid-code@test.com", "In", "Valid", "PassWord123!", "NONEXISTENT-CODE");
        assertFalse(result.success());
        assertEquals("Invalid registration code", result.message());
    }

    @Test
    @Order(49)
    void loginNoCredential() {
        // Account exists but has no credential — should fail
        var account2 = accountRepo.create("nocred@test.com", "NoCred", "User");
        accountRepo.setEmailVerified(account2.id());
        var result = service.login("nocred@test.com", "anypass", "agent", "DE");
        assertFalse(result.success());
        accountRepo.delete(account2.id());
    }

    /**
     * Spin up a fresh account with a station membership + LOGIN permission so it can sign in
     * cleanly even after earlier ordered tests have torn down the shared fixture.
     */
    private TrustedDeviceFixture createLoginCapableAccount(String emailPrefix) {
        String email = emailPrefix + "-" + UUID.randomUUID() + "@test.com";
        var passwordHasher = new PasswordHasher();
        var account = accountRepo.create(email, "Trust", "Test", true);
        accountRepo.createCredential(account.id(), passwordHasher.hash(PASSWORD));
        var station = stationRepo.create("trust-svc-" + UUID.randomUUID());
        var member = stationMemberRepo.create(station.id(), account.id());
        stationMemberRepo
                .findPermissionByName(StationPermission.LOGIN)
                .ifPresent(p -> stationMemberRepo.grantPermission(member.id(), p.id()));
        return new TrustedDeviceFixture(account.id(), email, station.id());
    }

    private record TrustedDeviceFixture(int accountId, String email, int stationId) {}

    @Test
    @Order(80)
    void loginWithTwoFactorRequiresPreAuth() {
        var fixture = createLoginCapableAccount("tf-required");
        twoFactorRepoLocal.createFactor(fixture.accountId(), TwoFactorKind.TOTP, "TestTOTP");

        var result = service.login(fixture.email(), PASSWORD, "agent", "DE", null);
        assertTrue(result.success(), result.message());
        assertTrue(result.twoFactorRequired());
        assertNotNull(result.preAuthToken());

        accountRepo.delete(fixture.accountId());
        stationRepo.delete(fixture.stationId());
    }

    @Test
    @Order(81)
    void loginWithTrustedDeviceCookieBypassesTwoFactor() {
        var fixture = createLoginCapableAccount("tf-trust");
        twoFactorRepoLocal.createFactor(fixture.accountId(), TwoFactorKind.TOTP, "TestTOTP");

        var issued = trustedDeviceService.issue(fixture.accountId(), 7, "agent");
        var result = service.login(fixture.email(), PASSWORD, "agent", "DE", issued.token());
        assertTrue(result.success(), result.message());
        assertFalse(result.twoFactorRequired(), "valid trusted-device cookie should bypass the 2FA challenge");
        assertNotNull(result.token());

        accountRepo.delete(fixture.accountId());
        stationRepo.delete(fixture.stationId());
    }

    @Test
    @Order(82)
    void loginWithTrustedDeviceForDifferentAccountStillRequires2FA() {
        var fixture = createLoginCapableAccount("tf-stranger");
        twoFactorRepoLocal.createFactor(fixture.accountId(), TwoFactorKind.TOTP, "TestTOTP");

        var other = accountRepo.create("other-trust-" + UUID.randomUUID() + "@test.com", "O", "T", true);
        var stranger = trustedDeviceService.issue(other.id(), 1, "ua");

        var result = service.login(fixture.email(), PASSWORD, "agent", "DE", stranger.token());
        assertTrue(result.success(), result.message());
        assertTrue(result.twoFactorRequired());

        accountRepo.delete(other.id());
        accountRepo.delete(fixture.accountId());
        stationRepo.delete(fixture.stationId());
    }

    @Test
    @Order(83)
    void loginWithGarbageCookieIgnored() {
        var fixture = createLoginCapableAccount("tf-garbage");
        twoFactorRepoLocal.createFactor(fixture.accountId(), TwoFactorKind.TOTP, "TestTOTP");

        var result = service.login(fixture.email(), PASSWORD, "agent", "DE", "not-a-real-cookie-value");
        assertTrue(result.success(), result.message());
        assertTrue(result.twoFactorRequired());

        accountRepo.delete(fixture.accountId());
        stationRepo.delete(fixture.stationId());
    }

    @Test
    @Order(99)
    void cleanup() {
        accountRepo.delete(accountId);
    }
}
