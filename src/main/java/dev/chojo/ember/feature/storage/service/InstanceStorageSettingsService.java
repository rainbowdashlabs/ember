/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.service;

import dev.chojo.ember.api.refusal.RefusalResponse;
import dev.chojo.ember.api.refusal.StorageRefusal;
import dev.chojo.ember.conf.Conf;
import dev.chojo.ember.conf.ConfigChanges;
import dev.chojo.ember.conf.file.elements.StorageBackendSettings;
import dev.chojo.ember.feature.storage.audit.StorageAuditAction;
import dev.chojo.ember.feature.storage.backend.StorageBackendFactory;
import dev.chojo.ember.feature.storage.backend.StorageBackendResolver;
import dev.chojo.ember.feature.storage.backend.StorageBackendType;
import dev.chojo.ember.feature.storage.core.BackendRedaction;
import dev.chojo.ember.feature.storage.core.BackendRequest;
import dev.chojo.ember.feature.storage.core.BackendSummary;
import dev.chojo.ember.feature.storage.core.BackendValidation;
import dev.chojo.ember.feature.storage.core.MigrationResponse;
import dev.chojo.ember.feature.storage.core.ProbeResult;
import dev.chojo.ember.feature.storage.credential.CredentialCipher;
import dev.chojo.ember.feature.storage.migration.MigrationException;
import dev.chojo.ember.feature.storage.service.InstanceStorageMigrationService.MigrationResult;
import dev.chojo.ember.feature.storage.service.InstanceStorageMigrationService.PreparedMigration;
import dev.chojo.ember.feature.storage.service.StorageBackendAuditService.Actor;
import dev.chojo.ember.owner.Owner;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * The storage the instance keeps its files on: what it is, whether it answers, and moving to another.
 *
 * <p>Moving is one step for the operator and several underneath. The files are copied and checked first,
 * then the configuration is changed and written in one go, and only after that are the old files let go
 * of. Every value the operator sent is checked before anything is written, with the same checks a station's
 * storage passes, and a configuration file that cannot be written leaves the running instance on the
 * storage the file still names, so the files and the setting never point at different places.
 *
 * <p>Credentials are encrypted before they reach the configuration and never read back out.
 */
@Singleton
public class InstanceStorageSettingsService {
    private static final Logger log = LoggerFactory.getLogger(InstanceStorageSettingsService.class);
    private static final Owner INSTANCE = new Owner.Instance();

    private final Conf conf;
    private final ConfigChanges configChanges;
    private final StorageBackendResolver resolver;
    private final StorageBackendFactory factory;
    private final CredentialCipher cipher;
    private final BackendValidation validation;
    private final InstanceStorageMigrationService migrationService;
    private final StorageBackendAuditService auditService;
    private final StorageProbeService probeService;

    @Inject
    public InstanceStorageSettingsService(
            Conf conf,
            ConfigChanges configChanges,
            StorageBackendResolver resolver,
            StorageBackendFactory factory,
            CredentialCipher cipher,
            BackendValidation validation,
            InstanceStorageMigrationService migrationService,
            StorageBackendAuditService auditService,
            StorageProbeService probeService) {
        this.conf = conf;
        this.configChanges = configChanges;
        this.resolver = resolver;
        this.factory = factory;
        this.cipher = cipher;
        this.validation = validation;
        this.migrationService = migrationService;
        this.auditService = auditService;
        this.probeService = probeService;
    }

    /**
     * The storage the instance stands on, with nothing secret in it.
     *
     * @return the summary of the backend in use
     */
    public BackendSummary summary() {
        return BackendRedaction.summaryOf(current());
    }

    /**
     * Whether the storage the instance stands on answers, written to the instance's history.
     *
     * @param actor who asked
     * @return the probe's answer, with the reason of a failure kept in the log
     */
    public ProbeResult probe(Actor actor) {
        return probeService.probeRunning(actor, INSTANCE, resolver.instanceDefault());
    }

