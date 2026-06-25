/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.route;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.InstancePermission;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.auth.StepUpCategory;
import dev.chojo.ember.conf.Conf;
import dev.chojo.ember.conf.file.elements.Storage;
import dev.chojo.ember.conf.file.elements.StorageBackendSettings;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.feature.storage.audit.StorageAuditAction;
import dev.chojo.ember.feature.storage.audit.StorageBackendAuditService;
import dev.chojo.ember.feature.storage.backend.HealthStatus;
import dev.chojo.ember.feature.storage.backend.StorageBackend;
import dev.chojo.ember.feature.storage.backend.StorageBackendFactory;
import dev.chojo.ember.feature.storage.backend.StorageBackendResolver;
import dev.chojo.ember.feature.storage.backend.StorageBackendType;
import dev.chojo.ember.feature.storage.credential.CredentialCipher;
import dev.chojo.ember.feature.storage.entity.StorageCategory;
import dev.chojo.ember.feature.storage.entity.StorageUsage;
import dev.chojo.ember.feature.storage.migration.InstanceStorageMigrationService;
import dev.chojo.ember.feature.storage.migration.MigrationException;
import dev.chojo.ember.feature.storage.repository.StorageBackendAuditRepository;
import dev.chojo.ember.feature.storage.repository.StorageQuotaPresetRepository;
import dev.chojo.ember.feature.storage.repository.StorageUsageRepository;
import dev.chojo.ember.feature.storage.service.StorageQuotaService;
import dev.chojo.ember.feature.storage.service.StorageReconciliationService;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.ForbiddenResponse;
import io.javalin.http.HttpStatus;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Singleton
public class StorageRoutes implements Routes {
    private final StorageQuotaService quotaService;
    private final StorageUsageRepository usageRepository;
    private final StorageQuotaPresetRepository presetRepository;
    private final StationRepository stationRepository;
    private final StorageReconciliationService reconciliationService;
    private final StorageBackendResolver backendResolver;
    private final StorageBackendAuditRepository auditRepository;
    private final Storage storageConfig;
    private final StorageBackendFactory backendFactory;
    private final CredentialCipher credentialCipher;
    private final InstanceStorageMigrationService instanceMigrationService;
    private final StorageBackendAuditService auditService;
    private final Conf conf;

