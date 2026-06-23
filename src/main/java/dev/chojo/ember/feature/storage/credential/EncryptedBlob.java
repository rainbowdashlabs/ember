/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.credential;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Base64;

/**
 * A {@link CredentialCipher} encryption result: the random per-encryption {@code iv} and the
 * {@code ciphertext} (with the GCM tag appended by the cipher). Jackson serializes both
 * fields as base64 strings so the JSONB column stays text-only.
 */
public record EncryptedBlob(byte[] iv, byte[] ciphertext) {

    @JsonCreator
    public static EncryptedBlob fromBase64(
            @JsonProperty("iv") String iv, @JsonProperty("ciphertext") String ciphertext) {
        return new EncryptedBlob(
                Base64.getDecoder().decode(iv), Base64.getDecoder().decode(ciphertext));
    }

    @JsonProperty("iv")
    public String ivBase64() {
        return Base64.getEncoder().encodeToString(iv);
    }

    @JsonProperty("ciphertext")
    public String ciphertextBase64() {
        return Base64.getEncoder().encodeToString(ciphertext);
    }
}
