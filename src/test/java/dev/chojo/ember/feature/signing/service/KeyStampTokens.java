/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.service;

import dev.chojo.ember.feature.twofactor.entity.CredentialKeyStamp;
import dev.chojo.ember.util.Sha256;
import org.bouncycastle.asn1.nist.NISTObjectIdentifiers;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.tsp.TimeStampToken;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** Checks a credential key stamp the way an offline reader would, with Bouncy Castle and nothing of Ember's. */
final class KeyStampTokens {
    private KeyStampTokens() {}

    /**
     * Asserts that the stamp's token is a timestamp from the local test service over the SHA-256 of the
     * public key, and states the time the stamp records.
     *
     * @param stamp         the stamp
     * @param publicKeyCose the public key, COSE encoded as stored
     * @throws Exception when the token cannot be read
     */
    static void assertStamps(CredentialKeyStamp stamp, byte[] publicKeyCose) throws Exception {
        var info = new TimeStampToken(new CMSSignedData(stamp.token())).getTimeStampInfo();
        assertEquals(
                NISTObjectIdentifiers.id_sha256.getId(),
                info.getMessageImprintAlgOID().getId());
        assertArrayEquals(Sha256.digest().digest(publicKeyCose), info.getMessageImprintDigest());
        assertEquals(info.getGenTime().toInstant(), stamp.stampedAt());
        assertDoesNotThrow(() -> TimestampTrust.trustedUntil(stamp.token(), LocalTimestampService.root()));
    }
}
