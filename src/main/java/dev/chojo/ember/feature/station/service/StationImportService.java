/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.station.service;

import dev.chojo.ember.api.refusal.StationRefusal;
import dev.chojo.ember.conf.file.elements.Api;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.account.service.AuthService;
import dev.chojo.ember.feature.cluster.entity.StationKind;
import dev.chojo.ember.feature.federation.service.FederationPartnerTransferFixupService;
import dev.chojo.ember.feature.federation.service.LendingUidClashes;
import dev.chojo.ember.feature.federation.service.OutboundHttp;
import dev.chojo.ember.feature.federation.service.PartnersLeftBehind;
import dev.chojo.ember.feature.federation.service.PartnersLeftBehind.LeftBehindPartner;
import dev.chojo.ember.feature.federation.service.RemoteUrlValidator;
import dev.chojo.ember.feature.federation.service.StationKeyTransfer;
import dev.chojo.ember.feature.knowledgebase.service.KbIconService;
import dev.chojo.ember.feature.quiz.service.StationAiKeyTransfer;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.feature.station.transfer.ImportProgress;
import dev.chojo.ember.feature.station.transfer.ImportedAccountLinks;
import dev.chojo.ember.feature.station.transfer.LendingRowScope;
import dev.chojo.ember.feature.station.transfer.SharedStorageFiles;
import dev.chojo.ember.feature.station.transfer.StationImportContext;
import dev.chojo.ember.feature.station.transfer.StationTableImporter;
import dev.chojo.ember.feature.station.transfer.TableImporter;
import dev.chojo.ember.feature.station.transfer.TransferFileImporter;
import dev.chojo.ember.feature.station.transfer.TransferPace;
import dev.chojo.ember.feature.station.transfer.TransferSourceClient;
import dev.chojo.ember.feature.storage.entity.StorageCategory;
import dev.chojo.ember.feature.storage.entity.StorageScope;
import dev.chojo.ember.feature.storage.transfer.TransferBackendImporter;
import dev.chojo.ember.lifecycle.SerialLane;
import dev.chojo.ember.lifecycle.TaskScheduler;
import dev.chojo.ember.tracking.DataTracking;
import dev.chojo.ember.tracking.DataTrackingLoader;
import dev.chojo.ember.tracking.OutputShape;
import dev.chojo.ember.tracking.engine.GenericTableImporter;
import dev.chojo.ember.tracking.engine.GenericTableImporter.IdRemapper;
import dev.chojo.ember.tracking.engine.TableOrder;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
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
 * The file side of a remote transfer is delegated to {@link TransferFileImporter}, or to
 * {@link SharedStorageFiles} when the destination takes over the source's own storage, and every
 * request to the source instance goes through a {@link TransferSourceClient}.
 */
@Singleton
public class StationImportService {

    private static final Logger log = LoggerFactory.getLogger(StationImportService.class);
    private static final int PAGE_SIZE = 500;
    private static final String LENDING_REQUESTS = "federation_lending_request";
    private static final String LENDING_MESSAGES = "federation_lending_message";
    private static final String LENDING_LINES = "federation_lending_request_item";

    private final AccountRepository accountRepository;
    private final AuthService authService;
    private final StationRepository stationRepository;
    private final StationExportService exportService;
    private final Api api;
    private final TransferBackendImporter backendImporter;
    private final TransferFileImporter fileImporter;
    private final SharedStorageFiles sharedFiles;
    private final FederationPartnerTransferFixupService federationFixup;
    private final StationKeyTransfer keyTransfer;
    private final PartnersLeftBehind partnersLeftBehind;
    private final LendingUidClashes lendingClashes;
    private final StationAiKeyTransfer aiKeyTransfer;
    private final RemoteUrlValidator urlValidator;
    private final OutboundHttp outbound;
    private final TransferPace pace;
    private final StationTableImporter stationImporter;
    private final Map<String, TableImporter> importers;
    private final GenericTableImporter engine;
    private final List<String> tableOrder;
    private final DataTracking tracking;
    private final ImportedAccountLinks importedLinks;

