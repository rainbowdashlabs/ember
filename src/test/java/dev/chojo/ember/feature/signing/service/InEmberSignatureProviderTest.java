/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.chojo.ember.api.refusal.DocumentRefusal;
import dev.chojo.ember.api.refusal.RefusalResponse;
import dev.chojo.ember.auth.TokenHasher;
import dev.chojo.ember.conf.file.elements.Api;
import dev.chojo.ember.conf.file.elements.WebAuthnSettings;
import dev.chojo.ember.feature.mail.service.EmailService;
import dev.chojo.ember.feature.mail.service.MailLocaleService;
import dev.chojo.ember.feature.passkey.service.PasskeyService;
import dev.chojo.ember.feature.passkey.service.TestAuthenticator;
import dev.chojo.ember.feature.signing.entity.CompletedSigning;
import dev.chojo.ember.feature.signing.entity.SignatureLevel;
import dev.chojo.ember.feature.signing.entity.Signer;
import dev.chojo.ember.feature.signing.entity.SignerConfirmation.StepUpPassed;
import dev.chojo.ember.feature.signing.entity.SignerConfirmation.WebAuthnAssertion;
import dev.chojo.ember.feature.signing.entity.SignerEntry;
import dev.chojo.ember.feature.signing.entity.SigningCircumstances;
import dev.chojo.ember.feature.signing.entity.SigningEvidence;
import dev.chojo.ember.feature.signing.entity.SigningRequest;
import dev.chojo.ember.feature.signing.entity.SigningStart;
import dev.chojo.ember.feature.twofactor.entity.KeyStampKind;
import dev.chojo.ember.feature.twofactor.entity.StepUpProof;
import dev.chojo.ember.feature.twofactor.entity.TwoFactorKind;
import dev.chojo.ember.feature.twofactor.repository.WebAuthnChallengeRepository;
import dev.chojo.ember.feature.twofactor.service.BackupCodeService;
import dev.chojo.ember.feature.twofactor.service.RelyingParties;
import dev.chojo.ember.feature.twofactor.service.SecondFactorCredentialStore;
import dev.chojo.ember.feature.twofactor.service.TotpService;
import dev.chojo.ember.feature.twofactor.service.TwoFactorAuditService;
import dev.chojo.ember.feature.twofactor.service.TwoFactorService;
import dev.chojo.ember.feature.twofactor.service.WebAuthnCredentialStore;
import dev.chojo.ember.feature.twofactor.service.WebAuthnRelyingPartyFactory;
import dev.chojo.ember.feature.twofactor.service.WebAuthnService;
import dev.chojo.ember.lifecycle.TaskScheduler;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * Ember's own provider against real credentials: which proofs a start offers per kind of account, that a
 * passkey or security key answer is checked against this request and nothing else, and what the evidence
 * records. The provider seals nothing; sealing a request's state has tests of its own.
 */
class InEmberSignatureProviderTest extends RepositoryTestBase {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String ORIGIN = "https://ember.test";
    private static final Instant NOW = Instant.parse("2026-10-08T12:00:00Z");
    private static final byte[] CONTENT = "%PDF-1.7 consent to the camp".getBytes(StandardCharsets.UTF_8);
    private static final SigningCircumstances FROM = new SigningCircumstances("203.0.113.7", "Firefox");

    private static RelyingParties parties;
    private static WebAuthnService securityKeys;
    private static PasskeyService passkeys;
    private static TwoFactorService twoFactor;

    private final InEmberSignatureProvider provider = providerStampingWith(TestKeyStamps.timestampsOff());

    @BeforeAll
    static void relyingParty() throws Exception {
        var settings = new WebAuthnSettings();
        var api = new Api();
        Field baseUrl = Api.class.getDeclaredField("baseUrl");
        baseUrl.setAccessible(true);
        baseUrl.set(api, ORIGIN);
        var store = new WebAuthnCredentialStore(twoFactorRepo);
        parties = WebAuthnRelyingPartyFactory.build(
                settings, api, store, new SecondFactorCredentialStore(twoFactorRepo, store));
        var challenges = new WebAuthnChallengeRepository(TokenHasher.forTesting("signing-test-pepper"));
        var audit = new TwoFactorAuditService(twoFactorRepo);
        var keyStamps = TestKeyStamps.off(twoFactorRepo);
        securityKeys = new WebAuthnService(parties, twoFactorRepo, audit, challenges, settings, keyStamps);
        passkeys = new PasskeyService(parties, twoFactorRepo, audit, challenges, settings, keyStamps);
        twoFactor = new TwoFactorService(
                twoFactorRepo,
                mock(TotpService.class),
                mock(BackupCodeService.class),
                audit,
                accountRepo,
                mock(MailLocaleService.class),
                mock(EmailService.class));
    }

