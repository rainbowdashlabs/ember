/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.route;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import dev.chojo.ember.feature.federation.service.RemoteUrlValidator;
import dev.chojo.ember.feature.storage.credential.CredentialCipher;
import dev.chojo.ember.feature.storage.credential.EncryptedBlob;
import dev.chojo.ember.feature.storage.credential.StoredCredentials;
import dev.chojo.ember.feature.storage.entity.StationStorageBackendConfig;
import io.javalin.http.BadRequestResponse;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.net.URI;
import java.util.Optional;

/**
 * What a storage backend looks like on the wire, in both directions, and the two conversions between that
 * and the stored configuration.
 *
 * <p>One copy of these shapes for the station's own screen and the association's, because the records carry
 * credentials: two copies is how one of them ends up not encrypting something. The summaries are the same
 * shapes with the credentials left out, which is the only form a backend is ever read back in.
 */
@Singleton
public class StorageBackendPayloads {
    private final CredentialCipher credentialCipher;
    private final RemoteUrlValidator urlValidator;

    @Inject
    public StorageBackendPayloads(CredentialCipher credentialCipher, RemoteUrlValidator urlValidator) {
        this.credentialCipher = credentialCipher;
        this.urlValidator = urlValidator;
    }

    /**
     * The stored form of a backend somebody just typed in, with its credentials encrypted.
     *
     * @param request what came in
     * @return the configuration to store, probe or migrate to
     * @throws BadRequestResponse when the host is not one this instance may reach, or credentials are missing
     */
    public StationStorageBackendConfig toEntity(BackendOverrideRequest request) {
        return switch (request) {
            case S3Request r -> {
                requireAllowedHost(hostOf(r.endpoint()));
                yield new StationStorageBackendConfig.S3Variant(
                        r.endpoint(),
                        r.region(),
                        r.bucket(),
                        r.pathStyle(),
                        Optional.ofNullable(r.sseAlgorithm()).filter(s -> !s.isBlank()),
                        r.basePath(),
                        encryptS3(r));
            }
            case SmbRequest r -> {
                requireAllowedHost(r.host());
                yield new StationStorageBackendConfig.SmbVariant(
                        r.host(), r.port(), r.share(), r.domain(), r.basePath(), r.seal(), r.dfs(), encryptSmb(r));
            }
            case SftpRequest r -> {
                requireAllowedHost(r.host());
                yield new StationStorageBackendConfig.SftpVariant(
                        r.host(), r.port(), r.username(), r.knownHostsFingerprint(), r.basePath(), encryptSftp(r));
            }
            case LocalRequest ignored ->
                throw new IllegalStateException("LOCAL names no backend; the caller must dispatch it separately");
            case ClusterRequest ignored ->
                throw new IllegalStateException("CLUSTER names its cluster's backend; the caller must look it up");
        };
    }

    /**
     * What a backend is read back as: everything but the credentials.
     *
     * @param config the stored configuration
     * @return the same destination with nothing secret in it
     */
    public static BackendOverrideSummary toSummary(StationStorageBackendConfig config) {
        return switch (config) {
            case StationStorageBackendConfig.S3Variant v ->
                new S3Summary(
                        v.endpoint(),
                        v.region(),
                        v.bucket(),
                        v.pathStyle(),
                        v.sseAlgorithm().orElse(""),
                        v.basePath());
            case StationStorageBackendConfig.SmbVariant v ->
                new SmbSummary(v.host(), v.port(), v.share(), v.domain(), v.basePath(), v.seal(), v.dfs());
            case StationStorageBackendConfig.SftpVariant v ->
                new SftpSummary(
                        v.host(),
                        v.port(),
                        v.username(),
                        !v.knownHostsFingerprint().isBlank(),
                        v.basePath());
        };
    }

    private static String hostOf(String endpoint) {
        if (endpoint == null || endpoint.isBlank()) {
            return null;
        }
        try {
            String host = URI.create(endpoint.trim()).getHost();
            return host != null ? host : endpoint.trim();
        } catch (IllegalArgumentException e) {
            return endpoint.trim();
        }
    }