    /**
     * Whether storage the operator has not saved yet would answer, without writing anything.
     *
     * @param request the storage as the form describes it
     * @return the probe's answer, with the reason of a failure kept in the log
     */
    public ProbeResult probe(BackendRequest request) {
        return probeService.probe(checkedSettingsFor(request));
    }

    /**
     * Whether a move of the instance files is running right now.
     *
     * @return the state of the move
     */
    public InstanceMigrationStatusResponse status() {
        return new InstanceMigrationStatusResponse(migrationService.isMigrationInFlight());
    }

    /**
     * Moves the instance onto the storage asked for: copies the files, writes the configuration, lets go
     * of the old files unless they are to be kept.
     *
     * @param actor   who asked
     * @param request where to, and whether the old files stay
     * @return what was carried over
     * @throws RefusalResponse when no usable storage is named, the files could not be copied, the
     *                         configuration could not be written, or the move broke after it was written
     */
    public MigrationResponse apply(Actor actor, InstanceMigrateRequest request) {
        StorageBackendSettings target = targetOf(actor, request);
        StorageBackendSettings live = current();
        StorageBackendSettings before = StorageBackendSettings.copyOf(live);
        var move = new Move(actor, BackendRedaction.summaryOf(before), BackendRedaction.summaryOf(target));
        move.record(StorageAuditAction.INSTANCE_MIGRATION_STARTED, null);
        PreparedMigration prepared = prepare(move, target);
        switchTo(move, prepared, live, target, before);
        auditService.recordConfigChange(
                actor, INSTANCE, StorageAuditAction.INSTANCE_DEFAULT_UPDATED, move.from, move.to);
        MigrationResult result = commit(move, prepared, Boolean.TRUE.equals(request.keepSource()));
        move.record(StorageAuditAction.INSTANCE_MIGRATION_COMPLETED, null);
        return new MigrationResponse(
                result.totalKeys(), result.copied(), result.skipped(), result.deleted(), result.copiedBytes());
    }

    /** The settings a move asks for, checked; a refusal is written to the history before it is answered. */
    private StorageBackendSettings targetOf(Actor actor, InstanceMigrateRequest request) {
        try {
            BackendRequest target = request.target();
            if (target == null) throw StorageRefusal.INSTANCE_STORAGE_TARGET_MISSING.raise();
            return checkedSettingsFor(target);
        } catch (RefusalResponse refused) {
            auditService.recordRejected(actor, INSTANCE, refused);
            throw refused;
        }
    }

    private PreparedMigration prepare(Move move, StorageBackendSettings target) {
        try {
            return migrationService.prepare(target);
        } catch (MigrationException e) {
            move.record(StorageAuditAction.INSTANCE_MIGRATION_FAILED, e.getMessage());
            log.warn("Instance storage move could not be prepared", e);
            throw StorageRefusal.INSTANCE_STORAGE_MOVE_NOT_DONE.raise();
        }
    }

    private void switchTo(
            Move move,
            PreparedMigration prepared,
            StorageBackendSettings live,
            StorageBackendSettings target,
            StorageBackendSettings before) {
        try {
            configChanges.apply(() -> live.copyFrom(target), () -> live.copyFrom(before));
        } catch (RefusalResponse notSaved) {
            migrationService.abort(prepared);
            move.record(StorageAuditAction.INSTANCE_MIGRATION_FAILED, "The configuration file could not be written");
            throw notSaved;
        }
    }

    private MigrationResult commit(Move move, PreparedMigration prepared, boolean keepSource) {
        try {
            factory.invalidateInstanceDefault();
            return migrationService.commit(prepared, keepSource);
        } catch (RuntimeException e) {
            migrationService.abort(prepared);
            move.record(StorageAuditAction.INSTANCE_MIGRATION_FAILED, e.getMessage());
            log.error("The move of the instance files broke while it was being committed", e);
            throw StorageRefusal.INSTANCE_STORAGE_MOVE_TAKEN_BACK.raise();
        }
    }

    private StorageBackendSettings current() {
        return conf.main().storage().backend();
    }