    @Inject
    public StorageRoutes(
            StorageQuotaService quotaService,
            StorageUsageRepository usageRepository,
            StorageQuotaPresetRepository presetRepository,
            StationRepository stationRepository,
            StorageReconciliationService reconciliationService,
            StorageBackendResolver backendResolver,
            StorageBackendAuditRepository auditRepository,
            Storage storageConfig,
            StorageBackendFactory backendFactory,
            CredentialCipher credentialCipher,
            InstanceStorageMigrationService instanceMigrationService,
            StorageBackendAuditService auditService,
            Conf conf) {
        this.quotaService = quotaService;
        this.usageRepository = usageRepository;
        this.presetRepository = presetRepository;
        this.stationRepository = stationRepository;
        this.reconciliationService = reconciliationService;
        this.backendResolver = backendResolver;
        this.auditRepository = auditRepository;
        this.storageConfig = storageConfig;
        this.backendFactory = backendFactory;
        this.credentialCipher = credentialCipher;
        this.instanceMigrationService = instanceMigrationService;
        this.auditService = auditService;
        this.conf = conf;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        // Station-level usage (managers)
        routes.get(prefix + "/storage/usage", this::getStationUsage, StationPermission.STATION_MANAGER);

        // Admin: overview
        routes.get(prefix + "/admin/storage/usage", this::getAdminUsage, InstancePermission.ADMINISTRATOR);

        // Admin: reconciliation
        routes.post(prefix + "/admin/storage/recalculate", this::recalculateAll, InstancePermission.ADMINISTRATOR);
        routes.post(
                prefix + "/admin/storage/recalculate/{stationUid}",
                this::recalculateStation,
                InstancePermission.ADMINISTRATOR);

        // Admin: quota presets CRUD
        routes.get(prefix + "/admin/storage/presets", this::listPresets, InstancePermission.ADMINISTRATOR);
        routes.post(
                prefix + "/admin/storage/presets",
                this::createPreset,
                InstancePermission.ADMINISTRATOR,
                StepUpCategory.INSTANCE_CONFIG);
        routes.put(
                prefix + "/admin/storage/presets/{id}",
                this::updatePreset,
                InstancePermission.ADMINISTRATOR,
                StepUpCategory.INSTANCE_CONFIG);
        routes.delete(
                prefix + "/admin/storage/presets/{id}",
                this::deletePreset,
                InstancePermission.ADMINISTRATOR,
                StepUpCategory.INSTANCE_CONFIG);
        routes.post(
                prefix + "/admin/storage/presets/{id}/apply",
                this::applyPreset,
                InstancePermission.ADMINISTRATOR,
                StepUpCategory.INSTANCE_CONFIG);

        // Admin: instance default storage backend
        routes.get(prefix + "/admin/storage/backend", this::getInstanceBackend, InstancePermission.ADMINISTRATOR);
        routes.post(
                prefix + "/admin/storage/backend/probe", this::probeInstanceBackend, InstancePermission.ADMINISTRATOR);
        routes.post(
                prefix + "/admin/storage/backend/probe-config",
                this::probeInstanceBackendConfig,
                InstancePermission.ADMINISTRATOR);
        routes.post(
                prefix + "/admin/storage/backend/apply",
                this::applyInstanceBackend,
                InstancePermission.ADMINISTRATOR,
                StepUpCategory.INSTANCE_CONFIG);
        routes.get(
                prefix + "/admin/storage/backend/apply/status",
                this::migrateInstanceStatus,
                InstancePermission.ADMINISTRATOR);

        // Admin: audit trail
        routes.get(prefix + "/admin/storage/audit", this::listAudit, InstancePermission.ADMINISTRATOR);

        // Admin: station quota management
        routes.put(
                prefix + "/admin/storage/stations/{stationUid}/quotas",
                this::updateStationQuotas,
                InstancePermission.ADMINISTRATOR,
                StepUpCategory.INSTANCE_CONFIG);
        routes.delete(
                prefix + "/admin/storage/stations/{stationUid}/quotas",
                this::resetStationQuotas,
                InstancePermission.ADMINISTRATOR,
                StepUpCategory.INSTANCE_CONFIG);
    }

    // -- Station usage --

    private void getStationUsage(Context ctx) {
        var session = UserSession.from(ctx);
        int stationId = session.stationId();
        var categories = usageRepository.findByStation(stationId);
        long totalBytes = categories.stream()
                .filter(u -> u.category().enforcesQuota())
                .mapToLong(StorageUsage::totalBytes)
                .sum();
        boolean usesOwnBackend = quotaService.hasOwnBackend(stationId);
        long quotaBytes = usesOwnBackend ? 0L : quotaService.getEffectiveTotalQuota(stationId);
        int quotaUsedPercent = quotaBytes > 0 ? (int) (totalBytes * 100 / quotaBytes) : 0;

        Map<String, Long> categoryQuotas = new HashMap<>();
        if (!usesOwnBackend) {
            for (StorageCategory cat : StorageCategory.values()) {
                if (cat.enforcesQuota()) {
                    categoryQuotas.put(cat.name(), quotaService.getEffectiveCategoryQuota(stationId, cat));
                }
            }
        }

        ctx.json(new StationUsageResponse(
                categories.stream()
                        .map(u -> new CategoryUsage(u.category().name(), u.totalBytes(), u.fileCount()))
                        .toList(),
                totalBytes,
                quotaBytes,
                quotaUsedPercent,
                categoryQuotas,
                usesOwnBackend));
    }

    // -- Admin overview --

