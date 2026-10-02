/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.core;

import dev.chojo.ember.conf.file.elements.StorageBackendSettings;
import dev.chojo.ember.feature.storage.backend.StorageBackendType;
import dev.chojo.ember.feature.storage.core.BackendSummary.LocalSummary;
import dev.chojo.ember.feature.storage.core.BackendSummary.S3Summary;
import dev.chojo.ember.feature.storage.core.BackendSummary.SftpSummary;
import dev.chojo.ember.feature.storage.core.BackendSummary.SmbSummary;
import dev.chojo.ember.feature.storage.credential.CredentialCipher;
import dev.chojo.ember.feature.storage.entity.StationStorageBackendConfig;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * One redaction for the screens and the history, for the stored rows and the instance's configuration file
 * alike: the destination as it is, the credentials as a fingerprint that tells a rotation apart.
 */
class BackendRedactionTest {
    private final CredentialCipher cipher =
            new CredentialCipher(Base64.getEncoder().encodeToString(new byte[32]));

    private StationStorageBackendConfig sftp(String password) {
        return new StationStorageBackendConfig.SftpVariant(
                "sftp.test", 22, "ember", "SHA256:x", "files", cipher.encrypt("{\"password\":\"" + password + "\"}"));
    }

    @Test
    void aStoredRowIsShownWithoutItsCredentialsAndWithAFingerprintThatTellsARotation() {
        var first = (SftpSummary) BackendRedaction.summaryOf(sftp("eins"));
        var rotated = (SftpSummary) BackendRedaction.summaryOf(sftp("zwei"));

        assertEquals("sftp.test", first.host());
        assertTrue(first.knownHostsPinned());
        assertNotNull(first.credentialFingerprint());
        assertNotEquals(first.credentialFingerprint(), rotated.credentialFingerprint());
        assertFalse(BackendRedaction.json(first).contains("eins"));
    }

    @Test
    void everyKindOfStoredRowKeepsItsDestination() {
        var s3 = BackendRedaction.summaryOf(new StationStorageBackendConfig.S3Variant(
                "https://s3.test", "eu", "ember", true, Optional.of("AES256"), "files", cipher.encrypt("{}")));
        var smb = BackendRedaction.summaryOf(new StationStorageBackendConfig.SmbVariant(
                "smb.test", 445, "share", "WORKGROUP", "files", true, false, cipher.encrypt("{}")));

        assertEquals("AES256", ((S3Summary) s3).sseAlgorithm());
        assertEquals("WORKGROUP", ((SmbSummary) smb).domain());
    }

    /** The instance is read the same way, with a fingerprint only for what its file keeps encrypted. */
    @Test
    void theInstanceIsShownTheWayAStationIs() {
        var settings = new StorageBackendSettings();
        assertEquals(new LocalSummary("data"), BackendRedaction.summaryOf(settings));

        settings.type(StorageBackendType.S3);
        settings.s3().bucket("ember");
        settings.s3().accessKey("plain");
        assertNull(((S3Summary) BackendRedaction.summaryOf(settings)).credentialFingerprint());
        settings.s3().accessKeyEnc(cipher.encrypt("key"));
        settings.s3().secretKeyEnc(cipher.encrypt("secret"));
        assertNotNull(((S3Summary) BackendRedaction.summaryOf(settings)).credentialFingerprint());

        settings.type(StorageBackendType.SMB);
        settings.smb().domain("WORKGROUP");
        settings.smb().passwordEnc(cipher.encrypt("password"));
        var smb = (SmbSummary) BackendRedaction.summaryOf(settings);
        assertEquals("WORKGROUP", smb.domain());
        assertNotNull(smb.credentialFingerprint());

        settings.type(StorageBackendType.SFTP);
        settings.sftp().privateKeyEnc(cipher.encrypt("KEY"));
        assertNotNull(((SftpSummary) BackendRedaction.summaryOf(settings)).credentialFingerprint());
    }

    @Test
    void nothingIsWrittenForNoStorage() {
        assertNull(BackendRedaction.json(null));
        assertTrue(BackendRedaction.json(new LocalSummary("data")).contains("\"type\":\"LOCAL\""));
    }
}
