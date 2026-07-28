/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.station.service;

import dev.chojo.ember.conf.file.elements.Api;
import dev.chojo.ember.feature.federation.service.FederationPartnerTransferFixupService;
import dev.chojo.ember.feature.federation.service.RemoteUrlValidator;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.feature.station.transfer.ImportProgress;
import dev.chojo.ember.feature.station.transfer.StationImportContext;
import dev.chojo.ember.feature.station.transfer.StationTableImporter;
import dev.chojo.ember.feature.station.transfer.TableImporter;
import dev.chojo.ember.feature.station.transfer.TransferFileImporter;
import dev.chojo.ember.feature.station.transfer.TransferSourceClient;
import dev.chojo.ember.feature.storage.entity.StorageCategory;
import dev.chojo.ember.feature.storage.entity.StorageScope;
import dev.chojo.ember.feature.storage.transfer.TransferBackendImporter;
import dev.chojo.ember.tracking.DataTracking;
import dev.chojo.ember.tracking.DataTrackingLoader;
import dev.chojo.ember.tracking.OutputShape;
import dev.chojo.ember.tracking.engine.GenericTableImporter;
import dev.chojo.ember.tracking.engine.GenericTableImporter.IdRemapper;
import dev.chojo.ember.tracking.engine.TableOrder;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.NotFoundResponse;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.stream.Collectors;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static dev.chojo.ember.feature.station.transfer.WireValues.asInteger;
import static dev.chojo.ember.feature.station.transfer.WireValues.asMap;
import static dev.chojo.ember.feature.station.transfer.WireValues.asString;

/**
 * Imports a station bundle produced by {@link StationExportService} using only metadata
 * from {@code data_tracking.json}.
 *
 * <p>The service owns the run itself: it creates or picks the destination station, resolves the
 * foreign-key-safe table order from the tracking metadata, and walks it. Each table is handed to
 * the {@link TableImporter} that claims it, or to {@link GenericTableImporter} when none does.
 * The file side of a remote transfer is delegated to {@link TransferFileImporter}, and every
 * request to the source instance goes through a {@link TransferSourceClient}.
 */
@Singleton
public class StationImportService {

    private static final Logger log = LoggerFactory.getLogger(StationImportService.class);
    private static final int PAGE_SIZE = 500;

