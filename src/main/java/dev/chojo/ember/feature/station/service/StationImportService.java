/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.station.service;

import de.chojo.sadu.queries.converter.StandardValueConverter;
import dev.chojo.ember.conf.file.elements.Api;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.account.service.AvatarService;
import dev.chojo.ember.feature.federation.service.FederationPartnerTransferFixupService;
import dev.chojo.ember.feature.federation.service.RemoteUrlValidator;
import dev.chojo.ember.feature.media.service.ImageVariantService;
import dev.chojo.ember.feature.page.service.PageFileStorageService;
import dev.chojo.ember.feature.page.service.PageImageVariantService;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.entity.StationModule;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.feature.storage.entity.StorageCategory;
import dev.chojo.ember.feature.storage.entity.StorageScope;
import dev.chojo.ember.feature.storage.service.StorageService;
import dev.chojo.ember.feature.storage.transfer.TransferBackendDescriptor;
import dev.chojo.ember.feature.storage.transfer.TransferBackendImporter;
import dev.chojo.ember.tracking.ColumnEntry;
import dev.chojo.ember.tracking.DataTracking;
import dev.chojo.ember.tracking.DataTrackingLoader;
import dev.chojo.ember.tracking.OutputShape;
import dev.chojo.ember.tracking.engine.GenericTableImporter;
import dev.chojo.ember.tracking.engine.GenericTableImporter.IdRemapper;
import dev.chojo.ember.tracking.engine.TableOrder;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.HttpResponseException;
import io.javalin.http.NotFoundResponse;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

/**
 * Imports a station bundle produced by {@link StationExportService} using only metadata
 * from {@code data_tracking.json}.
 *
 * <p>The service handles four kinds of wire entries:
 * <ul>
 *   <li><b>station</b> ({@link OutputShape#SINGLE}) — creates or reuses the station row and
 *       UPDATEs its settings columns from the payload.</li>
 *   <li><b>account</b> — matches by email; if no existing account is found a new one is created.</li>
 *   <li><b>account_credential</b> — links to the target account by email; if the target account
 *       has no credential yet, the source's password_hash is stored and {@code force_password_change}
 *       is set to TRUE so the user must reset on first login.</li>
 *   <li><b>station_disabled_module</b> ({@link OutputShape#FLAT}) — installs the flat list of module
 *       names via {@link StationRepository#setDisabledModules(int, java.util.Set)}.</li>
 *   <li>Everything else — delegated to {@link GenericTableImporter} which builds the INSERT from the
 *       table's tracking metadata, remapping FK ids via the {@link IdRemapper}.</li>
 * </ul>
 */
@Singleton
public class StationImportService {

    private static final Logger log = LoggerFactory.getLogger(StationImportService.class);
    private static final int PAGE_SIZE = 500;
    private static final long MIN_REQUEST_INTERVAL_MILLIS = 200L;

    private final StationRepository stationRepository;
    private final AccountRepository accountRepository;
    private final StationExportService exportService;
    private final Api api;
    private final TransferBackendImporter backendImporter;
    private final StorageService storageService;
    private final AvatarService avatarService;
    private final ImageVariantService imageVariantService;
    private final PageFileStorageService pageFileStorageService;
    private final PageImageVariantService pageImageVariantService;
    private final FederationPartnerTransferFixupService federationFixup;
    private final RemoteUrlValidator urlValidator;
    private final GenericTableImporter engine;
    private final List<String> tableOrder;
    private final DataTracking tracking;

    private final ConcurrentHashMap<Integer, ImportProgress> activeImports = new ConcurrentHashMap<>();
    private final ExecutorService importExecutor = Executors.newSingleThreadExecutor(r -> {
        var t = new Thread(r, "station-import");
        t.setDaemon(true);
        return t;
    });
    private volatile RemoteImportSession activeRemoteSession;
    private final Object throttleLock = new Object();
    private long lastRequestMillis;

    @Inject
    public StationImportService(
            StationRepository stationRepository,
            AccountRepository accountRepository,
            StationExportService exportService,
            Api api,
            TransferBackendImporter backendImporter,
            StorageService storageService,
            AvatarService avatarService,
            ImageVariantService imageVariantService,
            PageFileStorageService pageFileStorageService,
            PageImageVariantService pageImageVariantService,
            FederationPartnerTransferFixupService federationFixup,
            RemoteUrlValidator urlValidator) {
        this.stationRepository = stationRepository;
        this.accountRepository = accountRepository;
        this.exportService = exportService;
        this.api = api;
        this.backendImporter = backendImporter;
        this.storageService = storageService;
        this.avatarService = avatarService;
        this.imageVariantService = imageVariantService;
        this.pageFileStorageService = pageFileStorageService;
        this.pageImageVariantService = pageImageVariantService;
        this.federationFixup = federationFixup;
        this.urlValidator = urlValidator;
        DataTracking t;
        try {
            t = DataTrackingLoader.loadFromClasspath();
        } catch (IOException e) {
            log.warn("Could not load data_tracking.json — import engine will be unusable", e);
            t = DataTrackingLoader.empty();
        }
        this.tracking = t;
        this.engine = new GenericTableImporter(t);
        this.tableOrder = TableOrder.topological(t);
    }

    // -- Public API --

    /**
     * Registers the source-station-id → target-station-id mapping so FKs from other tables that
     * reference {@code station(id)} resolve correctly. The source id is carried on the {@code station}
     * wire entry (the int-id column is otherwise dropped by {@link #applyStationFields}).
     */
    private static void seedStationRemap(IdRemapper idMap, Map<String, Object> stationData, int targetStationId) {
        if (stationData == null) return;
        Integer sourceId = asInteger(stationData.get("id"));
        if (sourceId != null) idMap.put("station", sourceId, targetStationId);
    }

