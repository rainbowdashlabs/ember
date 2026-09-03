/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.passkey.service;

import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class PasskeyDeviceServiceTest extends RepositoryTestBase {

    private static final PasskeyDeviceRequestRepository deviceRepo = new PasskeyDeviceRequestRepository();
    private static final EmailService emailService = mock(EmailService.class);
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
                emailService,
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
    void aFinishedEnrolmentMintsTheFactorAndMailsTheNotice() throws Exception {
        int accountId = newAccount();
        var request = service.createRequest("Chrome on Android", "DE");
        service.approve(accountId, request.code());
        String enrollToken = service.poll(request.pollSecret()).enrollToken();
        var ceremony = service.beginEnrollment(enrollToken).orElseThrow();

        assertTrue(service.finishEnrollment(
                enrollToken, ceremony.challengeToken(), registrationResponse(ceremony.optionsJson()), "DE"));
        assertTrue(
                twoFactorRepo.hasSignInPasskey(accountId), "the ceremony that verified left a sign-in passkey behind");
        verify(emailService).sendPasskeyDeviceApprovedNotice(any(), any(), any(), any(), any());
    }

    /**
     * A registration response the relying party accepts: a fresh P-256 key, the ceremony's own
     * challenge, and a {@code none} attestation, which is exactly what the instance asks for.
     * Built by hand because no browser sits in this test, and the refusal paths alone never walk
     * the ceremony to its end.
     */
    private static String registrationResponse(String optionsJson) throws Exception {
        var mapper = new ObjectMapper();
        var options = mapper.readTree(optionsJson).path("publicKey");
        String challenge = options.path("challenge").asText();
        String rpId = options.path("rp").path("id").asText();

        var keyGen = KeyPairGenerator.getInstance("EC");
        keyGen.initialize(new ECGenParameterSpec("secp256r1"));
        var publicKey = (ECPublicKey) keyGen.generateKeyPair().getPublic();

        // COSE key: {1: 2 (EC2), 3: -7 (ES256), -1: 1 (P-256), -2: x, -3: y}
        var coseKey = new ByteArrayOutputStream();
        coseKey.write(0xA5);
        cborInt(coseKey, 1);
        cborInt(coseKey, 2);
        cborInt(coseKey, 3);
        cborInt(coseKey, -7);
        cborInt(coseKey, -1);
        cborInt(coseKey, 1);
        cborInt(coseKey, -2);
        cborBytes(coseKey, fixedLength(publicKey.getW().getAffineX(), 32));
        cborInt(coseKey, -3);
        cborBytes(coseKey, fixedLength(publicKey.getW().getAffineY(), 32));

        byte[] credentialId = new byte[16];
        new SecureRandom().nextBytes(credentialId);
        var authData = new ByteArrayOutputStream();
        authData.write(MessageDigest.getInstance("SHA-256").digest(rpId.getBytes(StandardCharsets.UTF_8)));
        authData.write(0x45); // user present, user verified, attested credential data
        authData.write(new byte[4]); // signature counter
        authData.write(new byte[16]); // aaguid
        authData.write(new byte[] {0, (byte) credentialId.length});
        authData.write(credentialId);
        authData.write(coseKey.toByteArray());

        // Attestation object: {"fmt": "none", "attStmt": {}, "authData": bytes}
        var attestation = new ByteArrayOutputStream();
        attestation.write(0xA3);
        cborText(attestation, "fmt");
        cborText(attestation, "none");
        cborText(attestation, "attStmt");
        attestation.write(0xA0);
        cborText(attestation, "authData");
        cborBytes(attestation, authData.toByteArray());

        var b64 = Base64.getUrlEncoder().withoutPadding();
        String clientData = mapper.writeValueAsString(mapper.createObjectNode()
                .put("type", "webauthn.create")
                .put("challenge", challenge)
                .put("origin", "https://ember.test"));
        var response = mapper.createObjectNode()
                .put("id", b64.encodeToString(credentialId))
                .put("rawId", b64.encodeToString(credentialId))
                .put("type", "public-key");
        response.putObject("clientExtensionResults");
        response.putObject("response")
                .put("clientDataJSON", b64.encodeToString(clientData.getBytes(StandardCharsets.UTF_8)))
                .put("attestationObject", b64.encodeToString(attestation.toByteArray()));
        return mapper.writeValueAsString(response);
    }

    /** One CBOR integer, covering the small values a COSE key needs. */
    private static void cborInt(ByteArrayOutputStream out, int value) {
        if (value >= 0) {
            out.write(value); // all our positives are below 24
        } else {
            out.write(0x20 | (-1 - value)); // all our negatives are above -25
        }
    }

    private static void cborBytes(ByteArrayOutputStream out, byte[] data) throws IOException {
        if (data.length < 24) {
            out.write(0x40 | data.length);
        } else if (data.length < 256) {
            out.write(0x58);
            out.write(data.length);
        } else {
            out.write(0x59);
            out.write(data.length >> 8);
            out.write(data.length & 0xff);
        }
        out.write(data);
    }

    private static void cborText(ByteArrayOutputStream out, String text) throws IOException {
        byte[] data = text.getBytes(StandardCharsets.UTF_8);
        out.write(0x60 | data.length); // all our strings are short
        out.write(data);
    }

    private static byte[] fixedLength(BigInteger coordinate, int length) {
        byte[] raw = coordinate.toByteArray();
        byte[] out = new byte[length];
        if (raw.length >= length) {
            System.arraycopy(raw, raw.length - length, out, 0, length);
        } else {
            System.arraycopy(raw, 0, out, length - raw.length, raw.length);
        }
        return out;
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