    private void getAdminUsage(Context ctx) {
        var stations = stationRepository.findAll();
        var allUsage = usageRepository.findAll();
        var presetAssignments = presetRepository.findStationPresetAssignments();

        Map<Integer, List<StorageUsage>> usageByStation = new HashMap<>();
        for (var usage : allUsage) {
            usageByStation
                    .computeIfAbsent(usage.stationId(), k -> new ArrayList<>())
                    .add(usage);
        }

        var result = stations.stream()
                .map(station -> {
                    var stationUsages = usageByStation.getOrDefault(station.id(), List.of());
                    long totalBytes = stationUsages.stream()
                            .filter(u -> u.category().enforcesQuota())
                            .mapToLong(StorageUsage::totalBytes)
                            .sum();
                    boolean usesOwnBackend = quotaService.hasOwnBackend(station.id());
                    long quotaBytes = usesOwnBackend ? 0L : quotaService.getEffectiveTotalQuota(station.id());
                    int quotaUsedPercent = quotaBytes > 0 ? (int) (totalBytes * 100 / quotaBytes) : 0;
                    var assignment = presetAssignments.get(station.id());
                    return new AdminStationUsage(
                            station.uid().toString(),
                            station.name(),
                            totalBytes,
                            quotaBytes,
                            quotaUsedPercent,
                            stationUsages.stream()
                                    .map(u -> new CategoryUsage(u.category().name(), u.totalBytes(), u.fileCount()))
                                    .toList(),
                            assignment != null ? assignment.presetId() : null,
                            assignment != null ? assignment.presetName() : null,
                            usesOwnBackend);
                })
                .toList();

        ctx.json(result);
    }

    // -- Reconciliation --

    private void recalculateAll(Context ctx) {
        Thread.ofVirtual().name("admin-reconcile-all").start(reconciliationService::reconcileAll);
        ctx.status(HttpStatus.ACCEPTED);
    }

    private void recalculateStation(Context ctx) {
        UUID uid = UUID.fromString(ctx.pathParam("stationUid"));
        var stationId = stationRepository.resolveId(uid);
        if (stationId.isEmpty()) {
            ctx.status(HttpStatus.NOT_FOUND);
            return;
        }
        reconciliationService.reconcileStation(stationId.get());
        ctx.status(HttpStatus.OK);
    }

    // -- Presets CRUD --

    private void listPresets(Context ctx) {
        ctx.json(presetRepository.findAll());
    }

    private void createPreset(Context ctx) {
        var req = ctx.bodyAsClass(PresetRequest.class);
        ctx.json(presetRepository.create(
                req.name(),
                req.total(),
                req.kb(),
                req.board(),
                req.images(),
                req.pages(),
                req.perFile(),
                req.perImage()));
    }

    private void updatePreset(Context ctx) {
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        var req = ctx.bodyAsClass(PresetRequest.class);
        ctx.json(presetRepository.update(
                id,
                req.name(),
                req.total(),
                req.kb(),
                req.board(),
                req.images(),
                req.pages(),
                req.perFile(),
                req.perImage()));
    }

    private void deletePreset(Context ctx) {
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        presetRepository.delete(id);
        ctx.status(HttpStatus.NO_CONTENT);
    }

    private void applyPreset(Context ctx) {
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        var req = ctx.bodyAsClass(ApplyPresetRequest.class);
        for (String uidStr : req.stationUids()) {
            UUID uid = UUID.fromString(uidStr);
            stationRepository.resolveId(uid).ifPresent(stationId -> presetRepository.applyToStation(id, stationId));
        }
        ctx.status(HttpStatus.OK);
    }

    // -- Instance default backend --

    private void getInstanceBackend(Context ctx) {
        var backend = backendResolver.instanceDefault();
        var settings = storageConfig.backend();
        InstanceBackendSummary summary =
                switch (backend.type()) {
                    case LOCAL -> new LocalSummary(settings.local().root());
                    case SMB ->
                        new SmbSummary(
                                settings.smb().host(),
                                settings.smb().port(),
                                settings.smb().share(),
                                settings.smb().basePath(),
                                settings.smb().seal(),
                                settings.smb().dfs());
                    case SFTP ->
                        new SftpSummary(
                                settings.sftp().host(),
                                settings.sftp().port(),
                                settings.sftp().username(),
                                settings.sftp().basePath(),
                                !settings.sftp().knownHostsFingerprint().isBlank());
                    case S3 ->
                        new S3Summary(
                                settings.s3().endpoint(),
                                settings.s3().region(),
                                settings.s3().bucket(),
                                settings.s3().pathStyle(),
                                settings.s3().sseAlgorithm(),
                                settings.s3().basePath());
                };
        ctx.json(summary);
    }

    private void probeInstanceBackend(Context ctx) {
        HealthStatus status = backendResolver.instanceDefault().probe();
        ctx.json(new ProbeResult(
                status.healthy(),
                status.error().orElse(null),
                status.checkedAt().toString()));
    }

