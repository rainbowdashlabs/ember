/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.backend;

import dev.chojo.ember.TestContainers;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.BucketAlreadyOwnedByYouException;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;

import java.net.URI;

/**
 * The three storage servers the backends are tested against, on the images the end-to-end stack runs
 * ({@code docker/compose.dev.yaml}), so a backend never passes against a server the stories do not use.
 *
 * <p>One container of each per test fork, started on first use and shared by every class that asks,
 * because the contract, concurrency and connection-loss tests all need the same three servers.
 */
public final class StorageContainers {
    /** The user every server signs in. */
    public static final String USER = "tester";

    /** That user's password. */
    public static final String PASSWORD = "testpass";

    /** The SMB share the backend writes to. */
    public static final String SHARE = "tests";

    /** The directory below the SFTP user's home the backend writes to. */
    public static final String SFTP_BASE = "/share";

    /** The S3 bucket the backend writes to. */
    public static final String BUCKET = "ember-test";

    /** The S3 region. */
    public static final String REGION = "us-east-1";

    private static final GenericContainer<?> SFTP = new GenericContainer<>(
                    "atmoz/sftp:latest@sha256:0960390462a4441dbb63698d7c185b76a41ffcee7b78ff4adf275f3e66f9c475")
            .withExposedPorts(22)
            .withCommand(USER + ":" + PASSWORD + ":1001:1001:share")
            .waitingFor(Wait.forListeningPort())
            .withStartupAttempts(4);

    private static final GenericContainer<?> SMB = new GenericContainer<>(
                    "dperson/samba:latest@sha256:66088b78a19810dd1457a8f39340e95e663c728083efa5fe7dc0d40b2478e869")
            .withExposedPorts(445)
            .withCommand("-p", "-w", "WORKGROUP", "-u", USER + ";" + PASSWORD, "-s", SHARE + ";/tmp;yes;no;no;" + USER)
            .waitingFor(Wait.forListeningPort())
            .withStartupAttempts(4);

    private static final GenericContainer<?> S3 = new GenericContainer<>("rustfs/rustfs:1.0.0")
            .withExposedPorts(9000)
            .withEnv("RUSTFS_ACCESS_KEY", USER)
            .withEnv("RUSTFS_SECRET_KEY", PASSWORD)
            .waitingFor(Wait.forListeningPort())
            .withStartupAttempts(4);

    private StorageContainers() {}

    /** The SFTP server, started. */
    public static GenericContainer<?> sftp() {
        TestContainers.startExclusively(SFTP);
        return SFTP;
    }

    /** The SMB server, started. */
    public static GenericContainer<?> smb() {
        TestContainers.startExclusively(SMB);
        return SMB;
    }

    /** The S3 server, started, with the test bucket in it. */
    public static synchronized GenericContainer<?> s3() {
        if (S3.isRunning()) return S3;
        TestContainers.startExclusively(S3);
        try (S3Client client = S3Client.builder()
                .endpointOverride(URI.create(s3Endpoint()))
                .region(Region.of(REGION))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(USER, PASSWORD)))
                .serviceConfiguration(
                        S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build()) {
            client.createBucket(CreateBucketRequest.builder().bucket(BUCKET).build());
        } catch (BucketAlreadyOwnedByYouException alreadyThere) {
            return S3;
        }
        return S3;
    }

    /** Where the S3 server answers. */
    public static String s3Endpoint() {
        return "http://" + S3.getHost() + ":" + S3.getMappedPort(9000);
    }
}
