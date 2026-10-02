/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.core;

import dev.chojo.ember.api.refusal.RefusalResponse;
import dev.chojo.ember.api.refusal.StorageRefusal;
import dev.chojo.ember.feature.federation.service.RemoteUrlValidator;
import dev.chojo.ember.feature.storage.core.BackendRequest.S3Request;
import dev.chojo.ember.feature.storage.core.BackendRequest.SftpRequest;
import dev.chojo.ember.feature.storage.core.BackendRequest.SmbRequest;
import dev.chojo.ember.feature.storage.credential.CredentialCipher;
import dev.chojo.ember.feature.storage.credential.EncryptedBlob;
import dev.chojo.ember.feature.storage.credential.StoredCredentials;
import dev.chojo.ember.feature.storage.entity.StationStorageBackendConfig;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.net.URI;
import java.util.Objects;
import java.util.Optional;

/**
 * The checks every typed-in storage passes before anything is stored, probed or moved, whoever owns it.
 *
 * <p>Two of them. The address must be one this instance may open a connection to, for a station, an
 * association and the instance alike, so none of them can point the instance at its own network. And the
 * credentials must be complete for the kind of storage: both keys for object storage, a name and a password
 * for a shared folder, exactly one of a password and a key for a file transfer server. A blank field counts
 * as a missing one, because a form sends blanks where nothing was typed.
 */
@Singleton
public class BackendValidation {
    private final CredentialCipher cipher;
    private final RemoteUrlValidator urlValidator;

    @Inject
    public BackendValidation(CredentialCipher cipher, RemoteUrlValidator urlValidator) {
        this.cipher = cipher;
        this.urlValidator = urlValidator;
    }

    /**
     * Refuses typed-in storage that is not usable; a request naming storage the instance already knows
     * passes as it is.
     *
     * @param request what came in
     * @throws RefusalResponse when the address is not one this instance may reach, or credentials are missing
     */
    public void requireUsable(BackendRequest request) {
        switch (request) {
            case S3Request r -> {
                requireAllowedHost(hostOf(r.endpoint()));
                if (isBlank(r.accessKey()) || isBlank(r.secretKey())) throw StorageRefusal.STORAGE_KEYS_MISSING.raise();
            }
            case SmbRequest r -> {
                requireAllowedHost(r.host());
                if (isBlank(r.username()) || isBlank(r.password())) {
                    throw StorageRefusal.STORAGE_SIGN_IN_MISSING.raise();
                }
            }
            case SftpRequest r -> {
                requireAllowedHost(r.host());
                if (isBlank(r.password()) == isBlank(r.privateKey())) {
                    throw StorageRefusal.STORAGE_SIGN_IN_AMBIGUOUS.raise();
                }
            }
            case BackendRequest.LocalRequest ignored -> {}
            case BackendRequest.ClusterStorageRequest ignored -> {}
        }
    }

    /**
     * The stored form of typed-in storage, checked and with its credentials encrypted.
     *
     * @param request what came in; S3, SMB or SFTP
     * @return the configuration to store, probe or move to
     * @throws RefusalResponse when the storage is not usable, or the request names no storage to type in
     */
    public StationStorageBackendConfig toConfig(BackendRequest request) {
        requireUsable(request);
        return switch (request) {
            case S3Request r ->
                new StationStorageBackendConfig.S3Variant(
                        r.endpoint(),
                        r.region(),
                        r.bucket(),
                        r.pathStyle(),
                        Optional.ofNullable(r.sseAlgorithm()).filter(s -> !s.isBlank()),
                        r.basePath(),
                        encrypt(new StoredCredentials.S3(r.accessKey(), r.secretKey()).toJson()));
            case SmbRequest r ->
                new StationStorageBackendConfig.SmbVariant(
                        r.host(),
                        r.port(),
                        r.share(),
                        r.domain(),
                        r.basePath(),
                        r.seal(),
                        r.dfs(),
                        encrypt(new StoredCredentials.Smb(r.username(), r.password()).toJson()));
            case SftpRequest r ->
                new StationStorageBackendConfig.SftpVariant(
                        r.host(),
                        r.port(),
                        r.username(),
                        r.knownHostsFingerprint(),
                        r.basePath(),
                        encrypt(new StoredCredentials.Sftp(r.username(), orBlank(r.password()), orBlank(r.privateKey()))
                                .toJson()));
            case BackendRequest.LocalRequest ignored -> throw StorageRefusal.STORAGE_DESTINATION_NOT_OFFERED.raise();
            case BackendRequest.ClusterStorageRequest ignored ->
                throw StorageRefusal.STORAGE_DESTINATION_NOT_OFFERED.raise();
        };
    }

    private EncryptedBlob encrypt(String credentials) {
        return cipher.encrypt(credentials);
    }

    private static @Nullable String hostOf(@Nullable String endpoint) {
        if (endpoint == null || endpoint.isBlank()) return null;
        try {
            String host = URI.create(endpoint.trim()).getHost();
            return host != null ? host : endpoint.trim();
        } catch (IllegalArgumentException e) {
            return endpoint.trim();
        }
    }

    private void requireAllowedHost(@Nullable String host) {
        if (host == null || !urlValidator.isHostAllowed(host)) {
            throw StorageRefusal.STORAGE_ADDRESS_NOT_ALLOWED.raise();
        }
    }

    private static boolean isBlank(@Nullable String value) {
        return value == null || value.isBlank();
    }

    private static String orBlank(@Nullable String value) {
        return Objects.requireNonNullElse(value, "");
    }
}
