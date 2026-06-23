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
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.feature.storage.backend.HealthStatus;
import dev.chojo.ember.feature.storage.backend.StorageBackend;
import dev.chojo.ember.feature.storage.backend.StorageBackendFactory;
import dev.chojo.ember.feature.storage.backend.StorageBackendResolver;
import dev.chojo.ember.feature.storage.backend.StorageBackendType;
import dev.chojo.ember.feature.storage.credential.CredentialCipher;
import dev.chojo.ember.feature.storage.credential.EncryptedBlob;
import dev.chojo.ember.feature.storage.credential.StoredCredentials;
import dev.chojo.ember.feature.storage.entity.StationStorageBackendConfig;
import dev.chojo.ember.feature.storage.entity.StorageCategory;
import dev.chojo.ember.feature.storage.repository.StationStorageConfigRepository;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.ForbiddenResponse;
import io.javalin.http.HttpStatus;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Optional;

/**
 * Station-scoped self-service routes for picking a remote storage backend. A station manager
 * can override the inherited instance default on any movable category without involving an
 * instance admin. Credentials are encrypted with {@link CredentialCipher} before they reach
 * the repository and are never returned to clients in plaintext.
 */
@Singleton
public class StationStorageBackendRoutes implements Routes {
    private final StationStorageConfigRepository repository;
    private final StorageBackendFactory factory;
    private final StorageBackendResolver resolver;
    private final CredentialCipher credentialCipher;
    private final StationRepository stationRepository;

    @Inject
    public StationStorageBackendRoutes(
            StationStorageConfigRepository repository,
            StorageBackendFactory factory,
            StorageBackendResolver resolver,
            CredentialCipher credentialCipher,
            StationRepository stationRepository) {
        this.repository = repository;
        this.factory = factory;
        this.resolver = resolver;
        this.credentialCipher = credentialCipher;
        this.stationRepository = stationRepository;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/station/storage/backend", this::list, StationPermission.STATION_MANAGER);
        routes.put(prefix + "/station/storage/backend/{category}", this::upsert, StationPermission.STATION_MANAGER);
        routes.delete(prefix + "/station/storage/backend/{category}", this::delete, StationPermission.STATION_MANAGER);
        routes.post(
                prefix + "/station/storage/backend/{category}/probe", this::probe, StationPermission.STATION_MANAGER);
    }

    private void list(Context ctx) {
        int stationId = sessionStationId(ctx);
        StorageBackendType instanceDefault = resolver.instanceDefault().type();
        List<BackendOverrideSummary> overrides = repository.findByStation(stationId).stream()
                .map(row -> toSummary(row.category(), row.config()))
                .toList();
        ctx.json(new BackendOverridesResponse(instanceDefault, overrides));
    }

    private void upsert(Context ctx) {
        int stationId = sessionStationId(ctx);
        StorageCategory category = parseMovableCategory(ctx);
        BackendOverrideRequest request = ctx.bodyAsClass(BackendOverrideRequest.class);
        StationStorageBackendConfig config = toEntity(request);
        try (StorageBackend probe = factory.buildForStation(config)) {
            HealthStatus status = probe.probe();
            if (!status.healthy()) {
                throw new BadRequestResponse("Probe failed: " + status.error().orElse("unknown error"));
            }
        } catch (BadRequestResponse e) {
            throw e;
        } catch (Exception e) {
            throw new BadRequestResponse("Probe failed: " + e.getMessage());
        }
        repository.upsert(stationId, category, config);
        resolver.invalidateStation(stationId, category);
        ctx.status(HttpStatus.NO_CONTENT);
    }

    private void delete(Context ctx) {
        int stationId = sessionStationId(ctx);
        StorageCategory category = parseMovableCategory(ctx);
        repository.delete(stationId, category);
        resolver.invalidateStation(stationId, category);
        ctx.status(HttpStatus.NO_CONTENT);
    }

    private void probe(Context ctx) {
        int stationId = sessionStationId(ctx);
        StorageCategory category = parseMovableCategory(ctx);
        var row = repository
                .findOne(stationId, category)
                .orElseThrow(() -> new BadRequestResponse("No override configured for this category"));
        try (StorageBackend backend = factory.buildForStation(row.config())) {
            HealthStatus status = backend.probe();
            ctx.json(new ProbeResult(
                    status.healthy(),
                    status.error().orElse(null),
                    status.checkedAt().toString()));
        } catch (Exception e) {
            ctx.json(new ProbeResult(
                    false, e.getMessage(), java.time.Instant.now().toString()));
        }
    }

    private int sessionStationId(Context ctx) {
        UserSession session = UserSession.from(ctx);
        Integer stationId = session.stationId();
        if (stationId == null) {
            throw new ForbiddenResponse("No station selected");
        }
        if (stationRepository.findById(stationId).isEmpty()) {
            throw new BadRequestResponse("Unknown station");
        }
        return stationId;
    }

    private static StorageCategory parseMovableCategory(Context ctx) {
        String raw = ctx.pathParam("category");
        StorageCategory category;
        try {
            category = StorageCategory.valueOf(raw);
        } catch (IllegalArgumentException e) {
            throw new BadRequestResponse("Unknown storage category: " + raw);
        }
        if (category.isLocalPinned()) {
            throw new BadRequestResponse("Category " + category + " is local-pinned and cannot be overridden");
        }
        if (category.scopeKind() != dev.chojo.ember.feature.storage.entity.StorageScope.Kind.STATION) {
            throw new BadRequestResponse(
                    "Category " + category + " is not station-scoped and cannot be overridden per station");
        }
        return category;
    }

