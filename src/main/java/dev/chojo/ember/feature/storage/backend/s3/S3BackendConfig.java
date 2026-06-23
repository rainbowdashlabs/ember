/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.backend.s3;

import java.util.Optional;

/**
 * Connection settings for {@link S3StorageBackend}. Any S3-compatible endpoint is supported
 * via {@code endpoint}; MinIO, Backblaze B2, Wasabi, Cloudflare R2 etc. typically need
 * {@code pathStyle=true} (subdomain bucket addressing only works on AWS S3 itself).
 *
 * @param endpoint            full endpoint URL — e.g. {@code https://s3.eu-central-003.backblazeb2.com}
 * @param region              AWS-style region identifier
 * @param bucket              bucket name the backend writes to; must already exist
 * @param accessKey           static access key id
 * @param secretKey           static secret access key
 * @param pathStyle           use path-style addressing ({@code endpoint/bucket/key}); required
 *                            for most non-AWS providers
 * @param sseAlgorithm        optional server-side-encryption header value (e.g. {@code AES256});
 *                            empty disables the SSE header on PutObject
 * @param basePath            optional prefix prepended to every key inside the bucket; an
 *                            empty string means "use the bucket root"
 */
public record S3BackendConfig(
        String endpoint,
        String region,
        String bucket,
        String accessKey,
        String secretKey,
        boolean pathStyle,
        Optional<String> sseAlgorithm,
        String basePath) {

    /** Returns a copy with the supplied {@code basePath} replacing the existing one. */
    public S3BackendConfig withBasePath(String basePath) {
        return new S3BackendConfig(endpoint, region, bucket, accessKey, secretKey, pathStyle, sseAlgorithm, basePath);
    }
}