    private static Integer asInteger(Object val) {
        if (val instanceof Number n) return n.intValue();
        if (val instanceof String s) {
            try {
                return Integer.parseInt(s);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private static List<StorageCategory> transferrableStationCategories() {
        var out = new ArrayList<StorageCategory>();
        for (StorageCategory c : StorageCategory.values()) {
            if (c.scopeKind() != StorageScope.Kind.STATION) continue;
            if (!c.isMovable()) continue;
            out.add(c);
        }
        return out;
    }

    /**
     * URL-encodes each path segment of a relative key, keeping the {@code /} separators intact.
     */
    private static String encodeKeyPath(String key) {
        String[] parts = key.split("/", -1);
        var sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) sb.append('/');
            sb.append(URLEncoder.encode(parts[i], StandardCharsets.UTF_8).replace("+", "%20"));
        }
        return sb.toString();
    }

    private static UUID parseUuidOrNull(Object raw) {
        if (raw == null) return null;
        try {
            return UUID.fromString(raw.toString());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String columnType(List<ColumnEntry> cols, String name) {
        for (var c : cols) if (c.name().equals(name)) return c.type();
        return null;
    }

    /**
     * Coerces a wire-format timestamp value to {@link Instant}. The canonical wire format is
     * epoch milliseconds (long) emitted by {@code GenericTableExporter}, but legacy / mixed
     * payloads with ISO-8601 strings or driver-native types are accepted so the importer keeps
     * working through a wire-format transition.
     */
    private static Instant toInstant(Object val) {
        return switch (val) {
            case null -> null;
            case Instant i -> i;
            case Number n -> Instant.ofEpochMilli(n.longValue());
            case String s when !s.isBlank() -> Instant.parse(s);
            default -> null;
        };
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object o) {
        return o instanceof Map ? (Map<String, Object>) o : null;
    }

    private static String asString(Object o, String defaultValue) {
        return o == null ? defaultValue : o.toString();
    }

    /**
     * Synchronously imports a bundle keyed by table name into a new station. Used by tests.
     * The bundle's {@code station} entry must be a {@code Map<String, Object>} (SINGLE shape).
     */
    public ImportResult importStation(Map<String, Object> bundle) {
        Map<String, Object> stationData = asMap(bundle.get("station"));
        String name = stationData == null ? "Imported Station" : asString(stationData.get("name"), "Imported Station");
        Station station = stationRepository.create(name);
        int stationId = station.id();
        applyStationFields(stationId, stationData);
        // Seed the id-remap for the station itself so any cross-table FK to station(id) — e.g.
        // federation_lending_request.requesting_station_id / owning_station_id, or station_ai_provider
        // — can be resolved during the rest of the import.
        var idMap = new IdRemapper();
        seedStationRemap(idMap, stationData, stationId);
        int total = 1 + runImport(stationId, bundle, idMap);
        log.info("Station import complete: created station id={} ('{}'), {} rows imported", stationId, name, total);
        return new ImportResult(stationId, name, total);
    }

    // -- Internals --

    /**
     * Synchronously merges a bundle into an existing station. The station's own settings get
     * applied (timezone, locale, themes, public toggles); all other TRACKED tables are inserted
     * alongside the station's existing data.
     */
    public void importStationInto(int targetStationId, Map<String, Object> bundle) {
        Map<String, Object> stationData = asMap(bundle.get("station"));
        if (stationData != null) applyStationFields(targetStationId, stationData);
        String name =
                stationRepository.findById(targetStationId).map(Station::name).orElse("Station");
        var idMap = new IdRemapper();
        seedStationRemap(idMap, stationData, targetStationId);
        int total = runImport(targetStationId, bundle, idMap);
        log.info("Station import-into complete: station={}, {} rows merged", targetStationId, total);
        new ImportResult(targetStationId, name, total);
    }

    /**
     * Returns the current progress for an active or recently completed import.
     */
    public ImportProgress getProgress(int stationId) {
        return activeImports.get(stationId);
    }

    /**
     * Rejects an import source URL that resolves to a private, loopback, or otherwise
     * non-public address before any request is issued. Every fetch derives its URL
     * from this same base, so validating it here guards the whole import run against
     * server-side request forgery.
     */
    private void validateImportSource(String baseUrl) {
        if (!urlValidator.isAllowed(baseUrl)) {
            throw new BadRequestResponse(RemoteUrlValidator.rejectReason());
        }
    }

    /**
     * Pulls a station bundle from a remote Ember instance and creates a new station from it.
     */
    public ImportResult startRemoteImport(String sourceUrl, String token) {
        String baseUrl = sourceUrl.replaceAll("/+$", "");
        validateImportSource(baseUrl);
        log.info("start remote-import-as-new-station from source {}", baseUrl);
        var mapper = JsonMapper.builder().build();
        var httpClient = buildImportHttpClient(baseUrl);
        verifyRemoteSchemaHash(httpClient, mapper, baseUrl, token);

        Map<String, Object> stationPage = fetchRemotePage(httpClient, mapper, baseUrl, token, "station", 0, PAGE_SIZE);
        Map<String, Object> stationData = asMap(stationPage.get("station"));
        if (stationData == null) {
            throw new BadRequestResponse("Remote station table missing 'station' field");
        }

        String stationName = asString(stationData.get("name"), "Imported Station");
        Station station = stationRepository.create(stationName);
        int stationId = station.id();
        applyStationFields(stationId, stationData);

        // Re-fetch after applyStationFields because it preserves the source UID via
        // stationRepository.updateUid(...) — the original Station instance's uid() is stale,
        // and getProgressByUid (and the StationIdModule-serialized response stationId) both
        // need the current uid.
        UUID currentUid =
                stationRepository.findById(stationId).map(Station::uid).orElse(station.uid());
        var progress = new ImportProgress(stationId, currentUid, stationName, buildPhases(), baseUrl, token);
        activeImports.put(stationId, progress);
        importExecutor.submit(() -> runRemoteImport(stationId, baseUrl, token, httpClient, mapper, progress));
        return new ImportResult(stationId, stationName, 0);
    }

    /**
     * Pulls a station bundle from a remote Ember instance and merges it INTO an existing station.
     */
    public void startRemoteImportInto(int stationId, String sourceUrl, String token) {
        String baseUrl = sourceUrl.replaceAll("/+$", "");
        validateImportSource(baseUrl);
        log.info("start remote-import-into-station {} from source {}", stationId, baseUrl);
        var mapper = JsonMapper.builder().build();
        var httpClient = buildImportHttpClient(baseUrl);
        verifyRemoteSchemaHash(httpClient, mapper, baseUrl, token);

        Map<String, Object> stationPage = fetchRemotePage(httpClient, mapper, baseUrl, token, "station", 0, PAGE_SIZE);
        Map<String, Object> stationData = asMap(stationPage.get("station"));
        if (stationData != null) applyStationFields(stationId, stationData);

        Station target = stationRepository
                .findById(stationId)
                .orElseThrow(() -> new BadRequestResponse("Target station not found"));
        var progress = new ImportProgress(stationId, target.uid(), target.name(), buildPhases(), baseUrl, token);
        activeImports.put(stationId, progress);
        importExecutor.submit(() -> runRemoteImport(stationId, baseUrl, token, httpClient, mapper, progress));
        new ImportResult(stationId, target.name(), 0);
    }

    /**
     * Returns the active or failed import progress for a station identified by its UUID, or
     * {@code null} when no progress (alive or failed) is on file. Searches the in-memory map
     * by uid so a failed import survives the destination-station deletion that follows
     * failure.
     */
    public ImportProgress getProgressByUid(UUID stationUid) {
        for (var progress : activeImports.values()) {
            if (stationUid.equals(progress.stationUid())) return progress;
        }
        return null;
    }

    /**
     * Cleans up the destination side of a failed import and starts a fresh run with the same
     * token: deletes the half-imported station (if it still exists) and re-invokes
     * {@link #startRemoteImport(String, String)} with the source URL and token captured on the
     * original attempt. Returns the freshly-minted import result so the caller can navigate
     * to the new progress page. Throws when the original progress is not in FAILED state.
     */
    public ImportResult retryFailedImport(UUID stationUid) {
        ImportProgress failed = getProgressByUid(stationUid);
        if (failed == null) {
            throw new NotFoundResponse("No import progress for that station");
        }
        if (failed.status() != ImportProgress.Status.FAILED) {
            throw new BadRequestResponse("Import is not in FAILED state");
        }
        try {
            stationRepository.delete(failed.stationId());
        } catch (Exception ignored) {
            // Station may already be gone; the auto-delete on failure handles that.
        }
        activeImports.remove(failed.stationId());
        return startRemoteImport(failed.sourceUrl(), failed.token());
    }

    /**
     * Builds the ordered list of phase ids the import will walk: every tracked table (in
     * topological order), the source storage backend handshake, one entry per movable
     * station-scoped file category, and finally the avatar carry-over for newly-created
     * accounts. The list is static for a given build of the importer, so the destination
     * UI can render the full checklist up front and tick each entry as the run progresses.
     */
    private List<String> buildPhases() {
        List<String> phases = new ArrayList<>(tableOrder);
        phases.add("storage_backend");
        for (StorageCategory category : transferrableStationCategories()) {
            phases.add("files_" + category.name().toLowerCase());
        }
        phases.add("account_avatars");
        return phases;
    }

    /**
     * Walks the topological table order and imports each payload from the bundle. Returns the total
     * number of rows imported across all tables. Also sets the station owner to the first imported
     * MANAGER member when no owner is set yet.
     */
    private int runImport(int stationId, Map<String, Object> bundle, IdRemapper idMap) {
        int total = 0;
        for (String table : tableOrder) {
            if ("station".equals(table)) continue;
            Object payload = bundle.get(table);
            if (payload == null) continue;
            total += importTable(stationId, table, payload, idMap);
        }
        assignDefaultOwnerIfNeeded(stationId);
        return total;
    }

    /**
     * If the target station has no owner yet, assigns the first MANAGER member as owner. This
     * preserves the semantic the legacy importer applied while processing memberUserTypes.
     */
    private void assignDefaultOwnerIfNeeded(int stationId) {
        var station = stationRepository.findById(stationId).orElse(null);
        if (station == null || station.ownerMemberId() != null) return;
        query("""
                SELECT id FROM station_member WHERE station_id = :station_id AND user_type = 'MANAGER'
                 ORDER BY id LIMIT 1;""")
                .single(call().bind("station_id", stationId))
                .map(row -> row.getInt("id"))
                .first()
                .ifPresent(memberId -> stationRepository.setOwner(stationId, memberId));
    }

    private void runRemoteImport(
            int stationId, String baseUrl, String token, HttpClient httpClient, ObjectMapper mapper, ImportProgress p) {
        var session = new RemoteImportSession();
        activeRemoteSession = session;
        log.info(
                "async run starting for station {} ('{}'), {} tables in topological order",
                stationId,
                p.stationName(),
                tableOrder.size());
        try {
            var idMap = new IdRemapper();
            int i = 0;
            for (String table : tableOrder) {
                p.startPhase(table);
                if ("station".equals(table)) {
                    log.info(
                            "table {}/{} '{}' — already applied synchronously, skipping",
                            ++i,
                            tableOrder.size(),
                            table);
                    p.completePhase();
                    continue;
                }
                log.info("table {}/{} '{}' — fetching from source", ++i, tableOrder.size(), table);
                fetchAndImportPaginated(stationId, table, baseUrl, token, httpClient, mapper, idMap);
                p.completePhase();
            }
            log.info("tables done, applying source storage backend");
            p.startPhase("storage_backend");
            boolean installedRemote = applySourceBackend(stationId, baseUrl, token, httpClient, mapper);
            p.completePhase();
            Station targetStation = stationRepository
                    .findById(stationId)
                    .orElseThrow(() -> new RuntimeException("Station " + stationId + " not found after table import"));
            StorageScope.Station scope = new StorageScope.Station(stationId, targetStation.uid());
            for (StorageCategory category : transferrableStationCategories()) {
                String phaseId = "files_" + category.name().toLowerCase();
                p.startPhase(phaseId);
                if (!installedRemote) {
                    copyCategory(scope, category, baseUrl, token, httpClient, mapper, p);
                }
                p.completePhase();
            }
            log.info("copying avatars for newly-created accounts");
            p.startPhase("account_avatars");
            copyNewAccountAvatars(session, baseUrl, token, httpClient, p);
            p.completePhase();
            federationFixup.rewriteAfterImport(stationId, baseUrl);
            federationFixup.announceNewHostToRemotePartners(stationId, api.baseUrl());
            notifySourceOfComplete(baseUrl, token, httpClient);
            p.complete();
            log.info("completed for station '{}' (id={})", p.stationName(), stationId);
        } catch (Exception e) {
            log.error("failed for station {}", stationId, e);
            p.fail(e.getMessage());
            notifySourceOfAbort(baseUrl, token, httpClient);
            try {
                stationRepository.delete(stationId);
                log.warn("deleted half-imported station {} after failure", stationId);
            } catch (Exception deleteErr) {
                log.error("could not clean up failed station {}", stationId, deleteErr);
            }
        } finally {
            activeRemoteSession = null;
        }
    }

    /**
     * Best-effort POST to the source's token-authenticated complete endpoint so the source can
     * flip {@code remote_host} on every {@code federation_partner} row that points at the
     * departed station, redirecting partnerships from the old local row to this destination's
     * URL. Any failure here is logged and swallowed — partnerships on the source will fall
     * back to manual reconfiguration if this call is lost.
     */
    private void notifySourceOfComplete(String baseUrl, String token, HttpClient httpClient) {
        postTransferSignal(baseUrl, token, httpClient, "complete", "completion");
    }

    /**
     * Best-effort POST to the source's token-authenticated abort endpoint so the source can
     * clear the {@code read_only_for_transfer} flag immediately instead of waiting for the
     * idle-timeout watchdog. Any failure here is logged and swallowed — the source's watchdog
     * is the safety net.
     */
    private void notifySourceOfAbort(String baseUrl, String token, HttpClient httpClient) {
        postTransferSignal(baseUrl, token, httpClient, "abort", "abort");
    }

    /**
     * Best-effort token-authenticated POST to one of the source's transfer signalling endpoints.
     * Any failure is logged and swallowed.
     *
     * @param pathSegment the endpoint suffix under {@code /transfer/{token}/}
     * @param label       the human-readable action name used in log lines
     */
    private void postTransferSignal(
            String baseUrl, String token, HttpClient httpClient, String pathSegment, String label) {
        try {
            var uri = URI.create(baseUrl + "/api/v1/public/transfer/" + token + "/" + pathSegment);
            var builder = HttpRequest.newBuilder()
                    .uri(uri)
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .timeout(Duration.ofSeconds(30));
            String ourUrl = api.baseUrl();
            if (ourUrl != null && !ourUrl.isBlank()) {
                builder.header("X-Ember-Importing-From", ourUrl);
            }
            var response = sendThrottled(httpClient, builder.build(), HttpResponse.BodyHandlers.discarding());
            log.info("notified source of {}: HTTP {}", label, response.statusCode());
        } catch (Exception e) {
            log.warn("could not notify source of {}: {}", label, e.getMessage());
        }
    }

    /**
     * Pulls the source station's backend descriptor and either installs the same remote backend
     * on the destination (re-encrypting the carried credentials) or clears any leftover override
     * row when the source used the instance default. The next phase (LOCAL byte-copy) inspects
     * the boolean returned here to decide whether to skip the streaming loop.
     */
    private boolean applySourceBackend(
            int stationId, String baseUrl, String token, HttpClient httpClient, ObjectMapper mapper) {
        TransferBackendDescriptor descriptor = fetchBackendDescriptor(httpClient, mapper, baseUrl, token);
        boolean installed = backendImporter.apply(stationId, descriptor);
        if (installed) {
            log.info(
                    "Imported source storage backend ({}) for station {}",
                    descriptor.getClass().getSimpleName(),
                    stationId);
        }
        return installed;
    }

    /**
     * Pulls every key in one station-scoped movable category from the source and stores it
     * on the destination's backend. Per-key streaming: the response body is piped straight
     * into {@link StorageService#store}. Keys that already exist on the destination are
     * skipped so a retried import after a partial failure is idempotent (cheap exists check
     * rather than a SHA round-trip).
     */
    private void copyCategory(
            StorageScope.Station scope,
            StorageCategory category,
            String baseUrl,
            String token,
            HttpClient httpClient,
            ObjectMapper mapper,
            ImportProgress progress) {
        int copied = 0;
        int skipped = 0;
        String after = null;
        boolean totalPinned = false;
        while (true) {
            ListKeysPage page = listRemoteKeys(httpClient, mapper, baseUrl, token, category, after);
            if (!totalPinned) {
                progress.setSubTotal(
                        page.total() != null ? page.total() : page.keys().size());
                totalPinned = true;
            } else if (page.total() == null) {
                progress.setSubTotal(progress.subTotal() + page.keys().size());
            }
            for (String key : page.keys()) {
                if (storageService.readRelative(scope, category, key).isPresent()) {
                    skipped++;
                } else if (streamRemoteFile(scope, category, baseUrl, token, key, httpClient)) {
                    copied++;
                }
                progress.incrementSub();
            }
            if (page.next() == null) break;
            after = page.next();
        }
        if (copied > 0 || skipped > 0) {
            log.info("Byte-copied {} key(s) for category {} (skipped {} already present)", copied, category, skipped);
        }
    }

    /**
     * Returns {@code true} when the key was streamed successfully; {@code false} when the source
     * answered 404 (the row was deleted concurrently — acceptable, the row will likely be
     * re-listed in a later transfer or stay absent).
     *
     * <p>For image-producing categories the source only ships the uploaded original; the
     * receiver re-generates the size and WebP variants locally via the matching domain
     * service. For everything else the bytes are persisted verbatim via {@link StorageService}.
     */
    private boolean streamRemoteFile(
            StorageScope.Station scope,
            StorageCategory category,
            String baseUrl,
            String token,
            String key,
            HttpClient httpClient) {
        String encodedKey = encodeKeyPath(key);
        var uri = URI.create(
                baseUrl + "/api/v1/public/transfer/" + token + "/files/" + category.name() + "/" + encodedKey);
        log.info("stream file: GET {}", redactToken(uri));
        try {
            var request = newImportRequest(uri);
            var response = sendThrottled(httpClient, request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() == 404) {
                log.info("stream file: category {} key '{}' — source 404, skipping", category, key);
                return false;
            }
            if (response.statusCode() != 200) {
                throw new RuntimeException("Failed to stream key '" + key + "' for category " + category + ": HTTP "
                        + response.statusCode());
            }
            String contentType = response.headers().firstValue("Content-Type").orElse("application/octet-stream");
            byte[] body = response.body();
            storeImported(scope, category, key, body, contentType);
            return true;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to stream key '" + key + "' from remote", e);
        }
    }

    /**
     * Routes a byte payload received during transfer to the right destination service. Image
     * categories go through their variant-generating service so the destination rebuilds the
     * resized / WebP set without pulling the duplicates over the wire. Non-image categories
     * fall back to a direct {@link StorageService} write.
     */
    private void storeImported(
            StorageScope.Station scope, StorageCategory category, String relativeKey, byte[] body, String contentType)
            throws IOException {
        switch (category) {
            case PAGE_FILES -> {
                String contentHash = parentOf(relativeKey);
                pageFileStorageService.store(scope.stationId(), contentHash, body, contentType);
                pageImageVariantService.generateVariants(scope.stationId(), contentHash, body, contentType);
            }
            case PAGE_IMAGES, IMAGE_LOST_AND_FOUND, IMAGE_QUIZ_QUESTION, IMAGE_KB_ICON, IMAGE_KB_IMAGE -> {
                String baseKey = parentOf(relativeKey);
                imageVariantService.store(scope, category, baseKey, body, contentType);
            }
            default ->
                storageService.store(
                        scope, category, relativeKey, new ByteArrayInputStream(body), body.length, contentType);
        }
    }

    /**
     * Returns the slash-separated parent segment of {@code relativeKey} — for an original key
     * like {@code <hash>/orig.png} this is the {@code <hash>}; for a flat key with no slash
     * the input is returned as-is.
     */
    private static String parentOf(String relativeKey) {
        int slash = relativeKey.lastIndexOf('/');
        return slash < 0 ? relativeKey : relativeKey.substring(0, slash);
    }

    @SuppressWarnings("unchecked")
    private ListKeysPage listRemoteKeys(
            HttpClient httpClient,
            ObjectMapper mapper,
            String baseUrl,
            String token,
            StorageCategory category,
            String after) {
        var sb = new StringBuilder(baseUrl)
                .append("/api/v1/public/transfer/")
                .append(token)
                .append("/files/")
                .append(category.name());
        if (after != null && !after.isBlank()) {
            sb.append("?after=").append(URLEncoder.encode(after, StandardCharsets.UTF_8));
        }
        var uri = URI.create(sb.toString());
        log.info("list keys: GET {}", redactToken(uri));
        try {
            var request = newImportRequest(uri);
            var response = sendThrottled(httpClient, request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new RuntimeException(
                        "Failed to list keys for category " + category + ": HTTP " + response.statusCode());
            }
            Map<String, Object> body = mapper.readValue(response.body(), Map.class);
            List<String> keys = (List<String>) body.getOrDefault("keys", List.of());
            String next = (String) body.get("next");
            Integer total = asInteger(body.get("total"));
            log.info(
                    "list keys: category {} returned {} key(s), next-cursor={}, total={}",
                    category,
                    keys.size(),
                    next == null ? "none" : "present",
                    total == null ? "absent" : total);
            return new ListKeysPage(keys, next, total);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to list keys for category " + category, e);
        }
    }

    /**
     * Streams the avatar for every account this import created on the destination. Existing
     * accounts on the destination are skipped; we never overwrite an avatar a user has already
     * uploaded locally. 404s from the source are normal (account had no avatar).
     */
    private void copyNewAccountAvatars(
            RemoteImportSession session, String baseUrl, String token, HttpClient httpClient, ImportProgress progress) {
        if (session.newAccounts().isEmpty()) {
            log.info("avatar carry-over: no newly-created accounts, skipping");
            return;
        }
        progress.setSubTotal(session.newAccounts().size());
        log.info(
                "avatar carry-over: {} newly-created account(s) to fetch",
                session.newAccounts().size());
        int copied = 0;
        int skipped = 0;
        for (NewAccountRef ref : session.newAccounts()) {
            try {
                var uri = URI.create(baseUrl + "/api/v1/public/transfer/" + token + "/avatars/" + ref.sourceUid());
                log.info("fetch avatar: GET {}", redactToken(uri));
                var request = newImportRequest(uri);
                var response = sendThrottled(httpClient, request, HttpResponse.BodyHandlers.ofByteArray());
                if (response.statusCode() == 404) {
                    skipped++;
                } else if (response.statusCode() != 200) {
                    log.warn(
                            "Source returned HTTP {} when fetching avatar for source account {}",
                            response.statusCode(),
                            ref.sourceUid());
                    skipped++;
                } else {
                    String contentType =
                            response.headers().firstValue("Content-Type").orElse("application/octet-stream");
                    avatarService.store(ref.destinationUid(), response.body(), contentType);
                    copied++;
                }
            } catch (Exception e) {
                log.warn("Failed to import avatar for source account {}", ref.sourceUid(), e);
                skipped++;
            }
            progress.incrementSub();
        }
        log.info("Avatar carry-over: imported {} (skipped {})", copied, skipped);
    }

    private TransferBackendDescriptor fetchBackendDescriptor(
            HttpClient httpClient, ObjectMapper mapper, String baseUrl, String token) {
        var uri = URI.create(baseUrl + "/api/v1/public/transfer/" + token + "/backend");
        log.info("fetch backend descriptor: GET {}", redactToken(uri));
        try {
            var request = newImportRequest(uri);
            var response = sendThrottled(httpClient, request, HttpResponse.BodyHandlers.ofString());
            log.info("fetch backend descriptor: HTTP {}", response.statusCode());
            if (response.statusCode() != 200) {
                throw new RuntimeException("Failed to fetch /backend from remote: HTTP " + response.statusCode());
            }
            return mapper.readValue(response.body(), TransferBackendDescriptor.class);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch backend descriptor from remote", e);
        }
    }

    private void fetchAndImportPaginated(
            int stationId,
            String table,
            String baseUrl,
            String token,
            HttpClient httpClient,
            ObjectMapper mapper,
            IdRemapper idMap) {
        OutputShape shape = shapeOf(table);
        int offset = 0;
        while (true) {
            var page = fetchRemotePage(httpClient, mapper, baseUrl, token, table, offset, PAGE_SIZE);
            Object payload = page.get(table);
            if (payload == null) return;
            int imported = importTable(stationId, table, payload, idMap);
            if (shape != OutputShape.ROWS) return; // SINGLE/FLAT are single-page
            if (imported < PAGE_SIZE) return;
            offset += PAGE_SIZE;
        }
    }

    /**
     * Dispatches a single wire payload (already extracted from the page envelope) to the right importer.
     */
    @SuppressWarnings("unchecked")
    private int importTable(int stationId, String table, Object payload, IdRemapper idMap) {
        return switch (table) {
            case "station" -> 0;
            case "account" -> importAccounts(stationId, (List<Map<String, Object>>) payload, idMap);
            case "account_credential" -> importAccountCredentials((List<Map<String, Object>>) payload);
            case "station_disabled_module" -> importDisabledModules(stationId, (List<Object>) payload);
            default -> engine.importRows(stationId, table, (List<Map<String, Object>>) payload, idMap);
        };
    }

    /**
     * Imports the source's account rows, populating the {@code account} entry in {@code idMap} for
     * every row so downstream tables (notably {@code station_member}, group memberships, audit
     * pointers) can remap their {@code account_id} FK without relying on email-lookup fallbacks.
     *
     * <p>Matching rules:
     * <ul>
     *   <li>A row whose source {@code email} is non-blank and already exists on the destination
     *       reuses that destination account — this is the "same human, different instance" merge
     *       case. The destination keeps its own UID.</li>
     *   <li>Every other row (blank email, or non-blank email with no destination match) creates a
     *       fresh destination account. Blank-email rows are intentional in this product — youth
     *       too young to have an address still need a member record.</li>
     * </ul>
     *
     * <p>Newly-created accounts try to preserve the source UID so UID-typed columns elsewhere
     * (e.g. {@code author_account_uid} on comments) round-trip without remap. A unique-constraint
     * collision on UID is rare; when it happens we accept the auto-generated UID and move on.
     */
    private int importAccounts(int stationId, List<Map<String, Object>> rows, IdRemapper idMap) {
        int created = 0;
        var session = activeRemoteSession;
        for (var row : rows) {
            Integer sourceId = asInteger(row.get("id"));
            String rawEmail = asString(row.get("email"), null);
            boolean hasEmail = rawEmail != null && !rawEmail.isBlank();
            String storedEmail = hasEmail ? rawEmail : null;
            int targetId;
            var existing = hasEmail ? accountRepository.findByEmail(rawEmail) : Optional.<Account>empty();
            if (existing.isPresent()) {
                targetId = existing.get().id();
            } else {
                String first = asString(row.get("first_name"), "");
                String last = asString(row.get("last_name"), "");
                var newAccount = accountRepository.create(storedEmail, first, last, true, stationId);
                targetId = newAccount.id();
                UUID sourceUid = parseUuidOrNull(row.get("uid"));
                UUID destinationUid = newAccount.uid();
                if (sourceUid != null && !sourceUid.equals(destinationUid)) {
                    try {
                        accountRepository.setUid(newAccount.id(), sourceUid);
                        destinationUid = sourceUid;
                    } catch (Exception e) {
                        log.warn(
                                "Source account UID {} already used on destination; keeping generated UID {}",
                                sourceUid,
                                destinationUid);
                    }
                }
                if (session != null && sourceUid != null) {
                    session.newAccounts().add(new NewAccountRef(sourceUid, destinationUid));
                }
                created++;
            }
            if (sourceId != null) idMap.put("account", sourceId, targetId);
        }
        return created;
    }

    /**
     * For each transferred credential, locate the matching target account by email and install the
     * source password hash + {@code force_password_change=TRUE} only if no credential exists yet.
     * Existing target credentials are never overwritten.
     */
    private int importAccountCredentials(List<Map<String, Object>> rows) {
        int installed = 0;
        for (var row : rows) {
            String email = asString(row.get("account_email"), null);
            String hash = asString(row.get("password_hash"), null);
            if (email == null || hash == null) continue;
            var account = accountRepository.findByEmail(email);
            if (account.isEmpty()) continue;
            int accountId = account.get().id();
            if (accountRepository.findCredential(accountId).isPresent()) continue;
            accountRepository.createCredential(accountId, hash);
            accountRepository.setForcePasswordChange(accountId, true);
            log.info("Imported credential for account {} ({}) with forced password change", accountId, email);
            installed++;
        }
        return installed;
    }

    private int importDisabledModules(int stationId, List<Object> moduleNames) {
        if (moduleNames == null || moduleNames.isEmpty()) return 0;
        var modules = new HashSet<StationModule>();
        for (Object o : moduleNames) {
            try {
                modules.add(StationModule.valueOf(o.toString()));
            } catch (IllegalArgumentException e) {
                log.warn("Unknown station module name in import payload: {}", o);
            }
        }
        stationRepository.setDisabledModules(stationId, modules);
        return modules.size();
    }

    /**
     * Applies the columns from a {@code station} SINGLE payload to the target station row.
     * Performs a single UPDATE keyed by the tracked column list, so any column added to the
     * station SELECTED set automatically flows through here.
     */
    private void applyStationFields(int stationId, Map<String, Object> stationData) {
        if (stationData == null || stationData.isEmpty()) return;
        var entry = tracking.tables() == null ? null : tracking.tables().get("station");
        if (entry == null) return;
        var ignored = entry.stationTransfer().ignoredColumns() == null
                ? List.<String>of()
                : entry.stationTransfer().ignoredColumns();

        // Build UPDATE column list from the payload's keys, intersected with the table's writable columns.
        // Null values are skipped so the target keeps its own defaults; that also avoids the bytea/varchar
        // binding mismatch on columns like `logo` when the source has no value.
        Map<String, Object> updates = new LinkedHashMap<>();
        for (var col : entry.columns()) {
            String name = col.name();
            if (name.equals("id")) continue;
            if (name.equals("uid")) continue; // handled separately so a UID collision doesn't abort the UPDATE
            if (ignored.contains(name)) continue;
            if (!stationData.containsKey(name)) continue;
            Object val = stationData.get(name);
            if (val == null) continue;
            updates.put(name, val);
        }
        if (updates.isEmpty()) return;

        var sb = new StringBuilder("UPDATE station SET ");
        boolean first = true;
        for (var k : updates.keySet()) {
            if (!first) sb.append(", ");
            first = false;
            sb.append(k).append(" = :").append(k);
            String type = columnType(entry.columns(), k);
            if ("jsonb".equals(type) || "json".equals(type)) sb.append("::jsonb");
            else if ("uuid".equals(type)) sb.append("::uuid");
        }
        sb.append(" WHERE id = :stationId;");

        var c = call().bind("stationId", stationId);
        for (var e : updates.entrySet()) {
            String type = columnType(entry.columns(), e.getKey());
            Object val = e.getValue();
            // uuid + jsonb/json take string bindings — the cast lives in the SQL fragment
            // built above (e.g. `uid = :uid::uuid`), so they fall through to the default branch.
            c = switch (type == null ? "" : type) {
                case "bytea" ->
                    c.bind(
                            e.getKey(),
                            val instanceof byte[] b ? b : Base64.getDecoder().decode(val.toString()));
                case "bool" -> c.bind(e.getKey(), val instanceof Boolean b ? b : Boolean.parseBoolean(val.toString()));
                case "int4", "int8" ->
                    c.bind(e.getKey(), val instanceof Number n ? n.intValue() : Integer.parseInt(val.toString()));
                case "timestamptz", "timestamp" ->
                    c.bind(e.getKey(), toInstant(val), StandardValueConverter.INSTANT_TIMESTAMP);
                default -> c.bind(e.getKey(), val.toString());
            };
        }

        query(sb.toString()).single(c).update();

        // Preserve source UUID so federation pairing codes still wor1k. If the UID already exists on
        // the target instance (e.g. when running source + target in the same database during tests, or
        // when an earlier import already claimed it), keep the freshly generated target UID and log.
        Object uid = stationData.get("uid");
        if (uid != null) {
            try {
                stationRepository.updateUid(stationId, UUID.fromString(uid.toString()));
            } catch (IllegalArgumentException e) {
                log.warn("Invalid station UID in import payload, keeping target UID");
            } catch (RuntimeException e) {
                log.warn("Could not apply source UID {} (likely already in use); keeping the target's UID", uid);
            }
        }
    }

    private OutputShape shapeOf(String table) {
        var e = tracking.tables() == null ? null : tracking.tables().get(table);
        return e == null ? OutputShape.ROWS : e.effectiveShape();
    }

    /**
     * Builds the HTTP client for talking to the source instance. Uses HTTP/2 over HTTPS so
     * production runs benefit from ALPN-negotiated multiplexing, but falls back to HTTP/1.1
     * over plain HTTP because the JDK client's HTTP/2 default sends an {@code Upgrade: h2c}
     * header that Node-based servers (e.g. a Nuxt dev server in front of the source) hold
     * open without responding — see the dev compose transfer profile.
     */
    private static HttpClient buildImportHttpClient(String baseUrl) {
        HttpClient.Version version = baseUrl != null && baseUrl.startsWith("https://")
                ? HttpClient.Version.HTTP_2
                : HttpClient.Version.HTTP_1_1;
        return HttpClient.newBuilder()
                .version(version)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    private HttpRequest newImportRequest(URI uri) {
        var builder = HttpRequest.newBuilder().uri(uri).GET().timeout(Duration.ofSeconds(30));
        String ourUrl = api.baseUrl();
        if (ourUrl != null && !ourUrl.isBlank()) {
            builder.header("X-Ember-Importing-From", ourUrl);
        }
        return builder.build();
    }

    /**
     * Caps outgoing transfer requests at two per second so a high-fanout import does not
     * overload the source instance. Called immediately before every {@code httpClient.send}
     * during the active import. Synchronized because the synchronous import-start path runs
     * on the request thread while the async run loop runs on {@code importExecutor}, and
     * both call into the throttle. Sleep is hard-capped at the interval itself so a
     * regression elsewhere cannot ever stall the request beyond one slot.
     */
    private void throttle() throws InterruptedException {
        long sleepMillis;
        synchronized (throttleLock) {
            long now = System.currentTimeMillis();
            long wait = lastRequestMillis + MIN_REQUEST_INTERVAL_MILLIS - now;
            if (wait > 0) {
                sleepMillis = Math.min(wait, MIN_REQUEST_INTERVAL_MILLIS);
                lastRequestMillis = now + sleepMillis;
            } else {
                sleepMillis = 0;
                lastRequestMillis = now;
            }
        }
        if (sleepMillis > 0) Thread.sleep(sleepMillis);
    }

    private <T> HttpResponse<T> sendThrottled(
            HttpClient httpClient, HttpRequest request, HttpResponse.BodyHandler<T> handler)
            throws IOException, InterruptedException {
        throttle();
        return httpClient.send(request, handler);
    }

    // -- HTTP --

    /**
     * Strips the secret transfer token from a logged URI so logs are safe to keep. The token
     * is the path segment after {@code /transfer/}; everything between the slashes is replaced
     * with {@code ***}.
     */
    private static String redactToken(URI uri) {
        return uri.toString().replaceFirst("/transfer/[^/?]+", "/transfer/***");
    }

    @SuppressWarnings("unchecked")
    private void verifyRemoteSchemaHash(HttpClient httpClient, ObjectMapper mapper, String baseUrl, String token) {
        String localHash = exportService.getSchemaHash();
        var uri = URI.create(baseUrl + "/api/v1/public/transfer/" + token + "/tables");
        log.info("verify schema hash: GET {}", redactToken(uri));
        try {
            var request = newImportRequest(uri);
            var response = sendThrottled(httpClient, request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new BadRequestResponse("Failed to fetch /tables from remote: HTTP " + response.statusCode());
            }
            Map<String, Object> body = mapper.readValue(response.body(), Map.class);
            String remoteHash = (String) body.get("schemaHash");
            if (remoteHash == null || remoteHash.isBlank()) {
                throw new BadRequestResponse("""
                        Cannot import: remote instance did not provide a schemaHash.
                        Upgrade the source instance to a version that supports schema parity checks.""");
            }
            if (!remoteHash.equals(localHash)) {
                throw new BadRequestResponse("""
                        Cannot import station bundle: schema hash mismatch.
                          Source schema: %s
                          This instance: %s
                        Both instances must be on the same DB schema version.
                        Update the importing instance to match, or re-export from a matching instance.
                        """.formatted(remoteHash, localHash));
            }
        } catch (HttpResponseException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(
                    "Failed to verify schema hash with remote at " + baseUrl + ": "
                            + e.getClass().getSimpleName() + (e.getMessage() == null ? "" : " — " + e.getMessage()),
                    e);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> fetchRemotePage(
            HttpClient httpClient,
            ObjectMapper mapper,
            String baseUrl,
            String token,
            String table,
            int offset,
            int limit) {
        var uri = URI.create(
                baseUrl + "/api/v1/public/transfer/" + token + "/" + table + "?offset=" + offset + "&limit=" + limit);
        log.info("fetch page: GET {}", redactToken(uri));
        try {
            var request = newImportRequest(uri);
            var response = sendThrottled(httpClient, request, HttpResponse.BodyHandlers.ofString());
            log.info("fetch page: table '{}' offset={} HTTP {}", table, offset, response.statusCode());
            if (response.statusCode() != 200) {
                throw new RuntimeException(
                        "Failed to fetch table '" + table + "' from remote: HTTP " + response.statusCode());
            }
            return mapper.readValue(response.body(), Map.class);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch table '" + table + "' from remote", e);
        }
    }

    private record ListKeysPage(List<String> keys, String next, Integer total) {}

    // -- Coercion helpers --

    /**
     * Tracks state that lives only for the duration of a single async remote import. The
     * single-thread executor guarantees only one session is active at a time; the volatile
     * field on the service exposes it to the table-import branches (notably
     * {@link #importAccounts}) without threading a parameter through every handler.
     */
    private static final class RemoteImportSession {
        private final List<NewAccountRef> newAccounts = new ArrayList<>();

        List<NewAccountRef> newAccounts() {
            return newAccounts;
        }
    }

    /**
     * Pairs the source's account UID with the destination UID created for the same email.
     */
    private record NewAccountRef(UUID sourceUid, UUID destinationUid) {}

    // -- Public records --

    public record ImportResult(int stationId, String stationName, int totalEntities) {}

    /**
     * Tracks the progress of an asynchronous station import. Volatile fields make the progress
     * readable from polling endpoints without locks.
     */
    public static class ImportProgress {
        private final int stationId;
        private final UUID stationUid;
        private final String stationName;
        private final List<String> phases;
        private final String sourceUrl;
        private final String token;
        private volatile Status status = Status.IN_PROGRESS;
        private volatile String currentPhase;
        private volatile int completedPhases;
        private volatile int subTotal;
        private volatile int subCompleted;
        private volatile String error;

        public ImportProgress(
                int stationId,
                UUID stationUid,
                String stationName,
                List<String> phases,
                String sourceUrl,
                String token) {
            this.stationId = stationId;
            this.stationUid = stationUid;
            this.stationName = stationName;
            this.phases = List.copyOf(phases);
            this.sourceUrl = sourceUrl;
            this.token = token;
        }

        public UUID stationUid() {
            return stationUid;
        }

        String sourceUrl() {
            return sourceUrl;
        }

        String token() {
            return token;
        }

        public int stationId() {
            return stationId;
        }

        public String stationName() {
            return stationName;
        }

        public Status status() {
            return status;
        }

        public List<String> phases() {
            return phases;
        }

        public int completedPhases() {
            return completedPhases;
        }

        public String currentPhase() {
            return currentPhase;
        }

        public String error() {
            return error;
        }

        public int subTotal() {
            return subTotal;
        }

        public int subCompleted() {
            return subCompleted;
        }

        void startPhase(String phase) {
            this.currentPhase = phase;
            this.subTotal = 0;
            this.subCompleted = 0;
        }

        void setSubTotal(int total) {
            this.subTotal = total;
        }

        synchronized void incrementSub() {
            this.subCompleted++;
        }

        synchronized void completePhase() {
            this.completedPhases++;
        }

        void complete() {
            this.status = Status.COMPLETED;
            this.currentPhase = null;
        }

        void fail(String error) {
            this.status = Status.FAILED;
            this.error = error;
        }

        public enum Status {
            IN_PROGRESS,
            COMPLETED,
            FAILED
        }
    }
}