    /**
     * Dry-run probe against an unsaved form payload — accepts an {@link InstanceBackendRequest},
     * builds a transient backend from it, runs {@link StorageBackend#probe()} and returns the
     * result without writing to conf.yml or invalidating any cached backend. The UI calls this
     * from the "Verbindung testen" button so an operator can validate credentials before
     * clicking Save (which itself probes and refuses to persist a broken config, but only
     * after committing the request).
     */
    private void probeInstanceBackendConfig(Context ctx) {
        InstanceBackendRequest req = ctx.bodyAsClass(InstanceBackendRequest.class);
        boolean healthy;
        String errorOrNull;
        java.time.Instant checkedAt;
        try (StorageBackend probe = backendFactory.buildForInstance(buildSettings(req, credentialCipher))) {
            HealthStatus status = probe.probe();
            healthy = status.healthy();
            errorOrNull = status.error().orElse(null);
            checkedAt = status.checkedAt();
        } catch (Exception e) {
            healthy = false;
            errorOrNull = e.getMessage();
            checkedAt = java.time.Instant.now();
        }
        ctx.status(HttpStatus.OK).json(new ProbeResult(healthy, errorOrNull, checkedAt.toString()));
    }

    /**
     * Unified "save and apply" for the instance-default backend: probes the target, holds an
     * instance-wide read-only window while the copy runs, flips {@code conf.yml} and
     * invalidates the cached backend only after the copy + sample-verify succeed, then deletes
     * the source bytes unless {@code keepSource} is set. The previous bare update endpoint that
     * swapped config without moving bytes is gone — every config change goes through this
     * primitive so an operator can never end up with bytes on one backend and reads pointed at
     * another. For an empty source the copy phase is a no-op.
     */
    private void applyInstanceBackend(Context ctx) {
        InstanceMigrateRequest req = ctx.bodyAsClass(InstanceMigrateRequest.class);
        if (req.target() == null) {
            throw new BadRequestResponse("target is required");
        }
        StorageBackendSettings targetSettings = buildSettings(req.target(), credentialCipher);
        String oldRedacted = redactedSettings(storageConfig.backend());
        String newRedacted = redactedSettings(targetSettings);
        var actor = actor(ctx);
        auditService.recordInstanceMigration(
                actor, StorageAuditAction.INSTANCE_MIGRATION_STARTED, oldRedacted, newRedacted, null);
        InstanceStorageMigrationService.PreparedMigration prepared;
        try {
            prepared = instanceMigrationService.prepare(targetSettings);
        } catch (MigrationException e) {
            auditService.recordInstanceMigration(
                    actor, StorageAuditAction.INSTANCE_MIGRATION_FAILED, oldRedacted, newRedacted, e.getMessage());
            throw new BadRequestResponse(e.getMessage());
        }
        InstanceStorageMigrationService.MigrationResult result;
        try {
            applyToConfig(req.target(), storageConfig.backend());
            conf.save();
            backendFactory.invalidateInstanceDefault();
            result = instanceMigrationService.commit(prepared, req.keepSource() != null && req.keepSource());
        } catch (Exception e) {
            instanceMigrationService.abort(prepared);
            auditService.recordInstanceMigration(
                    actor, StorageAuditAction.INSTANCE_MIGRATION_FAILED, oldRedacted, newRedacted, e.getMessage());
            throw new RuntimeException("Migration failed during commit: " + e.getMessage(), e);
        }
        auditService.recordInstanceMigration(
                actor, StorageAuditAction.INSTANCE_MIGRATION_COMPLETED, oldRedacted, newRedacted, null);
        ctx.status(HttpStatus.OK)
                .json(new InstanceMigrationResultResponse(
                        result.totalKeys(), result.copied(), result.skipped(), result.deleted(), result.copiedBytes()));
    }

    private void migrateInstanceStatus(Context ctx) {
        ctx.json(new InstanceMigrationStatusResponse(instanceMigrationService.isMigrationInFlight()));
    }

