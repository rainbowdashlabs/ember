/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.transfer;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Wire-shape returned by {@code GET /public/transfer/{token}/backend}. The destination uses it
 * to decide whether to byte-copy the station's files ({@link Local}) or to re-encrypt the
 * carried credentials and route future uploads to the same remote backend ({@link S3},
 * {@link Smb}, {@link Sftp}).
 *
 * <p>Credentials travel in plaintext on the wire - the source's {@code CredentialCipher}
 * decrypts on the request thread and Jackson serializes the result straight into the response.
 * The endpoint is one-shot per transfer token to keep the credential exposure window minimal.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = TransferBackendDescriptor.Local.class, name = "LOCAL"),
    @JsonSubTypes.Type(value = TransferBackendDescriptor.S3.class, name = "S3"),
    @JsonSubTypes.Type(value = TransferBackendDescriptor.Smb.class, name = "SMB"),
    @JsonSubTypes.Type(value = TransferBackendDescriptor.Sftp.class, name = "SFTP")
})
public sealed interface TransferBackendDescriptor {

    /**
     * Station bytes live on the source's local disk; the destination must byte-copy each key.
     */
    record Local() implements TransferBackendDescriptor {}

    /**
     * Station owns an S3-compatible bucket; the destination re-encrypts and reuses it as-is.
     */
    record S3(
            String endpoint,
            String region,
            String bucket,
            boolean pathStyle,
            String sseAlgorithm,
            String basePath,
            String accessKey,
            String secretKey)
            implements TransferBackendDescriptor {}

    /**
     * Station owns an SMB share.
     */
    record Smb(
            String host,
            int port,
            String share,
            String domain,
            String basePath,
            boolean seal,
            boolean dfs,
            String username,
            String password)
            implements TransferBackendDescriptor {}

    /**
     * Station owns an SFTP target. Exactly one of {@code password} / {@code privateKey} is non-null.
     */
    record Sftp(
            String host,
            int port,
            String username,
            String knownHostsFingerprint,
            String basePath,
            String password,
            String privateKey)
            implements TransferBackendDescriptor {}
}
