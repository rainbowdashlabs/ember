/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.service;

import com.yubico.webauthn.RelyingParty;
import com.yubico.webauthn.exception.AssertionFailedException;
import com.yubico.webauthn.exception.RegistrationFailedException;
import dev.chojo.ember.conf.file.elements.Api;
import dev.chojo.ember.conf.file.elements.Auth;
import dev.chojo.ember.conf.file.elements.TwoFactorSettings;
import dev.chojo.ember.feature.account.entity.TokenType;
import dev.chojo.ember.feature.twofactor.service.TwoFactorAuditService;
import dev.chojo.ember.feature.twofactor.service.WebAuthnCredentialStore;
import dev.chojo.ember.feature.twofactor.service.WebAuthnRelyingPartyFactory;
import dev.chojo.ember.feature.twofactor.service.WebAuthnService;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;

class WebAuthnServiceTest extends RepositoryTestBase {

    private static WebAuthnService service;

    @BeforeAll
    static void setupService() throws Exception {
        var settings = new TwoFactorSettings();
        setField(settings, "enabled", true);
        var api = new Api();
        setField(api, "baseUrl", "https://ember.test");
        var auth = new Auth();
        var store = new WebAuthnCredentialStore(twoFactorRepo);
        var rp = WebAuthnRelyingPartyFactory.build(settings, api, store);
        var audit = new TwoFactorAuditService(twoFactorRepo);
        service = new WebAuthnService(rp, twoFactorRepo, audit, accountRepo, settings, auth);
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }

    private int newAccount() {
        return accountRepo
                .create("wa-svc-" + UUID.randomUUID() + "@test.com", "WA", "Svc", true)
                .id();
    }

    @Test
    void startRegistrationPersistsChallenge() {
        int accountId = newAccount();
        var start = service.startRegistration(accountId, "wa@test.com", "WA User");
        assertNotNull(start.challengeToken());
        assertNotNull(start.optionsJson());
        assertTrue(start.optionsJson().contains("\"challenge\""));

        var token = accountRepo.findToken(start.challengeToken()).orElseThrow();
        assertEquals(accountId, token.accountId());
        assertEquals(TokenType.TWO_FACTOR_WEBAUTHN_REG, token.tokenType());
    }

    @Test
    void startAssertionPersistsChallenge() {
        int accountId = newAccount();
        var start = service.startAssertion(accountId);
        assertNotNull(start.challengeToken());
        assertNotNull(start.optionsJson());

        var token = accountRepo.findToken(start.challengeToken()).orElseThrow();
        assertEquals(accountId, token.accountId());
        assertEquals(TokenType.TWO_FACTOR_WEBAUTHN_ASSERT, token.tokenType());
    }

    @Test
    void finishRegistrationRejectsMissingChallenge() {
        int accountId = newAccount();
        assertTrue(service.finishRegistration(accountId, "no-such-token", "{}", "Key", "ua", null)
                .isEmpty());
    }

    @Test
    void finishRegistrationRejectsWrongAccount() {
        int accountId = newAccount();
        var start = service.startRegistration(accountId, "wa@test.com", "WA");
        // Different account → consume but reject
        assertTrue(service.finishRegistration(newAccount(), start.challengeToken(), "{}", "Key", "ua", null)
                .isEmpty());
    }

    @Test
    void finishRegistrationRejectsMalformedJson() {
        int accountId = newAccount();
        var start = service.startRegistration(accountId, "wa@test.com", "WA");
        assertTrue(service.finishRegistration(
                        accountId, start.challengeToken(), "not-a-credential-json", "Key", "ua", null)
                .isEmpty());
    }

    @Test
    void finishAssertionRejectsMissingChallenge() {
        int accountId = newAccount();
        assertFalse(service.finishAssertion(accountId, "no-such-token", "{}"));
    }

    @Test
    void finishAssertionRejectsMalformedJson() {
        int accountId = newAccount();
        var start = service.startAssertion(accountId);
        assertFalse(service.finishAssertion(accountId, start.challengeToken(), "garbage"));
    }

