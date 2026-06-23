/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.credential;

import com.fasterxml.jackson.databind.json.JsonMapper;

/**
 * Plaintext shapes for the credential payload that lives encrypted inside an
 * {@link EncryptedBlob}. One record per backend type; serialized by Jackson so the JSON shape
 * stays explicit instead of riding inside a free-form map.
 */
public final class StoredCredentials {
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private StoredCredentials() {}

    /** S3 static credentials. */
    public record S3(String accessKey, String secretKey) {
        public String toJson() {
            return write(this);
        }

        public static S3 parse(String json) {
            return read(json, S3.class);
        }
    }

    /** SMB user / password credentials. */
    public record Smb(String username, String password) {
        public String toJson() {
            return write(this);
        }

        public static Smb parse(String json) {
            return read(json, Smb.class);
        }
    }

    /**
     * SFTP credentials. Exactly one of {@code password} and {@code privateKey} is non-empty;
     * both empty is rejected by the route layer before the credentials reach this record.
     */
    public record Sftp(String username, String password, String privateKey) {
        public String toJson() {
            return write(this);
        }

        public static Sftp parse(String json) {
            return read(json, Sftp.class);
        }
    }

    private static String write(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize stored credentials", e);
        }
    }

    private static <T> T read(String json, Class<T> type) {
        try {
            return MAPPER.readValue(json, type);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to deserialize stored credentials", e);
        }
    }
}
