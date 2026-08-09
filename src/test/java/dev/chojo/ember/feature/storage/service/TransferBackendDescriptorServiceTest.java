/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.service;

import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.storage.credential.CredentialCipher;
import dev.chojo.ember.feature.storage.credential.StoredCredentials;
import dev.chojo.ember.feature.storage.entity.StationStorageBackendConfig;
import dev.chojo.ember.feature.storage.repository.StationStorageConfigRepository;
import dev.chojo.ember.feature.storage.transfer.TransferBackendDescriptor;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Tests the descriptor the source station hands to the destination during a station transfer.
 * The interesting behaviour is the credential hop: what is stored encrypted must come back out
 * as plaintext on the descriptor so the destination can re-encrypt it under its own key.
 */
class TransferBackendDescriptorServiceTest extends RepositoryTestBase {

    private static StationStorageConfigRepository configRepository;
    private static CredentialCipher cipher;
    private static TransferBackendDescriptorService service;

    @BeforeAll
    static void setup() {
        configRepository = new StationStorageConfigRepository();
        cipher = new CredentialCipher(Base64.getEncoder().encodeToString(new byte[32]));
        service = new TransferBackendDescriptorService(configRepository, cipher);
    }

    /**
     * A station without an override row lives on the instance default, which the destination
     * must treat as "byte-copy everything".
     */
    @Test
    void stationWithoutOverrideReportsLocal() {
        Station station = stationRepo.create("Transfer Descriptor Local");
        try {
            assertInstanceOf(TransferBackendDescriptor.Local.class, service.describe(station.id()));
        } finally {
            stationRepo.delete(station.id());
        }
    }

    /**
     * S3 overrides carry every non-secret connection field plus the decrypted access and secret
     * key.
     */
    @Test
    void s3OverrideCarriesDecryptedKeys() {
        Station station = stationRepo.create("Transfer Descriptor S3");
        try {
            configRepository.upsert(
                    station.id(),
                    new StationStorageBackendConfig.S3Variant(
                            "https://s3.example.invalid",
                            "eu-central-1",
                            "bucket-name",
                            true,
                            Optional.of("AES256"),
                            "/prefix",
                            cipher.encrypt(new StoredCredentials.S3("access-key", "secret-key").toJson())));

            var descriptor = assertInstanceOf(TransferBackendDescriptor.S3.class, service.describe(station.id()));
            assertEquals("https://s3.example.invalid", descriptor.endpoint());
            assertEquals("eu-central-1", descriptor.region());
            assertEquals("bucket-name", descriptor.bucket());
            assertEquals(true, descriptor.pathStyle());
            assertEquals("AES256", descriptor.sseAlgorithm());
            assertEquals("/prefix", descriptor.basePath());
            assertEquals("access-key", descriptor.accessKey());
            assertEquals("secret-key", descriptor.secretKey());
        } finally {
            configRepository.delete(station.id());
            stationRepo.delete(station.id());
        }
    }

    /**
     * The optional server-side-encryption algorithm collapses to {@code null} on the wire when
     * the override does not pin one.
     */
    @Test
    void s3OverrideWithoutSseReportsNullAlgorithm() {
        Station station = stationRepo.create("Transfer Descriptor S3 No SSE");
        try {
            configRepository.upsert(
                    station.id(),
                    new StationStorageBackendConfig.S3Variant(
                            "https://s3.example.invalid",
                            "us-east-1",
                            "plain-bucket",
                            false,
                            Optional.empty(),
                            "/",
                            cipher.encrypt(new StoredCredentials.S3("a", "b").toJson())));

            var descriptor = assertInstanceOf(TransferBackendDescriptor.S3.class, service.describe(station.id()));
            assertNull(descriptor.sseAlgorithm());
            assertEquals(false, descriptor.pathStyle());
        } finally {
            configRepository.delete(station.id());
            stationRepo.delete(station.id());
        }
    }

    /**
     * SMB overrides carry the share topology plus the decrypted user and password.
     */
    @Test
    void smbOverrideCarriesDecryptedUserAndPassword() {
        Station station = stationRepo.create("Transfer Descriptor SMB");
        try {
            configRepository.upsert(
                    station.id(),
                    new StationStorageBackendConfig.SmbVariant(
                            "smb.example.invalid",
                            4450,
                            "share-name",
                            "WORKGROUP",
                            "/base",
                            true,
                            true,
                            cipher.encrypt(new StoredCredentials.Smb("smb-user", "smb-password").toJson())));

            var descriptor = assertInstanceOf(TransferBackendDescriptor.Smb.class, service.describe(station.id()));
            assertEquals("smb.example.invalid", descriptor.host());
            assertEquals(4450, descriptor.port());
            assertEquals("share-name", descriptor.share());
            assertEquals("WORKGROUP", descriptor.domain());
            assertEquals("/base", descriptor.basePath());
            assertEquals(true, descriptor.seal());
            assertEquals(true, descriptor.dfs());
            assertEquals("smb-user", descriptor.username());
            assertEquals("smb-password", descriptor.password());
        } finally {
            configRepository.delete(station.id());
            stationRepo.delete(station.id());
        }
    }

    /**
     * SFTP overrides authenticated by password report the password and leave the private key
     * empty rather than sending a blank string over the wire.
     */
    @Test
    void sftpOverrideWithPasswordLeavesPrivateKeyNull() {
        Station station = stationRepo.create("Transfer Descriptor SFTP Password");
        try {
            configRepository.upsert(
                    station.id(),
                    new StationStorageBackendConfig.SftpVariant(
                            "sftp.example.invalid",
                            2222,
                            "row-user",
                            "SHA256:fingerprint",
                            "/upload",
                            cipher.encrypt(new StoredCredentials.Sftp("cred-user", "sftp-password", "").toJson())));

            var descriptor = assertInstanceOf(TransferBackendDescriptor.Sftp.class, service.describe(station.id()));
            assertEquals("sftp.example.invalid", descriptor.host());
            assertEquals(2222, descriptor.port());
            assertEquals("cred-user", descriptor.username(), "the username travels with the credentials");
            assertEquals("SHA256:fingerprint", descriptor.knownHostsFingerprint());
            assertEquals("/upload", descriptor.basePath());
            assertEquals("sftp-password", descriptor.password());
            assertNull(descriptor.privateKey());
        } finally {
            configRepository.delete(station.id());
            stationRepo.delete(station.id());
        }
    }

    /**
     * The mirror case: a key-authenticated SFTP override reports the key and no password.
     */
    @Test
    void sftpOverrideWithPrivateKeyLeavesPasswordNull() {
        Station station = stationRepo.create("Transfer Descriptor SFTP Key");
        try {
            configRepository.upsert(
                    station.id(),
                    new StationStorageBackendConfig.SftpVariant(
                            "sftp.example.invalid",
                            22,
                            "row-user",
                            "SHA256:fingerprint",
                            "/",
                            cipher.encrypt(new StoredCredentials.Sftp("key-user", null, "PRIVATE-KEY-BODY").toJson())));

            var descriptor = assertInstanceOf(TransferBackendDescriptor.Sftp.class, service.describe(station.id()));
            assertEquals("key-user", descriptor.username());
            assertNull(descriptor.password());
            assertEquals("PRIVATE-KEY-BODY", descriptor.privateKey());
        } finally {
            configRepository.delete(station.id());
            stationRepo.delete(station.id());
        }
    }
}
