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
 * Where files are kept, with nothing secret in it: what every storage screen is shown and what every row of
 * the storage history records, whoever owns the storage.
 *
 * <p>The credentials are replaced by a fingerprint of their encrypted form, so a reader can tell that they
 * were written again between two rows without anything that would open the storage. It is {@code null} where
 * nothing encrypted is kept, as for a directory or an instance whose configuration file holds the
 * credentials in plain text.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = BackendSummary.LocalSummary.class, name = "LOCAL"),
    @JsonSubTypes.Type(value = BackendSummary.S3Summary.class, name = "S3"),
    @JsonSubTypes.Type(value = BackendSummary.SmbSummary.class, name = "SMB"),
    @JsonSubTypes.Type(value = BackendSummary.SftpSummary.class, name = "SFTP")
})
public sealed interface BackendSummary {

    /** @param root the directory the instance keeps its files in */
    record LocalSummary(String root) implements BackendSummary {}

    record S3Summary(
            String endpoint,
            String region,
            String bucket,
            boolean pathStyle,
            String sseAlgorithm,
            String basePath,
            @Nullable String credentialFingerprint)
            implements BackendSummary {}

    record SmbSummary(
            String host,
            int port,
            String share,
            @Nullable String domain,
            String basePath,
            boolean seal,
            boolean dfs,
            @Nullable String credentialFingerprint)
            implements BackendSummary {}

    record SftpSummary(
            String host,
            int port,
            String username,
            boolean knownHostsPinned,
            String basePath,
            @Nullable String credentialFingerprint)
            implements BackendSummary {}
}
