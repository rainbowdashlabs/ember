/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.passkey.service;

import com.yubico.webauthn.AssertionResult;
import com.yubico.webauthn.RegisteredCredential;
import com.yubico.webauthn.RelyingParty;
import com.yubico.webauthn.data.ByteArray;
import dev.chojo.ember.auth.TokenHasher;
import dev.chojo.ember.conf.file.elements.Api;
import dev.chojo.ember.conf.file.elements.WebAuthnSettings;
import dev.chojo.ember.feature.twofactor.entity.ChallengePurpose;
import dev.chojo.ember.feature.twofactor.entity.TwoFactorKind;
import dev.chojo.ember.feature.twofactor.repository.WebAuthnChallengeRepository;
import dev.chojo.ember.feature.twofactor.service.RelyingParties;
import dev.chojo.ember.feature.twofactor.service.SecondFactorCredentialStore;
import dev.chojo.ember.feature.twofactor.service.TwoFactorAuditService;
import dev.chojo.ember.feature.twofactor.service.WebAuthnCredentialStore;
import dev.chojo.ember.feature.twofactor.service.WebAuthnRelyingPartyFactory;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

class PasskeyServiceTest extends RepositoryTestBase {

    private static WebAuthnChallengeRepository challengeRepo;
    private static RelyingParties realParties;
    private static PasskeyService service;

    // Shaped enough to parse; the verification itself is spied where a test needs to get past it.
    private static final String PARSEABLE_ASSERTION = parseableAssertion();

    private static String parseableAssertion() {
        var b64 = java.util.Base64.getUrlEncoder().withoutPadding();
        // rpIdHash (32) + flags (1) + counter (4): the smallest authenticator data that parses.
        String authData = b64.encodeToString(new byte[37]);
        String clientData = b64.encodeToString(
                "{\"type\":\"webauthn.get\",\"challenge\":\"AAAA\",\"origin\":\"https://ember.test\"}".getBytes());
        return "{\"id\":\"AA\",\"type\":\"public-key\",\"rawId\":\"AA\",\"response\":{\"authenticatorData\":\""
                + authData + "\",\"clientDataJSON\":\"" + clientData
                + "\",\"signature\":\"AA\",\"userHandle\":null},\"clientExtensionResults\":{}}";
    }

    @BeforeAll
    static void setup() throws Exception {
        var settings = new WebAuthnSettings();
        var api = new Api();
        setField(api, "baseUrl", "https://ember.test");
        var store = new WebAuthnCredentialStore(twoFactorRepo);
        var secondFactorStore = new SecondFactorCredentialStore(twoFactorRepo, store);
        realParties = WebAuthnRelyingPartyFactory.build(settings, api, store, secondFactorStore);
        challengeRepo = new WebAuthnChallengeRepository(TokenHasher.forTesting("repository-test-pepper"));
        service = new PasskeyService(
                realParties, twoFactorRepo, new TwoFactorAuditService(twoFactorRepo), challengeRepo, settings);
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }

    private PasskeyService serviceWith(RelyingParty passkeyView) {
        var parties = new RelyingParties(passkeyView, realParties.secondFactor(), false);
        return new PasskeyService(
                parties,
                twoFactorRepo,
                new TwoFactorAuditService(twoFactorRepo),
                challengeRepo,
                new WebAuthnSettings());
    }

    private int newAccount() {
        return accountRepo
                .create("pk-svc-" + UUID.randomUUID() + "@test.com", "PK", "Svc", true)
                .id();
    }

    @Test
    void creationAsksForResidentKeyAndUserVerification() {
        int accountId = newAccount();
        var start = service.startCreation(accountId, "pk@test.com", "PK User");

        assertTrue(start.optionsJson().contains("\"residentKey\":\"required\""));
        assertTrue(start.optionsJson().contains("\"userVerification\":\"required\""));

        var challenge = challengeRepo.consume(start.challengeToken()).orElseThrow();
        assertEquals(ChallengePurpose.REGISTRATION, challenge.purpose());
        assertEquals(accountId, challenge.accountId());
    }

    @Test
    void signInStartCarriesNoAccountAndNoAllowList() {
        var start = service.startSignIn();

        assertFalse(start.optionsJson().contains("allowCredentials"), "an allow list would name the account");
        assertTrue(start.optionsJson().contains("\"userVerification\":\"required\""));

        var challenge = challengeRepo.consume(start.challengeToken()).orElseThrow();
        assertEquals(ChallengePurpose.PASSKEY_SIGN_IN, challenge.purpose());
        assertNull(challenge.accountId(), "a passwordless challenge does not know the account yet");
    }

    @Test
    void signInRefusesUnknownChallenge() {
        assertTrue(service.finishSignIn("no-such-token", PARSEABLE_ASSERTION, "ua", null)
                .isEmpty());
    }

    @Test
    void signInChallengeIsNotSpendableAtCreation() {
        int accountId = newAccount();
        var start = service.startSignIn();
        assertTrue(
                service.finishCreation(accountId, start.challengeToken(), "{}", "Passkey", "ua", null)
                        .isEmpty(),
                "a challenge minted for one ceremony must not be spendable at another's finish");
    }

