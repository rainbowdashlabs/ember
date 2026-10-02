/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.core;

import dev.chojo.ember.conf.file.elements.StorageBackendSettings;
import dev.chojo.ember.feature.storage.core.BackendSummary.LocalSummary;
import dev.chojo.ember.feature.storage.core.BackendSummary.S3Summary;
import dev.chojo.ember.feature.storage.core.BackendSummary.SftpSummary;
import dev.chojo.ember.feature.storage.core.BackendSummary.SmbSummary;
import dev.chojo.ember.feature.storage.credential.EncryptedBlob;
import dev.chojo.ember.feature.storage.entity.StationStorageBackendConfig;
import dev.chojo.ember.util.Json;
import dev.chojo.ember.util.Sha256;
import org.jspecify.annotations.Nullable;

import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * The one way storage is told apart from its secrets, for the screens and for the history alike.
 *
 * <p>Two stores keep storage: a row of encrypted configuration for a station or an association, and the
 * configuration file for the instance. Both are read back through here, so neither can show a field the
 * other hides, and the history can prove a credential rotation for every owner.
 */
public final class BackendRedaction {
    private BackendRedaction() {}

    /**
     * What a station's or an association's storage is shown as.
     *
     * @param config the stored configuration, its credentials encrypted
     * @return the same destination with a fingerprint in place of the credentials
     */
    public static BackendSummary summaryOf(StationStorageBackendConfig config) {
        String fingerprint = fingerprint(config.credentials());
        return switch (config) {
            case StationStorageBackendConfig.S3Variant v ->
                new S3Summary(
                        v.endpoint(),
                        v.region(),
                        v.bucket(),
                        v.pathStyle(),
                        v.sseAlgorithm().orElse(""),
                        v.basePath(),
                        fingerprint);
            case StationStorageBackendConfig.SmbVariant v ->
                new SmbSummary(v.host(), v.port(), v.share(), v.domain(), v.basePath(), v.seal(), v.dfs(), fingerprint);
            case StationStorageBackendConfig.SftpVariant v ->
                new SftpSummary(
                        v.host(),
                        v.port(),
                        v.username(),
                        !v.knownHostsFingerprint().isBlank(),
                        v.basePath(),
                        fingerprint);
        };
    }

    /**
     * What the instance's storage is shown as.
     *
     * @param settings the settings as the configuration file holds them
     * @return the destination with a fingerprint of whatever credentials are kept encrypted
     */
    public static BackendSummary summaryOf(StorageBackendSettings settings) {
        return switch (settings.type()) {
            case LOCAL ->
                new LocalSummary(Objects.requireNonNullElse(settings.local().root(), ""));
            case S3 -> {
                var s3 = settings.s3();
                yield new S3Summary(
                        s3.endpoint(),
                        s3.region(),
                        s3.bucket(),
                        s3.pathStyle(),
                        Objects.requireNonNullElse(s3.sseAlgorithm(), ""),
                        s3.basePath(),
                        fingerprint(s3.accessKeyEnc(), s3.secretKeyEnc()));
            }
            case SMB -> {
                var smb = settings.smb();
                yield new SmbSummary(
                        smb.host(),
                        smb.port(),
                        smb.share(),
                        smb.domain(),
                        smb.basePath(),
                        smb.seal(),
                        smb.dfs(),
                        fingerprint(smb.passwordEnc()));
            }
            case SFTP -> {
                var sftp = settings.sftp();
                yield new SftpSummary(
                        sftp.host(),
                        sftp.port(),
                        sftp.username(),
                        !sftp.knownHostsFingerprint().isBlank(),
                        sftp.basePath(),
                        fingerprint(sftp.passwordEnc(), sftp.privateKeyEnc()));
            }
        };
    }

    /**
     * A summary as a history row keeps it.
     *
     * @param summary what to keep, or {@code null}
     * @return the JSON, or {@code null} for nothing
     */
    public static @Nullable String json(@Nullable BackendSummary summary) {
        if (summary == null) return null;
        try {
            return Json.MAPPER.writerFor(BackendSummary.class).writeValueAsString(summary);
        } catch (RuntimeException e) {
            throw new IllegalStateException("A storage summary could not be written as JSON", e);
        }
    }

    /**
     * The SHA-256 of the encrypted credentials, initialisation vectors included, or {@code null} when none
     * of them is kept encrypted.
     */
    private static @Nullable String fingerprint(@Nullable EncryptedBlob first, @Nullable EncryptedBlob second) {
        if (first == null && second == null) return null;
        MessageDigest digest = Sha256.digest();
        for (EncryptedBlob blob :
                Stream.of(first, second).filter(Objects::nonNull).toList()) {
            digest.update(blob.iv());
            digest.update(blob.ciphertext());
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static @Nullable String fingerprint(@Nullable EncryptedBlob only) {
        return fingerprint(only, null);
    }
}
