/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.service;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.api.RefusalResponse;
import dev.chojo.ember.conf.Conf;
import dev.chojo.ember.conf.ConfigChanges;
import dev.chojo.ember.conf.file.elements.StorageBackendSettings;
import dev.chojo.ember.feature.storage.audit.StorageAuditAction;
import dev.chojo.ember.feature.storage.backend.StorageBackendFactory;
import dev.chojo.ember.feature.storage.backend.StorageBackendResolver;
import dev.chojo.ember.feature.storage.backend.StorageBackendType;
import dev.chojo.ember.feature.storage.credential.CredentialCipher;
import dev.chojo.ember.feature.storage.migration.MigrationException;
import dev.chojo.ember.feature.storage.service.InstanceStorageMigrationService.MigrationResult;
import dev.chojo.ember.feature.storage.service.InstanceStorageMigrationService.PreparedMigration;
import dev.chojo.ember.feature.storage.service.StorageBackendAuditService.Actor;
import dev.chojo.ember.feature.storage.service.StorageBackendPayloads.ProbeResult;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The storage the instance keeps its files on: what it is, whether it answers, and moving to another.
 *
 * <p>Moving is one step for the operator and several underneath. The files are copied and checked first,
 * then the configuration is changed and written in one go, and only after that are the old files let go
 * of. Every value the operator sent is checked before anything is written, and a configuration file that
 * cannot be written leaves the running instance on the storage the file still names, so the files and
 * the setting never point at different places.
 *
 * <p>Credentials are encrypted before they reach the configuration and never read back out.
 */
@Singleton
public class InstanceStorageSettingsService {
    private static final Logger log = LoggerFactory.getLogger(InstanceStorageSettingsService.class);

