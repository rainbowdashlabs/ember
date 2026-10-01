/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.service;

import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.api.RefusalResponse;
import dev.chojo.ember.conf.Conf;
import dev.chojo.ember.conf.ConfigChanges;
import dev.chojo.ember.conf.UnwritableConf;
import dev.chojo.ember.conf.file.elements.StorageBackendSettings;
import dev.chojo.ember.feature.storage.audit.StorageAuditAction;
import dev.chojo.ember.feature.storage.backend.HealthStatus;
import dev.chojo.ember.feature.storage.backend.StorageBackend;
import dev.chojo.ember.feature.storage.backend.StorageBackendFactory;
import dev.chojo.ember.feature.storage.backend.StorageBackendResolver;
import dev.chojo.ember.feature.storage.backend.StorageBackendType;
import dev.chojo.ember.feature.storage.credential.CredentialCipher;
import dev.chojo.ember.feature.storage.credential.EncryptedBlob;
import dev.chojo.ember.feature.storage.migration.MigrationException;
import dev.chojo.ember.feature.storage.service.InstanceStorageMigrationService.MigrationResult;
import dev.chojo.ember.feature.storage.service.InstanceStorageMigrationService.PreparedMigration;
import dev.chojo.ember.feature.storage.service.InstanceStorageSettingsService.InstanceLocalRequest;
import dev.chojo.ember.feature.storage.service.InstanceStorageSettingsService.InstanceMigrateRequest;
import dev.chojo.ember.feature.storage.service.InstanceStorageSettingsService.InstanceS3Request;
import dev.chojo.ember.feature.storage.service.InstanceStorageSettingsService.InstanceS3Summary;
import dev.chojo.ember.feature.storage.service.InstanceStorageSettingsService.InstanceSftpRequest;
import dev.chojo.ember.feature.storage.service.StorageBackendAuditService.Actor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class InstanceStorageSettingsServiceTest {
    private static final Actor ACTOR = Actor.human(1, null);
    private static final PreparedMigration PREPARED = new PreparedMigration(null, null, null);

    @TempDir
    Path directory;

    private final CredentialCipher cipher =
            new CredentialCipher(Base64.getEncoder().encodeToString(new byte[32]));
    private StorageBackendResolver resolver;
    private StorageBackendFactory factory;
    private InstanceStorageMigrationService migration;
    private StorageBackendAuditService audit;

    @BeforeEach
    void setup() {
        resolver = mock(StorageBackendResolver.class);
        factory = mock(StorageBackendFactory.class);
        migration = mock(InstanceStorageMigrationService.class);
        audit = mock(StorageBackendAuditService.class);
        when(migration.prepare(any())).thenReturn(PREPARED);
        when(migration.commit(any(), anyBoolean())).thenReturn(new MigrationResult(3, 2, 1, 2, 42L));
    }

    private InstanceStorageSettingsService serviceOn(Conf conf) {
        return new InstanceStorageSettingsService(
                conf,
                new ConfigChanges(conf),
                resolver,
                factory,
                cipher,
                migration,
                audit,
                new StorageProbeService(factory));
    }

    private static InstanceS3Request bucket(String name) {
        return new InstanceS3Request("https://s3.test", "eu", name, true, "", "files", "access", "secret");
    }

    private String decrypted(EncryptedBlob blob) {
        return new String(cipher.decrypt(blob), StandardCharsets.UTF_8);
    }

    @Test
    void theNewStorageIsWrittenToTheFileWithItsCredentialsEncrypted() {
        var service = serviceOn(new Conf(directory));

        var answer = service.apply(ACTOR, new InstanceMigrateRequest(bucket("ember"), true));

        var written = new Conf(directory).main().storage().backend();
        assertEquals(StorageBackendType.S3, written.type());
        assertEquals("ember", written.s3().bucket());
        assertTrue(written.s3().pathStyle());
        assertEquals("", written.s3().accessKey());
        assertEquals("", written.s3().secretKey());
        assertEquals("access", decrypted(written.s3().accessKeyEnc()));
        assertEquals("secret", decrypted(written.s3().secretKeyEnc()));
        assertEquals(2, answer.copied());
        assertEquals(42L, answer.copiedBytes());
        verify(factory).invalidateInstanceDefault();
        verify(migration).commit(PREPARED, true);
        verify(audit)
                .recordInstanceMigration(
                        eq(ACTOR),
                        eq(StorageAuditAction.INSTANCE_MIGRATION_COMPLETED),
                        anyString(),
                        anyString(),
                        isNull());
    }

    @Test
    void aMoveWithoutATargetIsRefusedBeforeAnythingHappens() {
        var service = serviceOn(new Conf(directory));

        var refused = assertThrows(
                RefusalResponse.class, () -> service.apply(ACTOR, new InstanceMigrateRequest(null, false)));

        assertEquals(Refusal.INSTANCE_STORAGE_TARGET_MISSING, refused.refusal());
        verifyNoInteractions(migration, audit);
    }

    @Test
    void filesThatCannotBeCopiedLeaveTheSettingsAsTheyWere() {
        when(migration.prepare(any())).thenThrow(new MigrationException("target refused"));
        var service = serviceOn(new Conf(directory));

        var refused = assertThrows(
                RefusalResponse.class, () -> service.apply(ACTOR, new InstanceMigrateRequest(bucket("ember"), false)));

        assertEquals(Refusal.INSTANCE_STORAGE_MOVE_NOT_DONE, refused.refusal());
        assertEquals(
                StorageBackendType.LOCAL,
                new Conf(directory).main().storage().backend().type());
        verify(migration, never()).commit(any(), anyBoolean());
        verify(audit)
                .recordInstanceMigration(
                        eq(ACTOR),
                        eq(StorageAuditAction.INSTANCE_MIGRATION_FAILED),
                        anyString(),
                        anyString(),
                        eq("target refused"));
    }

    /**
     * A file that cannot be written takes every value back, not only some of them, and leaves the
     * old files where they are.
     */
    @Test
    void aFileThatCannotBeWrittenTakesTheWholeChangeBack() {
        var conf = UnwritableConf.create();
        var live = conf.main().storage().backend();
        live.local().root("kept");
        var service = serviceOn(conf);

        var refused = assertThrows(
                RefusalResponse.class, () -> service.apply(ACTOR, new InstanceMigrateRequest(bucket("ember"), false)));

        assertEquals(Refusal.SETTINGS_NOT_SAVED, refused.refusal());
        assertEquals(StorageBackendType.LOCAL, live.type());
        assertEquals("kept", live.local().root());
        assertEquals("", live.s3().bucket());
        assertNull(live.s3().accessKeyEnc());
        verify(migration).abort(PREPARED);
        verify(migration, never()).commit(any(), anyBoolean());
        verify(factory, never()).invalidateInstanceDefault();
    }

    @Test
    void aMoveThatBreaksWhileCommittingIsTakenBack() {
        when(migration.commit(any(), anyBoolean())).thenThrow(new IllegalStateException("disk gone"));
        var service = serviceOn(new Conf(directory));

        var refused = assertThrows(
                RefusalResponse.class, () -> service.apply(ACTOR, new InstanceMigrateRequest(bucket("ember"), false)));

        assertEquals(Refusal.INSTANCE_STORAGE_MOVE_TAKEN_BACK, refused.refusal());
        verify(migration).abort(PREPARED);
    }

    @Test
    void theAuditRowNamesWhereTheFilesAreButNeverTheCredentials() {
        var service = serviceOn(new Conf(directory));

        service.apply(ACTOR, new InstanceMigrateRequest(bucket("ember"), false));

        var target = ArgumentCaptor.forClass(String.class);
        verify(audit)
                .recordInstanceMigration(
                        eq(ACTOR),
                        eq(StorageAuditAction.INSTANCE_MIGRATION_STARTED),
                        eq("{\"type\":\"LOCAL\",\"root\":\"data\"}"),
                        target.capture(),
                        isNull());
        assertTrue(target.getValue().contains("\"bucket\":\"ember\""));
        assertFalse(target.getValue().contains("secret"));
        assertFalse(target.getValue().contains("access"));
    }

    @Test
    void aLocalStorageWithoutARootKeepsTheDefaultOne() {
        var service = serviceOn(new Conf(directory));
        service.apply(ACTOR, new InstanceMigrateRequest(bucket("ember"), false));

        service.apply(ACTOR, new InstanceMigrateRequest(new InstanceLocalRequest(null), false));

        var written = new Conf(directory).main().storage().backend();
        assertEquals(StorageBackendType.LOCAL, written.type());
        assertEquals("data", written.local().root());
        assertEquals("", written.s3().bucket());
    }

    @Test
    void aFileTransferServerSignedInToWithAKeyStoresNoPassword() {
        var service = serviceOn(new Conf(directory));

        service.apply(
                ACTOR,
                new InstanceMigrateRequest(
                        new InstanceSftpRequest("sftp.test", 22, "ember", "SHA256:abc", "files", " ", "KEY"), false));

        var sftp = new Conf(directory).main().storage().backend().sftp();
        assertNull(sftp.passwordEnc());
        assertEquals("KEY", decrypted(sftp.privateKeyEnc()));
        assertEquals("", sftp.privateKey());
    }

    @Test
    void storageNotSavedYetIsProbedWithoutWritingAnything() {
        var backend = mock(StorageBackend.class);
        when(backend.probe()).thenReturn(HealthStatus.unhealthy("no bucket"));
        var built = ArgumentCaptor.forClass(StorageBackendSettings.class);
        when(factory.buildForInstance(built.capture())).thenReturn(backend);
        var service = serviceOn(new Conf(directory));

        var result = service.probe(bucket("ember"));

        assertFalse(result.healthy());
        assertEquals("no bucket", result.error());
        assertEquals("ember", built.getValue().s3().bucket());
        assertEquals(
                StorageBackendType.LOCAL,
                new Conf(directory).main().storage().backend().type());
    }

    @Test
    void theSummaryLeavesTheCredentialsOut() {
        var conf = new Conf(directory);
        var service = serviceOn(conf);
        service.apply(ACTOR, new InstanceMigrateRequest(bucket("ember"), false));
        var backend = mock(StorageBackend.class);
        when(backend.type()).thenReturn(StorageBackendType.S3);
        when(resolver.instanceDefault()).thenReturn(backend);

        var summary = service.summary();

        assertEquals(new InstanceS3Summary("https://s3.test", "eu", "ember", true, "", "files"), summary);
    }
}