    @Test
    void challengeIsSingleUseAndExpiry() {
        int accountId = newAccount();
        var start = service.startAssertion(accountId);
        // First failed finish consumes the token
        service.finishAssertion(accountId, start.challengeToken(), "{}");
        assertTrue(accountRepo.findToken(start.challengeToken()).isEmpty());

        // Manually plant an expired token to exercise the expiry branch
        String expiredToken = "expired-" + UUID.randomUUID();
        accountRepo.createToken(
                accountId,
                expiredToken,
                TokenType.TWO_FACTOR_WEBAUTHN_ASSERT,
                "{}",
                Instant.now().minusSeconds(60));
        assertFalse(service.finishAssertion(accountId, expiredToken, "{}"));
    }

    @Test
    void registrationStartSurfacesUserHandle() {
        int accountId = newAccount();
        var first = service.startRegistration(accountId, "wa@test.com", "WA");
        assertTrue(first.optionsJson().contains("\"id\""));
    }

    @Test
    void finishRegistrationRejectsWhenVerificationThrows() throws Exception {
        // Build a service with a spied RelyingParty so the real start* path keeps writing
        // valid options JSON, but the finish* call surfaces the verification failure branch.
        var settings = new TwoFactorSettings();
        setField(settings, "enabled", true);
        var api = new Api();
        setField(api, "baseUrl", "https://ember.test");
        var store = new WebAuthnCredentialStore(twoFactorRepo);
        RelyingParty realRp = WebAuthnRelyingPartyFactory.build(settings, api, store);
        RelyingParty spiedRp = spy(realRp);
        doThrow(new RegistrationFailedException(new IllegalArgumentException("nope")))
                .when(spiedRp)
                .finishRegistration(any());
        var audit = new TwoFactorAuditService(twoFactorRepo);
        var spiedService = new WebAuthnService(spiedRp, twoFactorRepo, audit, accountRepo, settings, new Auth());

        int accountId = newAccount();
        var start = spiedService.startRegistration(accountId, "rf@test.com", "RF");
        // A real (but throwaway) PublicKeyCredential JSON is hard to forge — we route the
        // parse-then-verify path by handing the route a credential JSON shaped enough to
        // parse but rigged to throw on verification.
        String credentialJson = "{\"id\":\"AA\",\"type\":\"public-key\",\"rawId\":\"AA\","
                + "\"response\":{\"attestationObject\":\"AA\",\"clientDataJSON\":\"AA\"},"
                + "\"clientExtensionResults\":{}}";
        var result =
                spiedService.finishRegistration(accountId, start.challengeToken(), credentialJson, "Key", "ua", null);
        assertTrue(result.isEmpty(), "verification failure must drop the registration and leave no factor row behind");
    }

    @Test
    void finishAssertionRejectsWhenVerificationThrows() throws Exception {
        var settings = new TwoFactorSettings();
        setField(settings, "enabled", true);
        var api = new Api();
        setField(api, "baseUrl", "https://ember.test");
        var store = new WebAuthnCredentialStore(twoFactorRepo);
        RelyingParty realRp = WebAuthnRelyingPartyFactory.build(settings, api, store);
        RelyingParty spiedRp = spy(realRp);
        doThrow(new AssertionFailedException("nope")).when(spiedRp).finishAssertion(any());
        var audit = new TwoFactorAuditService(twoFactorRepo);
        var spiedService = new WebAuthnService(spiedRp, twoFactorRepo, audit, accountRepo, settings, new Auth());

        int accountId = newAccount();
        var start = spiedService.startAssertion(accountId);
        String credentialJson = "{\"id\":\"AA\",\"type\":\"public-key\",\"rawId\":\"AA\","
                + "\"response\":{\"authenticatorData\":\"AA\",\"clientDataJSON\":\"AA\",\"signature\":\"AA\","
                + "\"userHandle\":null},\"clientExtensionResults\":{}}";
        assertFalse(spiedService.finishAssertion(accountId, start.challengeToken(), credentialJson));
    }
}
