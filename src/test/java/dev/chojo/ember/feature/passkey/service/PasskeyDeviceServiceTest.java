/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.passkey.service;

import dev.chojo.ember.auth.TokenHasher;
import dev.chojo.ember.conf.file.elements.Api;
import dev.chojo.ember.conf.file.elements.WebAuthnSettings;
import dev.chojo.ember.feature.mail.service.EmailService;
import dev.chojo.ember.feature.mail.service.MailLocaleService;
import dev.chojo.ember.feature.passkey.repository.PasskeyDeviceRequestRepository;
import dev.chojo.ember.feature.system.repository.ApplicationSettingRepository;
import dev.chojo.ember.feature.twofactor.repository.WebAuthnChallengeRepository;
import dev.chojo.ember.feature.twofactor.service.SecondFactorCredentialStore;
import dev.chojo.ember.feature.twofactor.service.TwoFactorAuditService;
import dev.chojo.ember.feature.twofactor.service.WebAuthnCredentialStore;
import dev.chojo.ember.feature.twofactor.service.WebAuthnRelyingPartyFactory;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class PasskeyDeviceServiceTest extends RepositoryTestBase {

    private static final PasskeyDeviceRequestRepository deviceRepo = new PasskeyDeviceRequestRepository();
    private static PasskeyDeviceService service;

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
        service = new PasskeyDeviceService(
                deviceRepo,
                passkeyService,
                accountRepo,
                TokenHasher.forTesting("repository-test-pepper"),
                mock(EmailService.class),
                new MailLocaleService(accountRepo, new ApplicationSettingRepository()));
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }

    private int newAccount() {
        return accountRepo
                .create("device-" + UUID.randomUUID() + "@test.com", "Device", "Owner", true)
                .id();
    }

    @Test
    void theCodeOpensTheRequestAndAWrongOneNothing() {
        var request = service.createRequest("Firefox on Linux", "DE");
        assertEquals(8, request.code().length());

        var found = service.lookup(request.code()).orElseThrow();
        assertEquals("Firefox on Linux", found.requestedUserAgent());
        assertEquals("DE", found.requestedCountry());

        assertTrue(service.lookup("WRONGCOD").isEmpty(), "a wrong code earns nothing, not even a reason");
        assertTrue(
                service.lookup(request.code().toLowerCase()).isPresent(),
                "a code typed in lower case is the same code");
    }

    @Test
    void approvalHandsOutTheEnrolmentTokenExactlyOnce() {
        int accountId = newAccount();
        var request = service.createRequest("Chrome on Windows", "DE");

        assertEquals(
                PasskeyDeviceService.PollStatus.PENDING,
                service.poll(request.pollSecret()).status());
        assertTrue(service.approve(accountId, request.code()));
        assertFalse(service.approve(accountId, request.code()), "an approval happens exactly once");

        var first = service.poll(request.pollSecret());
        assertEquals(PasskeyDeviceService.PollStatus.APPROVED, first.status());
        assertNotNull(first.enrollToken(), "the first poll after the approval carries the token");

        var second = service.poll(request.pollSecret());
        assertEquals(PasskeyDeviceService.PollStatus.APPROVED, second.status());
        assertNull(second.enrollToken(), "the token is delivered exactly once");
    }

    @Test
    void theEnrolmentTokenHasExactlyOnePower() {
        int accountId = newAccount();
        var request = service.createRequest("Safari on iPhone", null);
        service.approve(accountId, request.code());
        String enrollToken = service.poll(request.pollSecret()).enrollToken();

        var ceremony = service.beginEnrollment(enrollToken).orElseThrow();
        assertNotNull(ceremony.optionsJson());

        // The claim happens at the finish; a garbage ceremony burns the token rather than
        // leaving it spendable a second time.
        assertFalse(service.finishEnrollment(enrollToken, ceremony.challengeToken(), "{}", null));
        assertFalse(
                service.finishEnrollment(enrollToken, ceremony.challengeToken(), "{}", null),
                "a spent token opens nothing");
        assertTrue(service.beginEnrollment(enrollToken).isEmpty(), "a spent token opens no ceremony either");
    }

    @Test
    void anUnapprovedTokenAndAnUnknownSecretOpenNothing() {
        var request = service.createRequest("Edge on Windows", null);
        assertTrue(service.beginEnrollment("no-such-token").isEmpty());
        assertEquals(
                PasskeyDeviceService.PollStatus.UNKNOWN,
                service.poll("no-such-secret").status());
        assertEquals(
                PasskeyDeviceService.PollStatus.PENDING,
                service.poll(request.pollSecret()).status());
    }
}