    private StorageBackendAuditService.Actor actor(Context ctx) {
        UserSession session = UserSession.from(ctx);
        if (session == null || session.account() == null) {
            throw new ForbiddenResponse("No account in session");
        }
        Integer memberId = session.member() != null ? session.member().id() : null;
        return StorageBackendAuditService.Actor.human(session.account().id(), memberId);
    }

    /**
     * Builds a fresh {@link StorageBackendSettings} from the request. Credentials inside the
     * non-LOCAL variants land in the encrypted slots so when this object reaches
     * {@code conf.save()} the YAML on disk stays free of plain-text secrets.
     */
    private static StorageBackendSettings buildSettings(InstanceBackendRequest req, CredentialCipher cipher) {
        StorageBackendSettings settings = new StorageBackendSettings();
        try {
            setField(StorageBackendSettings.class, settings, "type", typeOf(req));
            switch (req) {
                case LocalRequest r ->
                    setField(
                            StorageBackendSettings.LocalSettings.class,
                            settings.local(),
                            "root",
                            r.root() == null ? "data" : r.root());
                case S3Request r -> {
                    var s3 = settings.s3();
                    setField(StorageBackendSettings.S3Settings.class, s3, "endpoint", emptyToBlank(r.endpoint()));
                    setField(StorageBackendSettings.S3Settings.class, s3, "region", emptyToBlank(r.region()));
                    setField(StorageBackendSettings.S3Settings.class, s3, "bucket", emptyToBlank(r.bucket()));
                    setField(StorageBackendSettings.S3Settings.class, s3, "pathStyle", r.pathStyle());
                    setField(
                            StorageBackendSettings.S3Settings.class,
                            s3,
                            "sseAlgorithm",
                            emptyToBlank(r.sseAlgorithm()));
                    setField(StorageBackendSettings.S3Settings.class, s3, "basePath", emptyToBlank(r.basePath()));
                    setField(StorageBackendSettings.S3Settings.class, s3, "accessKey", "");
                    setField(StorageBackendSettings.S3Settings.class, s3, "secretKey", "");
                    setField(
                            StorageBackendSettings.S3Settings.class,
                            s3,
                            "accessKeyEnc",
                            cipher.encrypt(emptyToBlank(r.accessKey())));
                    setField(
                            StorageBackendSettings.S3Settings.class,
                            s3,
                            "secretKeyEnc",
                            cipher.encrypt(emptyToBlank(r.secretKey())));
                }
                case SmbRequest r -> {
                    var smb = settings.smb();
                    setField(StorageBackendSettings.SmbSettings.class, smb, "host", emptyToBlank(r.host()));
                    setField(StorageBackendSettings.SmbSettings.class, smb, "port", r.port());
                    setField(StorageBackendSettings.SmbSettings.class, smb, "share", emptyToBlank(r.share()));
                    setField(StorageBackendSettings.SmbSettings.class, smb, "domain", emptyToBlank(r.domain()));
                    setField(StorageBackendSettings.SmbSettings.class, smb, "basePath", emptyToBlank(r.basePath()));
                    setField(StorageBackendSettings.SmbSettings.class, smb, "seal", r.seal());
                    setField(StorageBackendSettings.SmbSettings.class, smb, "dfs", r.dfs());
                    setField(StorageBackendSettings.SmbSettings.class, smb, "username", emptyToBlank(r.username()));
                    setField(StorageBackendSettings.SmbSettings.class, smb, "password", "");
                    setField(
                            StorageBackendSettings.SmbSettings.class,
                            smb,
                            "passwordEnc",
                            cipher.encrypt(emptyToBlank(r.password())));
                }
                case SftpRequest r -> {
                    var sftp = settings.sftp();
                    setField(StorageBackendSettings.SftpSettings.class, sftp, "host", emptyToBlank(r.host()));
                    setField(StorageBackendSettings.SftpSettings.class, sftp, "port", r.port());
                    setField(StorageBackendSettings.SftpSettings.class, sftp, "username", emptyToBlank(r.username()));
                    setField(
                            StorageBackendSettings.SftpSettings.class,
                            sftp,
                            "knownHostsFingerprint",
                            emptyToBlank(r.knownHostsFingerprint()));
                    setField(StorageBackendSettings.SftpSettings.class, sftp, "basePath", emptyToBlank(r.basePath()));
                    setField(StorageBackendSettings.SftpSettings.class, sftp, "password", "");
                    setField(StorageBackendSettings.SftpSettings.class, sftp, "privateKey", "");
                    setField(
                            StorageBackendSettings.SftpSettings.class,
                            sftp,
                            "passwordEnc",
                            r.password() == null || r.password().isBlank() ? null : cipher.encrypt(r.password()));
                    setField(
                            StorageBackendSettings.SftpSettings.class,
                            sftp,
                            "privateKeyEnc",
                            r.privateKey() == null || r.privateKey().isBlank() ? null : cipher.encrypt(r.privateKey()));
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to assemble storage backend settings", e);
        }
        return settings;
    }

    /**
     * Copies the request's fields onto the in-memory {@link StorageBackendSettings} of
     * {@code conf.main().storage().backend()}. Used by the PUT and migrate handlers when they
     * are ready to flip the YAML; downstream {@code conf.save()} writes the new values to disk.
     */
    private void applyToConfig(InstanceBackendRequest req, StorageBackendSettings settings) throws Exception {
        StorageBackendSettings rebuilt = buildSettings(req, credentialCipher);
        setField(StorageBackendSettings.class, settings, "type", rebuilt.type());
        copyLocal(rebuilt.local(), settings.local());
        copyS3(rebuilt.s3(), settings.s3());
        copySmb(rebuilt.smb(), settings.smb());
        copySftp(rebuilt.sftp(), settings.sftp());
    }

    private static void copyLocal(StorageBackendSettings.LocalSettings src, StorageBackendSettings.LocalSettings dst)
            throws Exception {
        setField(StorageBackendSettings.LocalSettings.class, dst, "root", src.root());
    }

    private static void copyS3(StorageBackendSettings.S3Settings src, StorageBackendSettings.S3Settings dst)
            throws Exception {
        setField(StorageBackendSettings.S3Settings.class, dst, "endpoint", src.endpoint());
        setField(StorageBackendSettings.S3Settings.class, dst, "region", src.region());
        setField(StorageBackendSettings.S3Settings.class, dst, "bucket", src.bucket());
        setField(StorageBackendSettings.S3Settings.class, dst, "pathStyle", src.pathStyle());
        setField(StorageBackendSettings.S3Settings.class, dst, "sseAlgorithm", src.sseAlgorithm());
        setField(StorageBackendSettings.S3Settings.class, dst, "basePath", src.basePath());
        setField(StorageBackendSettings.S3Settings.class, dst, "accessKey", src.accessKey());
        setField(StorageBackendSettings.S3Settings.class, dst, "secretKey", src.secretKey());
        setField(StorageBackendSettings.S3Settings.class, dst, "accessKeyEnc", src.accessKeyEnc());
        setField(StorageBackendSettings.S3Settings.class, dst, "secretKeyEnc", src.secretKeyEnc());
    }

    private static void copySmb(StorageBackendSettings.SmbSettings src, StorageBackendSettings.SmbSettings dst)
            throws Exception {
        setField(StorageBackendSettings.SmbSettings.class, dst, "host", src.host());
        setField(StorageBackendSettings.SmbSettings.class, dst, "port", src.port());
        setField(StorageBackendSettings.SmbSettings.class, dst, "share", src.share());
        setField(StorageBackendSettings.SmbSettings.class, dst, "domain", src.domain());
        setField(StorageBackendSettings.SmbSettings.class, dst, "basePath", src.basePath());
        setField(StorageBackendSettings.SmbSettings.class, dst, "seal", src.seal());
        setField(StorageBackendSettings.SmbSettings.class, dst, "dfs", src.dfs());
        setField(StorageBackendSettings.SmbSettings.class, dst, "username", src.username());
        setField(StorageBackendSettings.SmbSettings.class, dst, "password", src.password());
        setField(StorageBackendSettings.SmbSettings.class, dst, "passwordEnc", src.passwordEnc());
    }

    private static void copySftp(StorageBackendSettings.SftpSettings src, StorageBackendSettings.SftpSettings dst)
            throws Exception {
        setField(StorageBackendSettings.SftpSettings.class, dst, "host", src.host());
        setField(StorageBackendSettings.SftpSettings.class, dst, "port", src.port());
        setField(StorageBackendSettings.SftpSettings.class, dst, "username", src.username());
        setField(StorageBackendSettings.SftpSettings.class, dst, "knownHostsFingerprint", src.knownHostsFingerprint());
        setField(StorageBackendSettings.SftpSettings.class, dst, "basePath", src.basePath());
        setField(StorageBackendSettings.SftpSettings.class, dst, "password", src.password());
        setField(StorageBackendSettings.SftpSettings.class, dst, "privateKey", src.privateKey());
        setField(StorageBackendSettings.SftpSettings.class, dst, "passwordEnc", src.passwordEnc());
        setField(StorageBackendSettings.SftpSettings.class, dst, "privateKeyEnc", src.privateKeyEnc());
    }

    private static String emptyToBlank(String s) {
        return s == null ? "" : s;
    }

    private static StorageBackendType typeOf(InstanceBackendRequest req) {
        return switch (req) {
            case LocalRequest ignored -> StorageBackendType.LOCAL;
            case S3Request ignored -> StorageBackendType.S3;
            case SmbRequest ignored -> StorageBackendType.SMB;
            case SftpRequest ignored -> StorageBackendType.SFTP;
        };
    }

    private static void setField(Class<?> clazz, Object target, String fieldName, Object value) throws Exception {
        Field field = clazz.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    /**
     * Tiny JSON summary of the current settings used by the audit row. Credentials are dropped
     * outright so the audit log never holds plain-text or even the encrypted form (the YAML on
     * disk already holds the encrypted form; logging it again is redundant noise).
     */
    private static String redactedSettings(StorageBackendSettings settings) {
        StringBuilder sb =
                new StringBuilder("{\"type\":\"").append(settings.type().name()).append("\"");
        switch (settings.type()) {
            case LOCAL ->
                sb.append(",\"root\":\"")
                        .append(escape(settings.local().root()))
                        .append("\"");
            case S3 -> {
                var s = settings.s3();
                sb.append(",\"endpoint\":\"")
                        .append(escape(s.endpoint()))
                        .append("\",\"region\":\"")
                        .append(escape(s.region()))
                        .append("\",\"bucket\":\"")
                        .append(escape(s.bucket()))
                        .append("\",\"pathStyle\":")
                        .append(s.pathStyle())
                        .append(",\"basePath\":\"")
                        .append(escape(s.basePath()))
                        .append("\"");
            }
            case SMB -> {
                var s = settings.smb();
                sb.append(",\"host\":\"")
                        .append(escape(s.host()))
                        .append("\",\"port\":")
                        .append(s.port())
                        .append(",\"share\":\"")
                        .append(escape(s.share()))
                        .append("\",\"basePath\":\"")
                        .append(escape(s.basePath()))
                        .append("\",\"seal\":")
                        .append(s.seal())
                        .append(",\"dfs\":")
                        .append(s.dfs());
            }
            case SFTP -> {
                var s = settings.sftp();
                sb.append(",\"host\":\"")
                        .append(escape(s.host()))
                        .append("\",\"port\":")
                        .append(s.port())
                        .append(",\"username\":\"")
                        .append(escape(s.username()))
                        .append("\",\"basePath\":\"")
                        .append(escape(s.basePath()))
                        .append("\"");
            }
        }
        return sb.append("}").toString();
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private void listAudit(Context ctx) {
        var before = ctx.queryParam("before") != null
                ? java.util.Optional.of(java.time.Instant.parse(ctx.queryParam("before")))
                : java.util.Optional.<java.time.Instant>empty();
        var stationId = ctx.queryParam("stationUid") == null
                ? java.util.Optional.<Integer>empty()
                : stationRepository.resolveId(UUID.fromString(ctx.queryParam("stationUid")));
        int limit = Math.clamp(ctx.queryParamAsClass("limit", Integer.class).getOrDefault(50), 1, 200);
        var entries = auditRepository.findAll(before, stationId, limit).stream()
                .map(StationStorageBackendRoutes::toResponse)
                .toList();
        ctx.json(entries);
    }

    // -- Station quota management --

    private void updateStationQuotas(Context ctx) {
        UUID uid = UUID.fromString(ctx.pathParam("stationUid"));
        var stationId = stationRepository.resolveId(uid);
        if (stationId.isEmpty()) {
            ctx.status(HttpStatus.NOT_FOUND);
            return;
        }
        var req = ctx.bodyAsClass(QuotaUpdateRequest.class);
        quotaService.updateStationQuotas(
                stationId.get(),
                req.totalBytes(),
                req.kbBytes(),
                req.boardBytes(),
                req.imagesBytes(),
                req.pagesBytes(),
                req.perFileBytes(),
                req.perImageBytes());
        ctx.status(HttpStatus.OK);
    }

    private void resetStationQuotas(Context ctx) {
        UUID uid = UUID.fromString(ctx.pathParam("stationUid"));
        var stationId = stationRepository.resolveId(uid);
        if (stationId.isEmpty()) {
            ctx.status(HttpStatus.NOT_FOUND);
            return;
        }
        presetRepository.resetStationQuotas(stationId.get());
        ctx.status(HttpStatus.OK);
    }

    // -- Request/Response records --

    /**
     * Sealed sum type for the GET /admin/storage/backend response. Each variant carries the
     * concrete settings for its backend type; credentials are never included.
     */
    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
    @JsonSubTypes({
        @JsonSubTypes.Type(value = LocalSummary.class, name = "LOCAL"),
        @JsonSubTypes.Type(value = S3Summary.class, name = "S3"),
        @JsonSubTypes.Type(value = SmbSummary.class, name = "SMB"),
        @JsonSubTypes.Type(value = SftpSummary.class, name = "SFTP")
    })
    public sealed interface InstanceBackendSummary {}

    record StationUsageResponse(
            List<CategoryUsage> categories,
            long totalBytes,
            long quotaBytes,
            int quotaUsedPercent,
            Map<String, Long> categoryQuotas,
            boolean usesOwnBackend) {}

    record CategoryUsage(String category, long totalBytes, int fileCount) {}

    record AdminStationUsage(
            String stationId,
            String stationName,
            long totalBytes,
            long quotaBytes,
            int quotaUsedPercent,
            List<CategoryUsage> categories,
            Integer presetId,
            String presetName,
            boolean usesOwnBackend) {}

    record PresetRequest(
            String name, long total, long kb, long board, long images, long pages, long perFile, long perImage) {}

    record ApplyPresetRequest(List<String> stationUids) {}

    record QuotaUpdateRequest(
            Long totalBytes,
            Long kbBytes,
            Long boardBytes,
            Long imagesBytes,
            Long pagesBytes,
            Long perFileBytes,
            Long perImageBytes) {}

    public record LocalSummary(String root) implements InstanceBackendSummary {}

    public record SmbSummary(String host, int port, String share, String basePath, boolean seal, boolean dfs)
            implements InstanceBackendSummary {}

    public record SftpSummary(String host, int port, String username, String basePath, boolean knownHostsPinned)
            implements InstanceBackendSummary {}

    public record S3Summary(
            String endpoint, String region, String bucket, boolean pathStyle, String sseAlgorithm, String basePath)
            implements InstanceBackendSummary {}

    record ProbeResult(boolean healthy, String error, String checkedAt) {}

    /**
     * Polymorphic payload for both PUT /admin/storage/backend and the inner {@code target}
     * field of POST /admin/storage/migrate. Credentials travel in plaintext over HTTPS and are
     * encrypted with {@link CredentialCipher} before they ever land on disk.
     */
    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
    @JsonSubTypes({
        @JsonSubTypes.Type(value = LocalRequest.class, name = "LOCAL"),
        @JsonSubTypes.Type(value = S3Request.class, name = "S3"),
        @JsonSubTypes.Type(value = SmbRequest.class, name = "SMB"),
        @JsonSubTypes.Type(value = SftpRequest.class, name = "SFTP")
    })
    public sealed interface InstanceBackendRequest permits LocalRequest, S3Request, SmbRequest, SftpRequest {}

    public record LocalRequest(String root) implements InstanceBackendRequest {}

    public record S3Request(
            String endpoint,
            String region,
            String bucket,
            boolean pathStyle,
            String sseAlgorithm,
            String basePath,
            String accessKey,
            String secretKey)
            implements InstanceBackendRequest {}

    public record SmbRequest(
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

    public record SftpRequest(
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
