/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.backend.s3;

import dev.chojo.ember.feature.storage.backend.ObjectMetadata;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Contract test for {@link S3StorageBackend} against rustfs — a Rust-based S3-compatible
 * server. Mirrors the same surface every other backend asserts on (store / read / delete /
 * exists / listByPrefix / sumSize / metadata round-trip / probe).
 */
@Tag("storage")
@Testcontainers
class S3StorageBackendTest {
    private static final String ACCESS_KEY = "rustfsadmin";
    private static final String SECRET_KEY = "rustfsadmin";
    private static final String BUCKET = "ember-test";
    private static final String REGION = "us-east-1";

    @Container
    static final GenericContainer<?> RUSTFS = new GenericContainer<>("rustfs/rustfs:latest")
            .withExposedPorts(9000)
            .withEnv("RUSTFS_ACCESS_KEY", ACCESS_KEY)
            .withEnv("RUSTFS_SECRET_KEY", SECRET_KEY)
            .waitingFor(Wait.forListeningPort())
            .withStartupAttempts(4);

    private static S3BackendConfig config;
    private static S3StorageBackend backend;

    @BeforeAll
    static void setup() {
        String endpoint = "http://" + RUSTFS.getHost() + ":" + RUSTFS.getMappedPort(9000);
        config = new S3BackendConfig(
                endpoint, REGION, BUCKET, ACCESS_KEY, SECRET_KEY, /* pathStyle */ true, Optional.empty(), "");

        try (S3Client setupClient = S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.of(REGION))
                .credentialsProvider(
                        StaticCredentialsProvider.create(AwsBasicCredentials.create(ACCESS_KEY, SECRET_KEY)))
                .serviceConfiguration(
                        S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build()) {
            setupClient.createBucket(
                    CreateBucketRequest.builder().bucket(BUCKET).build());
        }

        backend = new S3StorageBackend(config);
    }

    @AfterAll
    static void teardown() {
        if (backend != null) backend.close();
    }

    @AfterEach
    void cleanup() {
        for (String key : backend.listByPrefix("")) {
            backend.delete(key);
        }
    }

    @Test
    void storeAndReadRoundTrip() throws Exception {
        byte[] payload = "hello s3".getBytes(StandardCharsets.UTF_8);
        backend.store(
                "scope/cat/key", new ByteArrayInputStream(payload), payload.length, ObjectMetadata.of("text/plain"));

        try (var stream = backend.read("scope/cat/key").orElseThrow()) {
            assertEquals(payload.length, stream.contentLength());
            assertEquals("hello s3", new String(stream.body().readAllBytes(), StandardCharsets.UTF_8));
            assertEquals("text/plain", stream.metadata().contentType());
        }
    }

    @Test
    void existsAndDelete() {
        byte[] payload = "bytes".getBytes(StandardCharsets.UTF_8);
        backend.store(
                "scope/cat/del", new ByteArrayInputStream(payload), payload.length, ObjectMetadata.of("text/plain"));

        assertTrue(backend.exists("scope/cat/del"));
        backend.delete("scope/cat/del");
        assertFalse(backend.exists("scope/cat/del"));
    }

    @Test
    void listByPrefixReturnsEveryNestedKey() {
        byte[] payload = "data".getBytes(StandardCharsets.UTF_8);
        for (String key : List.of("scope/cat/a", "scope/cat/sub/b", "scope/cat/sub/c")) {
            backend.store(
                    key,
                    new ByteArrayInputStream(payload),
                    payload.length,
                    ObjectMetadata.of("application/octet-stream"));
        }

        List<String> keys = backend.listByPrefix("scope/cat");
        assertEquals(3, keys.size());
        assertTrue(keys.contains("scope/cat/a"));
        assertTrue(keys.contains("scope/cat/sub/b"));
        assertTrue(keys.contains("scope/cat/sub/c"));
    }

    @Test
    void sumSizeAggregatesAllStoredBytes() {
        byte[] payload = "12345".getBytes(StandardCharsets.UTF_8);
        backend.store(
                "scope/cat/x", new ByteArrayInputStream(payload), payload.length, ObjectMetadata.of("text/plain"));
        backend.store(
                "scope/cat/y", new ByteArrayInputStream(payload), payload.length, ObjectMetadata.of("text/plain"));
        assertEquals(payload.length * 2L, backend.sumSizeByPrefix("scope/cat"));
    }

    @Test
    void updateMetadataSeesNewSha256() throws Exception {
        byte[] payload = "data".getBytes(StandardCharsets.UTF_8);
        backend.store(
                "scope/cat/meta",
                new ByteArrayInputStream(payload),
                payload.length,
                ObjectMetadata.of("application/octet-stream"));

        backend.updateMetadata(
                "scope/cat/meta", ObjectMetadata.of("application/octet-stream").withSha256("abc123"));

        try (var stream = backend.read("scope/cat/meta").orElseThrow()) {
            assertEquals("abc123", stream.metadata().sha256());
        }
    }

    @Test
    void probeReturnsHealthy() {
        var status = backend.probe();
        assertNotNull(status);
        assertTrue(status.healthy(), () -> "expected healthy probe but got: " + status);
    }

    @Test
    void readMissingKeyReturnsEmpty() {
        assertTrue(backend.read("does/not/exist").isEmpty());
    }
}
