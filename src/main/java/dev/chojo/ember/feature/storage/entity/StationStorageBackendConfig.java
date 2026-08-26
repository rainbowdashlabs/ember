/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.entity;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import dev.chojo.ember.feature.storage.backend.StorageBackendType;
import dev.chojo.ember.feature.storage.credential.EncryptedBlob;
import tools.jackson.databind.json.JsonMapper;

import java.util.Optional;

/**
 * Typed sealed variant carried by every row in {@code station_storage_config}. Each variant
 * holds the non-secret connection settings as plain fields plus an {@link EncryptedBlob}
 * carrying the credentials encrypted at rest.
 *
 * <p>JSON shape: {@code "type"} is a discriminator and is one of {@code S3}, {@code SMB},
 * {@code SFTP}. Jackson polymorphic deserialization picks the right record from the value.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = StationStorageBackendConfig.S3Variant.class, name = "S3"),
    @JsonSubTypes.Type(value = StationStorageBackendConfig.SmbVariant.class, name = "SMB"),
    @JsonSubTypes.Type(value = StationStorageBackendConfig.SftpVariant.class, name = "SFTP")
})
public sealed interface StationStorageBackendConfig {
    JsonMapper MAPPER = JsonMapper.builder().build();

    /**
     * Deserializes a {@code StationStorageBackendConfig} from its JSONB representation.
     */
    static StationStorageBackendConfig parse(String json) {
        try {
            return MAPPER.readValue(json, StationStorageBackendConfig.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid station_storage_config payload", e);
        }
    }

    StorageBackendType type();

    /**
     * What this configuration names, with nothing about how it signs in.
     *
     * <p>Two configurations with the same key are the same place with a different secret, which is what makes
     * rotating a credential a write rather than a copy of everything stored there. Anything that would send
     * the bytes somewhere else belongs in it: the kind of backend, the host or endpoint, the bucket or share,
     * and the path underneath.
     *
     * @return the destination, as a comparable string
     */
    default String destinationKey() {
        return switch (this) {
            case S3Variant v -> String.join("|", "S3", v.endpoint(), v.region(), v.bucket(), v.basePath());
            case SmbVariant v ->
                String.join("|", "SMB", v.host(), String.valueOf(v.port()), v.share(), v.domain(), v.basePath());
            case SftpVariant v -> String.join("|", "SFTP", v.host(), String.valueOf(v.port()), v.basePath());
        };
    }

    /**
     * Serializes this variant to JSON for storage in the JSONB column.
     */
    default String toJson() {
        try {
            return MAPPER.writeValueAsString(this);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize station_storage_config", e);
        }
    }

    /**
     * S3 / S3-compatible backend override.
     */
    record S3Variant(
            String endpoint,
            String region,
            String bucket,
            boolean pathStyle,
            Optional<String> sseAlgorithm,
            String basePath,
            EncryptedBlob credentials)
            implements StationStorageBackendConfig {
        @Override
        public StorageBackendType type() {
            return StorageBackendType.S3;
        }
    }

    /**
     * SMB3 backend override.
     */
    record SmbVariant(
            String host,
            int port,
            String share,
            String domain,
            String basePath,
            boolean seal,
            boolean dfs,
            EncryptedBlob credentials)
            implements StationStorageBackendConfig {
        @Override
        public StorageBackendType type() {
            return StorageBackendType.SMB;
        }
    }

    /**
     * SFTP backend override.
     */
    record SftpVariant(
            String host,
            int port,
            String username,
            String knownHostsFingerprint,
            String basePath,
            EncryptedBlob credentials)
            implements StationStorageBackendConfig {
        @Override
        public StorageBackendType type() {
            return StorageBackendType.SFTP;
        }
    }
}