    private StationStorageBackendConfig toEntity(BackendOverrideRequest request) {
        return switch (request) {
            case S3Request r ->
                new StationStorageBackendConfig.S3Variant(
                        r.endpoint(),
                        r.region(),
                        r.bucket(),
                        r.pathStyle(),
                        Optional.ofNullable(r.sseAlgorithm()).filter(s -> !s.isBlank()),
                        r.basePath(),
                        encryptS3(r));
            case SmbRequest r ->
                new StationStorageBackendConfig.SmbVariant(
                        r.host(), r.port(), r.share(), r.domain(), r.basePath(), r.seal(), r.dfs(), encryptSmb(r));
            case SftpRequest r ->
                new StationStorageBackendConfig.SftpVariant(
                        r.host(), r.port(), r.username(), r.knownHostsFingerprint(), r.basePath(), encryptSftp(r));
        };
    }

    private EncryptedBlob encryptS3(S3Request r) {
        if (r.accessKey() == null || r.secretKey() == null) {
            throw new BadRequestResponse("S3 override requires accessKey and secretKey");
        }
        return credentialCipher.encrypt(new StoredCredentials.S3(r.accessKey(), r.secretKey()).toJson());
    }

    private EncryptedBlob encryptSmb(SmbRequest r) {
        if (r.username() == null || r.password() == null) {
            throw new BadRequestResponse("SMB override requires username and password");
        }
        return credentialCipher.encrypt(new StoredCredentials.Smb(r.username(), r.password()).toJson());
    }

    private EncryptedBlob encryptSftp(SftpRequest r) {
        boolean hasPassword = r.password() != null && !r.password().isBlank();
        boolean hasKey = r.privateKey() != null && !r.privateKey().isBlank();
        if (hasPassword == hasKey) {
            throw new BadRequestResponse("SFTP override requires exactly one of password or privateKey");
        }
        return credentialCipher.encrypt(new StoredCredentials.Sftp(
                        r.username(),
                        r.password() == null ? "" : r.password(),
                        r.privateKey() == null ? "" : r.privateKey())
                .toJson());
    }

    private BackendOverrideSummary toSummary(StorageCategory category, StationStorageBackendConfig config) {
        return switch (config) {
            case StationStorageBackendConfig.S3Variant v ->
                new S3Summary(
                        category.name(),
                        v.endpoint(),
                        v.region(),
                        v.bucket(),
                        v.pathStyle(),
                        v.sseAlgorithm().orElse(""),
                        v.basePath());
            case StationStorageBackendConfig.SmbVariant v ->
                new SmbSummary(
                        category.name(), v.host(), v.port(), v.share(), v.domain(), v.basePath(), v.seal(), v.dfs());
            case StationStorageBackendConfig.SftpVariant v ->
                new SftpSummary(
                        category.name(),
                        v.host(),
                        v.port(),
                        v.username(),
                        !v.knownHostsFingerprint().isBlank(),
                        v.basePath());
        };
    }

    // -- Response shapes --

    record BackendOverridesResponse(StorageBackendType instanceDefault, List<BackendOverrideSummary> overrides) {}

    public sealed interface BackendOverrideSummary {
        String category();

        StorageBackendType type();
    }

    public record S3Summary(
            String category,
            String endpoint,
            String region,
            String bucket,
            boolean pathStyle,
            String sseAlgorithm,
            String basePath)
            implements BackendOverrideSummary {
        @Override
        public StorageBackendType type() {
            return StorageBackendType.S3;
        }
    }

    public record SmbSummary(
            String category,
            String host,
            int port,
            String share,
            String domain,
            String basePath,
            boolean seal,
            boolean dfs)
            implements BackendOverrideSummary {
        @Override
        public StorageBackendType type() {
            return StorageBackendType.SMB;
        }
    }

    public record SftpSummary(
            String category, String host, int port, String username, boolean knownHostsPinned, String basePath)
            implements BackendOverrideSummary {
        @Override
        public StorageBackendType type() {
            return StorageBackendType.SFTP;
        }
    }

    record ProbeResult(boolean healthy, String error, String checkedAt) {}

    // -- Request shapes (plaintext credentials in transit only; server encrypts before persisting) --

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
    @JsonSubTypes({
        @JsonSubTypes.Type(value = S3Request.class, name = "S3"),
        @JsonSubTypes.Type(value = SmbRequest.class, name = "SMB"),
        @JsonSubTypes.Type(value = SftpRequest.class, name = "SFTP")
    })
    public sealed interface BackendOverrideRequest {}

    public record S3Request(
            String endpoint,
            String region,
            String bucket,
            boolean pathStyle,
            String sseAlgorithm,
            String basePath,
            String accessKey,
            String secretKey)
            implements BackendOverrideRequest {}

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
            implements BackendOverrideRequest {}

    public record SftpRequest(
            String host,
            int port,
            String username,
            String knownHostsFingerprint,
            String basePath,
            String password,
            String privateKey)
            implements BackendOverrideRequest {}
}
