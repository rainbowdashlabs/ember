/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.service;

import dev.chojo.ember.feature.signing.entity.Signer;
import dev.chojo.ember.feature.signing.entity.SignerEntry;
import dev.chojo.ember.util.Sha256;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.UUID;

/**
 * The input of a signing challenge as it is hashed, written in the one way every challenge layout shares:
 * each part as a four-byte big-endian length followed by that many bytes, so no two different sets of parts
 * can produce the same input. Text is UTF-8 exactly as given, without any normalisation; ids are four-byte
 * big-endian signed integers; a uid is its sixteen bytes, most significant first.
 */
final class ChallengeInput {
    private final MessageDigest digest = Sha256.digest();

    /**
     * @param label the label that opens the input and names its layout
     */
    ChallengeInput(String label) {
        part(label);
    }

    ChallengeInput part(String text) {
        return part(text.getBytes(StandardCharsets.UTF_8));
    }

    ChallengeInput part(byte[] bytes) {
        digest.update(integer(bytes.length));
        digest.update(bytes);
        return this;
    }

    ChallengeInput part(int id) {
        return part(integer(id));
    }

    ChallengeInput part(UUID uid) {
        return part(ByteBuffer.allocate(2 * Long.BYTES)
                .putLong(uid.getMostSignificantBits())
                .putLong(uid.getLeastSignificantBits())
                .array());
    }

    /** A count, written as a bare four-byte integer rather than as a part, before the parts it counts. */
    ChallengeInput count(int count) {
        digest.update(integer(count));
        return this;
    }

    /** The signer's capacity as text, then the member id, or a part of length zero for none. */
    ChallengeInput signer(Signer signer) {
        part(
                switch (signer.capacity()) {
                    case ACCOUNT_HOLDER -> "account-holder";
                    case GUARDIAN -> "guardian";
                    case MEMBER_THROUGH_ACCOUNT -> "member-through-account";
                });
        Integer memberId = signer.memberId();
        return part(memberId == null ? new byte[0] : integer(memberId));
    }

    /** The number of entries as a bare count, then each entry's field name and value as two parts. */
    ChallengeInput entries(List<SignerEntry> entries) {
        count(entries.size());
        for (SignerEntry entry : entries) {
            part(entry.field());
            part(entry.value());
        }
        return this;
    }

    /** @return the SHA-256 of everything written, thirty-two bytes */
    byte[] hash() {
        return digest.digest();
    }

    private static byte[] integer(int value) {
        return ByteBuffer.allocate(Integer.BYTES).putInt(value).array();
    }
}
