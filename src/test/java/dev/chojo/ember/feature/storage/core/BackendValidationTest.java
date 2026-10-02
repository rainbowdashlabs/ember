/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.core;

import dev.chojo.ember.api.refusal.Refusal;
import dev.chojo.ember.api.refusal.RefusalResponse;
import dev.chojo.ember.api.refusal.StorageRefusal;
import dev.chojo.ember.feature.federation.service.RemoteUrlValidator;
import dev.chojo.ember.feature.storage.core.BackendRequest.ClusterStorageRequest;
import dev.chojo.ember.feature.storage.core.BackendRequest.LocalRequest;
import dev.chojo.ember.feature.storage.core.BackendRequest.S3Request;
import dev.chojo.ember.feature.storage.core.BackendRequest.SftpRequest;
import dev.chojo.ember.feature.storage.core.BackendRequest.SmbRequest;
import dev.chojo.ember.feature.storage.credential.CredentialCipher;
import dev.chojo.ember.feature.storage.credential.StoredCredentials;
import dev.chojo.ember.feature.storage.entity.StationStorageBackendConfig;
import dev.chojo.ember.util.TestRemoteUrlValidator;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * What typed-in storage has to be before anything is stored, probed or moved, whoever owns it.
 */
class BackendValidationTest {
    private final CredentialCipher cipher =
            new CredentialCipher(Base64.getEncoder().encodeToString(new byte[32]));
    private final BackendValidation open = new BackendValidation(cipher, TestRemoteUrlValidator.permissive());

    private static S3Request s3(String endpoint, String accessKey, String secretKey) {
        return new S3Request(endpoint, "eu", "ember", false, "", "files", accessKey, secretKey);
    }

    private static SmbRequest smb(String username, String password) {
        return new SmbRequest("smb.test", 445, "share", "WORKGROUP", "files", true, false, username, password);
    }

    private static SftpRequest sftp(String password, String privateKey) {
        return new SftpRequest("sftp.test", 22, "ember", "", "files", password, privateKey);
    }

    private Refusal refusalOf(BackendValidation validation, BackendRequest request) {
        return assertThrows(RefusalResponse.class, () -> validation.toConfig(request))
                .refusal();
    }

    @Test
    void credentialsAreCompleteForTheirKindOrRefused() {
        assertEquals(StorageRefusal.STORAGE_KEYS_MISSING, refusalOf(open, s3("https://s3.test", "key", "")));
        assertEquals(StorageRefusal.STORAGE_KEYS_MISSING, refusalOf(open, s3("https://s3.test", null, "secret")));
        assertEquals(StorageRefusal.STORAGE_SIGN_IN_MISSING, refusalOf(open, smb("user", " ")));
        assertEquals(StorageRefusal.STORAGE_SIGN_IN_MISSING, refusalOf(open, smb(null, "password")));
        assertEquals(StorageRefusal.STORAGE_SIGN_IN_AMBIGUOUS, refusalOf(open, sftp("password", "KEY")));
        assertEquals(StorageRefusal.STORAGE_SIGN_IN_AMBIGUOUS, refusalOf(open, sftp("", null)));
    }

    /** The address is checked for every owner, an object storage endpoint by its host. */
    @Test
    void anAddressTheInstanceMayNotReachIsRefused() {
        var closed = new BackendValidation(cipher, new RemoteUrlValidator(null, null) {
            @Override
            public boolean isHostAllowed(String host) {
                return !"internal.test".equals(host);
            }
        });

        assertEquals(
                StorageRefusal.STORAGE_ADDRESS_NOT_ALLOWED,
                refusalOf(closed, s3("http://internal.test:9000", "key", "secret")));
        assertEquals(StorageRefusal.STORAGE_ADDRESS_NOT_ALLOWED, refusalOf(closed, s3(" ", "key", "secret")));
        assertDoesNotThrow(() -> closed.toConfig(s3("https://s3.test", "key", "secret")));
    }

    @Test
    void storageTheInstanceAlreadyKnowsPassesTheChecksButIsNoStorageToType() {
        assertDoesNotThrow(() -> open.requireUsable(new LocalRequest(null)));
        assertDoesNotThrow(() -> open.requireUsable(new ClusterStorageRequest()));
        assertEquals(StorageRefusal.STORAGE_DESTINATION_NOT_OFFERED, refusalOf(open, new LocalRequest("data")));
        assertEquals(StorageRefusal.STORAGE_DESTINATION_NOT_OFFERED, refusalOf(open, new ClusterStorageRequest()));
    }

    @Test
    void theCredentialsAreStoredEncryptedAndTheRestAsTyped() {
        var s3 = (StationStorageBackendConfig.S3Variant) open.toConfig(s3("https://s3.test", "key", "secret"));
        var smb = (StationStorageBackendConfig.SmbVariant) open.toConfig(smb("user", "password"));
        var sftp = (StationStorageBackendConfig.SftpVariant) open.toConfig(sftp(null, "KEY"));

        assertEquals("ember", s3.bucket());
        assertEquals(Optional.empty(), s3.sseAlgorithm());
        assertEquals(
                new StoredCredentials.S3("key", "secret"),
                StoredCredentials.S3.parse(cipher.decryptToString(s3.credentials())));
        assertEquals(
                new StoredCredentials.Smb("user", "password"),
                StoredCredentials.Smb.parse(cipher.decryptToString(smb.credentials())));
        assertEquals(
                new StoredCredentials.Sftp("ember", "", "KEY"),
                StoredCredentials.Sftp.parse(cipher.decryptToString(sftp.credentials())));
    }
}
