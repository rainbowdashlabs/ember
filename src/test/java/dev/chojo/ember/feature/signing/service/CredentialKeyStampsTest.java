/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.service;

import dev.chojo.ember.auth.TokenHasher;
import dev.chojo.ember.conf.file.elements.Api;
import dev.chojo.ember.conf.file.elements.WebAuthnSettings;
import dev.chojo.ember.feature.passkey.service.PasskeyService;
import dev.chojo.ember.feature.passkey.service.TestAuthenticator;
import dev.chojo.ember.feature.twofactor.entity.CredentialKeyStamp;
import dev.chojo.ember.feature.twofactor.entity.KeyStampKind;
import dev.chojo.ember.feature.twofactor.entity.WebAuthnCredential;
import dev.chojo.ember.feature.twofactor.repository.WebAuthnChallengeRepository;
import dev.chojo.ember.feature.twofactor.service.RelyingParties;
import dev.chojo.ember.feature.twofactor.service.SecondFactorCredentialStore;
import dev.chojo.ember.feature.twofactor.service.TwoFactorAuditService;
import dev.chojo.ember.feature.twofactor.service.WebAuthnCredentialStore;
import dev.chojo.ember.feature.twofactor.service.WebAuthnRelyingPartyFactory;
import dev.chojo.ember.feature.twofactor.service.WebAuthnService;
import dev.chojo.ember.lifecycle.Schedule;
import dev.chojo.ember.lifecycle.TaskScheduler;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Timestamps over credential public keys against a local timestamp service on the loopback interface: at
 * the registration of a security key and of a passkey, left empty when no service answers, and stamped
 * later by the retry under its own kind. Signing acts are covered in {@link InEmberSignatureProviderTest}.
 */
class CredentialKeyStampsTest extends RepositoryTestBase {
    private static final Duration DRAIN = Duration.ofSeconds(10);

    private static RelyingParties parties;
    private static WebAuthnChallengeRepository challenges;
    private static WebAuthnSettings settings;

    @BeforeAll
    static void relyingParty() throws Exception {
        settings = new WebAuthnSettings();
        var api = new Api();
        Field baseUrl = Api.class.getDeclaredField("baseUrl");
        baseUrl.setAccessible(true);
        baseUrl.set(api, "https://ember.test");
        var store = new WebAuthnCredentialStore(twoFactorRepo);
        parties = WebAuthnRelyingPartyFactory.build(
                settings, api, store, new SecondFactorCredentialStore(twoFactorRepo, store));
        challenges = new WebAuthnChallengeRepository(TokenHasher.forTesting("key-stamp-pepper"));
    }

    @Test
    void aSecurityKeyIsStampedRightAfterItsRegistration() throws Exception {
        int account = account();
        try (var tsa = LocalTimestampService.start()) {
            var scheduler = new TaskScheduler();
            var stamps = new CredentialKeyStamps(TestKeyStamps.asking(tsa.pinned()), twoFactorRepo, scheduler);

            enrolSecurityKey(account, stamps);
            scheduler.stop(DRAIN);

            var credential = onlyCredentialOf(account);
            var stamp = Objects.requireNonNull(credential.keyStamp());
            assertEquals(KeyStampKind.AT_REGISTRATION, stamp.kind());
            assertEquals(tsa.url(), stamp.service());
            KeyStampTokens.assertStamps(stamp, credential.publicKeyCose());
        }
    }

    @Test
    void aPasskeyIsStampedRightAfterItsRegistration() throws Exception {
        int account = account();
        try (var tsa = LocalTimestampService.start()) {
            var scheduler = new TaskScheduler();
            var stamps = new CredentialKeyStamps(TestKeyStamps.asking(tsa.pinned()), twoFactorRepo, scheduler);

            enrolPasskey(account, stamps);
            scheduler.stop(DRAIN);

            var credential = onlyCredentialOf(account);
            var stamp = Objects.requireNonNull(credential.keyStamp());
            assertEquals(KeyStampKind.AT_REGISTRATION, stamp.kind());
            KeyStampTokens.assertStamps(stamp, credential.publicKeyCose());
        }
    }

    @Test
    void aRegistrationNoServiceAnswersForSucceedsWithTheKeyLeftUnstamped() throws Exception {
        int account = account();
        var scheduler = new TaskScheduler();
        var stamps = new CredentialKeyStamps(
                TestKeyStamps.asking(LocalTimestampService.pinned(LocalTimestampService.unreachableUrl())),
                twoFactorRepo,
                scheduler);

        enrolSecurityKey(account, stamps);
        scheduler.stop(DRAIN);

        assertNull(onlyCredentialOf(account).keyStamp());
    }

    @Test
    void theRetryStampsAKeyLeftUnstampedAsStampedAfterItsRegistration() throws Exception {
        int account = account();
        enrolSecurityKey(account, TestKeyStamps.off(twoFactorRepo));
        assertNull(onlyCredentialOf(account).keyStamp());

        try (var tsa = LocalTimestampService.start()) {
            retryWith(TestKeyStamps.asking(tsa.pinned()), Instant.now().plus(Duration.ofHours(1)))
                    .stampLate();

            var credential = onlyCredentialOf(account);
            var stamp = Objects.requireNonNull(credential.keyStamp());
            assertEquals(KeyStampKind.AFTER_REGISTRATION, stamp.kind());
            KeyStampTokens.assertStamps(stamp, credential.publicKeyCose());
        }
    }

