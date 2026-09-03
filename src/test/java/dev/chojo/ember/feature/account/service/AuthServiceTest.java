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
import dev.chojo.ember.feature.mail.service.MailLocaleService;
import dev.chojo.ember.feature.mail.service.MailRecipientService;
import dev.chojo.ember.feature.system.repository.ApplicationSettingRepository;
import dev.chojo.ember.feature.twofactor.entity.TwoFactorKind;
import dev.chojo.ember.feature.twofactor.repository.TwoFactorRepository;
import dev.chojo.ember.feature.twofactor.service.TrustedDeviceService;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AuthServiceTest extends RepositoryTestBase {
    private static AuthService service;
    private static final String EMAIL = "auth-test@test.com";
    private static final String PASSWORD = "TestPassword123!";
    private static int accountId;
    private static String verifyToken;
    private static String sessionToken;

    private static EmailService emailService;
    private static TwoFactorRepository twoFactorRepoLocal;
    private static TrustedDeviceService trustedDeviceService;

    @BeforeAll
    static void setup() {
        var passwordHasher = new PasswordHasher();
        emailService = mock(EmailService.class);
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
                new MailLocaleService(accountRepo, new ApplicationSettingRepository()),
                new MailRecipientService(accountRepo, stationMemberRepo),
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

    /**
     * The whole way round, because only the ends of it were covered: asking is one call, the link
     * that arrives is another, and nothing checked that the second follows from the first or that
     * the password it sets is the one that then works.
     */
    @Test
    @Order(18)
    void aForgottenPasswordIsSetAgainFromTheLinkThatWasMailed() {
        var forgetful = accountRepo.create("forgot-flow@test.com", "For", "Getful");
        accountRepo.createCredential(forgetful.id(), new PasswordHasher().hash("DasAlteKennwort123!"));

        service.requestPasswordReset("forgot-flow@test.com");

        var mailed = ArgumentCaptor.forClass(String.class);
        verify(emailService)
                .sendPasswordResetEmail(eq("forgot-flow@test.com"), anyString(), mailed.capture(), anyString());
        String link = mailed.getValue();

        assertEquals(
                AuthService.SetPasswordOutcome.OK,
                service.setPassword(link, "DasNeueKennwort123!"),
                "the link sets a new password");
        assertTrue(service.verifyPassword(forgetful.id(), "DasNeueKennwort123!"), "which is the one that now works");
        assertFalse(service.verifyPassword(forgetful.id(), "DasAlteKennwort123!"), "and the old one no longer does");
        assertEquals(
                AuthService.SetPasswordOutcome.TOKEN_INVALID,
                service.setPassword(link, "EinDrittesKennwort123!"),
                "a link is good for one reset");

        accountRepo.delete(forgetful.id());
    }

    /**
     * An invitation waits for the evening somebody next reads their mail, a reset does not. The two
     * links are therefore given their own lifetimes, and the setup one is the long one.
     */
    @Test
    @Order(18)
    void theSetupLinkOutlivesTheResetLink() {
        var invited = accountRepo.create("setup-window@test.com", "In", "Vited");

        service.sendPasswordSetup(invited.id());
        service.requestPasswordReset("setup-window@test.com");

        var setupMail = ArgumentCaptor.forClass(String.class);
        var resetMail = ArgumentCaptor.forClass(String.class);
        verify(emailService)
                .sendPasswordSetupEmail(eq("setup-window@test.com"), anyString(), setupMail.capture(), anyString());
        verify(emailService)
                .sendPasswordResetEmail(eq("setup-window@test.com"), anyString(), resetMail.capture(), anyString());

        var setup = accountRepo.findToken(setupMail.getValue()).orElseThrow().expiresAt();
        var reset = accountRepo.findToken(resetMail.getValue()).orElseThrow().expiresAt();
        assertTrue(setup.isAfter(reset), "the invitation is good for longer than the reset");
        assertTrue(setup.isAfter(Instant.now().plus(29, ChronoUnit.DAYS)), "and for the month the setting asks for");

        accountRepo.delete(invited.id());
    }

    /** Whatever the configuration asks for, a link in a mailbox is not made to live for ever. */
    @Test
    @Order(18)
    void theSetupWindowIsCappedAtAMonth() throws Exception {
        assertEquals(Auth.SETUP_TOKEN_MAX_DAYS, configuredSetupDays(3650), "ten years is still a month");
        assertEquals(1, configuredSetupDays(0), "and nothing at all is still a day");
        assertEquals(7, configuredSetupDays(7), "anything inside the ceiling is taken as asked");
    }

    /** Reads back what an {@link Auth} configured with this many days actually hands out. */
    private static int configuredSetupDays(int asked) throws Exception {
        var config = new Auth();
        var field = Auth.class.getDeclaredField("setupTokenDays");
        field.setAccessible(true);
        field.setInt(config, asked);
        return config.setupTokenDays();
    }

    /**
     * A link that ran out and a link that never existed need different words, so the two are told
     * apart rather than both answered with "invalid". Only then can the page say what happened and
     * who can put it right.
     */
    @Test
    @Order(18)
    void anExpiredLinkIsToldApartFromAnUnknownOne() {
        var invited = accountRepo.create("expired-link@test.com", "Ex", "Pired");
        accountRepo.createToken(
                invited.id(),
                "long-since-expired",
                TokenType.SET_PASSWORD,
                Instant.now().minus(1, ChronoUnit.HOURS));

        assertEquals(
                AuthService.TokenStanding.EXPIRED,
                service.checkPasswordToken("long-since-expired").standing(),
                "asking about it says it ran out");
        assertEquals(
                AuthService.TokenPurpose.SETUP,
                service.checkPasswordToken("long-since-expired").purpose(),
                "and that it was an invitation, which only a station can send again");
        assertEquals(
                AuthService.TokenStanding.UNKNOWN,
                service.checkPasswordToken("no-such-link-was-ever-issued").standing());

        assertEquals(
                AuthService.SetPasswordOutcome.TOKEN_EXPIRED,
                service.setPassword("long-since-expired", "EinGutesKennwort123!"),
                "and using it says the same");

        accountRepo.delete(invited.id());
    }

    /** Asking what a link is worth must not use it up: a reader who reloads sees the same answer. */
    @Test
    @Order(18)
    void askingAboutALinkDoesNotSpendIt() {
        var invited = accountRepo.create("still-good@test.com", "St", "Good");
        accountRepo.createToken(
                invited.id(),
                "still-good-token",
                TokenType.SET_PASSWORD,
                Instant.now().plus(5, ChronoUnit.DAYS));

        assertEquals(
                AuthService.TokenStanding.VALID,
                service.checkPasswordToken("still-good-token").standing());
        assertEquals(
                AuthService.TokenStanding.VALID,
                service.checkPasswordToken("still-good-token").standing());
        assertEquals(
                AuthService.SetPasswordOutcome.OK,
                service.setPassword("still-good-token", "EinGutesKennwort123!"),
                "and it still works afterwards");

        accountRepo.delete(invited.id());
    }

    /** Nobody to write to, nothing written down: a reset nobody could receive is not prepared. */
    @Test
    @Order(18)
    void anAccountNobodyCanBeWrittenToAboutGetsNoResetToken() {
        var unreachable = accountRepo.create("unreachable@import.local", "Un", "Reachable");

        service.requestPasswordReset("unreachable@import.local");

        verify(emailService, never())
                .sendPasswordResetEmail(eq("unreachable@import.local"), anyString(), anyString(), anyString());

        accountRepo.delete(unreachable.id());
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
        assertDoesNotThrow(() -> service.sendPasswordSetup(accountId));
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
                AuthService.SetPasswordOutcome.TOKEN_EXPIRED,
                service.setPassword("expired-setpass-token", "NewPassword123!"),
                "a link that ran out says so, rather than being lumped in with an unknown one");
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
        // Account exists but has no credential - should fail
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
    @Order(84)
    void refreshSessionKeepsTwoFactorVerification() {
        var fixture = createLoginCapableAccount("refresh-stepup");
        var device = trustedDeviceService.issue(fixture.accountId(), 7, "agent");
        var verifiedAt = Instant.now().minus(30, ChronoUnit.SECONDS);
        String token = "refresh-keeps-stepup-" + UUID.randomUUID();
        // Seeded well short of the configured session length, so the refreshed expiry is visibly later.
        accountRepo.createSession(
                fixture.accountId(),
                token,
                Instant.now().plus(5, ChronoUnit.MINUTES),
                "agent",
                "DE",
                verifiedAt,
                device.device().id(),
                true);
        var before = accountRepo.findSession(token).orElseThrow();

        var result = service.refreshSession(token, "agent", "DE");
        assertTrue(result.success(), result.message());

        var after = accountRepo.findSession(result.token()).orElseThrow();
        assertEquals(before.id(), after.id(), "a refresh continues the same session rather than starting a new one");
        assertEquals(
                before.twoFactorVerifiedAt(),
                after.twoFactorVerifiedAt(),
                "the step-up window has to survive a token refresh");
        assertTrue(after.trustedDevice(), "the trusted-device flag has to survive a token refresh");
        assertTrue(after.expiresAt().isAfter(before.expiresAt()), "a refresh pushes the expiry back");

        accountRepo.delete(fixture.accountId());
        stationRepo.delete(fixture.stationId());
    }

    @Test
    @Order(85)
    void refreshSessionRetiresTheOldToken() {
        var fixture = createLoginCapableAccount("refresh-rotate");
        String token = "refresh-rotates-" + UUID.randomUUID();
        accountRepo.createSession(
                fixture.accountId(), token, Instant.now().plus(60, ChronoUnit.MINUTES), "agent", "DE");

        var result = service.refreshSession(token, "agent", "DE");
        assertTrue(result.success(), result.message());
        assertNotEquals(token, result.token(), "the refreshed session must be handed a new token");
        assertTrue(accountRepo.findSession(token).isEmpty(), "the old token must stop working");

        accountRepo.delete(fixture.accountId());
        stationRepo.delete(fixture.stationId());
    }

    /**
     * The same service on an instance that is in demo mode.
     *
     * <p>A second service rather than a flag, because whether this is a demo instance is configuration and
     * is read where it was injected. Quick login is the only thing that behaves differently for it here.
     */
    private AuthService demoModeService() {
        var demo = mock(Demo.class);
        when(demo.enabled()).thenReturn(true);
        var hibpClient = mock(HibpClient.class);
        when(hibpClient.isPwned(anyString())).thenReturn(false);
        return new AuthService(
                accountRepo,
                new MailLocaleService(accountRepo, new ApplicationSettingRepository()),
                new MailRecipientService(accountRepo, stationMemberRepo),
                registrationCodeRepo,
                stationMemberRepo,
                memberGroupRepo,
                new PasswordHasher(),
                mock(EmailService.class),
                new Auth(),
                demo,
                hibpClient,
                mock(BreachCheckWorker.class),
                twoFactorRepoLocal,
                trustedDeviceService);
    }

    /**
     * Quick login signs somebody in by address alone, and an ordinary instance refuses it.
     *
     * <p>The route is only registered on a dev or demo instance, so this is the second of two gates. It is
     * the one that would still hold if a change ever exposed the path somewhere else, which is what makes it
     * worth a test rather than a comment.
     */
    @Test
    @Order(86)
    void quickLoginIsRefusedOffADemoInstance() {
        var fixture = createLoginCapableAccount("quick-refused");

        var result = service.loginAsDemo(fixture.email(), "agent", "DE");
        assertFalse(result.success(), "quick login must sign nobody in on an ordinary instance");
        assertNull(result.token());

        accountRepo.delete(fixture.accountId());
        stationRepo.delete(fixture.stationId());
    }

    /**
     * On a demo instance it signs the account in without a password, and an address nobody holds is refused
     * the way a wrong password is rather than by saying the address is unknown.
     */
    @Test
    @Order(87)
    void quickLoginSignsInByAddressOnADemoInstance() {
        var fixture = createLoginCapableAccount("quick-login");
        var demoService = demoModeService();

        var result = demoService.loginAsDemo(fixture.email(), "agent", "DE");
        assertTrue(result.success(), result.message());
        // The address is the session token in demo mode, so a restart does not sign everybody out again
        assertEquals(fixture.email(), result.token());
        assertTrue(accountRepo.findSession(fixture.email()).isPresent(), "the session has to be there to use");

        var unknown = demoService.loginAsDemo("nobody-" + UUID.randomUUID() + "@test.com", "agent", "DE");
        assertFalse(unknown.success());
        assertNull(unknown.token());

        accountRepo.delete(fixture.accountId());
        stationRepo.delete(fixture.stationId());
    }

    /**
     * Signing in by name rather than by address, including the member who has no address at all:
     * their name is the only thing they could type, and the unverified-address refusal has nothing
     * to refuse.
     */
    @Test
    @Order(98)
    void signingInByName() {
        var withAddress = accountRepo.create("named@test.com", "Nina", "Name", true);
        accountRepo.createCredential(withAddress.id(), new PasswordHasher().hash(PASSWORD));
        accountRepo.updateUsername(withAddress.id(), "nina.name");

        assertTrue(service.login("nina.name", PASSWORD, "agent", "DE").success(), "the name signs them in");
        assertTrue(service.login("named@test.com", PASSWORD, "agent", "DE").success(), "so does the address");
        assertFalse(service.login("NINA.NAME", "WrongPassword!", "agent", "DE").success());
        assertTrue(service.login("NINA.NAME", PASSWORD, "agent", "DE").success(), "case is not part of the name");

        var withoutAddress = accountRepo.create("kid@managed.local", "Kim", "Kind", false);
        accountRepo.createCredential(withoutAddress.id(), new PasswordHasher().hash(PASSWORD));
        accountRepo.updateUsername(withoutAddress.id(), "kim.kind");

        assertTrue(
                service.login("kim.kind", PASSWORD, "agent", "DE").success(),
                "an account with no address has no address to verify");

        accountRepo.delete(withAddress.id());
        accountRepo.delete(withoutAddress.id());
    }

    @Test
    @Order(98)
    void aPasswordSetOnSomebodysBehalfEndsTheirSessionsAndSignsThemIn() {
        var child = accountRepo.create("kid-behalf@managed.local", "Kim", "Kind", false);
        accountRepo.updateUsername(child.id(), "kim.behalf");
        accountRepo.createCredential(child.id(), new PasswordHasher().hash(PASSWORD));
        var session = service.login("kim.behalf", PASSWORD, "agent", "DE");
        assertTrue(session.success(), "the old password works before it is replaced");

        var outcome = service.setPasswordFor(child, "SomebodyElseSetThis1!");

        assertEquals(AuthService.SetPasswordOutcome.OK, outcome);
        assertTrue(accountRepo.findSessionsByAccount(child.id()).isEmpty(), "the sessions that were open have ended");
        assertTrue(
                service.login("kim.behalf", "SomebodyElseSetThis1!", "agent", "DE")
                        .success(),
                "the new password signs them in");
        assertFalse(service.login("kim.behalf", PASSWORD, "agent", "DE").success(), "and the old one no longer does");

        accountRepo.delete(child.id());
    }

    @Test
    @Order(98)
    void anAccountWithoutCredentialsGetsThemFromSomebodyElse() {
        var child = accountRepo.create("kid-fresh@managed.local", "Kai", "Kind", false);
        accountRepo.updateUsername(child.id(), "kai.fresh");

        assertEquals(AuthService.SetPasswordOutcome.OK, service.setPasswordFor(child, "AFirstPassword1!"));
        assertTrue(service.login("kai.fresh", "AFirstPassword1!", "agent", "DE").success());

        accountRepo.delete(child.id());
    }

    /**
     * Setting the password from a link signs the person in with it.
     *
     * <p>Choosing the password proves the same thing as typing it into the sign-in form would, so
     * being sent round to say it again protects nothing.
     */
    @Test
    @Order(90)
    void settingAPasswordFromALinkSignsIn() {
        var fixture = createLoginCapableAccount("setpass-signin");
        String token = "setpass-signin-" + UUID.randomUUID();
        accountRepo.createToken(
                fixture.accountId(),
                token,
                TokenType.SET_PASSWORD,
                Instant.now().plus(1, ChronoUnit.HOURS));

        var result = service.setPasswordAndSignIn(token, "AFreshPassword1!", "agent", "DE");

        assertEquals(AuthService.SetPasswordOutcome.OK, result.outcome());
        assertNotNull(result.login(), "a password that was accepted earns a session");
        assertTrue(result.login().success(), result.login().message());
        assertFalse(result.login().twoFactorRequired());
        assertNotNull(result.login().token());

        accountRepo.delete(fixture.accountId());
        stationRepo.delete(fixture.stationId());
    }

    /**
     * The second factor is the one thing choosing a password does not stand in for.
     *
     * <p>Otherwise a reset link turns an account guarded by a factor into an account guarded by a
     * mailbox, which is the whole reason the factor is there.
     */
    @Test
    @Order(91)
    void aResetStillAsksForTheSecondFactor() {
        var fixture = createLoginCapableAccount("setpass-2fa");
        twoFactorRepoLocal.createFactor(fixture.accountId(), TwoFactorKind.TOTP, "TestTOTP");
        String token = "setpass-2fa-" + UUID.randomUUID();
        accountRepo.createToken(
                fixture.accountId(),
                token,
                TokenType.RESET_PASSWORD,
                Instant.now().plus(1, ChronoUnit.HOURS));

        var result = service.setPasswordAndSignIn(token, "AFreshPassword2!", "agent", "DE");

        assertEquals(AuthService.SetPasswordOutcome.OK, result.outcome());
        assertNotNull(result.login());
        assertTrue(result.login().twoFactorRequired(), "a factor on the account still has to be given");
        assertNull(result.login().token(), "no session before the factor is given");
        assertNotNull(result.login().preAuthToken());

        accountRepo.delete(fixture.accountId());
        stationRepo.delete(fixture.stationId());
    }

    /** A password that is refused earns nothing, least of all a session. */
    @Test
    @Order(92)
    void arefusedPasswordSignsNobodyIn() {
        var result = service.setPasswordAndSignIn("no-such-token", "AFreshPassword3!", "agent", "DE");

        assertEquals(AuthService.SetPasswordOutcome.TOKEN_INVALID, result.outcome());
        assertNull(result.login());
    }

    /**
     * D3's other half: switching password sign-in off refuses the password on the login screen,
     * but only after it proved out, so a guesser learns nothing about the account's state.
     */
    @Test
    @Order(93)
    void passwordRefusedWhileSignInIsSwitchedOff() {
        String email = "pw-off@test.com";
        var registered = service.registerSelf(email, "Pw", "Off", PASSWORD, null);
        int id = registered.account().id();
        accountRepo.setEmailVerified(id);
        accountRepo.setPasswordLoginDisabled(id, true);

        var refused = service.login(email, PASSWORD, "agent", "DE");
        assertFalse(refused.success());
        assertTrue(refused.message().contains("passkey"), "the refusal must name the ways back in");

        var wrongPassword = service.login(email, "WrongPassword!", "agent", "DE");
        assertEquals(
                "Invalid email or password",
                wrongPassword.message(),
                "a wrong password must not learn that sign-in is switched off");

        accountRepo.setPasswordLoginDisabled(id, false);
        assertTrue(service.login(email, PASSWORD, "agent", "DE").success(), "the switch must open again");
        accountRepo.delete(id);
    }

    /** A passkey sign-in mints a session that already counts as freshly proved (D2). */
    @Test
    @Order(94)
    void passkeyAdmissionMintsAVerifiedSession() {
        String email = "pk-admit@test.com";
        var registered = service.registerSelf(email, "Pk", "Admit", PASSWORD, null);
        int id = registered.account().id();
        accountRepo.setEmailVerified(id);

        var result = service.admitPasskeyAccount(id, "agent", "DE", false);
        assertTrue(result.success());
        var session = accountRepo.findSession(result.token()).orElseThrow();
        assertNotNull(session.twoFactorVerifiedAt(), "the user-verified assertion is the second factor");
        assertNotNull(accountRepo.findLastSignInAt(id).orElseThrow(), "every sign-in stamps when it happened");
        accountRepo.delete(id);
    }

    @Test
    @Order(95)
    void passkeyAdmissionRefusesUnverifiedEmail() {
        String email = "pk-unverified@test.com";
        var registered = service.registerSelf(email, "Pk", "Unverified", PASSWORD, null);
        int id = registered.account().id();

        var result = service.admitPasskeyAccount(id, "agent", "DE", false);
        assertFalse(result.success());
        assertEquals("Email not verified", result.message());
        accountRepo.delete(id);
    }

    /**
     * The forced rotation guards the login screen, so it is only demanded while the password
     * still works there. Where it does, a passkey sign-in stops for it like a password one.
     */
    @Test
    @Order(96)
    void passkeyAdmissionAsksForRotationOnlyWhileThePasswordWorks() {
        String email = "pk-rotate@test.com";
        var registered = service.registerSelf(email, "Pk", "Rotate", PASSWORD, null);
        int id = registered.account().id();
        accountRepo.setEmailVerified(id);
        accountRepo.setForcePasswordChange(id, true);

        var stopped = service.admitPasskeyAccount(id, "agent", "DE", false);
        assertTrue(stopped.passwordChangeRequired(), "a leaked password must still be rotated");

        accountRepo.setPasswordLoginDisabled(id, true);
        var admitted = service.admitPasskeyAccount(id, "agent", "DE", false);
        assertTrue(admitted.success());
        assertFalse(
                admitted.passwordChangeRequired(),
                "with password sign-in off there is nothing to rotate and nothing to reopen");
        accountRepo.delete(id);
    }

    @Test
    @Order(99)
    void cleanup() {
        accountRepo.delete(accountId);
    }
}
