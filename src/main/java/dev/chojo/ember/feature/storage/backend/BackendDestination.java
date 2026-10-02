/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.backend;

import org.jspecify.annotations.Nullable;

/**
 * Where a backend keeps its bytes, spelled one way for a running backend and for a stored configuration.
 *
 * <p>A move compares running backends and an association compares configurations before it decides between a
 * new version and new credentials. Both ask here, so the two can never disagree about whether two settings
 * are the same place: a disagreement is exactly what lets a move delete the files it meant to keep. Only what
 * decides the place is part of it: the kind of backend, the host or endpoint, the bucket or share and the
 * path underneath, the path with the slashes a backend ignores taken off. User names, domains, regions and
 * addressing styles stay out.
 */
public final class BackendDestination {
    private BackendDestination() {}

    /**
     * @param endpoint the service address
     * @param bucket   the bucket
     * @param basePath the prefix every key lives below
     * @return the destination of object storage
     */
    public static String s3(String endpoint, String bucket, @Nullable String basePath) {
        return String.join("|", StorageBackendType.S3.name(), endpoint, bucket, objectPrefix(basePath));
    }

    /**
     * @param host     the server
     * @param port     its port
     * @param share    the share
     * @param basePath the directory inside the share
     * @return the destination of a shared folder
     */
    public static String smb(String host, int port, String share, @Nullable String basePath) {
        return tree(StorageBackendType.SMB, String.join("|", host, String.valueOf(port), share), basePath);
    }

    /**
     * @param host     the server
     * @param port     its port
     * @param basePath the directory on the server
     * @return the destination of a file transfer server
     */
    public static String sftp(String host, int port, @Nullable String basePath) {
        return tree(StorageBackendType.SFTP, host + "|" + port, basePath);
    }

    /**
     * @param type     the kind of file tree
     * @param tree     the tree the backend works in, without the base path
     * @param basePath the directory inside the tree
     * @return the destination of a backend working in a file tree
     */
    public static String tree(StorageBackendType type, String tree, @Nullable String basePath) {
        return String.join("|", type.name(), tree, treePath(basePath));
    }

    /**
     * A directory inside a file tree as the tree reads it: forward slashes, none at either end.
     *
     * @param path the directory as configured, or {@code null}
     * @return the directory, empty for the root
     */
    public static String treePath(@Nullable String path) {
        if (path == null) return "";
        String normalized = path.replace('\\', '/');
        while (normalized.startsWith("/")) normalized = normalized.substring(1);
        while (normalized.endsWith("/")) normalized = normalized.substring(0, normalized.length() - 1);
        return normalized;
    }

    /**
     * A key prefix inside a bucket as object storage reads it: no slash at either end.
     *
     * @param basePath the prefix as configured, or {@code null}
     * @return the prefix, empty for none
     */
    public static String objectPrefix(@Nullable String basePath) {
        if (basePath == null || basePath.isBlank() || basePath.equals("/")) return "";
        String trimmed = basePath;
        while (trimmed.startsWith("/")) trimmed = trimmed.substring(1);
        while (trimmed.endsWith("/")) trimmed = trimmed.substring(0, trimmed.length() - 1);
        return trimmed;
    }
}
