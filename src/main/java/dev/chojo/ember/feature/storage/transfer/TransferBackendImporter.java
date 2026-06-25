/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.transfer;

import dev.chojo.ember.feature.storage.backend.StorageBackendResolver;
import dev.chojo.ember.feature.storage.credential.CredentialCipher;
import dev.chojo.ember.feature.storage.credential.StoredCredentials;
import dev.chojo.ember.feature.storage.entity.StationStorageBackendConfig;
import dev.chojo.ember.feature.storage.repository.StationStorageConfigRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.Optional;

/**
 * Destination-side counterpart to {@link TransferBackendDescriptorService}. Takes the
 * sealed-variant descriptor returned by the source's {@code /backend} endpoint and either
 * installs it on the destination's {@code station_storage_config} row (re-encrypting the
 * carried plaintext credentials under the destination's {@link CredentialCipher}) or
 * deletes any pre-existing override when the descriptor indicates the source used the
 * instance default.
 */
@Singleton
public class TransferBackendImporter {

    private final StationStorageConfigRepository configRepository;
    private final CredentialCipher credentialCipher;
    private final StorageBackendResolver resolver;

    @Inject
    public TransferBackendImporter(
            StationStorageConfigRepository configRepository,
            CredentialCipher credentialCipher,
            StorageBackendResolver resolver) {
        this.configRepository = configRepository;
        this.credentialCipher = credentialCipher;
        this.resolver = resolver;
    }

    /**
     * Applies the descriptor against {@code stationId}. {@link TransferBackendDescriptor.Local}
     * drops any existing override so future uploads route to the destination's instance
     * default; the other variants re-encrypt credentials and upsert the override row.
     *
     * @return {@code true} when a remote backend was installed (caller skips the byte-copy
     * loop), {@code false} when the source used LOCAL storage (caller proceeds with the
     * byte-copy loop)
     */
    public boolean apply(int stationId, TransferBackendDescriptor descriptor) {
        switch (descriptor) {
            case TransferBackendDescriptor.Local ignored -> {
                configRepository.delete(stationId);
                resolver.invalidateStation(stationId);
                return false;
            }
            case TransferBackendDescriptor.S3 v -> {
                configRepository.upsert(stationId, toS3Variant(v));
                resolver.invalidateStation(stationId);
                return true;
            }
            case TransferBackendDescriptor.Smb v -> {
                configRepository.upsert(stationId, toSmbVariant(v));
                resolver.invalidateStation(stationId);
                return true;
            }
            case TransferBackendDescriptor.Sftp v -> {
                configRepository.upsert(stationId, toSftpVariant(v));
                resolver.invalidateStation(stationId);
                return true;
            }
        }
    }

    private StationStorageBackendConfig.S3Variant toS3Variant(TransferBackendDescriptor.S3 v) {
        var credentials = credentialCipher.encrypt(new StoredCredentials.S3(v.accessKey(), v.secretKey()).toJson());
        return new StationStorageBackendConfig.S3Variant(
                v.endpoint(),
                v.region(),
                v.bucket(),
                v.pathStyle(),
                v.sseAlgorithm() == null || v.sseAlgorithm().isBlank()
                        ? Optional.empty()
                        : Optional.of(v.sseAlgorithm()),
                v.basePath(),
                credentials);
    }

    private StationStorageBackendConfig.SmbVariant toSmbVariant(TransferBackendDescriptor.Smb v) {
        var credentials = credentialCipher.encrypt(new StoredCredentials.Smb(v.username(), v.password()).toJson());
        return new StationStorageBackendConfig.SmbVariant(
                v.host(), v.port(), v.share(), v.domain(), v.basePath(), v.seal(), v.dfs(), credentials);
    }

    private StationStorageBackendConfig.SftpVariant toSftpVariant(TransferBackendDescriptor.Sftp v) {
        var credentials = credentialCipher.encrypt(new StoredCredentials.Sftp(
                        v.username(),
                        v.password() == null ? "" : v.password(),
                        v.privateKey() == null ? "" : v.privateKey())
                .toJson());
        return new StationStorageBackendConfig.SftpVariant(
                v.host(), v.port(), v.username(), v.knownHostsFingerprint(), v.basePath(), credentials);
    }
}
