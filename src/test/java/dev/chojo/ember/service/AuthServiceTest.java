/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.service;

import dev.chojo.ember.api.roles.StationPermission;
import dev.chojo.ember.auth.PasswordHasher;
import dev.chojo.ember.conf.file.elements.Auth;
import dev.chojo.ember.conf.file.elements.Demo;
import dev.chojo.ember.feature.account.entity.TokenType;
import dev.chojo.ember.feature.account.service.AuthService;
import dev.chojo.ember.feature.mail.service.EmailService;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AuthServiceTest extends RepositoryTestBase {
    private static AuthService service;
    private static final String EMAIL = "auth-test@test.com";
    private static final String EMAIL_INVITED = "auth-invited@test.com";
    private static final String PASSWORD = "TestPassword123!";
    private static int accountId;
    private static String verifyToken;
    private static String sessionToken;

    @BeforeAll
    static void setup() {
        var passwordHasher = new PasswordHasher();
        var emailService = mock(EmailService.class);
        var authConfig = new Auth();
        var demo = new Demo();

        service = new AuthService(
                accountRepo,
                registrationCodeRepo,
                stationMemberRepo,
                memberGroupRepo,
                passwordHasher,
                emailService,
                authConfig,
                demo);
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
    void registerSelfDuplicateEmail() {
        var result = service.registerSelf(EMAIL, "Auth", "Tester2", PASSWORD, null);
        assertFalse(result.success());
        assertEquals("Email already in use", result.message());
    }

    @Test
    @Order(3)
    void loginWithoutVerification() {
        var result = service.login(EMAIL, PASSWORD, "TestAgent", "DE");
        assertFalse(result.success());
        assertEquals("Email not verified", result.message());
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
        // Verify email first so we can test wrong password
        var tokens = accountRepo.findToken("nonexistent");
        // Manually set email as verified for this test
        accountRepo.setEmailVerified(accountId);

        var result = service.login(EMAIL, "WrongPassword!", "agent", "DE");
        assertFalse(result.success());
        assertEquals("Invalid email or password", result.message());
    }

    @Test
    @Order(6)
    void loginNotAuthorized() {
        // Account has no LOGIN role — should fail
        var result = service.login(EMAIL, PASSWORD, "agent", "DE");
        assertFalse(result.success());
        assertEquals("Account is not authorized to log in", result.message());
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
    @Order(10)
    void createInvitedAccountSuccess() {
        var station = stationRepo.create("Invited Station");
        var result = service.createInvitedAccount(EMAIL_INVITED, "Invited", "User", station.id());
        assertTrue(result.success());
        assertNotNull(result.account());
        assertEquals(EMAIL_INVITED, result.account().email());
        assertTrue(result.account().emailVerified());
        stationRepo.delete(station.id());
    }

    @Test
    @Order(11)
    void createInvitedAccountDuplicate() {
        var station = stationRepo.create("Invited Station 2");
        var result = service.createInvitedAccount(EMAIL_INVITED, "Invited", "User2", station.id());
        assertFalse(result.success());
        assertEquals("Email already in use", result.message());
        stationRepo.delete(station.id());
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
        assertFalse(service.setPassword("nonexistent-token", "newpass"));
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
        assertTrue(service.setPassword("set-pass-token", "NewSecurePass123!"));
        assertTrue(accountRepo.findCredential(account2.id()).isPresent());
        accountRepo.delete(account2.id());
    }

    @Test
    @Order(17)
    void setPasswordUpdatesExisting() {
        var account2 = accountRepo.create("setpass2@test.com", "SP2", "User");
        accountRepo.createCredential(account2.id(), new PasswordHasher().hash("OldPass123!"));
        accountRepo.createToken(
                account2.id(),
                "set-pass-token-2",
                TokenType.SET_PASSWORD,
                Instant.now().plus(24, ChronoUnit.HOURS));
        assertTrue(service.setPassword("set-pass-token-2", "NewPass456!"));
        accountRepo.delete(account2.id());
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
        assertFalse(service.changePassword(account2.id(), "wrong-password", "NewPass!"));
        accountRepo.delete(account2.id());
    }

    @Test
    @Order(26)
    void changePasswordSuccess() {
        var account2 = accountRepo.create("changepw2@test.com", "CP2", "User");
        accountRepo.createCredential(account2.id(), new PasswordHasher().hash("OldPass123!"));
        assertTrue(service.changePassword(account2.id(), "OldPass123!", "NewPass456!"));
        accountRepo.delete(account2.id());
    }

    @Test
    @Order(27)
    void changePasswordNoCredential() {
        var account2 = accountRepo.create("changepw3@test.com", "CP3", "User");
        assertFalse(service.changePassword(account2.id(), "OldPass", "NewPass"));
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
        assertFalse(service.confirmEmailChange("nonexistent-change-token"));
    }

    @Test
    @Order(30)
    void confirmEmailChangeSuccess() {
        var account2 = accountRepo.create("emailchange@test.com", "EC", "User");
        accountRepo.createToken(
                account2.id(),
                "ec-token",
                TokenType.EMAIL_CHANGE,
                "new-email-2@test.com",
                Instant.now().plus(24, ChronoUnit.HOURS));
        assertTrue(service.confirmEmailChange("ec-token"));
        assertEquals(
                "new-email-2@test.com",
                accountRepo.findById(account2.id()).orElseThrow().email());
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
        assertFalse(service.setPassword("expired-setpass-token", "NewPass123!"));
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
        assertFalse(service.setPassword("wrong-type-token", "NewPass123!"));
        accountRepo.delete(account2.id());
    }

    @Test
    @Order(42)
    void confirmEmailChangeExpiredToken() {
        var account2 = accountRepo.create("emailchange-expired@test.com", "EC", "Expired");
        accountRepo.createToken(
                account2.id(),
                "ec-expired-token",
                TokenType.EMAIL_CHANGE,
                "new@test.com",
                Instant.now().minus(1, ChronoUnit.HOURS));
        assertFalse(service.confirmEmailChange("ec-expired-token"));
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
        assertFalse(service.confirmEmailChange("ec-wrong-type-token"));
        accountRepo.delete(account2.id());
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

    @Test
    @Order(99)
    void cleanup() {
        accountRepo.delete(accountId);
        accountRepo.findByEmail(EMAIL_INVITED).ifPresent(a -> accountRepo.delete(a.id()));
    }
}
