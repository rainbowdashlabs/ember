/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.service;

import dev.chojo.ember.feature.signing.entity.Signer;
import dev.chojo.ember.feature.signing.entity.SignerCapacity;
import dev.chojo.ember.feature.signing.entity.SignerEntry;
import dev.chojo.ember.feature.signing.entity.SigningAct;
import dev.chojo.ember.feature.signing.entity.SigningRequest;
import dev.chojo.ember.util.Sha256;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The challenge is a pure function of the attempt: the same parts always give the same bytes, any part
 * that changes gives other bytes, and the layout is the one its documentation promises, so a reader can
 * recompute it from the evidence without Ember.
 */
class SigningChallengeTest {
    private static final UUID REQUEST = UUID.fromString("0e3c1d7a-5b2f-4f4e-9a51-6d3f8c2b1a90");
    private static final byte[] CONTENT = "%PDF-1.7 frozen content".getBytes(StandardCharsets.UTF_8);
    private static final String STATEMENT = "Ich bin einverstanden mit der Teilnahme am Zeltlager.";
    private static final String FIELD = "guardian1";
    private static final List<SignerEntry> ENTRIES =
            List.of(new SignerEntry("Telefon", "0171 2345678"), new SignerEntry("Notfallkontakt", "Oma Erika"));

    private static byte[] nonce(int fill) {
        byte[] nonce = new byte[SigningChallenge.NONCE_BYTES];
        Arrays.fill(nonce, (byte) fill);
        return nonce;
    }

    private static SigningRequest request(
            byte[] content, String statement, Signer signer, String field, List<SignerEntry> entries) {
        return new SigningRequest(REQUEST, 1, content, statement, signer, field, entries);
    }

    private static SigningRequest base() {
        return request(CONTENT, STATEMENT, Signer.guardian(7, 11), FIELD, ENTRIES);
    }

    @Test
    void theSamePartsGiveTheSameChallenge() {
        assertArrayEquals(SigningChallenge.of(nonce(1), base()), SigningChallenge.of(nonce(1), base()));
        assertEquals(32, SigningChallenge.of(nonce(1), base()).length);
    }

    @Test
    void everyPartChangesTheChallenge() {
        byte[] original = SigningChallenge.of(nonce(1), base());
        List<byte[]> changed = List.of(
                SigningChallenge.of(nonce(2), base()),
                SigningChallenge.of(
                        nonce(1),
                        request(
                                "%PDF-1.7 other".getBytes(StandardCharsets.UTF_8),
                                STATEMENT,
                                Signer.guardian(7, 11),
                                FIELD,
                                ENTRIES)),
                SigningChallenge.of(nonce(1), request(CONTENT, STATEMENT, Signer.guardian(8, 11), FIELD, ENTRIES)),
                SigningChallenge.of(
                        nonce(1), request(CONTENT, STATEMENT + " ", Signer.guardian(7, 11), FIELD, ENTRIES)),
                SigningChallenge.of(nonce(1), request(CONTENT, STATEMENT, Signer.guardian(7, 12), FIELD, ENTRIES)),
                SigningChallenge.of(
                        nonce(1), request(CONTENT, STATEMENT, Signer.memberThroughAccount(7, 11), FIELD, ENTRIES)),
                SigningChallenge.of(nonce(1), request(CONTENT, STATEMENT, Signer.accountHolder(7), FIELD, ENTRIES)),
                SigningChallenge.of(
                        nonce(1), request(CONTENT, STATEMENT, Signer.guardian(7, 11), "guardian2", ENTRIES)),
                SigningChallenge.of(
                        nonce(1),
                        request(
                                CONTENT,
                                STATEMENT,
                                Signer.guardian(7, 11),
                                FIELD,
                                List.of(new SignerEntry("Telefon", "0171 2345679"), ENTRIES.getLast()))),
                SigningChallenge.of(
                        nonce(1),
                        request(CONTENT, STATEMENT, Signer.guardian(7, 11), FIELD, List.of(ENTRIES.getFirst()))));
        for (byte[] other : changed) {
            assertFalse(Arrays.equals(original, other));
        }
        assertEquals(
                changed.size(),
                changed.stream().map(Arrays::toString).distinct().count());
    }

