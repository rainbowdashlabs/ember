/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.backend;

import dev.chojo.ember.conf.file.elements.Storage;
import dev.chojo.ember.conf.file.elements.StorageBackendSettings;
import dev.chojo.ember.feature.storage.backend.local.LocalStorageBackend;
import dev.chojo.ember.feature.storage.backend.s3.S3BackendConfig;
import dev.chojo.ember.feature.storage.backend.s3.S3StorageBackend;
import dev.chojo.ember.feature.storage.backend.sftp.SftpBackendConfig;
import dev.chojo.ember.feature.storage.backend.sftp.SftpStorageBackend;
import dev.chojo.ember.feature.storage.backend.smb.SmbBackendConfig;
import dev.chojo.ember.feature.storage.backend.smb.SmbStorageBackend;
import dev.chojo.ember.feature.storage.credential.CredentialCipher;
import dev.chojo.ember.feature.storage.credential.StoredCredentials;
import dev.chojo.ember.feature.storage.entity.StationStorageBackendConfig;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Paths;
import java.util.Optional;

/**
 * Constructs the configured instance-default {@link StorageBackend} from {@link Storage}
 * config and exposes it as the single source of truth used by the resolver. The instance
 * default is built lazily on first call and reused for the lifetime of the process — the same
 * {@link StorageBackend} instance carries its own connection pool / SSH session / SDK client
 * so re-creating it would discard those.
 */
@Singleton
public class StorageBackendFactory {
    private static final Logger log = LoggerFactory.getLogger(StorageBackendFactory.class);

    private final Storage storageConfig;
    private final LocalStorageBackend localBackend;
    private final CredentialCipher credentialCipher;

    private StorageBackend instanceDefault;

    @Inject
    public StorageBackendFactory(
            Storage storageConfig, LocalStorageBackend localBackend, CredentialCipher credentialCipher) {
        this.storageConfig = storageConfig;
        this.localBackend = localBackend;
        this.credentialCipher = credentialCipher;
    }

    /**
     * Returns the always-on local backend, used by every local-pinned category regardless of
     * the configured instance default.
     */
    public LocalStorageBackend localBackend() {
        return localBackend;
    }

    /** Returns the configured instance-default backend, building it lazily on first call. */
    public synchronized StorageBackend instanceDefault() {
        if (instanceDefault == null) {
            instanceDefault = build(storageConfig.backend());
            log.info("Storage instance-default backend resolved to {}", instanceDefault.type());
        }
        return instanceDefault;
    }

    private StorageBackend build(StorageBackendSettings settings) {
        StorageBackendType type = settings.type();
        return switch (type) {
            case LOCAL -> buildLocal(settings.local());
            case SMB -> new SmbStorageBackend(toSmbConfig(settings.smb()));
            case SFTP -> new SftpStorageBackend(toSftpConfig(settings.sftp()));
            case S3 -> new S3StorageBackend(toS3Config(settings.s3()));
        };
    }

    private LocalStorageBackend buildLocal(StorageBackendSettings.LocalSettings local) {
        String root = local.root() == null || local.root().isBlank() ? "data" : local.root();
        if ("data".equals(root)) return localBackend;
        return new LocalStorageBackend(Paths.get(root));
    }

    private static SmbBackendConfig toSmbConfig(StorageBackendSettings.SmbSettings smb) {
        return new SmbBackendConfig(
                smb.host(),
                smb.port(),
                smb.share(),
                smb.domain(),
                smb.username(),
                smb.password(),
                smb.basePath(),
                smb.seal(),
                smb.dfs());
    }

    private static SftpBackendConfig toSftpConfig(StorageBackendSettings.SftpSettings sftp) {
        Optional<String> password =
                sftp.password() == null || sftp.password().isBlank() ? Optional.empty() : Optional.of(sftp.password());
        Optional<String> privateKey =
                sftp.privateKey() == null || sftp.privateKey().isBlank()
                        ? Optional.empty()
                        : Optional.of(sftp.privateKey());
        return new SftpBackendConfig(
                sftp.host(),
                sftp.port(),
                sftp.username(),
                password,
                privateKey,
                sftp.knownHostsFingerprint(),
                sftp.basePath());
    }

    /**
     * Builds a backend for a station's override row by decrypting its credentials and
     * combining them with the non-secret fields the row carries. Each call constructs a fresh
     * backend instance; the resolver caches it on top.
     */
    public StorageBackend buildForStation(StationStorageBackendConfig config) {
        return switch (config) {
            case StationStorageBackendConfig.S3Variant v -> new S3StorageBackend(toS3Config(v));
            case StationStorageBackendConfig.SmbVariant v -> new SmbStorageBackend(toSmbConfig(v));
            case StationStorageBackendConfig.SftpVariant v -> new SftpStorageBackend(toSftpConfig(v));
        };
    }

    private S3BackendConfig toS3Config(StationStorageBackendConfig.S3Variant v) {
        StoredCredentials.S3 creds = StoredCredentials.S3.parse(credentialCipher.decryptToString(v.credentials()));
        return new S3BackendConfig(
                v.endpoint(),
                v.region(),
                v.bucket(),
                creds.accessKey(),
                creds.secretKey(),
                v.pathStyle(),
                v.sseAlgorithm(),
                v.basePath());
    }

    private SmbBackendConfig toSmbConfig(StationStorageBackendConfig.SmbVariant v) {
        StoredCredentials.Smb creds = StoredCredentials.Smb.parse(credentialCipher.decryptToString(v.credentials()));
        return new SmbBackendConfig(
                v.host(),
                v.port(),
                v.share(),
                v.domain(),
                creds.username(),
                creds.password(),
                v.basePath(),
                v.seal(),
                v.dfs());
    }

    private SftpBackendConfig toSftpConfig(StationStorageBackendConfig.SftpVariant v) {
        StoredCredentials.Sftp creds = StoredCredentials.Sftp.parse(credentialCipher.decryptToString(v.credentials()));
        Optional<String> password = creds.password() == null || creds.password().isBlank()
                ? Optional.empty()
                : Optional.of(creds.password());
        Optional<String> privateKey =
                creds.privateKey() == null || creds.privateKey().isBlank()
                        ? Optional.empty()
                        : Optional.of(creds.privateKey());
        return new SftpBackendConfig(
                v.host(), v.port(), creds.username(), password, privateKey, v.knownHostsFingerprint(), v.basePath());
    }

    private static S3BackendConfig toS3Config(StorageBackendSettings.S3Settings s3) {
        Optional<String> sse = s3.sseAlgorithm() == null || s3.sseAlgorithm().isBlank()
                ? Optional.empty()
                : Optional.of(s3.sseAlgorithm());
        return new S3BackendConfig(
                s3.endpoint(),
                s3.region(),
                s3.bucket(),
                s3.accessKey(),
                s3.secretKey(),
                s3.pathStyle(),
                sse,
                s3.basePath());
    }
}
