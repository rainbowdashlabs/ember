/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.service;

import dev.chojo.ember.feature.storage.credential.CredentialCipher;
import dev.chojo.ember.feature.storage.credential.StoredCredentials;
import dev.chojo.ember.feature.storage.entity.StationStorageBackendConfig;
import dev.chojo.ember.feature.storage.repository.StationStorageConfigRepository;
import dev.chojo.ember.feature.storage.transfer.TransferBackendDescriptor;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Builds the {@link TransferBackendDescriptor} that the source sends to the destination over
 * {@code GET /public/transfer/{token}/backend}. Stations without an override report
 * {@link TransferBackendDescriptor.Local}; stations with one decrypt their credentials and
 * carry plaintext on the wire so the destination can re-encrypt with its own key.
 */
@Singleton
public class TransferBackendDescriptorService {

    private final StationStorageConfigRepository configRepository;
    private final CredentialCipher credentialCipher;

    @Inject
    public TransferBackendDescriptorService(
            StationStorageConfigRepository configRepository, CredentialCipher credentialCipher) {
        this.configRepository = configRepository;
        this.credentialCipher = credentialCipher;
    }

    private static String emptyToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    /**
     * Returns the descriptor for the given station. Stations on the instance default report
     * {@link TransferBackendDescriptor.Local}.
     */
    public TransferBackendDescriptor describe(int stationId) {
        return configRepository
                .findOne(stationId)
                .map(row -> toDescriptor(row.config()))
                .orElseGet(TransferBackendDescriptor.Local::new);
    }

    private TransferBackendDescriptor toDescriptor(StationStorageBackendConfig config) {
        return switch (config) {
            case StationStorageBackendConfig.S3Variant v -> {
                var creds = StoredCredentials.S3.parse(credentialCipher.decryptToString(v.credentials()));
                yield new TransferBackendDescriptor.S3(
                        v.endpoint(),
                        v.region(),
                        v.bucket(),
                        v.pathStyle(),
                        v.sseAlgorithm().orElse(null),
                        v.basePath(),
                        creds.accessKey(),
                        creds.secretKey());
            }
            case StationStorageBackendConfig.SmbVariant v -> {
                var creds = StoredCredentials.Smb.parse(credentialCipher.decryptToString(v.credentials()));
                yield new TransferBackendDescriptor.Smb(
                        v.host(),
                        v.port(),
                        v.share(),
                        v.domain(),
                        v.basePath(),
                        v.seal(),
                        v.dfs(),
                        creds.username(),
                        creds.password());
            }
            case StationStorageBackendConfig.SftpVariant v -> {
                var creds = StoredCredentials.Sftp.parse(credentialCipher.decryptToString(v.credentials()));
                yield new TransferBackendDescriptor.Sftp(
                        v.host(),
                        v.port(),
                        creds.username(),
                        v.knownHostsFingerprint(),
                        v.basePath(),
                        emptyToNull(creds.password()),
                        emptyToNull(creds.privateKey()));
            }
        };
    }
}
