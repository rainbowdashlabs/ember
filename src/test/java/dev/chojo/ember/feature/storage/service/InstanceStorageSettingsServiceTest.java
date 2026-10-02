/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.service;

import dev.chojo.ember.api.refusal.RefusalResponse;
import dev.chojo.ember.api.refusal.StorageRefusal;
import dev.chojo.ember.api.refusal.SystemRefusal;
import dev.chojo.ember.conf.Conf;
import dev.chojo.ember.conf.ConfigChanges;
import dev.chojo.ember.conf.UnwritableConf;
import dev.chojo.ember.conf.file.elements.StorageBackendSettings;
import dev.chojo.ember.feature.federation.service.RemoteUrlValidator;
import dev.chojo.ember.feature.storage.audit.StorageAuditAction;
import dev.chojo.ember.feature.storage.audit.StorageAuditOutcome;
import dev.chojo.ember.feature.storage.backend.HealthStatus;
import dev.chojo.ember.feature.storage.backend.StorageBackend;
import dev.chojo.ember.feature.storage.backend.StorageBackendFactory;
import dev.chojo.ember.feature.storage.backend.StorageBackendResolver;
import dev.chojo.ember.feature.storage.backend.StorageBackendType;
import dev.chojo.ember.feature.storage.core.BackendRequest;
import dev.chojo.ember.feature.storage.core.BackendRequest.LocalRequest;
import dev.chojo.ember.feature.storage.core.BackendRequest.S3Request;
import dev.chojo.ember.feature.storage.core.BackendRequest.SftpRequest;
import dev.chojo.ember.feature.storage.core.BackendSummary;
import dev.chojo.ember.feature.storage.core.BackendSummary.LocalSummary;
import dev.chojo.ember.feature.storage.core.BackendSummary.S3Summary;
import dev.chojo.ember.feature.storage.core.BackendValidation;
import dev.chojo.ember.feature.storage.credential.CredentialCipher;
import dev.chojo.ember.feature.storage.credential.EncryptedBlob;
import dev.chojo.ember.feature.storage.migration.MigrationException;
import dev.chojo.ember.feature.storage.service.InstanceStorageMigrationService.MigrationResult;
import dev.chojo.ember.feature.storage.service.InstanceStorageMigrationService.PreparedMigration;
import dev.chojo.ember.feature.storage.service.InstanceStorageSettingsService.InstanceMigrateRequest;
import dev.chojo.ember.feature.storage.service.StorageBackendAuditService.Actor;
import dev.chojo.ember.owner.Owner;
import dev.chojo.ember.util.TestRemoteUrlValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
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
        return serviceOn(conf, TestRemoteUrlValidator.permissive());
    }

    private InstanceStorageSettingsService serviceOn(Conf conf, RemoteUrlValidator addresses) {
        return new InstanceStorageSettingsService(
                conf,
                new ConfigChanges(conf),
                resolver,
                factory,
                cipher,
                new BackendValidation(cipher, addresses),
                migration,
                audit,
                new StorageProbeService(factory, audit));
    }

    private static S3Request bucket(String name) {
        return new S3Request("https://s3.test", "eu", name, true, "", "files", "access", "secret");
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
                .recordConfigChange(
                        eq(ACTOR),
                        eq(new Owner.Instance()),
                        eq(StorageAuditAction.INSTANCE_DEFAULT_UPDATED),
                        any(BackendSummary.class),
                        any(BackendSummary.class));
        verify(audit)
                .recordInstanceMigration(
                        eq(ACTOR),
                        eq(StorageAuditAction.INSTANCE_MIGRATION_COMPLETED),
                        any(BackendSummary.class),
                        any(BackendSummary.class),
                        isNull());
    }

    /** The instance is held to what a station is held to: a storage nobody can sign in to is a mistake. */
    @Test
    void incompleteCredentialsAreRefusedBeforeAnythingMoves() {
        var service = serviceOn(new Conf(directory));
        var noKeys = new S3Request("https://s3.test", "eu", "ember", true, "", "files", "", "");
        var bothWays = new SftpRequest("sftp.test", 22, "ember", "SHA256:abc", "files", "pw", "KEY");

        assertEquals(
                StorageRefusal.STORAGE_KEYS_MISSING,
                assertThrows(
                                RefusalResponse.class,
                                () -> service.apply(ACTOR, new InstanceMigrateRequest(noKeys, false)))
                        .refusal());
        assertEquals(
                StorageRefusal.STORAGE_SIGN_IN_AMBIGUOUS,
                assertThrows(
                                RefusalResponse.class,
                                () -> service.apply(ACTOR, new InstanceMigrateRequest(bothWays, false)))
                        .refusal());
        verify(migration, never()).prepare(any());
    }

    /** And so is the address: the instance does not open connections into its own network either. */
    @Test
    void anAddressTheInstanceMayNotReachIsRefusedAndWrittenDown() {
        var closed = new RemoteUrlValidator(null, null) {
            @Override
            public boolean isHostAllowed(String host) {
                return false;
            }
        };
        var service = serviceOn(new Conf(directory), closed);

        var refused = assertThrows(
                RefusalResponse.class, () -> service.apply(ACTOR, new InstanceMigrateRequest(bucket("ember"), false)));

        assertEquals(StorageRefusal.STORAGE_ADDRESS_NOT_ALLOWED, refused.refusal());
        verify(audit).recordRejected(ACTOR, new Owner.Instance(), refused);
        verify(migration, never()).prepare(any());
    }

    @Test
    void anAssociationsStorageIsNotTheInstancesToChoose() {
        var service = serviceOn(new Conf(directory));

        var refused = assertThrows(
                RefusalResponse.class,
                () -> service.apply(
                        ACTOR, new InstanceMigrateRequest(new BackendRequest.ClusterStorageRequest(), false)));

        assertEquals(StorageRefusal.STORAGE_DESTINATION_NOT_OFFERED, refused.refusal());
    }

    @Test
    void aMoveWithoutATargetIsRefusedBeforeAnythingHappens() {
        var service = serviceOn(new Conf(directory));

        var refused = assertThrows(
                RefusalResponse.class, () -> service.apply(ACTOR, new InstanceMigrateRequest(null, false)));

        assertEquals(StorageRefusal.INSTANCE_STORAGE_TARGET_MISSING, refused.refusal());
        verifyNoInteractions(migration);
        verify(audit).recordRejected(ACTOR, new Owner.Instance(), refused);
    }

    @Test
    void filesThatCannotBeCopiedLeaveTheSettingsAsTheyWere() {
        when(migration.prepare(any())).thenThrow(new MigrationException("target refused"));
        var service = serviceOn(new Conf(directory));

        var refused = assertThrows(
                RefusalResponse.class, () -> service.apply(ACTOR, new InstanceMigrateRequest(bucket("ember"), false)));

        assertEquals(StorageRefusal.INSTANCE_STORAGE_MOVE_NOT_DONE, refused.refusal());
        assertEquals(
                StorageBackendType.LOCAL,
                new Conf(directory).main().storage().backend().type());
        verify(migration, never()).commit(any(), anyBoolean());
        verify(audit)
                .recordInstanceMigration(
                        eq(ACTOR),
                        eq(StorageAuditAction.INSTANCE_MIGRATION_FAILED),
                        any(BackendSummary.class),
                        any(BackendSummary.class),
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

        assertEquals(SystemRefusal.SETTINGS_NOT_SAVED, refused.refusal());
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

        assertEquals(StorageRefusal.INSTANCE_STORAGE_MOVE_TAKEN_BACK, refused.refusal());
        verify(migration).abort(PREPARED);
    }

    /**
     * The history names where the files are and a fingerprint of the credentials, so a rotation shows,
     * but never the credentials themselves.
     */
    @Test
    void theHistoryNamesWhereTheFilesAreButNeverTheCredentials() {
        var service = serviceOn(new Conf(directory));

        service.apply(ACTOR, new InstanceMigrateRequest(bucket("ember"), false));

        var target = ArgumentCaptor.forClass(BackendSummary.class);
        verify(audit)
                .recordInstanceMigration(
                        eq(ACTOR),
                        eq(StorageAuditAction.INSTANCE_MIGRATION_STARTED),
                        eq(new LocalSummary("data")),
                        target.capture(),
                        isNull());
        var s3 = (S3Summary) target.getValue();
        assertEquals("ember", s3.bucket());
        assertNotNull(s3.credentialFingerprint());
        assertFalse(s3.toString().contains("secret"));
        assertFalse(s3.toString().contains("access"));
    }

    @Test
    void aLocalStorageWithoutARootKeepsTheDefaultOne() {
        var service = serviceOn(new Conf(directory));
        service.apply(ACTOR, new InstanceMigrateRequest(bucket("ember"), false));

        service.apply(ACTOR, new InstanceMigrateRequest(new LocalRequest(null), false));

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
                        new SftpRequest("sftp.test", 22, "ember", "SHA256:abc", "files", " ", "KEY"), false));

        var sftp = new Conf(directory).main().storage().backend().sftp();
        assertNull(sftp.passwordEnc());
        assertEquals("KEY", decrypted(sftp.privateKeyEnc()));
        assertEquals("", sftp.privateKey());
    }

    /** The operator is told what a station is told; the reason is in the instance log. */
    @Test
    void storageNotSavedYetIsProbedWithoutWritingAnythingOrSayingWhy() {
        var backend = mock(StorageBackend.class);
        when(backend.probe()).thenReturn(HealthStatus.unhealthy("no bucket"));
        var built = ArgumentCaptor.forClass(StorageBackendSettings.class);
        when(factory.buildForInstance(built.capture())).thenReturn(backend);
        var service = serviceOn(new Conf(directory));

        var result = service.probe(bucket("ember"));

        assertFalse(result.healthy());
        assertEquals(StorageProbeService.PROBE_FAILED, result.error());
        assertEquals("ember", built.getValue().s3().bucket());
        assertEquals(
                StorageBackendType.LOCAL,
                new Conf(directory).main().storage().backend().type());
    }

    @Test
    void theStorageInUseIsProbedForTheInstancesHistory() {
        var running = mock(StorageBackend.class);
        when(running.probe()).thenReturn(HealthStatus.unhealthy("disk full"));
        when(resolver.instanceDefault()).thenReturn(running);
        var service = serviceOn(new Conf(directory));

        var result = service.probe(ACTOR);

        assertEquals(StorageProbeService.PROBE_FAILED, result.error());
        verify(audit)
                .recordProbe(
                        eq(ACTOR),
                        eq(new Owner.Instance()),
                        eq(StorageAuditOutcome.FAILED),
                        eq(StorageProbeService.PROBE_FAILED));
    }

    @Test
    void theSummaryLeavesTheCredentialsOut() {
        var conf = new Conf(directory);
        var service = serviceOn(conf);
        service.apply(ACTOR, new InstanceMigrateRequest(bucket("ember"), false));

        var summary = (S3Summary) service.summary();

        assertEquals("https://s3.test", summary.endpoint());
        assertEquals("eu", summary.region());
        assertEquals("ember", summary.bucket());
        assertTrue(summary.pathStyle());
        assertEquals("files", summary.basePath());
        assertNotNull(summary.credentialFingerprint());
    }
}