    @Test
    void anAccountWithOnlyAPasswordIsOfferedThePassword() {
        int account = account("Karin", "Muster");
        accountRepo.createCredential(account, "hash");

        assertEquals(
                Set.of(StepUpProof.PASSWORD),
                started(request(Signer.accountHolder(account))).acceptedProofs());
    }

    @Test
    void anAccountWithASecondFactorIsNeverOfferedThePasswordOrABackupCode() {
        int account = account("Karin", "Muster");
        accountRepo.createCredential(account, "hash");
        twoFactorRepo.createFactor(account, TwoFactorKind.TOTP, "App");
        var codes = twoFactorRepo.createFactor(account, TwoFactorKind.BACKUP_CODES, "Codes");
        twoFactorRepo.createBackupCode(codes.id(), "code-hash");
        assertTrue(twoFactor.availableProofs(account).contains(StepUpProof.BACKUP_CODE));

        assertEquals(
                Set.of(StepUpProof.TOTP),
                started(request(Signer.accountHolder(account))).acceptedProofs());
    }

    @Test
    void anAccountWithAPasskeyAndASecurityKeyIsOfferedBoth() {
        int account = account("Karin", "Muster");
        accountRepo.createCredential(account, "hash");
        enrolPasskey(account);
        enrolSecurityKey(account);

        assertEquals(
                Set.of(StepUpProof.PASSKEY, StepUpProof.SECURITY_KEY),
                started(request(Signer.accountHolder(account))).acceptedProofs());
    }

    @Test
    void anAccountWithAPasskeyAndAPasswordIsOfferedBoth() {
        int account = account("Karin", "Muster");
        accountRepo.createCredential(account, "hash");
        enrolPasskey(account);

        assertEquals(
                Set.of(StepUpProof.PASSKEY, StepUpProof.PASSWORD),
                started(request(Signer.accountHolder(account))).acceptedProofs());
    }

    @Test
    void anAccountWithNothingToConfirmWithCannotStart() {
        int account = account("Lena", "Muster");

        assertRefused(DocumentRefusal.SIGNING_NO_PROOF, () -> provider.start(request(Signer.accountHolder(account))));
    }

    @Test
    void aStartIssuesAFreshNonceAndTheChallengeForIt() {
        int account = account("Karin", "Muster");
        accountRepo.createCredential(account, "hash");
        SigningRequest request = request(Signer.accountHolder(account));

        var first = started(request);
        var second = started(request);

        assertEquals(SigningChallenge.NONCE_BYTES, first.nonce().length);
        assertFalse(Arrays.equals(first.nonce(), second.nonce()));
        assertArrayEquals(SigningChallenge.of(first.nonce(), request), first.challenge());
    }

    @Test
    void aSecurityKeyAnswerBindsTheActToTheDocument() {
        int account = account("Karin", "Muster");
        TestAuthenticator key = enrolSecurityKey(account);
        SigningRequest request = request(Signer.accountHolder(account));
        var start = started(request);
        CompletedSigning signed = provider.complete(request, answer(key, start, true));

        var evidence = assertInstanceOf(SigningEvidence.WebAuthnBound.class, signed.evidence());
        assertEquals(StepUpProof.SECURITY_KEY, evidence.proof());
        assertTrue(evidence.boundToDocument());
        assertTrue(evidence.userVerified());
        assertEquals("ember.test", evidence.relyingPartyId());
        assertArrayEquals(start.challenge(), evidence.challenge());
        assertArrayEquals(SigningChallenge.of(evidence.act()), evidence.challenge());
        assertArrayEquals(
                twoFactorRepo.findActiveWebAuthnForAccount(account).getFirst().publicKeyCose(),
                evidence.credentialPublicKeyCose());
        assertEquals(1, evidence.signatureCount());
        assertNull(evidence.credentialKeyStamp());
        assertEquals(SignatureLevel.SIMPLE, signed.level());
    }

