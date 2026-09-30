/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.twofactor.service;

import org.apache.commons.codec.binary.Base32;

import java.nio.ByteBuffer;
import java.time.Instant;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * An authenticator app in a few lines: RFC 6238 with HMAC-SHA1, six digits and thirty seconds,
 * written against the JDK alone so the tests check the service against the standard rather than
 * against the library the service uses.
 */
final class TotpCodes {

    private TotpCodes() {}

    /** The code an app shows right now for this Base32 secret. */
    static String current(String base32Secret) {
        return at(base32Secret, Instant.now().getEpochSecond());
    }

    /** The code an app shows at this second for this Base32 secret. */
    static String at(String base32Secret, long epochSecond) {
        try {
            var mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(new Base32().decode(base32Secret), "HmacSHA1"));
            byte[] hash =
                    mac.doFinal(ByteBuffer.allocate(8).putLong(epochSecond / 30).array());
            int offset = hash[hash.length - 1] & 0x0f;
            int binary = ((hash[offset] & 0x7f) << 24)
                    | ((hash[offset + 1] & 0xff) << 16)
                    | ((hash[offset + 2] & 0xff) << 8)
                    | (hash[offset + 3] & 0xff);
            return String.format("%06d", binary % 1_000_000);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
