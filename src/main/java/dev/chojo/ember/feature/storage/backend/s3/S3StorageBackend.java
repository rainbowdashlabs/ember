/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.backend.s3;

import dev.chojo.ember.feature.storage.backend.HealthStatus;
import dev.chojo.ember.feature.storage.backend.ObjectMetadata;
import dev.chojo.ember.feature.storage.backend.StorageBackend;
import dev.chojo.ember.feature.storage.backend.StorageBackendType;
import dev.chojo.ember.feature.storage.backend.StorageException;
import dev.chojo.ember.feature.storage.backend.StoredStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.MetadataDirective;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * S3-backed {@link StorageBackend}. Uses AWS SDK v2 against any S3-compatible endpoint
 * (AWS S3, MinIO, Backblaze B2, Wasabi, Cloudflare R2, Hetzner Object Storage, …).
 *
 * <p>Unlike the filesystem backends, S3 carries metadata natively on the object — no sidecar
 * file. Content type lands on the object header; SHA-256, optional filename and content
 * encoding ride in {@code x-amz-meta-*} user metadata. {@link #updateMetadata} reissues a
 * {@code CopyObject} with {@code MetadataDirective=REPLACE} so the SHA-256 sealed after the
 * upload stream drains lands on the object without re-uploading bytes.
 *
 * <p>The probe writes a small marker under {@code _probe/<uuid>} and removes it afterwards;
 * if the bucket itself is unreachable the SDK surfaces the underlying failure and the probe
 * returns unhealthy.
 */
public class S3StorageBackend implements StorageBackend, AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(S3StorageBackend.class);
    private static final String PROBE_PREFIX = "_probe";
    private static final String META_SHA256 = "sha256";
    private static final String META_FILENAME = "original-filename";
    private static final String META_ENCODING = "content-encoding";

    private final S3BackendConfig config;
    private final S3Client s3;
    private final String basePath;

    public S3StorageBackend(S3BackendConfig config) {
        this(config, defaultClient(config));
    }

    /** Visible for tests that want to inject a pre-configured {@link S3Client}. */
    S3StorageBackend(S3BackendConfig config, S3Client s3) {
        this.config = config;
        this.s3 = s3;
        this.basePath = normalizeBasePath(config.basePath());
    }

    private static S3Client defaultClient(S3BackendConfig config) {
        return S3Client.builder()
                .endpointOverride(URI.create(config.endpoint()))
                .region(Region.of(config.region()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(config.accessKey(), config.secretKey())))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(config.pathStyle())
                        .build())
                .build();
    }

    private static String normalizeBasePath(String basePath) {
        if (basePath == null || basePath.isBlank() || basePath.equals("/")) return "";
        String trimmed = basePath;
        while (trimmed.startsWith("/")) trimmed = trimmed.substring(1);
        while (trimmed.endsWith("/")) trimmed = trimmed.substring(0, trimmed.length() - 1);
        return trimmed;
    }

    @Override
    public StorageBackendType type() {
        return StorageBackendType.S3;
    }

    @Override
    public void store(String fullKey, InputStream body, long contentLength, ObjectMetadata metadata) {
        String objectKey = key(fullKey);
        Map<String, String> userMeta = encodeMetadata(metadata);
        try {
            PutObjectRequest.Builder put = PutObjectRequest.builder()
                    .bucket(config.bucket())
                    .key(objectKey)
                    .contentType(metadata.contentType())
                    .contentLength(contentLength)
                    .metadata(userMeta);
            config.sseAlgorithm().ifPresent(put::serverSideEncryption);
            s3.putObject(put.build(), RequestBody.fromInputStream(body, contentLength));
        } catch (S3Exception e) {
            throw new StorageException("S3 store failed for " + fullKey, e);
        }
    }

    @Override
    public void updateMetadata(String fullKey, ObjectMetadata metadata) {
        String objectKey = key(fullKey);
        Map<String, String> userMeta = encodeMetadata(metadata);
        try {
            CopyObjectRequest.Builder copy = CopyObjectRequest.builder()
                    .sourceBucket(config.bucket())
                    .sourceKey(objectKey)
                    .destinationBucket(config.bucket())
                    .destinationKey(objectKey)
                    .metadataDirective(MetadataDirective.REPLACE)
                    .contentType(metadata.contentType())
                    .metadata(userMeta);
            config.sseAlgorithm().ifPresent(copy::serverSideEncryption);
            s3.copyObject(copy.build());
        } catch (S3Exception e) {
            throw new StorageException("S3 updateMetadata failed for " + fullKey, e);
        }
    }

    @Override
    public Optional<StoredStream> read(String fullKey) {
        String objectKey = key(fullKey);
        try {
            ResponseInputStream<GetObjectResponse> stream = s3.getObject(GetObjectRequest.builder()
                    .bucket(config.bucket())
                    .key(objectKey)
                    .build());
            GetObjectResponse resp = stream.response();
            ObjectMetadata metadata = decodeMetadata(resp.contentType(), resp.metadata());
            return Optional.of(new StoredStream(stream, resp.contentLength(), metadata));
        } catch (NoSuchKeyException e) {
            return Optional.empty();
        } catch (S3Exception e) {
            throw new StorageException("S3 read failed for " + fullKey, e);
        }
    }

    @Override
    public void delete(String fullKey) {
        try {
            s3.deleteObject(DeleteObjectRequest.builder()
                    .bucket(config.bucket())
                    .key(key(fullKey))
                    .build());
        } catch (S3Exception e) {
            log.warn("S3 delete failed for {}", fullKey, e);
        }
    }

    @Override
    public boolean exists(String fullKey) {
        try {
            s3.headObject(HeadObjectRequest.builder()
                    .bucket(config.bucket())
                    .key(key(fullKey))
                    .build());
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        } catch (S3Exception e) {
            if (e.statusCode() == 404) return false;
            throw new StorageException("S3 head failed for " + fullKey, e);
        }
    }

    @Override
    public List<String> listByPrefix(String prefix) {
        String rooted = prefix == null ? "" : prefix;
        String s3Prefix = rooted.isEmpty() ? basePath : key(rooted);
        var out = new ArrayList<String>();
        String continuation = null;
        do {
            ListObjectsV2Request.Builder req =
                    ListObjectsV2Request.builder().bucket(config.bucket()).prefix(s3Prefix);
            if (continuation != null) req.continuationToken(continuation);
            var resp = s3.listObjectsV2(req.build());
            for (var obj : resp.contents()) {
                String relative = stripBase(obj.key());
                if (relative == null) continue;
                out.add(relative);
            }
            continuation = Boolean.TRUE.equals(resp.isTruncated()) ? resp.nextContinuationToken() : null;
        } while (continuation != null);
        out.sort(String::compareTo);
        return out;
    }

    @Override
    public long sumSizeByPrefix(String prefix) {
        String rooted = prefix == null ? "" : prefix;
        String s3Prefix = rooted.isEmpty() ? basePath : key(rooted);
        long total = 0;
        String continuation = null;
        do {
            ListObjectsV2Request.Builder req =
                    ListObjectsV2Request.builder().bucket(config.bucket()).prefix(s3Prefix);
            if (continuation != null) req.continuationToken(continuation);
            var resp = s3.listObjectsV2(req.build());
            for (var obj : resp.contents()) {
                total += obj.size();
            }
            continuation = Boolean.TRUE.equals(resp.isTruncated()) ? resp.nextContinuationToken() : null;
        } while (continuation != null);
        return total;
    }

    @Override
    public HealthStatus probe() {
        String probeKey = PROBE_PREFIX + "/" + UUID.randomUUID();
        byte[] payload = "probe".getBytes(StandardCharsets.UTF_8);
        try {
            store(
                    probeKey,
                    new java.io.ByteArrayInputStream(payload),
                    payload.length,
                    ObjectMetadata.of("application/octet-stream"));
            try (var stream = read(probeKey).orElseThrow()) {
                String readBack = new String(stream.body().readAllBytes(), StandardCharsets.UTF_8);
                if (!"probe".equals(readBack)) {
                    return HealthStatus.unhealthy("S3 backend read returned unexpected payload");
                }
            }
            return HealthStatus.ok();
        } catch (Exception e) {
            return HealthStatus.unhealthy("S3 backend probe failed: " + e.getMessage());
        } finally {
            try {
                delete(probeKey);
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    public void close() {
        s3.close();
    }

    private String key(String fullKey) {
        if (fullKey == null || fullKey.isEmpty()) {
            throw new IllegalArgumentException("fullKey must not be empty");
        }
        return basePath.isEmpty() ? fullKey : basePath + "/" + fullKey;
    }

    private String stripBase(String objectKey) {
        if (basePath.isEmpty()) return objectKey;
        String prefix = basePath + "/";
        if (objectKey.equals(basePath)) return null;
        if (!objectKey.startsWith(prefix)) return null;
        return objectKey.substring(prefix.length());
    }

    private static Map<String, String> encodeMetadata(ObjectMetadata metadata) {
        var meta = new HashMap<String, String>();
        if (metadata.sha256() != null && !metadata.sha256().isEmpty()) {
            meta.put(META_SHA256, metadata.sha256());
        }
        metadata.originalFilename().ifPresent(name -> meta.put(META_FILENAME, name));
        metadata.contentEncoding().ifPresent(enc -> meta.put(META_ENCODING, enc));
        return meta;
    }

    private static ObjectMetadata decodeMetadata(String contentType, Map<String, String> userMeta) {
        String ct = contentType == null || contentType.isBlank() ? "application/octet-stream" : contentType;
        String sha = userMeta.getOrDefault(META_SHA256, "");
        Optional<String> filename = Optional.ofNullable(userMeta.get(META_FILENAME));
        Optional<String> encoding = Optional.ofNullable(userMeta.get(META_ENCODING));
        return new ObjectMetadata(ct, sha, filename, encoding);
    }
}