    private final ConcurrentHashMap<Integer, ImportProgress> activeImports = new ConcurrentHashMap<>();
    private final SerialLane importLane;

    @Inject
    public StationImportService(
            StationRepository stationRepository,
            StationExportService exportService,
            Api api,
            TransferBackendImporter backendImporter,
            TransferFileImporter fileImporter,
            SharedStorageFiles sharedFiles,
            FederationPartnerTransferFixupService federationFixup,
            StationKeyTransfer keyTransfer,
            PartnersLeftBehind partnersLeftBehind,
            LendingUidClashes lendingClashes,
            StationAiKeyTransfer aiKeyTransfer,
            RemoteUrlValidator urlValidator,
            OutboundHttp outbound,
            TransferPace pace,
            StationTableImporter stationImporter,
            Set<TableImporter> importers,
            AccountRepository accountRepository,
            AuthService authService,
            ImportedAccountLinks importedLinks,
            TaskScheduler scheduler) {
        this.importLane = scheduler.lane("station-import");
        this.importedLinks = importedLinks;
        this.accountRepository = accountRepository;
        this.authService = authService;
        this.stationRepository = stationRepository;
        this.exportService = exportService;
        this.api = api;
        this.backendImporter = backendImporter;
        this.fileImporter = fileImporter;
        this.sharedFiles = sharedFiles;
        this.federationFixup = federationFixup;
        this.keyTransfer = keyTransfer;
        this.partnersLeftBehind = partnersLeftBehind;
        this.lendingClashes = lendingClashes;
        this.aiKeyTransfer = aiKeyTransfer;
        this.urlValidator = urlValidator;
        this.outbound = outbound;
        this.pace = pace;
        this.stationImporter = stationImporter;
        this.importers = importers.stream().collect(Collectors.toMap(TableImporter::table, Function.identity()));
        DataTracking t;
        try {
            t = DataTrackingLoader.loadFromClasspath();
        } catch (IOException e) {
            log.warn("Could not load data_tracking.json - import engine will be unusable", e);
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
    private static void seedStationRemap(
            IdRemapper idMap, @Nullable Map<String, Object> stationData, int targetStationId) {
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
        refuseWhileItsMovedAwayCopyIsHere(stationData);
        String name = stationData == null ? "Imported Station" : asString(stationData.get("name"), "Imported Station");
        Station station = stationRepository.create(name);
        int stationId = station.id();
        stationImporter.applyFields(stationId, stationData);
        stationImporter.adoptSourceUid(stationId, stationData);
        var context = newContext(stationId, stationData);
        int total = 1 + runImport(context, bundle);
        log.info("Station import complete: created station id={} ('{}'), {} rows imported", stationId, name, total);
        return new ImportResult(stationId, name, total);
    }

    /**
     * Synchronously merges a bundle into an existing station. The station's own settings get
     * applied (timezone, locale, themes, public toggles); all other TRACKED tables are inserted
     * alongside the station's existing data. The station keeps its own uid.
     *
     * <p>A cluster's home station is refused: it holds what its cluster owns, and a merge would hand
     * that cluster content nobody gave it.
     *
     * @param targetStationId the station to merge into
     * @param bundle          the whole bundle, keyed by table name
     */
    public void importStationInto(int targetStationId, Map<String, Object> bundle) {
        stationRepository.findById(targetStationId).ifPresent(station -> {
            if (station.stationKind() == StationKind.CLUSTER_HOME) {
                throw StationRefusal.STATION_IMPORT_INTO_CLUSTER_HOME.raise();
            }
            if (station.clusterId() != null) {
                throw StationRefusal.STATION_IMPORT_INTO_CLUSTER_MEMBER.raise();
            }
        });
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
    public @Nullable ImportProgress getProgressByUid(UUID stationUid) {
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
        var client = new TransferSourceClient(baseUrl, token, api.baseUrl(), outbound, pace);
        verifyRemoteSchemaHash(client, baseUrl);

        Map<String, Object> stationPage = fetchStationPage(client);
        Map<String, Object> stationData = asMap(stationPage.get("station"));
        if (stationData == null) {
            throw StationRefusal.STATION_IMPORT_SOURCE_HAS_NO_STATION.raise();
        }
        refuseWhileItsMovedAwayCopyIsHere(stationData);

        String stationName = asString(stationData.get("name"), "Imported Station");
        Station station = stationRepository.create(stationName);
        int stationId = station.id();
        stationImporter.applyFields(stationId, stationData);
        stationImporter.adoptSourceUid(stationId, stationData);
        keyTransfer.adopt(stationId, stationPage, stationData, token);
        aiKeyTransfer.adopt(stationId, stationPage, token);

        UUID currentUid =
                stationRepository.findById(stationId).map(Station::uid).orElse(station.uid());
        var progress = new ImportProgress(
                stationId, currentUid, stationName, buildPhases(), baseUrl, token, ImportProgress.Target.NEW_STATION);
        activeImports.put(stationId, progress);
        var leftBehind = PartnersLeftBehind.read(stationPage);
        importLane.submit(() -> runRemoteImport(stationId, stationData, leftBehind, client, progress));
        return new ImportResult(stationId, stationName, 0);
    }

    /**
     * Pulls a station bundle from a remote Ember instance and merges it INTO an existing station.
     *
     * <p>The station keeps its identity: its uid, under which its files are kept and its partners
     * know it, and the federation key its partners verify it by. Neither the source's uid nor its key
     * would make it the station the bundle came from, they would only cut it off from its own.
     *
     * @param stationId the station to merge into
     * @param sourceUrl the source instance's base URL
     * @param token     the transfer token issued by the source
     */
    public void startRemoteImportInto(int stationId, String sourceUrl, String token) {
        String baseUrl = normalizeSource(sourceUrl);
        log.info("start remote-import-into-station {} from source {}", stationId, baseUrl);
        var client = new TransferSourceClient(baseUrl, token, api.baseUrl(), outbound, pace);
        verifyRemoteSchemaHash(client, baseUrl);

        Map<String, Object> stationPage = fetchStationPage(client);
        Map<String, Object> stationData = asMap(stationPage.get("station"));
        if (stationData != null) stationImporter.applyFields(stationId, stationData);
        aiKeyTransfer.adopt(stationId, stationPage, token);

        Station target =
                stationRepository.findById(stationId).orElseThrow(StationRefusal.STATION_IMPORT_TARGET_NOT_HERE::raise);
        var progress = new ImportProgress(
                stationId,
                target.uid(),
                target.name(),
                buildPhases(),
                baseUrl,
                token,
                ImportProgress.Target.EXISTING_STATION);
        activeImports.put(stationId, progress);
        var leftBehind = PartnersLeftBehind.read(stationPage);
        importLane.submit(() -> runRemoteImport(stationId, stationData, leftBehind, client, progress));
    }

    /**
     * Cleans up the destination side of a failed import and starts a fresh run with the same
     * token: deletes the half-imported station (if it still exists) and re-invokes
     * {@link #startRemoteImport(String, String)} with the source URL and token captured on the
     * original attempt. Throws when the original progress is not in FAILED state, and for an import
     * into a station that was here before, which a retry would delete.
     *
     * @param stationUid the destination station UUID of the failed run
     * @return the freshly minted import result
     */
    public ImportResult retryFailedImport(UUID stationUid) {
        ImportProgress failed = getProgressByUid(stationUid);
        if (failed == null) {
            throw StationRefusal.STATION_IMPORT_NOTHING_TO_RETRY.raise();
        }
        if (failed.status() != ImportProgress.Status.FAILED) {
            throw StationRefusal.STATION_IMPORT_NOT_FAILED.raise();
        }
        if (failed.target() == ImportProgress.Target.EXISTING_STATION) {
            throw StationRefusal.STATION_IMPORT_INTO_NOT_RETRIED.raise();
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
            throw StationRefusal.STATION_IMPORT_SOURCE_NOT_PUBLIC.raise();
        }
        return baseUrl;
    }

    private Map<String, Object> fetchStationPage(TransferSourceClient client) {
        return client.fetchPage("station", 0, PAGE_SIZE);
    }

    private StationImportContext newContext(int stationId, @Nullable Map<String, Object> stationData) {
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
            log.warn("The import source at {} could not be read: {}", baseUrl, e.getMessage());
            throw StationRefusal.STATION_IMPORT_SOURCE_NOT_READ.raise();
        }
        if (remoteHash == null || remoteHash.isBlank()) {
            throw StationRefusal.STATION_IMPORT_SOURCE_TOO_OLD.raise();
        }
        if (!remoteHash.equals(localHash)) {
            log.warn("Import source {} has schema {}, this instance {}", baseUrl, remoteHash, localHash);
            throw StationRefusal.STATION_IMPORT_SCHEMA_DIFFERS.raise();
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
        total += engine.settle(context.stationId(), context.idMap(), context.waitingRows());
        mergeLendingClashes(context);
        relinkFolderIcons(context.stationId());
        assignDefaultOwnerIfNeeded(context.stationId());
        importedLinks.ask(context);
        return total;
    }

    private void mergeLendingClashes(StationImportContext context) {
        lendingClashes.merge(context.lendingStandIns(), context.idMap().sourceIds(LENDING_LINES));
    }

    /**
     * Points the icon of every imported wiki folder that has one at the folder's new id. The folder
     * row brings the icon's key along, but that key still names the id the folder had at the source,
     * while the icon file itself moves to the new id.
     */
    private void relinkFolderIcons(int stationId) {
        query("""
                UPDATE kb_folder
                   SET icon_url = :prefix || id
                 WHERE station_id = :station_id
                   AND icon_url IS NOT NULL;""")
                .single(call().bind("prefix", KbIconService.KEY_PREFIX).bind("station_id", stationId))
                .update();
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
            int stationId,
            Map<String, Object> stationData,
            List<LeftBehindPartner> leftBehind,
            TransferSourceClient client,
            ImportProgress p) {
        log.info(
                "async run starting for station {} ('{}'), {} tables in topological order",
                stationId,
                p.stationName(),
                tableOrder.size());
        var context = newContext(stationId, stationData);
        SharedStorageFiles.@Nullable Run sharedStorage = null;
        try {
            int i = 0;
            for (String table : tableOrder) {
                p.startPhase(table);
                if ("station".equals(table)) {
                    log.info(
                            "table {}/{} '{}' - already applied synchronously, skipping",
                            ++i,
                            tableOrder.size(),
                            table);
                    p.completePhase();
                    continue;
                }
                log.info("table {}/{} '{}' - fetching from source", ++i, tableOrder.size(), table);
                fetchAndImportPaginated(context, table, client);
                p.completePhase();
            }
            engine.settle(stationId, context.idMap(), context.waitingRows());
            mergeLendingClashes(context);
            relinkFolderIcons(stationId);
            importedLinks.ask(context);
            sharedStorage = adoptStorage(context, client, p, stationData);
            copyFiles(context, client, p, sharedStorage);
            federationFixup.rewriteAfterImport(stationId, p.sourceUrl());
            partnersLeftBehind.adopt(stationId, leftBehind);
            federationFixup.announceNewHostToRemotePartners(stationId, api.baseUrl());
            client.notifyComplete();
            if (sharedStorage != null) sharedFiles.release(sharedStorage);
            p.complete();
            log.info("completed for station '{}' (id={})", p.stationName(), stationId);
        } catch (Exception e) {
            log.error("failed for station {}", stationId, e);
            settleSharedStorage(sharedStorage, p);
            client.notifyAbort();
            removeStationMadeFor(p);
            p.fail(e.getMessage());
        }
    }

    /**
     * Takes away the station of a failed import where the import made it. A station that was here
     * before keeps everything it had, and what the import merged into it stays with it, logged.
     */
    private void removeStationMadeFor(ImportProgress p) {
        int stationId = p.stationId();
        if (p.target() == ImportProgress.Target.EXISTING_STATION) {
            log.warn("import into existing station {} failed; the station stays with what arrived so far", stationId);
            return;
        }
        try {
            stationRepository.delete(stationId);
            log.warn("deleted half-imported station {} after failure", stationId);
        } catch (Exception deleteErr) {
            log.error("could not clean up failed station {}", stationId, deleteErr);
        }
    }

    /**
     * Gives the source back the files a failed import copied over in the storage both share, when the
     * import takes away the station it made. A station that was here before keeps what arrived, so only
     * the staged copies go.
     */
    private void settleSharedStorage(SharedStorageFiles.@Nullable Run sharedStorage, ImportProgress p) {
        if (sharedStorage == null) return;
        if (p.target() == ImportProgress.Target.NEW_STATION) {
            sharedFiles.restore(sharedStorage);
        } else {
            sharedFiles.release(sharedStorage);
        }
    }

    /**
     * Installs the source's storage backend on the destination.
     *
     * @return the copy within the storage the destination took over from the source, or null when the source
     * used local storage and its files are pulled over the wire
     */
    private SharedStorageFiles.@Nullable Run adoptStorage(
            StationImportContext context,
            TransferSourceClient client,
            ImportProgress p,
            @Nullable Map<String, Object> stationData) {
        int stationId = context.stationId();
        log.info("tables done, applying source storage backend");
        p.startPhase("storage_backend");
        var descriptor = client.fetchBackendDescriptor();
        boolean installedRemote = backendImporter.apply(stationId, descriptor);
        p.completePhase();
        if (!installedRemote) return null;
        log.info(
                "Imported source storage backend ({}) for station {}",
                descriptor.getClass().getSimpleName(),
                stationId);
        UUID sourceUid = StationTableImporter.sourceUid(stationData)
                .orElseThrow(() -> new IllegalStateException(
                        "The source did not name its station, so its files cannot be found in its storage"));
        return new SharedStorageFiles.Run(scopeOf(stationId), sourceUid);
    }

    /**
     * Refuses to bring a station back to the installation that still keeps the copy it left when it
     * moved away. The copy holds its uid, so the station would arrive under a fresh one that none of
     * its partners knows.
     */
    private void refuseWhileItsMovedAwayCopyIsHere(@Nullable Map<String, Object> stationData) {
        boolean copyHere = StationTableImporter.sourceUid(stationData)
                .flatMap(stationRepository::resolveId)
                .flatMap(stationRepository::movedAway)
                .isPresent();
        if (copyHere) throw StationRefusal.STATION_IMPORT_MOVED_AWAY_COPY_HERE.raise();
    }

    private StorageScope.Station scopeOf(int stationId) {
        Station station = stationRepository
                .findById(stationId)
                .orElseThrow(() -> new RuntimeException("Station " + stationId + " not found after table import"));
        return new StorageScope.Station(stationId, station.uid());
    }

    /**
     * Copies every movable category, within the storage taken over from the source when there is one and
     * over the wire otherwise, before carrying over the avatars of the accounts this run created.
     */
    private void copyFiles(
            StationImportContext context,
            TransferSourceClient client,
            ImportProgress p,
            SharedStorageFiles.@Nullable Run sharedStorage) {
        StorageScope.Station scope = scopeOf(context.stationId());
        for (StorageCategory category : TransferFileImporter.transferrableStationCategories()) {
            p.startPhase("files_" + category.name().toLowerCase());
            if (sharedStorage != null) {
                sharedFiles.copyCategory(sharedStorage, category, context.idMap(), p);
            } else {
                fileImporter.copyCategory(client, scope, category, context.idMap(), p);
            }
            p.completePhase();
        }
        log.info("copying avatars for newly-created accounts");
        p.startPhase("account_avatars");
        fileImporter.copyNewAccountAvatars(client, context.newAccounts(), p);
        p.completePhase();

        sendReOnboardingMails(context);
    }

    /**
     * Passkeys do not survive a transfer: a WebAuthn credential is bound to the source's domain,
     * so an account that had no password there arrives here with no way in at all. Every such
     * account with a reachable address gets the re-onboarding mail at import time, and the ones
     * nobody can mail are named in the log: they are the QR code's population, reached in the
     * room rather than by post.
     */
    private void sendReOnboardingMails(StationImportContext context) {
        int mailed = 0;
        int unreachable = 0;
        for (var ref : context.newAccounts()) {
            var account = accountRepository.findByUid(ref.destinationUid());
            if (account.isEmpty()) continue;
            if (accountRepository.findCredential(account.get().id()).isPresent()) continue;
            if (authService.sendPasswordSetup(account.get().id())) {
                mailed++;
            } else {
                unreachable++;
                log.info(
                        "Transferred account {} has no way in and no reachable address; onboard them again in person",
                        account.get().id());
            }
        }
        if (mailed > 0 || unreachable > 0) {
            log.info(
                    "Re-onboarding after transfer: {} setup mail(s) sent, {} account(s) reachable only in person",
                    mailed,
                    unreachable);
        }
    }

    /**
     * Pulls a table page by page until the source sends a short one. The page the source sent decides
     * this, not the rows written from it: rows left behind and accounts merged into existing ones count
     * as fewer, and must not end the table early.
     */
    private void fetchAndImportPaginated(StationImportContext context, String table, TransferSourceClient client) {
        OutputShape shape = shapeOf(table);
        int offset = 0;
        while (true) {
            var page = client.fetchPage(table, offset, PAGE_SIZE);
            Object payload = page.get(table);
            if (payload == null) return;
            importTable(context, table, payload);
            if (shape != OutputShape.ROWS) return;
            if (!(payload instanceof List<?> rows) || rows.size() < PAGE_SIZE) return;
            offset += PAGE_SIZE;
        }
    }

    /**
     * Dispatches a single wire payload (already extracted from the page envelope) to the importer
     * that claims the table, falling back to the metadata-driven engine, and then writes the rows that
     * were waiting for what this payload brought. Lending requests and their messages arrive only where
     * they are the imported station's ({@link LendingRowScope}). A lending request whose uid the
     * partner's copy here already carries arrives under a stand-in and is merged into that copy once the
     * run has settled. Members whose account the run found here by its address are noted, so their
     * owners can be asked once the run has settled ({@link ImportedAccountLinks}).
     */
    @SuppressWarnings("unchecked")
    private int importTable(StationImportContext context, String table, Object payload) {
        TableImporter importer = importers.get(table);
        if (importer != null) {
            return importer.importRows(context, payload)
                    + engine.admitWaiting(context.stationId(), context.idMap(), context.waitingRows());
        }
        var rows = (List<Map<String, Object>>) payload;
        if (LENDING_REQUESTS.equals(table)) {
            UUID importedUid = stationRepository.requireUid(context.stationId());
            rows = lendingClashes.setAside(
                    LendingRowScope.requests(context, importedUid, rows), context.lendingStandIns());
        }
        if (LENDING_MESSAGES.equals(table)) rows = LendingRowScope.messages(context, rows);
        if (ImportedAccountLinks.isMembers(table)) importedLinks.note(context, rows);
        int imported = engine.importRows(context.stationId(), table, rows, context.idMap(), context.waitingRows());
        return imported + engine.admitWaiting(context.stationId(), context.idMap(), context.waitingRows());
    }

    private OutputShape shapeOf(String table) {
        var e = tracking.tables() == null ? null : tracking.tables().get(table);
        return e == null ? OutputShape.ROWS : e.effectiveShape();
    }

    public record ImportResult(int stationId, String stationName, int totalEntities) {}
}
