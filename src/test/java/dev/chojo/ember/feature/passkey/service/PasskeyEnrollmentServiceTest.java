/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.passkey.service;

import dev.chojo.ember.auth.TokenHasher;
import dev.chojo.ember.conf.file.elements.Api;
import dev.chojo.ember.conf.file.elements.PasskeySettings;
import dev.chojo.ember.conf.file.elements.WebAuthnSettings;
import dev.chojo.ember.feature.account.entity.TokenType;
import dev.chojo.ember.feature.account.service.AuthService;
import dev.chojo.ember.feature.mail.service.EmailService;
import dev.chojo.ember.feature.mail.service.MailLocaleService;
import dev.chojo.ember.feature.mail.service.MailRecipientService;
import dev.chojo.ember.feature.passkey.repository.PasskeyRepository;
import dev.chojo.ember.feature.system.repository.ApplicationSettingRepository;
import dev.chojo.ember.feature.twofactor.repository.WebAuthnChallengeRepository;
import dev.chojo.ember.feature.twofactor.service.SecondFactorCredentialStore;
import dev.chojo.ember.feature.twofactor.service.TotpService;
import dev.chojo.ember.feature.twofactor.service.TwoFactorAuditService;
import dev.chojo.ember.feature.twofactor.service.WebAuthnCredentialStore;
import dev.chojo.ember.feature.twofactor.service.WebAuthnRelyingPartyFactory;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PasskeyEnrollmentServiceTest extends RepositoryTestBase {

    private static final PasskeyRepository passkeyRepo = new PasskeyRepository();
    private static final AuthService authService = mock(AuthService.class);
    private static final MailRecipientService mailRecipientService = mock(MailRecipientService.class);
    private static final PasskeyModeService modeService = mock(PasskeyModeService.class);
    private static final EmailService emailService = mock(EmailService.class);
    private static final TotpService totpService = mock(TotpService.class);
    private static PasskeyEnrollmentService service;

    @BeforeAll
    static void setup() throws Exception {
        var settings = new WebAuthnSettings();
        var api = new Api();
        setField(api, "baseUrl", "https://ember.test");
        var store = new WebAuthnCredentialStore(twoFactorRepo);
        var parties = WebAuthnRelyingPartyFactory.build(
                settings, api, store, new SecondFactorCredentialStore(twoFactorRepo, store));
        var challengeRepo = new WebAuthnChallengeRepository(TokenHasher.forTesting("repository-test-pepper"));
        var passkeyService = new PasskeyService(
                parties, twoFactorRepo, new TwoFactorAuditService(twoFactorRepo), challengeRepo, settings);
        when(totpService.generateQrPng(anyString(), anyInt())).thenReturn(new byte[] {1, 2, 3});
        service = new PasskeyEnrollmentService(
                accountRepo,
                passkeyRepo,
                passkeyService,
                authService,
                new TwoFactorAuditService(twoFactorRepo),
                mailRecipientService,
                modeService,
                emailService,
                new MailLocaleService(accountRepo, new ApplicationSettingRepository()),
                totpService,
                api);
        when(modeService.effectiveMode()).thenReturn(PasskeySettings.Mode.OPTIONAL);
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }

    private int newAccount() {
        return accountRepo
                .create("enroll-" + UUID.randomUUID() + "@test.com", "Enroll", "Owner", true)
                .id();
    }

    @Test
    void aCodeOpensItsAccountHoweverItIsTyped() {
        int accountId = newAccount();
        String code = service.issueCode(accountId, PasskeyEnrollmentService.QR_TTL);

        assertEquals(accountId, service.lookup(code).orElseThrow().id());
        String mangled = (code.substring(0, 4) + "-" + code.substring(4)).toLowerCase();
        assertEquals(
                accountId, service.lookup(mangled).orElseThrow().id(), "a typed code arrives grouped and lower-cased");
        assertTrue(service.lookup("NO-SUCH-CODE").isEmpty());
    }

    @Test
    void aFreshCodeKillsTheOneBeforeAndRevokeTheOpenOne() {
        int accountId = newAccount();
        String first = service.issueCode(accountId, PasskeyEnrollmentService.QR_TTL);
        String second = service.issueCode(accountId, PasskeyEnrollmentService.QR_TTL);

        assertTrue(service.lookup(first).isEmpty(), "an abandoned code must not stay photographable");
        assertTrue(service.lookup(second).isPresent());

        service.revokeCode(accountId);
        assertTrue(service.lookup(second).isEmpty());
    }

    @Test
    void theCeremonyBehindATokenMintsTheCredentialExactlyOnce() {
        int accountId = newAccount();
        var authenticator = new TestAuthenticator();
        String code = service.issueCode(accountId, PasskeyEnrollmentService.QR_TTL);

        var ceremony = service.begin(code).orElseThrow();
        assertTrue(
                service.finish(code, ceremony.challengeToken(), authenticator.register(ceremony.optionsJson()), "DE"));
        assertTrue(passkeyRepo.hasTriedSignInPasskey(accountId) || twoFactorRepo.hasSignInPasskey(accountId));

        assertFalse(
                service.finish(code, ceremony.challengeToken(), authenticator.register(ceremony.optionsJson()), "DE"),
                "the token is spent however the ceremony ended");
        assertTrue(service.begin(code).isEmpty(), "a spent token opens no ceremony either");
    }

    @Test
    void aVerifyMailTokenIsADoorOnlyOnAPasswordlessInstance() {
        int accountId = newAccount();
        accountRepo.createToken(
                accountId,
                "verify-door-" + accountId,
                TokenType.VERIFY_EMAIL,
                Instant.now().plus(Duration.ofHours(1)));

        assertTrue(
                service.lookup("verify-door-" + accountId).isEmpty(),
                "everywhere else the verification mail only verifies");

        when(modeService.effectiveMode()).thenReturn(PasskeySettings.Mode.PASSWORDLESS);
        try {
            assertEquals(
                    accountId,
                    service.lookup("verify-door-" + accountId).orElseThrow().id());
        } finally {
            when(modeService.effectiveMode()).thenReturn(PasskeySettings.Mode.OPTIONAL);
        }
    }

    @Test
    void theQrIssueTellsWhoeverMailAboutTheAccountReaches() {
        int accountId = newAccount();
        when(mailRecipientService.forAccount(accountId))
                .thenReturn(List.of(new MailRecipientService.Recipient("guardian@test.com", "Guardian", true)));

        var issued = service.issueCodeWithQr(accountId, null, PasskeyEnrollmentService.QR_TTL, "ua", null);

        assertEquals(8, issued.code().length());
        assertFalse(issued.qrPng().isBlank());
        verify(emailService).sendPasskeyCodeIssuedNotice(any(), any(), any());
    }

    @Test
    void onboardingAgainWipesThePasskeysAndSaysWhetherMailWent() {
        int accountId = newAccount();
        var factor = twoFactorRepo.createFactor(
                accountId, dev.chojo.ember.feature.twofactor.entity.TwoFactorKind.WEBAUTHN, "Passkey");
        twoFactorRepo.createWebAuthn(
                factor.id(),
                ("onboard-" + factor.id()).getBytes(),
                new byte[] {1},
                0,
                null,
                List.of("internal"),
                "none",
                new byte[] {1, 2},
                true,
                false,
                true,
                true);
        assertTrue(twoFactorRepo.hasSignInPasskey(accountId));

        when(authService.sendPasswordSetup(accountId)).thenReturn(true);
        assertTrue(service.onboardAgain(accountId, accountId, "ua", null));
        assertFalse(twoFactorRepo.hasSignInPasskey(accountId), "every sign-in passkey is disabled");

        when(authService.sendPasswordSetup(accountId)).thenReturn(false);
        assertFalse(
                service.onboardAgain(accountId, accountId, "ua", null),
                "an unreachable account is told the QR code is the way");
    }

    @Test
    void reachabilityIsTheMailRecipientsAnswer() {
        when(mailRecipientService.isReachable(42)).thenReturn(true);
        assertTrue(service.isReachable(42));
    }
}
