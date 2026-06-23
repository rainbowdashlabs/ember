/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.station.service;

import dev.chojo.ember.conf.file.elements.Api;
import dev.chojo.ember.feature.account.repository.AccountRepository;
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
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    private final StationRepository stationRepository;
    private final AccountRepository accountRepository;
    private final StationExportService exportService;
    private final Api api;
    private final TransferBackendImporter backendImporter;
    private final StorageService storageService;
    private final GenericTableImporter engine;
    private final List<String> tableOrder;
    private final DataTracking tracking;

    private final ConcurrentHashMap<Integer, ImportProgress> activeImports = new ConcurrentHashMap<>();
    private final ExecutorService importExecutor = Executors.newSingleThreadExecutor(r -> {
        var t = new Thread(r, "station-import");
        t.setDaemon(true);
        return t;
    });

    @Inject
    public StationImportService(
            StationRepository stationRepository,
            AccountRepository accountRepository,
            StationExportService exportService,
            Api api,
            TransferBackendImporter backendImporter,
            StorageService storageService) {
        this.stationRepository = stationRepository;
        this.accountRepository = accountRepository;
        this.exportService = exportService;
        this.api = api;
        this.backendImporter = backendImporter;
        this.storageService = storageService;
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
        return new ImportResult(stationId, name, total);
    }

    /**
     * Synchronously merges a bundle into an existing station. The station's own settings get
     * applied (timezone, locale, themes, public toggles); all other TRACKED tables are inserted
     * alongside the station's existing data.
     */
    public ImportResult importStationInto(int targetStationId, Map<String, Object> bundle) {
        Map<String, Object> stationData = asMap(bundle.get("station"));
        if (stationData != null) applyStationFields(targetStationId, stationData);
        String name =
                stationRepository.findById(targetStationId).map(Station::name).orElse("Station");
        var idMap = new IdRemapper();
        seedStationRemap(idMap, stationData, targetStationId);
        int total = runImport(targetStationId, bundle, idMap);
        return new ImportResult(targetStationId, name, total);
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

    /**
     * Returns the current progress for an active or recently completed import.
     */
    public ImportProgress getProgress(int stationId) {
        return activeImports.get(stationId);
    }

    /**
     * Pulls a station bundle from a remote Ember instance and creates a new station from it.
     */
    public ImportResult startRemoteImport(String sourceUrl, String token) {
        String baseUrl = sourceUrl.replaceAll("/+$", "");
        var mapper = JsonMapper.builder().build();
        var httpClient = HttpClient.newHttpClient();
        verifyRemoteSchemaHash(httpClient, mapper, baseUrl, token);

        Map<String, Object> stationPage = fetchRemotePage(httpClient, mapper, baseUrl, token, "station", 0, PAGE_SIZE);
        Map<String, Object> stationData = asMap(stationPage.get("station"));
        if (stationData == null) {
            throw new io.javalin.http.BadRequestResponse("Remote station table missing 'station' field");
        }

        String stationName = asString(stationData.get("name"), "Imported Station");
        Station station = stationRepository.create(stationName);
        int stationId = station.id();
        applyStationFields(stationId, stationData);

        var progress = new ImportProgress(stationId, stationName, tableOrder.size());
        activeImports.put(stationId, progress);
        importExecutor.submit(() -> runRemoteImport(stationId, baseUrl, token, httpClient, mapper, progress));
        return new ImportResult(stationId, stationName, 0);
    }

    /**
     * Pulls a station bundle from a remote Ember instance and merges it INTO an existing station.
     */
    public ImportResult startRemoteImportInto(int stationId, String sourceUrl, String token) {
        String baseUrl = sourceUrl.replaceAll("/+$", "");
        var mapper = JsonMapper.builder().build();
        var httpClient = HttpClient.newHttpClient();
        verifyRemoteSchemaHash(httpClient, mapper, baseUrl, token);

        Map<String, Object> stationPage = fetchRemotePage(httpClient, mapper, baseUrl, token, "station", 0, PAGE_SIZE);
        Map<String, Object> stationData = asMap(stationPage.get("station"));
        if (stationData != null) applyStationFields(stationId, stationData);

        String stationName =
                stationRepository.findById(stationId).map(Station::name).orElse("Station");
        var progress = new ImportProgress(stationId, stationName, tableOrder.size());
        activeImports.put(stationId, progress);
        importExecutor.submit(() -> runRemoteImport(stationId, baseUrl, token, httpClient, mapper, progress));
        return new ImportResult(stationId, stationName, 0);
    }

    // -- Internals --

    private void runRemoteImport(
            int stationId, String baseUrl, String token, HttpClient httpClient, ObjectMapper mapper, ImportProgress p) {
        try {
            var idMap = new IdRemapper();
            int i = 0;
            for (String table : tableOrder) {
                p.startTable(i++, table);
                if ("station".equals(table)) {
                    // already applied synchronously before the async dispatch
                    p.completeTable();
                    continue;
                }
                fetchAndImportPaginated(stationId, table, baseUrl, token, httpClient, mapper, idMap);
                p.completeTable();
            }
            boolean installedRemote = applySourceBackend(stationId, baseUrl, token, httpClient, mapper);
            if (!installedRemote) {
                copyLocalFiles(stationId, baseUrl, token, httpClient, mapper);
            }
            p.complete();
            log.info("Remote import completed for station '{}' (id={})", p.stationName(), stationId);
        } catch (Exception e) {
            log.error("Remote import failed for station {}", stationId, e);
            p.fail(e.getMessage());
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
     * Pulls every key in every station-scoped movable category from the source and stores it on
     * the destination's backend. Per-key streaming: the response body is piped straight into
     * {@link StorageService#store}. Keys that already exist on the destination are skipped so
     * a retried import after a partial failure is idempotent (cheap exists check rather than a
     * SHA round-trip).
     */
    private void copyLocalFiles(
            int stationId, String baseUrl, String token, HttpClient httpClient, ObjectMapper mapper) {
        Station station = stationRepository
                .findById(stationId)
                .orElseThrow(() -> new RuntimeException("Station " + stationId + " not found after table import"));
        StorageScope.Station scope = new StorageScope.Station(stationId, station.uid());
        for (StorageCategory category : transferrableStationCategories()) {
            copyCategory(scope, category, baseUrl, token, httpClient, mapper);
        }
    }

    private void copyCategory(
            StorageScope.Station scope,
            StorageCategory category,
            String baseUrl,
            String token,
            HttpClient httpClient,
            ObjectMapper mapper) {
        int copied = 0;
        int skipped = 0;
        String after = null;
        while (true) {
            ListKeysPage page = listRemoteKeys(httpClient, mapper, baseUrl, token, category, after);
            for (String key : page.keys()) {
                if (storageService.readRelative(scope, category, key).isPresent()) {
                    skipped++;
                    continue;
                }
                if (streamRemoteFile(scope, category, baseUrl, token, key, httpClient)) {
                    copied++;
                }
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
     */
    private boolean streamRemoteFile(
            StorageScope.Station scope,
            StorageCategory category,
            String baseUrl,
            String token,
            String key,
            HttpClient httpClient) {
        try {
            String encodedKey = encodeKeyPath(key);
            var uri = URI.create(
                    baseUrl + "/api/v1/public/transfer/" + token + "/files/" + category.name() + "/" + encodedKey);
            var request = newImportRequest(uri);
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() == 404) {
                return false;
            }
            if (response.statusCode() != 200) {
                throw new RuntimeException("Failed to stream key '" + key + "' for category " + category + ": HTTP "
                        + response.statusCode());
            }
            String contentType = response.headers().firstValue("Content-Type").orElse("application/octet-stream");
            long contentLength = response.headers()
                    .firstValueAsLong("Content-Length")
                    .orElseThrow(() ->
                            new RuntimeException("Source did not advertise Content-Length for key '" + key + "'"));
            try (InputStream body = response.body()) {
                storageService.store(scope, category, key, body, contentLength, contentType);
            }
            return true;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to stream key '" + key + "' from remote", e);
        }
    }

    @SuppressWarnings("unchecked")
    private ListKeysPage listRemoteKeys(
            HttpClient httpClient,
            ObjectMapper mapper,
            String baseUrl,
            String token,
            StorageCategory category,
            String after) {
        try {
            var sb = new StringBuilder(baseUrl)
                    .append("/api/v1/public/transfer/")
                    .append(token)
                    .append("/files/")
                    .append(category.name());
            if (after != null && !after.isBlank()) {
                sb.append("?after=").append(java.net.URLEncoder.encode(after, java.nio.charset.StandardCharsets.UTF_8));
            }
            var request = newImportRequest(URI.create(sb.toString()));
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new RuntimeException(
                        "Failed to list keys for category " + category + ": HTTP " + response.statusCode());
            }
            Map<String, Object> body = mapper.readValue(response.body(), Map.class);
            List<String> keys = (List<String>) body.getOrDefault("keys", List.of());
            String next = (String) body.get("next");
            return new ListKeysPage(keys, next);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to list keys for category " + category, e);
        }
    }

    private static List<StorageCategory> transferrableStationCategories() {
        var out = new ArrayList<StorageCategory>();
        for (StorageCategory c : StorageCategory.values()) {
            if (c.scopeKind() != StorageScope.Kind.STATION) continue;
            if (!c.isMovable()) continue;
            if (StorageCategory.LEGACY_CATEGORIES.contains(c)) continue;
            out.add(c);
        }
        return out;
    }

    /** URL-encodes each path segment of a relative key, keeping the {@code /} separators intact. */
    private static String encodeKeyPath(String key) {
        String[] parts = key.split("/", -1);
        var sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) sb.append('/');
            sb.append(java.net.URLEncoder.encode(parts[i], java.nio.charset.StandardCharsets.UTF_8)
                    .replace("+", "%20"));
        }
        return sb.toString();
    }

    private record ListKeysPage(List<String> keys, String next) {}

    @SuppressWarnings("unchecked")
    private TransferBackendDescriptor fetchBackendDescriptor(
            HttpClient httpClient, ObjectMapper mapper, String baseUrl, String token) {
        try {
            var uri = URI.create(baseUrl + "/api/v1/public/transfer/" + token + "/backend");
            var request = newImportRequest(uri);
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
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
            case "account" -> importAccounts((List<Map<String, Object>>) payload);
            case "account_credential" -> importAccountCredentials((List<Map<String, Object>>) payload);
            case "station_disabled_module" -> importDisabledModules(stationId, (List<Object>) payload);
            default -> engine.importRows(stationId, table, (List<Map<String, Object>>) payload, idMap);
        };
    }

    /** Match-by-email; create with no credential if the email is new. */
    private int importAccounts(List<Map<String, Object>> rows) {
        int created = 0;
        for (var row : rows) {
            String email = asString(row.get("email"), null);
            if (email == null || email.isBlank()) continue;
            if (accountRepository.findByEmail(email).isEmpty()) {
                String first = asString(row.get("first_name"), "");
                String last = asString(row.get("last_name"), "");
                accountRepository.create(email, first, last, true);
                created++;
            }
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
                default -> c.bind(e.getKey(), val.toString());
            };
        }

        query(sb.toString()).single(c).update();

        // Preserve source UUID so federation pairing codes still work. If the UID already exists on
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

    private static String columnType(List<ColumnEntry> cols, String name) {
        for (var c : cols) if (c.name().equals(name)) return c.type();
        return null;
    }

    private OutputShape shapeOf(String table) {
        var e = tracking.tables() == null ? null : tracking.tables().get(table);
        return e == null ? OutputShape.ROWS : e.effectiveShape();
    }

    // -- HTTP --

    /**
     * Builds a GET request and pins the destination instance URL on the source so the source
     * banner can surface where the station is going. The source records the value off the first
     * {@code /tables} call; subsequent calls re-send it but the source treats them as no-ops.
     */
    private HttpRequest newImportRequest(URI uri) {
        var builder = HttpRequest.newBuilder().uri(uri).GET();
        String ourUrl = api.baseUrl();
        if (ourUrl != null && !ourUrl.isBlank()) {
            builder.header("X-Ember-Importing-From", ourUrl);
        }
        return builder.build();
    }

    @SuppressWarnings("unchecked")
    private void verifyRemoteSchemaHash(HttpClient httpClient, ObjectMapper mapper, String baseUrl, String token) {
        String localHash = exportService.getSchemaHash();
        try {
            var uri = URI.create(baseUrl + "/api/v1/public/transfer/" + token + "/tables");
            var request = newImportRequest(uri);
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new io.javalin.http.BadRequestResponse(
                        "Failed to fetch /tables from remote: HTTP " + response.statusCode());
            }
            Map<String, Object> body = mapper.readValue(response.body(), Map.class);
            String remoteHash = (String) body.get("schemaHash");
            if (remoteHash == null || remoteHash.isBlank()) {
                throw new io.javalin.http.BadRequestResponse("""
                        Cannot import: remote instance did not provide a schemaHash. \
                        Upgrade the source instance to a version that supports schema parity checks.""");
            }
            if (!remoteHash.equals(localHash)) {
                throw new io.javalin.http.BadRequestResponse("""
                        Cannot import station bundle: schema hash mismatch.
                          Source schema: %s
                          This instance: %s
                        Both instances must be on the same DB schema version. \
                        Update the importing instance to match, or re-export from a matching instance.\
                        """.formatted(remoteHash, localHash));
            }
        } catch (io.javalin.http.HttpResponseException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to verify schema hash with remote", e);
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
        try {
            var uri = URI.create(baseUrl + "/api/v1/public/transfer/" + token + "/" + table + "?offset=" + offset
                    + "&limit=" + limit);
            var request = newImportRequest(uri);
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
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

    // -- Coercion helpers --

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object o) {
        return o instanceof Map ? (Map<String, Object>) o : null;
    }

    private static String asString(Object o, String defaultValue) {
        return o == null ? defaultValue : o.toString();
    }

    // -- Public records --

    public record ImportResult(int stationId, String stationName, int totalEntities) {}

    /**
     * Tracks the progress of an asynchronous station import. Volatile fields make the progress
     * readable from polling endpoints without locks.
     */
    public static class ImportProgress {
        public enum Status {
            IN_PROGRESS,
            COMPLETED,
            FAILED
        }

        private final int stationId;
        private final String stationName;
        private final int totalTables;

        private volatile Status status = Status.IN_PROGRESS;
        private volatile String currentTable;
        private volatile int completedTables;
        private volatile String error;

        public ImportProgress(int stationId, String stationName, int totalTables) {
            this.stationId = stationId;
            this.stationName = stationName;
            this.totalTables = totalTables;
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

        public int totalTables() {
            return totalTables;
        }

        public int completedTables() {
            return completedTables;
        }

        public String currentTable() {
            return currentTable;
        }

        public String error() {
            return error;
        }

        void startTable(int index, String table) {
            this.currentTable = table;
        }

        synchronized void completeTable() {
            this.completedTables++;
        }

        void complete() {
            this.status = Status.COMPLETED;
            this.currentTable = null;
        }

        void fail(String error) {
            this.status = Status.FAILED;
            this.error = error;
        }
    }
}