    private final Conf conf;
    private final ConfigChanges configChanges;
    private final StorageBackendResolver resolver;
    private final StorageBackendFactory factory;
    private final CredentialCipher cipher;
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
            InstanceStorageMigrationService migrationService,
            StorageBackendAuditService auditService,
            StorageProbeService probeService) {
        this.conf = conf;
        this.configChanges = configChanges;
        this.resolver = resolver;
        this.factory = factory;
        this.cipher = cipher;
        this.migrationService = migrationService;
        this.auditService = auditService;
        this.probeService = probeService;
    }

    /**
     * The storage the instance stands on, with nothing secret in it.
     *
     * @return the summary of the backend in use
     */
    public InstanceBackendSummary summary() {
        var settings = current();
        return switch (resolver.instanceDefault().type()) {
            case LOCAL -> new InstanceLocalSummary(settings.local().root());
            case SMB -> {
                var smb = settings.smb();
                yield new InstanceSmbSummary(
                        smb.host(), smb.port(), smb.share(), smb.basePath(), smb.seal(), smb.dfs());
            }
            case SFTP -> {
                var sftp = settings.sftp();
                yield new InstanceSftpSummary(
                        sftp.host(),
                        sftp.port(),
                        sftp.username(),
                        sftp.basePath(),
                        !sftp.knownHostsFingerprint().isBlank());
            }
            case S3 -> {
                var s3 = settings.s3();
                yield new InstanceS3Summary(
                        s3.endpoint(), s3.region(), s3.bucket(), s3.pathStyle(), s3.sseAlgorithm(), s3.basePath());
            }
        };
    }

    /**
     * Whether the storage the instance stands on answers.
     *
     * @return the probe's answer
     */
    public ProbeResult probe() {
        return StorageProbeService.resultOf(resolver.instanceDefault().probe());
    }

    /**
     * Whether storage the operator has not saved yet would answer, without writing anything.
     *
     * @param request the storage as the form describes it
     * @return the probe's answer
     */
    public ProbeResult probe(InstanceBackendRequest request) {
        return probeService.probe(settingsFor(request));
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
     * @throws RefusalResponse when no storage is named, the files could not be copied, the configuration
     *                         could not be written, or the move broke after it was written
     */
    public InstanceMigrationResultResponse apply(Actor actor, InstanceMigrateRequest request) {
        if (request.target() == null) throw Refusal.INSTANCE_STORAGE_TARGET_MISSING.raise();
        StorageBackendSettings target = settingsFor(request.target());
        StorageBackendSettings live = current();
        StorageBackendSettings before = StorageBackendSettings.copyOf(live);
        var move = new Move(actor, redacted(before), redacted(target));
        move.record(StorageAuditAction.INSTANCE_MIGRATION_STARTED, null);
        PreparedMigration prepared = prepare(move, target);
        switchTo(move, prepared, live, target, before);
        MigrationResult result = commit(move, prepared, Boolean.TRUE.equals(request.keepSource()));
        move.record(StorageAuditAction.INSTANCE_MIGRATION_COMPLETED, null);
        return new InstanceMigrationResultResponse(
                result.totalKeys(), result.copied(), result.skipped(), result.deleted(), result.copiedBytes());
    }

    private PreparedMigration prepare(Move move, StorageBackendSettings target) {
        try {
            return migrationService.prepare(target);
        } catch (MigrationException e) {
            move.record(StorageAuditAction.INSTANCE_MIGRATION_FAILED, e.getMessage());
            log.warn("Instance storage move could not be prepared", e);
            throw Refusal.INSTANCE_STORAGE_MOVE_NOT_DONE.raise();
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
            throw Refusal.INSTANCE_STORAGE_MOVE_TAKEN_BACK.raise();
        }
    }

    private StorageBackendSettings current() {
        return conf.main().storage().backend();
    }

    /**
     * The settings a request describes, with its credentials in the encrypted slots and the plain ones
     * emptied, so the configuration written from them holds no secret in the clear.
     */
    private StorageBackendSettings settingsFor(InstanceBackendRequest request) {
        var settings = new StorageBackendSettings();
        settings.type(typeOf(request));
        switch (request) {
            case InstanceLocalRequest r -> settings.local().root(r.root() == null ? "data" : r.root());
            case InstanceS3Request r -> describe(settings.s3(), r);
            case InstanceSmbRequest r -> describe(settings.smb(), r);
            case InstanceSftpRequest r -> describe(settings.sftp(), r);
        }
        return settings;
    }

    private void describe(StorageBackendSettings.S3Settings s3, InstanceS3Request r) {
        s3.endpoint(orBlank(r.endpoint()));
        s3.region(orBlank(r.region()));
        s3.bucket(orBlank(r.bucket()));
        s3.pathStyle(r.pathStyle());
        s3.sseAlgorithm(orBlank(r.sseAlgorithm()));
        s3.basePath(orBlank(r.basePath()));
        s3.accessKey("");
        s3.secretKey("");
        s3.accessKeyEnc(cipher.encrypt(orBlank(r.accessKey())));
        s3.secretKeyEnc(cipher.encrypt(orBlank(r.secretKey())));
    }

    private void describe(StorageBackendSettings.SmbSettings smb, InstanceSmbRequest r) {
        smb.host(orBlank(r.host()));
        smb.port(r.port());
        smb.share(orBlank(r.share()));
        smb.domain(orBlank(r.domain()));
        smb.basePath(orBlank(r.basePath()));
        smb.seal(r.seal());
        smb.dfs(r.dfs());
        smb.username(orBlank(r.username()));
        smb.password("");
        smb.passwordEnc(cipher.encrypt(orBlank(r.password())));
    }

    private void describe(StorageBackendSettings.SftpSettings sftp, InstanceSftpRequest r) {
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

    private static StorageBackendType typeOf(InstanceBackendRequest request) {
        return switch (request) {
            case InstanceLocalRequest ignored -> StorageBackendType.LOCAL;
            case InstanceS3Request ignored -> StorageBackendType.S3;
            case InstanceSmbRequest ignored -> StorageBackendType.SMB;
            case InstanceSftpRequest ignored -> StorageBackendType.SFTP;
        };
    }

    private static String orBlank(String value) {
        return value == null ? "" : value;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /**
     * The settings as the audit row keeps them: where the files are, never how to get in. Not even the
     * encrypted credentials are kept, because the configuration file already holds them.
     */
    static String redacted(StorageBackendSettings settings) {
        var json =
                new StringBuilder("{\"type\":\"").append(settings.type().name()).append("\"");
        switch (settings.type()) {
            case LOCAL -> text(json, "root", settings.local().root());
            case S3 -> {
                var s3 = settings.s3();
                text(json, "endpoint", s3.endpoint());
                text(json, "region", s3.region());
                text(json, "bucket", s3.bucket());
                json.append(",\"pathStyle\":").append(s3.pathStyle());
                text(json, "basePath", s3.basePath());
            }
            case SMB -> {
                var smb = settings.smb();
                text(json, "host", smb.host());
                json.append(",\"port\":").append(smb.port());
                text(json, "share", smb.share());
                text(json, "basePath", smb.basePath());
                json.append(",\"seal\":").append(smb.seal());
                json.append(",\"dfs\":").append(smb.dfs());
            }
            case SFTP -> {
                var sftp = settings.sftp();
                text(json, "host", sftp.host());
                json.append(",\"port\":").append(sftp.port());
                text(json, "username", sftp.username());
                text(json, "basePath", sftp.basePath());
            }
        }
        return json.append("}").toString();
    }

    private static void text(StringBuilder json, String name, String value) {
        json.append(",\"").append(name).append("\":\"").append(escape(value)).append("\"");
    }

    private static String escape(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /**
     * One move as the audit log follows it, from the settings it left to the ones it went to.
     */
    private final class Move {
        private final Actor actor;
        private final String from;
        private final String to;

        private Move(Actor actor, String from, String to) {
            this.actor = actor;
            this.from = from;
            this.to = to;
        }

        private void record(StorageAuditAction action, String errorOrNull) {
            auditService.recordInstanceMigration(actor, action, from, to, errorOrNull);
        }
    }

    /**
     * The storage the instance stands on, one variant per kind of backend; credentials are never part of it.
     */
    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
    @JsonSubTypes({
        @JsonSubTypes.Type(value = InstanceLocalSummary.class, name = "LOCAL"),
        @JsonSubTypes.Type(value = InstanceS3Summary.class, name = "S3"),
        @JsonSubTypes.Type(value = InstanceSmbSummary.class, name = "SMB"),
        @JsonSubTypes.Type(value = InstanceSftpSummary.class, name = "SFTP")
    })
    public sealed interface InstanceBackendSummary {}

    public record InstanceLocalSummary(String root) implements InstanceBackendSummary {}

    public record InstanceSmbSummary(String host, int port, String share, String basePath, boolean seal, boolean dfs)
            implements InstanceBackendSummary {}

    public record InstanceSftpSummary(String host, int port, String username, String basePath, boolean knownHostsPinned)
            implements InstanceBackendSummary {}

    public record InstanceS3Summary(
            String endpoint, String region, String bucket, boolean pathStyle, String sseAlgorithm, String basePath)
            implements InstanceBackendSummary {}

    /**
     * Storage for the instance as the form describes it. Credentials travel in plain text over HTTPS and
     * are encrypted before they are written anywhere.
     */
    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
    @JsonSubTypes({
        @JsonSubTypes.Type(value = InstanceLocalRequest.class, name = "LOCAL"),
        @JsonSubTypes.Type(value = InstanceS3Request.class, name = "S3"),
        @JsonSubTypes.Type(value = InstanceSmbRequest.class, name = "SMB"),
        @JsonSubTypes.Type(value = InstanceSftpRequest.class, name = "SFTP")
    })
    public sealed interface InstanceBackendRequest
            permits InstanceLocalRequest, InstanceS3Request, InstanceSmbRequest, InstanceSftpRequest {}

    public record InstanceLocalRequest(String root) implements InstanceBackendRequest {}

    public record InstanceS3Request(
            String endpoint,
            String region,
            String bucket,
            boolean pathStyle,
            String sseAlgorithm,
            String basePath,
            String accessKey,
            String secretKey)
            implements InstanceBackendRequest {}

    public record InstanceSmbRequest(
            String host,
            int port,
            String share,
            String domain,
            String basePath,
            boolean seal,
            boolean dfs,
            String username,
            String password)
            implements InstanceBackendRequest {}

    public record InstanceSftpRequest(
            String host,
            int port,
            String username,
            String knownHostsFingerprint,
            String basePath,
            String password,
            String privateKey)
            implements InstanceBackendRequest {}

    public record InstanceMigrateRequest(InstanceBackendRequest target, Boolean keepSource) {}

    public record InstanceMigrationResultResponse(
            int totalKeys, int copied, int skipped, int deleted, long copiedBytes) {}

    public record InstanceMigrationStatusResponse(boolean migrationInFlight) {}
}