    @Test
    void aPasskeyAnswerIsRecordedAsAPasskey() {
        int account = account("Karin", "Muster");
        TestAuthenticator passkey = enrolPasskey(account);
        SigningRequest request = request(Signer.accountHolder(account));
        var start = started(request);

        var evidence = provider.complete(request, answer(passkey, start, true)).evidence();

        assertEquals(StepUpProof.PASSKEY, evidence.proof());
        assertTrue(evidence.boundToDocument());
    }

    @Test
    void anUnstampedKeyIsStampedAtItsFirstSigningAndTheEvidenceCarriesTheToken() throws Exception {
        int account = account("Karin", "Muster");
        TestAuthenticator key = enrolSecurityKey(account);
        SigningRequest request = request(Signer.accountHolder(account));

        try (var tsa = LocalTimestampService.start()) {
            var stamping = providerStampingWith(TestKeyStamps.asking(tsa.pinned()));
            var start = assertInstanceOf(SigningStart.InEmber.class, stamping.start(request));
            var evidence = assertInstanceOf(
                    SigningEvidence.WebAuthnBound.class,
                    stamping.complete(request, answer(key, start, true)).evidence());

            var stamp = Objects.requireNonNull(evidence.credentialKeyStamp());
            assertEquals(KeyStampKind.AT_FIRST_SIGNING, stamp.kind());
            assertEquals(tsa.url(), stamp.service());
            KeyStampTokens.assertStamps(stamp, evidence.credentialPublicKeyCose());
            var kept = Objects.requireNonNull(twoFactorRepo
                    .findActiveWebAuthnForAccount(account)
                    .getFirst()
                    .keyStamp());
            assertArrayEquals(stamp.token(), kept.token());
            assertEquals(KeyStampKind.AT_FIRST_SIGNING, kept.kind());
            assertEquals(1, tsa.requests());
        }
    }

    @Test
    void aKeyStampedAtRegistrationIsCopiedIntoTheEvidenceWithoutAskingAgain() throws Exception {
        int account = account("Karin", "Muster");
        TestAuthenticator passkey = enrolPasskey(account);
        int factorId =
                twoFactorRepo.findActiveWebAuthnForAccount(account).getFirst().factorId();
        SigningRequest request = request(Signer.accountHolder(account));

        try (var tsa = LocalTimestampService.start()) {
            new CredentialKeyStamps(TestKeyStamps.asking(tsa.pinned()), twoFactorRepo, new TaskScheduler())
                    .stampRegistered(factorId);
            var registered = Objects.requireNonNull(
                    twoFactorRepo.findWebAuthnByFactorId(factorId).orElseThrow().keyStamp());

            var stamping = providerStampingWith(TestKeyStamps.asking(tsa.pinned()));
            var start = assertInstanceOf(SigningStart.InEmber.class, stamping.start(request));
            var evidence = assertInstanceOf(
                    SigningEvidence.WebAuthnBound.class,
                    stamping.complete(request, answer(passkey, start, true)).evidence());

            var stamp = Objects.requireNonNull(evidence.credentialKeyStamp());
            assertEquals(KeyStampKind.AT_REGISTRATION, stamp.kind());
            assertArrayEquals(registered.token(), stamp.token());
            assertEquals(registered.stampedAt(), stamp.stampedAt());
            assertEquals(registered.service(), stamp.service());
            assertEquals(1, tsa.requests());
        }
    }

    @Test
    void aSigningActWithoutAnyAnsweringServiceStillSucceedsAndRecordsTheKeyAsNotStamped() throws Exception {
        int account = account("Karin", "Muster");
        TestAuthenticator key = enrolSecurityKey(account);
        SigningRequest request = request(Signer.accountHolder(account));

        var stamping = providerStampingWith(
                TestKeyStamps.asking(LocalTimestampService.pinned(LocalTimestampService.unreachableUrl())));
        var start = assertInstanceOf(SigningStart.InEmber.class, stamping.start(request));
        var signed = stamping.complete(request, answer(key, start, true));

        var evidence = assertInstanceOf(SigningEvidence.WebAuthnBound.class, signed.evidence());
        assertNull(evidence.credentialKeyStamp());
        assertNull(
                twoFactorRepo.findActiveWebAuthnForAccount(account).getFirst().keyStamp());
    }

