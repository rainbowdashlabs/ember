/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.push.service;

import dev.chojo.ember.feature.push.repository.VapidKeyRepository;
import dev.chojo.ember.feature.signing.service.SigningKeyWrap;
import dev.chojo.ember.feature.signing.service.SigningKeyWrapException;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.Signature;
import java.util.Base64;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The push key pair is made once and kept: every later start, and a second node, uses the same one,
 * and a key that does not open is an error rather than a reason to make a new one.
 */
class VapidKeysTest extends RepositoryTestBase {
    private static final String SECRET = Base64.getEncoder().encodeToString(new byte[32]);
    private static final String OTHER_SECRET = Base64.getEncoder().encodeToString(new byte[] {
        1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30,
        31, 32
    });

    private final VapidKeyRepository repository = new VapidKeyRepository();

    @BeforeEach
    void noKeyYet() {
        query("DELETE FROM push_vapid_key;").single(call()).delete();
    }

    @Test
    void theKeyIsMadeOnceAndKeptForEveryLaterStart() throws Exception {
        var first = new VapidKeys(repository, new SigningKeyWrap(SECRET));
        byte[] publicKey = first.publicKey();

        var later = new VapidKeys(repository, new SigningKeyWrap(SECRET));
        assertArrayEquals(publicKey, later.publicKey(), "a later start reads the same key");
        assertEquals(65, publicKey.length);

        var signature = Signature.getInstance("SHA256withECDSA");
        signature.initSign(later.keys().privateKey());
        signature.update("probe".getBytes(StandardCharsets.US_ASCII));
        var verifier = Signature.getInstance("SHA256withECDSA");
        verifier.initVerify(EcP256.publicKeyOf(publicKey));
        verifier.update("probe".getBytes(StandardCharsets.US_ASCII));
        assertTrue(verifier.verify(signature.sign()), "the private half belongs to the public one");
    }

    /** A key wrapped under another secret is not replaced, since that would end every subscription. */
    @Test
    void aKeyThatDoesNotOpenIsAnErrorAndStaysAsItIs() {
        byte[] publicKey = new VapidKeys(repository, new SigningKeyWrap(SECRET)).publicKey();

        var elsewhere = new VapidKeys(repository, new SigningKeyWrap(OTHER_SECRET));
        assertThrows(SigningKeyWrapException.class, elsewhere::keys);

        assertArrayEquals(publicKey, repository.find().orElseThrow().publicKey(), "the stored key is untouched");
    }

    /** Two nodes starting together: the second store is turned away and both read the first key. */
    @Test
    void aSecondKeyStoredAtTheSameTimeLosesToTheFirst() {
        assertTrue(repository.storeIfAbsent(new byte[] {1}, new byte[] {2}));
        assertFalse(repository.storeIfAbsent(new byte[] {3}, new byte[] {4}));

        assertArrayEquals(new byte[] {1}, repository.find().orElseThrow().publicKey());
    }
}