    @Test
    void trialAndStepUpChallengesDoNotCrossSpend() {
        int accountId = newAccount();

        var trial = service.startTrial(accountId);
        assertFalse(
                service.finishStepUp(accountId, trial.challengeToken(), PARSEABLE_ASSERTION),
                "a trial challenge must not clear a step-up");

        var stepUp = service.startStepUp(accountId);
        assertEquals(
                PasskeyService.TrialOutcome.FAILED,
                service.finishTrial(accountId, stepUp.challengeToken(), PARSEABLE_ASSERTION),
                "a step-up challenge must not pass a trial");

        var signIn = service.startSignIn();
        assertFalse(
                service.finishStepUp(accountId, signIn.challengeToken(), PARSEABLE_ASSERTION),
                "a sign-in challenge must not clear a step-up");
    }

    @Test
    void signInRefusesAssertionWithoutUserVerification() throws Exception {
        RelyingParty spied = spy(realParties.passkey());
        AssertionResult unverified = mock(AssertionResult.class);
        when(unverified.isSuccess()).thenReturn(true);
        when(unverified.isUserVerified()).thenReturn(false);
        doReturn(unverified).when(spied).finishAssertion(any());
        PasskeyService spiedService = serviceWith(spied);

        var start = spiedService.startSignIn();
        assertTrue(
                spiedService
                        .finishSignIn(start.challengeToken(), PARSEABLE_ASSERTION, "ua", null)
                        .isEmpty(),
                "an assertion without user verification must be refused rather than downgraded");
    }

    @Test
    void signInRefusesCredentialThatMayNotStartOne() throws Exception {
        // A second-factor security key that happens to answer the discoverable ceremony: the
        // verification succeeds, and the sign-in flag on the stored row is what refuses it.
        int accountId = newAccount();
        var factor = twoFactorRepo.createFactor(accountId, TwoFactorKind.WEBAUTHN, "Key");
        byte[] credentialId = ("sf-cred-" + factor.id()).getBytes();
        byte[] userHandle = new byte[64];
        userHandle[0] = 7;
        twoFactorRepo.createWebAuthn(
                factor.id(),
                credentialId,
                new byte[] {1},
                0,
                null,
                List.of("usb"),
                "none",
                userHandle,
                false,
                true,
                null,
                false);

        RelyingParty spied = spy(realParties.passkey());
        AssertionResult verified = mock(AssertionResult.class);
        when(verified.isSuccess()).thenReturn(true);
        when(verified.isUserVerified()).thenReturn(true);
        when(verified.getCredential())
                .thenReturn(RegisteredCredential.builder()
                        .credentialId(new ByteArray(credentialId))
                        .userHandle(new ByteArray(userHandle))
                        .publicKeyCose(new ByteArray(new byte[] {1}))
                        .signatureCount(1)
                        .build());
        doReturn(verified).when(spied).finishAssertion(any());
        PasskeyService spiedService = serviceWith(spied);

        var start = spiedService.startSignIn();
        assertTrue(
                spiedService
                        .finishSignIn(start.challengeToken(), PARSEABLE_ASSERTION, "ua", null)
                        .isEmpty(),
                "a credential that is not sign-in capable must not start a sign-in");
    }

    @Test
    void signInAcceptsASignInCredentialAndResolvesTheAccount() throws Exception {
        int accountId = newAccount();
        var factor = twoFactorRepo.createFactor(accountId, TwoFactorKind.WEBAUTHN, "Passkey");
        byte[] credentialId = ("pk-cred-" + factor.id()).getBytes();
        byte[] userHandle = new byte[64];
        userHandle[0] = 9;
        userHandle[1] = (byte) factor.id();
        twoFactorRepo.createWebAuthn(
                factor.id(),
                credentialId,
                new byte[] {1},
                0,
                null,
                List.of("internal"),
                "none",
                userHandle,
                true,
                false,
                true,
                true);

        RelyingParty spied = spy(realParties.passkey());
        AssertionResult verified = mock(AssertionResult.class);
        when(verified.isSuccess()).thenReturn(true);
        when(verified.isUserVerified()).thenReturn(true);
        when(verified.getSignatureCount()).thenReturn(1L);
        when(verified.getCredential())
                .thenReturn(RegisteredCredential.builder()
                        .credentialId(new ByteArray(credentialId))
                        .userHandle(new ByteArray(userHandle))
                        .publicKeyCose(new ByteArray(new byte[] {1}))
                        .signatureCount(1)
                        .build());
        doReturn(verified).when(spied).finishAssertion(any());
        PasskeyService spiedService = serviceWith(spied);

        var start = spiedService.startSignIn();
        var resolved = spiedService.finishSignIn(start.challengeToken(), PARSEABLE_ASSERTION, "ua", null);
        assertEquals(accountId, resolved.orElseThrow());
        assertNotNull(
                twoFactorRepo.findActiveFactors(accountId).getFirst().lastUsedAt(),
                "a sign-in must stamp the factor as used");
    }
}
