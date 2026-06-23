/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.entity;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.json.JsonMapper;
import dev.chojo.ember.feature.storage.backend.StorageBackendType;
import dev.chojo.ember.feature.storage.credential.EncryptedBlob;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * JSON-friendly variant of {@link StationStorageBackendConfig} with the credential ciphertext
 * stripped and replaced by a fingerprint. Used for the {@code old_config} / {@code new_config}
 * columns on every {@code storage_backend_audit} row so a reader can see the full non-secret
 * shape and prove credentials changed between rows, without ever touching the raw ciphertext.
 *
 * <p>The JSON shape mirrors {@link StationStorageBackendConfig} ({@code type} discriminator,
 * variant per backend) so an admin reading the audit table sees the same structure they would
 * see from the override repository.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = RedactedStationConfig.S3Redacted.class, name = "S3"),
    @JsonSubTypes.Type(value = RedactedStationConfig.SmbRedacted.class, name = "SMB"),
    @JsonSubTypes.Type(value = RedactedStationConfig.SftpRedacted.class, name = "SFTP")
})
public sealed interface RedactedStationConfig {
    /**
     * Constant marker placed in the {@code credentials} field of every redacted variant.
     */
    String REDACTED_MARKER = "redacted";

    JsonMapper MAPPER = JsonMapper.builder().build();

    /**
     * Produces the redacted, audit-safe view of a station config.
     */
    static RedactedStationConfig from(StationStorageBackendConfig config) {
        return switch (config) {
            case StationStorageBackendConfig.S3Variant v ->
                new S3Redacted(
                        v.endpoint(),
                        v.region(),
                        v.bucket(),
                        v.pathStyle(),
                        v.sseAlgorithm().orElse(""),
                        v.basePath(),
                        REDACTED_MARKER,
                        fingerprint(v.credentials()));
            case StationStorageBackendConfig.SmbVariant v ->
                new SmbRedacted(
                        v.host(),
                        v.port(),
                        v.share(),
                        v.domain(),
                        v.basePath(),
                        v.seal(),
                        v.dfs(),
                        REDACTED_MARKER,
                        fingerprint(v.credentials()));
            case StationStorageBackendConfig.SftpVariant v ->
                new SftpRedacted(
                        v.host(),
                        v.port(),
                        v.username(),
                        v.knownHostsFingerprint(),
                        v.basePath(),
                        REDACTED_MARKER,
                        fingerprint(v.credentials()));
        };
    }

    /**
     * Convenience for callers that need the JSON string for the JSONB column.
     */
    static String toJson(StationStorageBackendConfig config) {
        try {
            return MAPPER.writeValueAsString(from(config));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize redacted station config", e);
        }
    }

    private static String fingerprint(EncryptedBlob blob) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(blob.iv());
            byte[] hash = digest.digest(blob.ciphertext());
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    StorageBackendType type();

    record S3Redacted(
            String endpoint,
            String region,
            String bucket,
            boolean pathStyle,
            String sseAlgorithm,
            String basePath,
            String credentials,
            String sha256OfCiphertext)
            implements RedactedStationConfig {
        @Override
        public StorageBackendType type() {
            return StorageBackendType.S3;
        }
    }

    record SmbRedacted(
            String host,
            int port,
            String share,
            String domain,
            String basePath,
            boolean seal,
            boolean dfs,
            String credentials,
            String sha256OfCiphertext)
            implements RedactedStationConfig {
        @Override
        public StorageBackendType type() {
            return StorageBackendType.SMB;
        }
    }

    record SftpRedacted(
            String host,
            int port,
            String username,
            String knownHostsFingerprint,
            String basePath,
            String credentials,
            String sha256OfCiphertext)
            implements RedactedStationConfig {
        @Override
        public StorageBackendType type() {
            return StorageBackendType.SFTP;
        }
    }
}