    @Test
    void lengthPrefixesKeepPartsFromRunningIntoEachOther() {
        var split = List.of(new SignerEntry("ab", "c"));
        var shifted = List.of(new SignerEntry("a", "bc"));
        assertFalse(Arrays.equals(
                SigningChallenge.of(nonce(1), request(CONTENT, STATEMENT, Signer.accountHolder(7), FIELD, split)),
                SigningChallenge.of(nonce(1), request(CONTENT, STATEMENT, Signer.accountHolder(7), FIELD, shifted))));
    }

    @Test
    void theLayoutIsTheDocumentedOne() {
        var out = new ByteArrayOutputStream();
        part(out, SigningChallenge.LABEL.getBytes(StandardCharsets.UTF_8));
        part(out, nonce(1));
        part(out, Sha256.digest().digest(CONTENT));
        part(out, integer(7));
        part(out, STATEMENT.getBytes(StandardCharsets.UTF_8));
        part(out, "guardian".getBytes(StandardCharsets.UTF_8));
        part(out, integer(11));
        part(out, FIELD.getBytes(StandardCharsets.UTF_8));
        out.writeBytes(integer(2));
        for (SignerEntry entry : ENTRIES) {
            part(out, entry.field().getBytes(StandardCharsets.UTF_8));
            part(out, entry.value().getBytes(StandardCharsets.UTF_8));
        }

        assertArrayEquals(Sha256.digest().digest(out.toByteArray()), SigningChallenge.of(nonce(1), base()));
    }

    @Test
    void anAccountHolderSigningAloneWritesAnEmptyMemberPart() {
        var out = new ByteArrayOutputStream();
        part(out, SigningChallenge.LABEL.getBytes(StandardCharsets.UTF_8));
        part(out, nonce(1));
        part(out, Sha256.digest().digest(CONTENT));
        part(out, integer(7));
        part(out, STATEMENT.getBytes(StandardCharsets.UTF_8));
        part(out, "account-holder".getBytes(StandardCharsets.UTF_8));
        part(out, new byte[0]);
        part(out, FIELD.getBytes(StandardCharsets.UTF_8));
        out.writeBytes(integer(0));

        assertArrayEquals(
                Sha256.digest().digest(out.toByteArray()),
                SigningChallenge.of(nonce(1), request(CONTENT, STATEMENT, Signer.accountHolder(7), FIELD, List.of())));
    }

    @Test
    void theRecordedActGivesTheChallengeBack() {
        SigningRequest request = base();
        var act = new SigningAct(
                REQUEST,
                request.signer(),
                "Karin Muster",
                "Lena Muster",
                request.fieldName(),
                request.statement(),
                request.contentSha256(),
                request.entries(),
                nonce(3),
                Instant.parse("2026-10-08T10:00:00Z"),
                "203.0.113.0",
                "Firefox");

        assertArrayEquals(SigningChallenge.of(nonce(3), request), SigningChallenge.of(act));
    }

    @Test
    void aNonceOfAnotherLengthIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> SigningChallenge.of(new byte[16], base()));
    }

    @Test
    void aFieldFilledInTwiceIsRefused() {
        assertThrows(
                IllegalArgumentException.class,
                () -> request(
                        CONTENT,
                        STATEMENT,
                        Signer.accountHolder(7),
                        FIELD,
                        List.of(new SignerEntry("Telefon", "1"), new SignerEntry("Telefon", "2"))));
    }

    @Test
    void aSignerNamesAMemberExactlyWhenTheHolderDoesNotSignAlone() {
        assertThrows(IllegalArgumentException.class, () -> new Signer(SignerCapacity.ACCOUNT_HOLDER, 7, 11));
        assertThrows(IllegalArgumentException.class, () -> new Signer(SignerCapacity.GUARDIAN, 7, null));
    }

    private static void part(ByteArrayOutputStream out, byte[] bytes) {
        out.writeBytes(integer(bytes.length));
        out.writeBytes(bytes);
    }

    private static byte[] integer(int value) {
        return ByteBuffer.allocate(Integer.BYTES).putInt(value).array();
    }
}
