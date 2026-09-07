/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.twofactor.service;

import com.yubico.webauthn.RelyingParty;
import com.yubico.webauthn.exception.AssertionFailedException;
import com.yubico.webauthn.exception.RegistrationFailedException;
import dev.chojo.ember.auth.TokenHasher;
import dev.chojo.ember.conf.file.elements.Api;
import dev.chojo.ember.conf.file.elements.WebAuthnSettings;
import dev.chojo.ember.feature.twofactor.entity.ChallengePurpose;
import dev.chojo.ember.feature.twofactor.repository.WebAuthnChallengeRepository;
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
    private static WebAuthnChallengeRepository challengeRepo;

    @BeforeAll
    static void setupService() throws Exception {
        var settings = new WebAuthnSettings();
        var api = new Api();
        setField(api, "baseUrl", "https://ember.test");
        var store = new WebAuthnCredentialStore(twoFactorRepo);
        var secondFactorStore = new SecondFactorCredentialStore(twoFactorRepo, store);
        var parties = WebAuthnRelyingPartyFactory.build(settings, api, store, secondFactorStore);
        var audit = new TwoFactorAuditService(twoFactorRepo);
        challengeRepo = new WebAuthnChallengeRepository(TokenHasher.forTesting("repository-test-pepper"));
        service = new WebAuthnService(parties, twoFactorRepo, audit, challengeRepo, settings);
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

        var challenge = challengeRepo.consume(start.challengeToken()).orElseThrow();
        assertEquals(accountId, challenge.accountId());
        assertEquals(ChallengePurpose.REGISTRATION, challenge.purpose());
    }

    @Test
    void startAssertionPersistsChallenge() {
        int accountId = newAccount();
        var start = service.startAssertion(accountId);
        assertNotNull(start.challengeToken());
        assertNotNull(start.optionsJson());

        var challenge = challengeRepo.consume(start.challengeToken()).orElseThrow();
        assertEquals(accountId, challenge.accountId());
        assertEquals(ChallengePurpose.SECOND_FACTOR_ASSERTION, challenge.purpose());
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
        assertTrue(challengeRepo.consume(start.challengeToken()).isEmpty());

        // Manually plant an expired challenge to exercise the expiry branch
        String expiredToken = "expired-" + UUID.randomUUID();
        challengeRepo.create(
                expiredToken,
                ChallengePurpose.SECOND_FACTOR_ASSERTION,
                accountId,
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
        var settings = new WebAuthnSettings();
        var api = new Api();
        setField(api, "baseUrl", "https://ember.test");
        var store = new WebAuthnCredentialStore(twoFactorRepo);
        var secondFactorStore = new SecondFactorCredentialStore(twoFactorRepo, store);
        RelyingParties real = WebAuthnRelyingPartyFactory.build(settings, api, store, secondFactorStore);
        RelyingParty spiedRp = spy(real.passkey());
        doThrow(new RegistrationFailedException(new IllegalArgumentException("nope")))
                .when(spiedRp)
                .finishRegistration(any());
        var parties = new RelyingParties(spiedRp, real.secondFactor(), false);
        var audit = new TwoFactorAuditService(twoFactorRepo);
        var spiedService = new WebAuthnService(parties, twoFactorRepo, audit, challengeRepo, settings);

        int accountId = newAccount();
        var start = spiedService.startRegistration(accountId, "rf@test.com", "RF");
        // A real (but throwaway) PublicKeyCredential JSON is hard to forge - we route the
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
    void finishAssertionRefusesACredentialThatIsNotASecondFactor() throws Exception {
        // For an account whose only credentials are passkeys the allow list is empty, and the
        // library then accepts any credential the account owns. The flag on the verified result
        // is the check that holds.
        int accountId = newAccount();
        var factor = twoFactorRepo.createFactor(
                accountId, dev.chojo.ember.feature.twofactor.entity.TwoFactorKind.WEBAUTHN, "Passkey");
        byte[] credentialId = ("wa-pk-" + factor.id()).getBytes();
        byte[] userHandle = new byte[64];
        userHandle[0] = 3;
        userHandle[1] = (byte) factor.id();
        twoFactorRepo.createWebAuthn(
                factor.id(),
                credentialId,
                new byte[] {1},
                0,
                null,
                java.util.List.of("internal"),
                "none",
                userHandle,
                true,
                false,
                true,
                true);

        var settings = new WebAuthnSettings();
        var api = new Api();
        setField(api, "baseUrl", "https://ember.test");
        var store = new WebAuthnCredentialStore(twoFactorRepo);
        var secondFactorStore = new SecondFactorCredentialStore(twoFactorRepo, store);
        RelyingParties real = WebAuthnRelyingPartyFactory.build(settings, api, store, secondFactorStore);
        RelyingParty spiedRp = spy(real.secondFactor());
        var result = org.mockito.Mockito.mock(com.yubico.webauthn.AssertionResult.class);
        org.mockito.Mockito.when(result.isSuccess()).thenReturn(true);
        org.mockito.Mockito.when(result.getCredential())
                .thenReturn(com.yubico.webauthn.RegisteredCredential.builder()
                        .credentialId(new com.yubico.webauthn.data.ByteArray(credentialId))
                        .userHandle(new com.yubico.webauthn.data.ByteArray(userHandle))
                        .publicKeyCose(new com.yubico.webauthn.data.ByteArray(new byte[] {1}))
                        .signatureCount(1)
                        .build());
        org.mockito.Mockito.doReturn(result).when(spiedRp).finishAssertion(any());
        var parties = new RelyingParties(real.passkey(), spiedRp, false);
        var audit = new TwoFactorAuditService(twoFactorRepo);
        var spiedService = new WebAuthnService(parties, twoFactorRepo, audit, challengeRepo, settings);

        var start = spiedService.startAssertion(accountId);
        String credentialJson = "{\"id\":\"AA\",\"type\":\"public-key\",\"rawId\":\"AA\","
                + "\"response\":{\"authenticatorData\":\""
                + java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[37])
                + "\",\"clientDataJSON\":\""
                + java.util.Base64.getUrlEncoder()
                        .withoutPadding()
                        .encodeToString(
                                "{\"type\":\"webauthn.get\",\"challenge\":\"AAAA\",\"origin\":\"https://ember.test\"}"
                                        .getBytes())
                + "\",\"signature\":\"AA\",\"userHandle\":null},\"clientExtensionResults\":{}}";
        assertFalse(
                spiedService.finishAssertion(accountId, start.challengeToken(), credentialJson),
                "a passkey must not satisfy a second-factor assertion");
    }

    @Test
    void finishAssertionRejectsWhenVerificationThrows() throws Exception {
        var settings = new WebAuthnSettings();
        var api = new Api();
        setField(api, "baseUrl", "https://ember.test");
        var store = new WebAuthnCredentialStore(twoFactorRepo);
        var secondFactorStore = new SecondFactorCredentialStore(twoFactorRepo, store);
        RelyingParties real = WebAuthnRelyingPartyFactory.build(settings, api, store, secondFactorStore);
        RelyingParty spiedRp = spy(real.secondFactor());
        doThrow(new AssertionFailedException("nope")).when(spiedRp).finishAssertion(any());
        var parties = new RelyingParties(real.passkey(), spiedRp, false);
        var audit = new TwoFactorAuditService(twoFactorRepo);
        var spiedService = new WebAuthnService(parties, twoFactorRepo, audit, challengeRepo, settings);

        int accountId = newAccount();
        var start = spiedService.startAssertion(accountId);
        String credentialJson = "{\"id\":\"AA\",\"type\":\"public-key\",\"rawId\":\"AA\","
                + "\"response\":{\"authenticatorData\":\"AA\",\"clientDataJSON\":\"AA\",\"signature\":\"AA\","
                + "\"userHandle\":null},\"clientExtensionResults\":{}}";
        assertFalse(spiedService.finishAssertion(accountId, start.challengeToken(), credentialJson));
    }
}
