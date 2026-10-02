/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.twofactor.service;

import com.yubico.webauthn.data.ByteArray;
import dev.chojo.ember.auth.TokenHasher;
import dev.chojo.ember.conf.file.elements.Api;
import dev.chojo.ember.conf.file.elements.WebAuthnSettings;
import dev.chojo.ember.feature.passkey.service.TestAuthenticator;
import dev.chojo.ember.feature.twofactor.entity.ChallengePurpose;
import dev.chojo.ember.feature.twofactor.entity.TwoFactorFactor;
import dev.chojo.ember.feature.twofactor.repository.WebAuthnChallengeRepository;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.HashSet;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class WebAuthnCeremoniesTest extends RepositoryTestBase {

    private static WebAuthnCeremonies ceremonies;
    private static WebAuthnChallengeRepository challengeRepo;

    @BeforeAll
    static void setup() throws Exception {
        var settings = new WebAuthnSettings();
        var api = new Api();
        Field baseUrl = Api.class.getDeclaredField("baseUrl");
        baseUrl.setAccessible(true);
        baseUrl.set(api, "https://ember.test");
        var store = new WebAuthnCredentialStore(twoFactorRepo);
        var parties = WebAuthnRelyingPartyFactory.build(
                settings, api, store, new SecondFactorCredentialStore(twoFactorRepo, store));
        challengeRepo = new WebAuthnChallengeRepository(TokenHasher.forTesting("repository-test-pepper"));
        ceremonies = new WebAuthnCeremonies(parties, twoFactorRepo, challengeRepo, settings);
    }

    private int newAccount() {
        return accountRepo
                .create("wa-cer-" + UUID.randomUUID() + "@test.com", "WA", "Cer", true)
                .id();
    }

    private String storedOptions(String token, ChallengePurpose purpose, int accountId) {
        return ceremonies
                .consumeChallenge(token, purpose, accountId)
                .orElseThrow()
                .optionsJson();
    }

    private Optional<TwoFactorFactor> register(
            int accountId, TestAuthenticator authenticator, CredentialRole role, boolean userVerified) {
        var start = ceremonies.startRegistration(accountId, "wa@test.com", "WA", role, ChallengePurpose.REGISTRATION);
        String options = storedOptions(start.challengeToken(), ChallengePurpose.REGISTRATION, accountId);
        return ceremonies.finishRegistration(
                accountId, options, authenticator.register(start.optionsJson(), userVerified), null, role);
    }

    private Optional<WebAuthnCeremonies.VerifiedAssertion> assertWith(
            int accountId, TestAuthenticator authenticator, CredentialRole role, boolean userVerified) {
        var start = ceremonies.startAssertion(role, accountId, ChallengePurpose.SECOND_FACTOR_ASSERTION);
        String request = storedOptions(start.challengeToken(), ChallengePurpose.SECOND_FACTOR_ASSERTION, accountId);
        return ceremonies.finishAssertion(role, request, authenticator.sign(start.optionsJson(), userVerified));
    }

    @Test
    void creationOptionsFollowTheRole() {
        int accountId = newAccount();
        var secondFactor = ceremonies.startRegistration(
                accountId, "wa@test.com", "WA", CredentialRole.SECOND_FACTOR, ChallengePurpose.REGISTRATION);
        assertTrue(secondFactor.optionsJson().contains("\"residentKey\":\"discouraged\""));
        assertTrue(secondFactor.optionsJson().contains("\"userVerification\":\"preferred\""));

        var passkey = ceremonies.startRegistration(
                accountId, "wa@test.com", "WA", CredentialRole.PASSKEY, ChallengePurpose.DEVICE_ENROLLMENT);
        assertTrue(passkey.optionsJson().contains("\"residentKey\":\"required\""));
        assertTrue(passkey.optionsJson().contains("\"userVerification\":\"required\""));
        assertEquals(
                accountId,
                challengeRepo.consume(passkey.challengeToken()).orElseThrow().accountId());
    }

    @Test
    void aSecondFactorIsCreatedWithoutUserVerification() {
        int accountId = newAccount();
        var factor = register(accountId, new TestAuthenticator(), CredentialRole.SECOND_FACTOR, false)
                .orElseThrow();

        assertEquals("Security Key", factor.label());
        var stored = twoFactorRepo.findActiveSecondFactorWebAuthnForAccount(accountId);
        assertEquals(1, stored.size());
        assertTrue(stored.getFirst().secondFactor());
        assertFalse(stored.getFirst().signIn());
        assertFalse(stored.getFirst().userVerified());
        assertFalse(twoFactorRepo.hasSignInPasskey(accountId));
    }

    @Test
    void aPasskeyIsRefusedWithoutUserVerification() {
        int accountId = newAccount();
        assertTrue(register(accountId, new TestAuthenticator(), CredentialRole.PASSKEY, false)
                .isEmpty());
        assertFalse(twoFactorRepo.hasSignInPasskey(accountId));
    }

    @Test
    void aPasskeyIsCreatedWithUserVerification() {
        int accountId = newAccount();
        var factor = register(accountId, new TestAuthenticator(), CredentialRole.PASSKEY, true)
                .orElseThrow();

        assertEquals("Passkey", factor.label());
        assertTrue(twoFactorRepo.hasSignInPasskey(accountId));
        assertTrue(twoFactorRepo
                .findActiveSecondFactorWebAuthnForAccount(accountId)
                .isEmpty());
    }

    @Test
    void aSecondFactorAssertionPassesWithoutUserVerification() {
        int accountId = newAccount();
        var authenticator = new TestAuthenticator();
        var factor = register(accountId, authenticator, CredentialRole.SECOND_FACTOR, false)
                .orElseThrow();

        var verified = assertWith(accountId, authenticator, CredentialRole.SECOND_FACTOR, false)
                .orElseThrow();
        assertEquals(factor.id(), verified.credential().factorId());
        assertFalse(verified.result().isUserVerified());
    }

    @Test
    void aPasskeyAssertionIsRefusedWithoutUserVerification() {
        int accountId = newAccount();
        var authenticator = new TestAuthenticator();
        register(accountId, authenticator, CredentialRole.PASSKEY, true).orElseThrow();

        assertTrue(assertWith(accountId, authenticator, CredentialRole.PASSKEY, false)
                .isEmpty());
        var verified = assertWith(accountId, authenticator, CredentialRole.PASSKEY, true)
                .orElseThrow();
        assertTrue(verified.credential().signIn());
    }

    @Test
    void aPasskeyDoesNotPassASecondFactorAssertion() {
        int accountId = newAccount();
        var authenticator = new TestAuthenticator();
        register(accountId, authenticator, CredentialRole.PASSKEY, true).orElseThrow();

        assertTrue(
                assertWith(accountId, authenticator, CredentialRole.SECOND_FACTOR, true)
                        .isEmpty(),
                "an empty allow list lets the library accept any credential, so the role must refuse it");
    }

    @Test
    void aSecondFactorDoesNotPassAPasskeyAssertion() {
        int accountId = newAccount();
        var authenticator = new TestAuthenticator();
        register(accountId, authenticator, CredentialRole.SECOND_FACTOR, true).orElseThrow();

        assertTrue(assertWith(accountId, authenticator, CredentialRole.PASSKEY, true)
                .isEmpty());
    }

    @Test
    void anAssertionStartForAPasskeyNamesNoAccount() {
        int accountId = newAccount();
        register(accountId, new TestAuthenticator(), CredentialRole.SECOND_FACTOR, true)
                .orElseThrow();

        var passkey = ceremonies.startAssertion(CredentialRole.PASSKEY, null, ChallengePurpose.PASSKEY_SIGN_IN);
        assertFalse(passkey.optionsJson().contains("allowCredentials"));
        assertNull(challengeRepo.consume(passkey.challengeToken()).orElseThrow().accountId());

        var secondFactor = ceremonies.startAssertion(
                CredentialRole.SECOND_FACTOR, accountId, ChallengePurpose.SECOND_FACTOR_ASSERTION);
        assertTrue(secondFactor.optionsJson().contains("allowCredentials"));
    }

    @Test
    void garbledInputFailsClosed() {
        int accountId = newAccount();
        assertTrue(ceremonies
                .finishRegistration(accountId, "not-json", "{}", null, CredentialRole.PASSKEY)
                .isEmpty());
        assertTrue(ceremonies
                .finishAssertion(CredentialRole.SECOND_FACTOR, "not-json", "{}")
                .isEmpty());
    }

    @Test
    void aChallengeIsSpentOnlyForItsPurposeAndAccount() {
        int accountId = newAccount();
        var start = ceremonies.startAssertion(CredentialRole.PASSKEY, accountId, ChallengePurpose.PASSKEY_TRIAL);
        assertTrue(ceremonies
                .consumeChallenge(start.challengeToken(), ChallengePurpose.STEPUP_ASSERTION, accountId)
                .isEmpty());
        assertTrue(
                ceremonies
                        .consumeChallenge(start.challengeToken(), ChallengePurpose.PASSKEY_TRIAL, accountId)
                        .isEmpty(),
                "a refused challenge is spent all the same");

        var foreign = ceremonies.startAssertion(CredentialRole.PASSKEY, accountId, ChallengePurpose.PASSKEY_TRIAL);
        assertTrue(ceremonies
                .consumeChallenge(foreign.challengeToken(), ChallengePurpose.PASSKEY_TRIAL, newAccount())
                .isEmpty());

        var anonymous = ceremonies.startAssertion(CredentialRole.PASSKEY, null, ChallengePurpose.PASSKEY_SIGN_IN);
        assertTrue(
                ceremonies
                        .consumeChallenge(anonymous.challengeToken(), ChallengePurpose.PASSKEY_SIGN_IN, accountId)
                        .isEmpty(),
                "a challenge without an account is not spendable where one is expected");

        String expired = "expired-" + UUID.randomUUID();
        challengeRepo.create(
                expired,
                ChallengePurpose.PASSKEY_SIGN_IN,
                null,
                "{}",
                Instant.now().minusSeconds(60));
        assertTrue(ceremonies
                .consumeChallenge(expired, ChallengePurpose.PASSKEY_SIGN_IN)
                .isEmpty());
    }

    @Test
    void challengeTokensAreFreshHex() {
        var seen = new HashSet<String>();
        for (int i = 0; i < 20; i++) {
            String token = WebAuthnCeremonies.newChallengeToken();
            assertTrue(token.matches("[0-9a-f]{64}"));
            assertTrue(seen.add(token));
        }
    }

    @Test
    void anAaguidBecomesAUuidOnlyWhenItSaysSomething() {
        assertNull(WebAuthnCeremonies.aaguidToUuid(null));
        assertNull(WebAuthnCeremonies.aaguidToUuid(new ByteArray(new byte[8])));
        assertNull(WebAuthnCeremonies.aaguidToUuid(new ByteArray(new byte[16])));

        UUID model = UUID.randomUUID();
        byte[] bytes = ByteBuffer.allocate(16)
                .putLong(model.getMostSignificantBits())
                .putLong(model.getLeastSignificantBits())
                .array();
        assertEquals(model, WebAuthnCeremonies.aaguidToUuid(new ByteArray(bytes)));
    }
}