    private final StationRepository stationRepository;
    private final StationExportService exportService;
    private final Api api;
    private final TransferBackendImporter backendImporter;
    private final TransferFileImporter fileImporter;
    private final FederationPartnerTransferFixupService federationFixup;
    private final RemoteUrlValidator urlValidator;
    private final StationTableImporter stationImporter;
    private final Map<String, TableImporter> importers;
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
            StationExportService exportService,
            Api api,
            TransferBackendImporter backendImporter,
            TransferFileImporter fileImporter,
            FederationPartnerTransferFixupService federationFixup,
            RemoteUrlValidator urlValidator,
            StationTableImporter stationImporter,
            Set<TableImporter> importers) {
        this.stationRepository = stationRepository;
        this.exportService = exportService;
        this.api = api;
        this.backendImporter = backendImporter;
        this.fileImporter = fileImporter;
        this.federationFixup = federationFixup;
        this.urlValidator = urlValidator;
        this.stationImporter = stationImporter;
        this.importers = importers.stream().collect(Collectors.toMap(TableImporter::table, Function.identity()));
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

    /**
     * Registers the source-station-id to target-station-id mapping so foreign keys from other
     * tables that reference {@code station(id)} resolve correctly. The source id is carried on the
     * {@code station} wire entry (the int-id column is otherwise dropped when the settings are
     * applied).
     */
    private static void seedStationRemap(IdRemapper idMap, Map<String, Object> stationData, int targetStationId) {
        if (stationData == null) return;
        Integer sourceId = asInteger(stationData.get("id"));
        if (sourceId != null) idMap.put("station", sourceId, targetStationId);
    }

    /**
     * Synchronously imports a bundle keyed by table name into a new station. Used by tests.
     * The bundle's {@code station} entry must be a {@code Map<String, Object>} (SINGLE shape).
     *
     * @param bundle the whole bundle, keyed by table name
     * @return the created station and the number of rows imported
     */
    public ImportResult importStation(Map<String, Object> bundle) {
        Map<String, Object> stationData = asMap(bundle.get("station"));
        String name = stationData == null ? "Imported Station" : asString(stationData.get("name"), "Imported Station");
        Station station = stationRepository.create(name);
        int stationId = station.id();
        stationImporter.applyFields(stationId, stationData);
        var context = newContext(stationId, stationData);
        int total = 1 + runImport(context, bundle);
        log.info("Station import complete: created station id={} ('{}'), {} rows imported", stationId, name, total);
        return new ImportResult(stationId, name, total);
    }

    /**
     * Synchronously merges a bundle into an existing station. The station's own settings get
     * applied (timezone, locale, themes, public toggles); all other TRACKED tables are inserted
     * alongside the station's existing data.
     *
     * @param targetStationId the station to merge into
     * @param bundle          the whole bundle, keyed by table name
     */
    public void importStationInto(int targetStationId, Map<String, Object> bundle) {
        Map<String, Object> stationData = asMap(bundle.get("station"));
        if (stationData != null) stationImporter.applyFields(targetStationId, stationData);
        var context = newContext(targetStationId, stationData);
        int total = runImport(context, bundle);
        log.info("Station import-into complete: station={}, {} rows merged", targetStationId, total);
    }

    /**
     * Returns the current progress for an active or recently completed import.
     *
     * @param stationId the destination station
     * @return the progress, or {@code null} when no import ran for that station
     */
    public ImportProgress getProgress(int stationId) {
        return activeImports.get(stationId);
    }

    /**
     * Returns the active or failed import progress for a station identified by its UUID, or
     * {@code null} when no progress (alive or failed) is on file. Searches the in-memory map
     * by uid so a failed import survives the destination-station deletion that follows
     * failure.
     *
     * @param stationUid the destination station UUID
     * @return the progress, or {@code null}
     */
    public ImportProgress getProgressByUid(UUID stationUid) {
        for (var progress : activeImports.values()) {
            if (stationUid.equals(progress.stationUid())) return progress;
        }
        return null;
    }

    /**
     * Pulls a station bundle from a remote Ember instance and creates a new station from it. The
     * station is re-read after its settings are applied because the source UID is preserved there,
     * and both the progress lookup by uid and the serialized response station id need the current
     * one.
     *
     * @param sourceUrl the source instance's base URL
     * @param token     the transfer token issued by the source
     * @return the freshly created station, whose progress the caller can poll
     */
    public ImportResult startRemoteImport(String sourceUrl, String token) {
        String baseUrl = normalizeSource(sourceUrl);
        log.info("start remote-import-as-new-station from source {}", baseUrl);
        var client = new TransferSourceClient(baseUrl, token, api.baseUrl());
        verifyRemoteSchemaHash(client, baseUrl);

        Map<String, Object> stationData = fetchStationEntry(client);
        if (stationData == null) {
            throw new BadRequestResponse("Remote station table missing 'station' field");
        }

        String stationName = asString(stationData.get("name"), "Imported Station");
        Station station = stationRepository.create(stationName);
        int stationId = station.id();
        stationImporter.applyFields(stationId, stationData);

        UUID currentUid =
                stationRepository.findById(stationId).map(Station::uid).orElse(station.uid());
        var progress = new ImportProgress(stationId, currentUid, stationName, buildPhases(), baseUrl, token);
        activeImports.put(stationId, progress);
        importExecutor.submit(() -> runRemoteImport(stationId, stationData, client, progress));
        return new ImportResult(stationId, stationName, 0);
    }

    /**
     * Pulls a station bundle from a remote Ember instance and merges it INTO an existing station.
     *
     * @param stationId the station to merge into
     * @param sourceUrl the source instance's base URL
     * @param token     the transfer token issued by the source
     */
    public void startRemoteImportInto(int stationId, String sourceUrl, String token) {
        String baseUrl = normalizeSource(sourceUrl);
        log.info("start remote-import-into-station {} from source {}", stationId, baseUrl);
        var client = new TransferSourceClient(baseUrl, token, api.baseUrl());
        verifyRemoteSchemaHash(client, baseUrl);

        Map<String, Object> stationData = fetchStationEntry(client);
        if (stationData != null) stationImporter.applyFields(stationId, stationData);

        Station target = stationRepository
                .findById(stationId)
                .orElseThrow(() -> new BadRequestResponse("Target station not found"));
        var progress = new ImportProgress(stationId, target.uid(), target.name(), buildPhases(), baseUrl, token);
        activeImports.put(stationId, progress);
        importExecutor.submit(() -> runRemoteImport(stationId, stationData, client, progress));
    }

    /**
     * Cleans up the destination side of a failed import and starts a fresh run with the same
     * token: deletes the half-imported station (if it still exists) and re-invokes
     * {@link #startRemoteImport(String, String)} with the source URL and token captured on the
     * original attempt. Throws when the original progress is not in FAILED state.
     *
     * @param stationUid the destination station UUID of the failed run
     * @return the freshly minted import result
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
            log.info("Station {} already gone before retry", failed.stationId());
        }
        activeImports.remove(failed.stationId());
        return startRemoteImport(failed.sourceUrl(), failed.token());
    }

    /**
     * Rejects an import source URL that resolves to a private, loopback, or otherwise
     * non-public address before any request is issued. Every fetch derives its URL
     * from this same base, so validating it here guards the whole import run against
     * server-side request forgery.
     */
    private String normalizeSource(String sourceUrl) {
        String baseUrl = sourceUrl.replaceAll("/+$", "");
        if (!urlValidator.isAllowed(baseUrl)) {
            throw new BadRequestResponse(RemoteUrlValidator.rejectReason());
        }
        return baseUrl;
    }

    private Map<String, Object> fetchStationEntry(TransferSourceClient client) {
        return asMap(client.fetchPage("station", 0, PAGE_SIZE).get("station"));
    }

    private StationImportContext newContext(int stationId, Map<String, Object> stationData) {
        var idMap = new IdRemapper();
        seedStationRemap(idMap, stationData, stationId);
        return new StationImportContext(stationId, idMap);
    }

    /**
     * Rejects a source whose schema differs from this instance's: a bundle written against a
     * different schema version cannot be inserted safely.
     */
    private void verifyRemoteSchemaHash(TransferSourceClient client, String baseUrl) {
        String localHash = exportService.getSchemaHash();
        String remoteHash;
        try {
            remoteHash = client.fetchSchemaHash();
        } catch (TransferSourceClient.TransferSourceException e) {
            throw new BadRequestResponse(e.getMessage());
        }
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
        log.info("schema hash verified against source at {}", baseUrl);
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
        for (StorageCategory category : TransferFileImporter.transferrableStationCategories()) {
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
    private int runImport(StationImportContext context, Map<String, Object> bundle) {
        int total = 0;
        for (String table : tableOrder) {
            if ("station".equals(table)) continue;
            Object payload = bundle.get(table);
            if (payload == null) continue;
            total += importTable(context, table, payload);
        }
        assignDefaultOwnerIfNeeded(context.stationId());
        return total;
    }

    /**
     * If the target station has no owner yet, assigns the first MANAGER member as owner. This
     * preserves the semantic the legacy importer applied while processing member user types.
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
            int stationId, Map<String, Object> stationData, TransferSourceClient client, ImportProgress p) {
        log.info(
                "async run starting for station {} ('{}'), {} tables in topological order",
                stationId,
                p.stationName(),
                tableOrder.size());
        var context = newContext(stationId, stationData);
        try {
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
                fetchAndImportPaginated(context, table, client);
                p.completePhase();
            }
            copyFiles(context, client, p);
            federationFixup.rewriteAfterImport(stationId, p.sourceUrl());
            federationFixup.announceNewHostToRemotePartners(stationId, api.baseUrl());
            client.notifyComplete();
            p.complete();
            log.info("completed for station '{}' (id={})", p.stationName(), stationId);
        } catch (Exception e) {
            log.error("failed for station {}", stationId, e);
            p.fail(e.getMessage());
            client.notifyAbort();
            try {
                stationRepository.delete(stationId);
                log.warn("deleted half-imported station {} after failure", stationId);
            } catch (Exception deleteErr) {
                log.error("could not clean up failed station {}", stationId, deleteErr);
            }
        }
    }

    /**
     * Installs the source's storage backend and, when the destination did not adopt the source's
     * remote backend, byte-copies every movable category before carrying over the avatars of the
     * accounts this run created.
     */
    private void copyFiles(StationImportContext context, TransferSourceClient client, ImportProgress p) {
        int stationId = context.stationId();
        log.info("tables done, applying source storage backend");
        p.startPhase("storage_backend");
        var descriptor = client.fetchBackendDescriptor();
        boolean installedRemote = backendImporter.apply(stationId, descriptor);
        if (installedRemote) {
            log.info(
                    "Imported source storage backend ({}) for station {}",
                    descriptor.getClass().getSimpleName(),
                    stationId);
        }
        p.completePhase();
        Station targetStation = stationRepository
                .findById(stationId)
                .orElseThrow(() -> new RuntimeException("Station " + stationId + " not found after table import"));
        StorageScope.Station scope = new StorageScope.Station(stationId, targetStation.uid());
        for (StorageCategory category : TransferFileImporter.transferrableStationCategories()) {
            p.startPhase("files_" + category.name().toLowerCase());
            if (!installedRemote) {
                fileImporter.copyCategory(client, scope, category, p);
            }
            p.completePhase();
        }
        log.info("copying avatars for newly-created accounts");
        p.startPhase("account_avatars");
        fileImporter.copyNewAccountAvatars(client, context.newAccounts(), p);
        p.completePhase();
    }

    private void fetchAndImportPaginated(StationImportContext context, String table, TransferSourceClient client) {
        OutputShape shape = shapeOf(table);
        int offset = 0;
        while (true) {
            var page = client.fetchPage(table, offset, PAGE_SIZE);
            Object payload = page.get(table);
            if (payload == null) return;
            int imported = importTable(context, table, payload);
            if (shape != OutputShape.ROWS) return;
            if (imported < PAGE_SIZE) return;
            offset += PAGE_SIZE;
        }
    }

    /**
     * Dispatches a single wire payload (already extracted from the page envelope) to the importer
     * that claims the table, falling back to the metadata-driven engine.
     */
    @SuppressWarnings("unchecked")
    private int importTable(StationImportContext context, String table, Object payload) {
        TableImporter importer = importers.get(table);
        if (importer != null) return importer.importRows(context, payload);
        return engine.importRows(context.stationId(), table, (List<Map<String, Object>>) payload, context.idMap());
    }

    private OutputShape shapeOf(String table) {
        var e = tracking.tables() == null ? null : tracking.tables().get(table);
        return e == null ? OutputShape.ROWS : e.effectiveShape();
    }

    public record ImportResult(int stationId, String stationName, int totalEntities) {}
}
