/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.core;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.jspecify.annotations.Nullable;

/**
 * Where files are to be kept, as a station, an association or the instance describes it on the wire.
 *
 * <p>One model for every owner. Which destinations an owner may name is the owner's to say: a station may
 * ask for the instance's storage and its association's, an association only for remote storage, and the
 * instance for remote storage or a directory of its own. Credentials travel in plain text over HTTPS and are
 * encrypted before they are written anywhere.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = BackendRequest.LocalRequest.class, name = "LOCAL"),
    @JsonSubTypes.Type(value = BackendRequest.ClusterStorageRequest.class, name = "CLUSTER"),
    @JsonSubTypes.Type(value = BackendRequest.S3Request.class, name = "S3"),
    @JsonSubTypes.Type(value = BackendRequest.SmbRequest.class, name = "SMB"),
    @JsonSubTypes.Type(value = BackendRequest.SftpRequest.class, name = "SFTP")
})
public sealed interface BackendRequest {

    /**
     * The instance's own disk. For a station it means going back to whatever the instance provides, and the
     * root is not the station's to choose; for the instance it is the directory its files are kept in.
     *
     * @param root the directory, for the instance only; {@code null} keeps the default one
     */
    record LocalRequest(@Nullable String root) implements BackendRequest {}

    /**
     * The current version of the association's storage. Has no fields: which storage that is, is the
     * association's to say and not the station's to type.
     */
    record ClusterStorageRequest() implements BackendRequest {}

    record S3Request(
            String endpoint,
            String region,
            String bucket,
            boolean pathStyle,
            String sseAlgorithm,
            String basePath,
            String accessKey,
            String secretKey)
            implements BackendRequest {}

    record SmbRequest(
            String host,
            int port,
            String share,
            String domain,
            String basePath,
            boolean seal,
            boolean dfs,
            String username,
            String password)
            implements BackendRequest {}

    record SftpRequest(
            String host,
            int port,
            String username,
            String knownHostsFingerprint,
            String basePath,
            String password,
            String privateKey)
            implements BackendRequest {}
}