    /**
     * The settings a request describes once it passed the checks every owner's storage passes, with its
     * credentials in the encrypted slots and the plain ones emptied, so the configuration written from them
     * holds no secret in the clear. The instance has no association, so storage of one is not on offer.
     */
    private StorageBackendSettings checkedSettingsFor(BackendRequest request) {
        validation.requireUsable(request);
        var settings = new StorageBackendSettings();
        switch (request) {
            case BackendRequest.LocalRequest r -> {
                settings.type(StorageBackendType.LOCAL);
                settings.local().root(Objects.requireNonNullElse(r.root(), "data"));
            }
            case BackendRequest.S3Request r -> describe(settings, r);
            case BackendRequest.SmbRequest r -> describe(settings, r);
            case BackendRequest.SftpRequest r -> describe(settings, r);
            case BackendRequest.ClusterStorageRequest ignored ->
                throw StorageRefusal.STORAGE_DESTINATION_NOT_OFFERED.raise();
        }
        return settings;
    }

    private void describe(StorageBackendSettings settings, BackendRequest.S3Request r) {
        settings.type(StorageBackendType.S3);
        var s3 = settings.s3();
        s3.endpoint(orBlank(r.endpoint()));
        s3.region(orBlank(r.region()));
        s3.bucket(orBlank(r.bucket()));
        s3.pathStyle(r.pathStyle());
        s3.sseAlgorithm(orBlank(r.sseAlgorithm()));
        s3.basePath(orBlank(r.basePath()));
        s3.accessKey("");
        s3.secretKey("");
        s3.accessKeyEnc(cipher.encrypt(r.accessKey()));
        s3.secretKeyEnc(cipher.encrypt(r.secretKey()));
    }

    private void describe(StorageBackendSettings settings, BackendRequest.SmbRequest r) {
        settings.type(StorageBackendType.SMB);
        var smb = settings.smb();
        smb.host(orBlank(r.host()));
        smb.port(r.port());
        smb.share(orBlank(r.share()));
        smb.domain(orBlank(r.domain()));
        smb.basePath(orBlank(r.basePath()));
        smb.seal(r.seal());
        smb.dfs(r.dfs());
        smb.username(r.username());
        smb.password("");
        smb.passwordEnc(cipher.encrypt(r.password()));
    }

    private void describe(StorageBackendSettings settings, BackendRequest.SftpRequest r) {
        settings.type(StorageBackendType.SFTP);
        var sftp = settings.sftp();
        sftp.host(orBlank(r.host()));
        sftp.port(r.port());
        sftp.username(orBlank(r.username()));
        sftp.knownHostsFingerprint(orBlank(r.knownHostsFingerprint()));
        sftp.basePath(orBlank(r.basePath()));
        sftp.password("");
        sftp.privateKey("");
        sftp.passwordEnc(isBlank(r.password()) ? null : cipher.encrypt(r.password()));
        sftp.privateKeyEnc(isBlank(r.privateKey()) ? null : cipher.encrypt(r.privateKey()));
    }

    private static String orBlank(@Nullable String value) {
        return value == null ? "" : value;
    }

    private static boolean isBlank(@Nullable String value) {
        return value == null || value.isBlank();
    }

    /**
     * One move as the history follows it, from the storage it left to the one it went to.
     */
    private final class Move {
        private final Actor actor;
        private final BackendSummary from;
        private final BackendSummary to;

        private Move(Actor actor, BackendSummary from, BackendSummary to) {
            this.actor = actor;
            this.from = from;
            this.to = to;
        }

        private void record(StorageAuditAction action, @Nullable String errorOrNull) {
            auditService.recordInstanceMigration(actor, action, from, to, errorOrNull);
        }
    }

    /**
     * Where the instance's files are to go, and whether the old ones stay.
     *
     * @param target     the storage; every kind but an association's
     * @param keepSource whether the files are left where they were as well
     */
    public record InstanceMigrateRequest(
            @Nullable BackendRequest target, @Nullable Boolean keepSource) {}

    public record InstanceMigrationStatusResponse(boolean migrationInFlight) {}
}