    @Test
    void theActRecordsOfficialNamesTheFieldTheEntriesAndWhereItCameFrom() {
        int guardian = account("Karin", "Muster");
        int child = stationMemberRepo
                .create(stationRepo.create("Signing " + UUID.randomUUID()).id(), account("Lena", "Muster"))
                .id();
        TestAuthenticator key = enrolSecurityKey(guardian);
        SigningRequest request = request(Signer.memberThroughAccount(guardian, child));
        var start = started(request);

        var act =
                provider.complete(request, answer(key, start, true)).evidence().act();

        assertEquals(request.requestUid(), act.requestUid());
        assertEquals("Karin Muster", act.accountHolderName());
        assertEquals("Lena Muster", act.memberName());
        assertEquals("Lena Muster", act.signerName());
        assertTrue(act.signer().throughAnotherAccount());
        assertEquals("participant", act.fieldName());
        assertEquals(request.statement(), act.statement());
        assertEquals(request.entries(), act.entries());
        assertArrayEquals(request.contentSha256(), act.contentSha256());
        assertArrayEquals(start.nonce(), act.nonce());
        assertEquals(NOW, act.signedAt());
        assertEquals("203.0.113.0", act.truncatedIp());
        assertEquals("Firefox", act.userAgent());
    }

    @Test
    void aGuardianSigningForAMemberIsTheSigner() {
        int guardian = account("Karin", "Muster");
        accountRepo.createCredential(guardian, "hash");
        int child = stationMemberRepo
                .create(stationRepo.create("Signing " + UUID.randomUUID()).id(), account("Lena", "Muster"))
                .id();
        SigningRequest request = request(Signer.guardian(guardian, child));
        var start = started(request);

        var act = provider.complete(request, passed(start, StepUpProof.PASSWORD))
                .evidence()
                .act();

        assertEquals("Karin Muster", act.signerName());
        assertEquals("Lena Muster", act.memberName());
        assertFalse(act.signer().throughAnotherAccount());
    }

    @Test
    void anAnswerToAnotherChallengeIsRefused() {
        int account = account("Karin", "Muster");
        TestAuthenticator key = enrolSecurityKey(account);
        SigningRequest request = request(Signer.accountHolder(account));
        var start = started(request);
        var other = started(request);

        WebAuthnAssertion signedOther = answer(key, other, true);
        var mixed = new WebAuthnAssertion(
                start.nonce(),
                signedOther.credentialId(),
                signedOther.clientDataJson(),
                signedOther.authenticatorData(),
                signedOther.signature(),
                signedOther.userHandle(),
                FROM);

        assertRefused(DocumentRefusal.SIGNING_CHALLENGE_MISMATCH, () -> provider.complete(request, mixed));
    }

    @Test
    void anAnswerForAnotherDocumentIsRefused() {
        int account = account("Karin", "Muster");
        TestAuthenticator key = enrolSecurityKey(account);
        SigningRequest request = request(Signer.accountHolder(account));
        var start = started(request);
        SigningRequest otherContent = new SigningRequest(
                request.requestUid(),
                request.stationId(),
                "%PDF-1.7 another document".getBytes(StandardCharsets.UTF_8),
                request.statement(),
                request.signer(),
                request.fieldName(),
                request.entries());

        assertRefused(
                DocumentRefusal.SIGNING_CHALLENGE_MISMATCH,
                () -> provider.complete(otherContent, answer(key, start, true)));
    }

    @Test
    void anAnswerOfAnotherTypeIsRefused() {
        int account = account("Karin", "Muster");
        TestAuthenticator key = enrolSecurityKey(account);
        SigningRequest request = request(Signer.accountHolder(account));
        var start = started(request);

        var created = withClientData(answer(key, start, true), "webauthn.create", start.challenge(), ORIGIN);

        assertRefused(DocumentRefusal.SIGNING_NOT_AN_ASSERTION, () -> provider.complete(request, created));
    }