    private void requireAllowedHost(String host) {
        if (!urlValidator.isHostAllowed(host)) {
            throw new BadRequestResponse("Storage backend host is not a permitted address");
        }
    }

    private EncryptedBlob encryptS3(S3Request r) {
        if (r.accessKey() == null || r.secretKey() == null) {
            throw new BadRequestResponse("S3 override requires accessKey and secretKey");
        }
        return credentialCipher.encrypt(new StoredCredentials.S3(r.accessKey(), r.secretKey()).toJson());
    }

    private EncryptedBlob encryptSmb(SmbRequest r) {
        if (r.username() == null || r.password() == null) {
            throw new BadRequestResponse("SMB override requires username and password");
        }
        return credentialCipher.encrypt(new StoredCredentials.Smb(r.username(), r.password()).toJson());
    }

    private EncryptedBlob encryptSftp(SftpRequest r) {
        boolean hasPassword = r.password() != null && !r.password().isBlank();
        boolean hasKey = r.privateKey() != null && !r.privateKey().isBlank();
        if (hasPassword == hasKey) {
            throw new BadRequestResponse("SFTP override requires exactly one of password or privateKey");
        }
        return credentialCipher.encrypt(new StoredCredentials.Sftp(
                        r.username(),
                        r.password() == null ? "" : r.password(),
                        r.privateKey() == null ? "" : r.privateKey())
                .toJson());
    }

    // -- Request shapes (plaintext credentials in transit only; the server encrypts before persisting) --

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
    @JsonSubTypes({
        @JsonSubTypes.Type(value = LocalRequest.class, name = "LOCAL"),
        @JsonSubTypes.Type(value = ClusterRequest.class, name = "CLUSTER"),
        @JsonSubTypes.Type(value = S3Request.class, name = "S3"),
        @JsonSubTypes.Type(value = SmbRequest.class, name = "SMB"),
        @JsonSubTypes.Type(value = SftpRequest.class, name = "SFTP")
    })
    public sealed interface BackendOverrideRequest {}

    /**
     * Drops the override and migrates bytes back to the instance default. Has no fields: the {@code type}
     * discriminator is the whole intent.
     */
    public record LocalRequest() implements BackendOverrideRequest {}

    /**
     * Moves the station onto the current version of its association's storage. Has no fields for the same
     * reason: which storage that is, is the association's to say and not the station's to type.
     */
    public record ClusterRequest() implements BackendOverrideRequest {}

    public record S3Request(
            String endpoint,
            String region,
            String bucket,
            boolean pathStyle,
            String sseAlgorithm,
            String basePath,
            String accessKey,
            String secretKey)
            implements BackendOverrideRequest {}

    public record SmbRequest(
            String host,
            int port,
            String share,
            String domain,
            String basePath,
            boolean seal,
            boolean dfs,
            String username,
            String password)
            implements BackendOverrideRequest {}

    public record SftpRequest(
            String host,
            int port,
            String username,
            String knownHostsFingerprint,
            String basePath,
            String password,
            String privateKey)
            implements BackendOverrideRequest {}

    // -- Response shapes --

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
    @JsonSubTypes({
        @JsonSubTypes.Type(value = S3Summary.class, name = "S3"),
        @JsonSubTypes.Type(value = SmbSummary.class, name = "SMB"),
        @JsonSubTypes.Type(value = SftpSummary.class, name = "SFTP")
    })
    public sealed interface BackendOverrideSummary {}

    public record S3Summary(
            String endpoint, String region, String bucket, boolean pathStyle, String sseAlgorithm, String basePath)
            implements BackendOverrideSummary {}

    public record SmbSummary(
            String host, int port, String share, String domain, String basePath, boolean seal, boolean dfs)
            implements BackendOverrideSummary {}

    public record SftpSummary(String host, int port, String username, boolean knownHostsPinned, String basePath)
            implements BackendOverrideSummary {}

    public record ProbeResult(boolean healthy, String error, String checkedAt) {}

    public record MigrationResponse(int totalKeys, int copied, int skipped, int deleted, long copiedBytes) {}
}