    @Test
    void theRetryLeavesFreshRegistrationsDisabledCredentialsAndStampedKeysAlone() throws Exception {
        int fresh = account();
        enrolSecurityKey(fresh, TestKeyStamps.off(twoFactorRepo));
        int disabled = account();
        enrolSecurityKey(disabled, TestKeyStamps.off(twoFactorRepo));
        int disabledFactor = onlyCredentialOf(disabled).factorId();
        twoFactorRepo.disableFactor(disabledFactor);
        int stamped = account();
        enrolSecurityKey(stamped, TestKeyStamps.off(twoFactorRepo));
        var earlier = new CredentialKeyStamp(
                new byte[] {1}, Instant.parse("2026-10-01T08:00:00Z"), "http://tsa.test", KeyStampKind.AT_REGISTRATION);
        twoFactorRepo.recordKeyStamp(onlyCredentialOf(stamped).factorId(), earlier);

        try (var tsa = LocalTimestampService.start()) {
            retryWith(TestKeyStamps.asking(tsa.pinned()), Instant.now()).stampLate();
            assertNull(onlyCredentialOf(fresh).keyStamp(), "registered too recently for the retry");

            retryWith(TestKeyStamps.asking(tsa.pinned()), Instant.now().plus(Duration.ofHours(1)))
                    .stampLate();
            assertNull(twoFactorRepo
                    .findWebAuthnByFactorId(disabledFactor)
                    .orElseThrow()
                    .keyStamp());
            var kept = Objects.requireNonNull(onlyCredentialOf(stamped).keyStamp());
            assertArrayEquals(earlier.token(), kept.token());
            assertEquals(KeyStampKind.AT_REGISTRATION, kept.kind());
        }
    }

    @Test
    void aSecondStampNeverReplacesTheFirst() {
        int account = account();
        enrolSecurityKey(account, TestKeyStamps.off(twoFactorRepo));
        int factorId = onlyCredentialOf(account).factorId();
        var first = new CredentialKeyStamp(
                new byte[] {1}, Instant.parse("2026-10-01T08:00:00Z"), "http://a.test", KeyStampKind.AT_REGISTRATION);
        var second = new CredentialKeyStamp(
                new byte[] {2}, Instant.parse("2026-10-02T08:00:00Z"), "http://b.test", KeyStampKind.AT_FIRST_SIGNING);

        twoFactorRepo.recordKeyStamp(factorId, first);

        assertFalse(twoFactorRepo.recordKeyStamp(factorId, second));
        var kept = Objects.requireNonNull(onlyCredentialOf(account).keyStamp());
        assertArrayEquals(first.token(), kept.token());
        assertEquals(first.stampedAt(), kept.stampedAt());
        assertEquals("http://a.test", kept.service());
    }

    @Test
    void withTimestampsOffNothingIsStampedOrAsked() {
        int account = account();
        var scheduler = new TaskScheduler();
        var stamps = new CredentialKeyStamps(TestKeyStamps.timestampsOff(), twoFactorRepo, scheduler);

        enrolSecurityKey(account, stamps);
        stamps.stampLate();
        scheduler.stop(DRAIN);

        assertNull(onlyCredentialOf(account).keyStamp());
    }

    @Test
    void theRetryRunsDaily() {
        var stamps = TestKeyStamps.off(twoFactorRepo);
        var task = stamps.scheduledTasks().getFirst();

        assertEquals("credential-key-stamps", task.name());
        assertEquals(Schedule.fixedDelay(Duration.ofMinutes(15), Duration.ofDays(1)), task.schedule());
        task.work().run();
    }

    private static CredentialKeyStamps retryWith(TimestampServices timestamps, Instant now) {
        return new CredentialKeyStamps(
                timestamps, twoFactorRepo, new TaskScheduler(), Clock.fixed(now, ZoneOffset.UTC));
    }

    private static void enrolSecurityKey(int account, CredentialKeyStamps stamps) {
        var service = new WebAuthnService(
                parties, twoFactorRepo, new TwoFactorAuditService(twoFactorRepo), challenges, settings, stamps);
        var key = new TestAuthenticator();
        var start = service.startRegistration(account, "key@test.com", "Key");
        service.finishRegistration(
                        account, start.challengeToken(), key.register(start.optionsJson()), "Key", null, null)
                .orElseThrow();
    }

    private static void enrolPasskey(int account, CredentialKeyStamps stamps) {
        var service = new PasskeyService(
                parties, twoFactorRepo, new TwoFactorAuditService(twoFactorRepo), challenges, settings, stamps);
        var passkey = new TestAuthenticator();
        var start = service.startCreation(account, "passkey@test.com", "Passkey");
        service.finishCreation(
                        account, start.challengeToken(), passkey.register(start.optionsJson()), "Passkey", null, null)
                .orElseThrow();
    }

    private static WebAuthnCredential onlyCredentialOf(int account) {
        var credentials = twoFactorRepo.findActiveWebAuthnForAccount(account);
        assertEquals(1, credentials.size());
        return credentials.getFirst();
    }

    private static int account() {
        return accountRepo
                .create("key-stamp-" + UUID.randomUUID() + "@test.com", "Karin", "Muster", true)
                .id();
    }
}