    @Test
    void anAnswerFromAnotherSiteIsRefused() {
        int account = account("Karin", "Muster");
        TestAuthenticator key = enrolSecurityKey(account);
        SigningRequest request = request(Signer.accountHolder(account));
        var start = started(request);

        for (String origin : List.of("https://evil.test", "https://sub.ember.test", "http://ember.test")) {
            var foreign = withClientData(answer(key, start, true), "webauthn.get", start.challenge(), origin);
            assertRefused(DocumentRefusal.SIGNING_FOREIGN_ORIGIN, () -> provider.complete(request, foreign));
        }
    }

    @Test
    void anAnswerWithoutUserVerificationIsRefused() {
        int account = account("Karin", "Muster");
        TestAuthenticator key = enrolSecurityKey(account);
        SigningRequest request = request(Signer.accountHolder(account));
        var start = started(request);

        assertRefused(
                DocumentRefusal.SIGNING_NOT_USER_VERIFIED, () -> provider.complete(request, answer(key, start, false)));
    }

    @Test
    void anAnswerWithABrokenSignatureIsRefused() {
        int account = account("Karin", "Muster");
        TestAuthenticator key = enrolSecurityKey(account);
        SigningRequest request = request(Signer.accountHolder(account));
        var start = started(request);
        WebAuthnAssertion answer = answer(key, start, true);
        byte[] signature = answer.signature().clone();
        signature[signature.length - 1] ^= 1;
        var broken = new WebAuthnAssertion(
                answer.nonce(),
                answer.credentialId(),
                answer.clientDataJson(),
                answer.authenticatorData(),
                signature,
                answer.userHandle(),
                FROM);

        assertRefused(DocumentRefusal.SIGNING_ASSERTION_INVALID, () -> provider.complete(request, broken));
    }

    @Test
    void anotherAccountsKeyIsRefused() {
        int signer = account("Karin", "Muster");
        accountRepo.createCredential(signer, "hash");
        TestAuthenticator strangersKey = enrolSecurityKey(account("Fremde", "Person"));
        SigningRequest request = request(Signer.accountHolder(signer));
        var start = started(request);

        assertRefused(
                DocumentRefusal.SIGNING_ASSERTION_INVALID,
                () -> provider.complete(request, answer(strangersKey, start, true)));
    }

    @Test
    void anAuthenticatorAppCodeGivesUnboundEvidence() {
        int account = account("Karin", "Muster");
        twoFactorRepo.createFactor(account, TwoFactorKind.TOTP, "App");
        SigningRequest request = request(Signer.accountHolder(account));

        var evidence = provider.complete(request, passed(started(request), StepUpProof.TOTP))
                .evidence();

        assertInstanceOf(SigningEvidence.TotpUnbound.class, evidence);
        assertEquals(StepUpProof.TOTP, evidence.proof());
        assertFalse(evidence.boundToDocument());
    }

    @Test
    void thePasswordGivesUnboundEvidenceForAnAccountWithoutASecondFactor() {
        int account = account("Karin", "Muster");
        accountRepo.createCredential(account, "hash");
        SigningRequest request = request(Signer.accountHolder(account));

        var evidence = provider.complete(request, passed(started(request), StepUpProof.PASSWORD))
                .evidence();

        assertInstanceOf(SigningEvidence.PasswordUnbound.class, evidence);
        assertFalse(evidence.boundToDocument());
        assertNull(evidence.act().memberName());
    }

    @Test
    void thePasswordIsRefusedOnceASecondFactorIsEnrolled() {
        int account = account("Karin", "Muster");
        accountRepo.createCredential(account, "hash");
        twoFactorRepo.createFactor(account, TwoFactorKind.TOTP, "App");
        SigningRequest request = request(Signer.accountHolder(account));
        var start = started(request);

        assertRefused(
                DocumentRefusal.SIGNING_PROOF_NOT_ACCEPTED,
                () -> provider.complete(request, passed(start, StepUpProof.PASSWORD)));
    }

    @Test
    void aBackupCodeAnotherDeviceAndAnUnansweredKeyAreRefused() {
        int account = account("Karin", "Muster");
        accountRepo.createCredential(account, "hash");
        enrolPasskey(account);
        var codes = twoFactorRepo.createFactor(account, TwoFactorKind.BACKUP_CODES, "Codes");
        twoFactorRepo.createBackupCode(codes.id(), "code-hash");
        SigningRequest request = request(Signer.accountHolder(account));
        var start = started(request);

        for (StepUpProof proof : List.of(
                StepUpProof.BACKUP_CODE, StepUpProof.ANOTHER_DEVICE, StepUpProof.PASSKEY, StepUpProof.SECURITY_KEY)) {
            assertRefused(
                    DocumentRefusal.SIGNING_PROOF_NOT_ACCEPTED, () -> provider.complete(request, passed(start, proof)));
        }
    }

