/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.service;

import dev.chojo.ember.tracking.ColumnEntry;
import dev.chojo.ember.tracking.DataTracking;
import dev.chojo.ember.tracking.DataTrackingLoader;
import dev.chojo.ember.tracking.GdprDeletionContext;
import dev.chojo.ember.tracking.GdprExportContext;
import dev.chojo.ember.tracking.TableEntry;
import dev.chojo.ember.tracking.TrackingStatus;
import dev.chojo.ember.tracking.TransferContext;
import io.javalin.http.BadRequestResponse;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Dev-mode service for reading and mutating {@code data_tracking.json} from a running instance.
 *
 * <p>Reads the file from {@link #DEFAULT_TRACKING_PATH} (under the source tree) so an in-process
 * developer build can persist edits between restarts. Production deployments must never
 * expose this service - the route layer guards on {@code Demo.dev()}.
 */
@Singleton
public class DataTrackingAdminService {

    private static final Logger log = LoggerFactory.getLogger(DataTrackingAdminService.class);
    private static final Path DEFAULT_TRACKING_PATH = Path.of("src/main/resources/data_tracking.json");

    private final Path trackingPath;

    public DataTrackingAdminService() {
        this(DEFAULT_TRACKING_PATH);
    }

    /**
     * Test-friendly constructor that lets the caller route reads/writes to a temp file.
     */
    public DataTrackingAdminService(Path trackingPath) {
        this.trackingPath = trackingPath;
    }

    /**
     * Loads the tracking file from disk every call so concurrent edits remain visible, or the
     * classpath copy when the source tree is not available.
     */
    public DataTracking load() throws IOException {
        if (Files.exists(trackingPath)) return DataTrackingLoader.load(trackingPath);
        return DataTrackingLoader.loadFromClasspath();
    }

    /**
     * Returns count-by-status across every tracking dimension for the dashboard.
     */
    public Summary summarize() throws IOException {
        var tracking = load();
        int totalTables = tracking.tables().size();
        int transferTracked = 0, transferIgnored = 0, transferUnverified = 0;
        int gdprExportTracked = 0, gdprExportIgnored = 0, gdprExportUnverified = 0;
        int gdprDeletionTracked = 0, gdprDeletionIgnored = 0, gdprDeletionUnverified = 0;
        int totalColumns = 0, verifiedColumns = 0;
        for (var t : tracking.tables().values()) {
            if (t.stationTransfer() != null) {
                switch (t.stationTransfer().status()) {
                    case TRACKED -> transferTracked++;
                    case IGNORED -> transferIgnored++;
                    case UNVERIFIED -> transferUnverified++;
                }
            }
            if (t.gdprExport() != null) {
                switch (t.gdprExport().status()) {
                    case TRACKED -> gdprExportTracked++;
                    case IGNORED -> gdprExportIgnored++;
                    case UNVERIFIED -> gdprExportUnverified++;
                }
            }
            if (t.gdprDeletion() != null) {
                switch (t.gdprDeletion().status()) {
                    case TRACKED -> gdprDeletionTracked++;
                    case IGNORED -> gdprDeletionIgnored++;
                    case UNVERIFIED -> gdprDeletionUnverified++;
                }
            }
            for (var c : t.columns()) {
                totalColumns++;
                if (c.verified()) verifiedColumns++;
            }
        }
        return new Summary(
                totalTables,
                totalColumns,
                verifiedColumns,
                new StatusCounts(transferTracked, transferIgnored, transferUnverified),
                new StatusCounts(gdprExportTracked, gdprExportIgnored, gdprExportUnverified),
                new StatusCounts(gdprDeletionTracked, gdprDeletionIgnored, gdprDeletionUnverified));
    }

    /**
     * Replaces a table entry in the tracking file. The new entry must have the same column list
     * (only verification flags, statuses, rationales, ignoredColumns, etc. may change). Column
     * descriptions mirror the live schema and are kept verbatim.
     */
    public TableEntry updateTable(String tableName, TableUpdate update) throws IOException {
        var tracking = load();
        var existing = tracking.tables().get(tableName);
        if (existing == null) throw new BadRequestResponse("Unknown table: " + tableName);

        List<ColumnEntry> newColumns = new ArrayList<>();
        Map<String, Boolean> verifiedOverrides = update.columnVerified();
        for (var col : existing.columns()) {
            boolean v = verifiedOverrides != null && verifiedOverrides.containsKey(col.name())
                    ? verifiedOverrides.get(col.name())
                    : col.verified();
            newColumns.add(new ColumnEntry(col.name(), col.type(), col.nullable(), v, col.description()));
        }

        TransferContext transfer = update.stationTransfer();
        TransferContext newTransfer = transfer != null
                ? new TransferContext(
                        transfer.status(), transfer.reason(), copyOf(transfer.ignoredColumns()), transfer.rationale())
                : existing.stationTransfer();

        GdprExportContext gdprExport = update.gdprExport();
        GdprExportContext newGdprExport = gdprExport != null
                ? new GdprExportContext(
                        gdprExport.status(),
                        gdprExport.reason(),
                        copyOf(gdprExport.identityColumns()),
                        copyOf(gdprExport.ignoredColumns()))
                : existing.gdprExport();

        GdprDeletionContext gdprDeletion = update.gdprDeletion();
        GdprDeletionContext newGdprDeletion = gdprDeletion != null
                ? new GdprDeletionContext(
                        gdprDeletion.status(), gdprDeletion.reason(), copyOf(gdprDeletion.strategies()))
                : existing.gdprDeletion();

        TableEntry updated = new TableEntry(
                update.feature() != null ? update.feature() : existing.feature(),
                existing.scope(),
                existing.tableHash(),
                newColumns,
                existing.foreignKeys(),
                existing.lookups(),
                existing.outputShape(),
                existing.flatField(),
                existing.customScope(),
                newTransfer,
                newGdprExport,
                newGdprDeletion,
                existing.description());

        var newTables = new LinkedHashMap<>(tracking.tables());
        newTables.put(tableName, updated);

        var refreshed = new DataTracking(
                tracking.version(), tracking.schemaHash(), Instant.now(), newTables, tracking.fileStores());
        DataTrackingLoader.write(trackingPath, refreshed);
        log.info("Updated tracking entry for {}", tableName);
        return updated;
    }

    /**
     * Convenience: marks every column of a table as verified.
     */
    public TableEntry verifyAllColumns(String tableName) throws IOException {
        var tracking = load();
        var existing = tracking.tables().get(tableName);
        if (existing == null) throw new BadRequestResponse("Unknown table: " + tableName);

        Map<String, Boolean> overrides = new LinkedHashMap<>();
        for (var c : existing.columns()) overrides.put(c.name(), true);
        return updateTable(
                tableName,
                new TableUpdate(
                        null, overrides, existing.stationTransfer(), existing.gdprExport(), existing.gdprDeletion()));
    }

    /**
     * An unmodifiable copy of a list an update may leave out, which stays left out.
     *
     * @param list the list from the update, or {@code null} when it was not sent
     * @param <T>  the element type
     * @return the copy, or {@code null} when there was no list
     */
    private static <T> @Nullable List<T> copyOf(@Nullable List<T> list) {
        return list == null ? null : List.copyOf(list);
    }

    /**
     * Counts per {@link TrackingStatus} for one tracking dimension. Tracked + Ignored + Unverified = total.
     */
    public record StatusCounts(int tracked, int ignored, int unverified) {}

    /**
     * Aggregated counts for the admin dashboard overview.
     */
    public record Summary(
            int totalTables,
            int totalColumns,
            int verifiedColumns,
            StatusCounts stationTransfer,
            StatusCounts gdprExport,
            StatusCounts gdprDeletion) {}

    /**
     * Mutable update payload for a single table. Any field left {@code null} is preserved from the
     * existing entry; the column list itself can't be changed (only verification flags).
     */
    public record TableUpdate(
            @Nullable String feature,
            @Nullable Map<String, Boolean> columnVerified,
            @Nullable TransferContext stationTransfer,
            @Nullable GdprExportContext gdprExport,
            @Nullable GdprDeletionContext gdprDeletion) {}
}