    private InEmberSignatureProvider providerStampingWith(TimestampServices timestamps) {
        return new InEmberSignatureProvider(
                twoFactor,
                new SigningAssertions(parties, twoFactorRepo, new WebAuthnSettings()),
                new SignerNames(accountRepo, memberNameResolver),
                new CredentialKeyStamps(timestamps, twoFactorRepo, new TaskScheduler()),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private int account(String firstName, String lastName) {
        return accountRepo
                .create("signing-" + UUID.randomUUID() + "@test.com", firstName, lastName, true)
                .id();
    }

    private static TestAuthenticator enrolSecurityKey(int account) {
        var key = new TestAuthenticator();
        var start = securityKeys.startRegistration(account, "key@test.com", "Key");
        securityKeys
                .finishRegistration(
                        account, start.challengeToken(), key.register(start.optionsJson()), "Key", null, null)
                .orElseThrow();
        return key;
    }

    private static TestAuthenticator enrolPasskey(int account) {
        var passkey = new TestAuthenticator();
        var start = passkeys.startCreation(account, "passkey@test.com", "Passkey");
        passkeys.finishCreation(
                        account, start.challengeToken(), passkey.register(start.optionsJson()), "Passkey", null, null)
                .orElseThrow();
        return passkey;
    }

    private static SigningRequest request(Signer signer) {
        return new SigningRequest(
                UUID.randomUUID(),
                1,
                CONTENT,
                "Ich bin einverstanden, dass mein Kind am Zeltlager teilnimmt.",
                signer,
                "participant",
                List.of(new SignerEntry("Telefon", "0171 2345678")));
    }

    private SigningStart.InEmber started(SigningRequest request) {
        return assertInstanceOf(SigningStart.InEmber.class, provider.start(request));
    }

    private static StepUpPassed passed(SigningStart.InEmber start, StepUpProof proof) {
        return new StepUpPassed(start.nonce(), proof, FROM);
    }

    private static WebAuthnAssertion answer(
            TestAuthenticator authenticator, SigningStart.InEmber start, boolean verified) {
        try {
            var requestJson = MAPPER.writeValueAsString(MAPPER.createObjectNode()
                    .set(
                            "publicKey",
                            MAPPER.createObjectNode()
                                    .put(
                                            "challenge",
                                            Base64.getUrlEncoder()
                                                    .withoutPadding()
                                                    .encodeToString(start.challenge()))
                                    .put("rpId", "ember.test")));
            JsonNode credential = MAPPER.readTree(authenticator.sign(requestJson, verified));
            JsonNode response = credential.path("response");
            JsonNode userHandle = response.path("userHandle");
            return new WebAuthnAssertion(
                    start.nonce(),
                    decode(credential.path("rawId").asText()),
                    decode(response.path("clientDataJSON").asText()),
                    decode(response.path("authenticatorData").asText()),
                    decode(response.path("signature").asText()),
                    userHandle.isTextual() ? decode(userHandle.asText()) : null,
                    FROM);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static WebAuthnAssertion withClientData(
            WebAuthnAssertion answer, String type, byte[] challenge, String origin) {
        try {
            byte[] clientData = MAPPER.writeValueAsBytes(MAPPER.createObjectNode()
                    .put("type", type)
                    .put("challenge", Base64.getUrlEncoder().withoutPadding().encodeToString(challenge))
                    .put("origin", origin));
            return new WebAuthnAssertion(
                    answer.nonce(),
                    answer.credentialId(),
                    clientData,
                    answer.authenticatorData(),
                    answer.signature(),
                    answer.userHandle(),
                    FROM);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] decode(String base64Url) {
        return Base64.getUrlDecoder().decode(base64Url);
    }

    private static void assertRefused(DocumentRefusal refusal, Executable call) {
        assertSame(refusal, assertThrows(RefusalResponse.class, call).refusal());
    }
}
