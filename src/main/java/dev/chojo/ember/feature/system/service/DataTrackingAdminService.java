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
import dev.chojo.ember.tracking.Status;
import dev.chojo.ember.tracking.TableEntry;
import dev.chojo.ember.tracking.TransferContext;
import io.javalin.http.BadRequestResponse;
import jakarta.inject.Singleton;
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
 * expose this service — the route layer guards on {@code Demo.dev()}.
 */
@Singleton
public class DataTrackingAdminService {

    private static final Logger log = LoggerFactory.getLogger(DataTrackingAdminService.class);
    private static final Path DEFAULT_TRACKING_PATH = Path.of("src/main/resources/data_tracking.json");

    private final Path trackingPath;

    public DataTrackingAdminService() {
        this(DEFAULT_TRACKING_PATH);
    }

    /** Test-friendly constructor that lets the caller route reads/writes to a temp file. */
    public DataTrackingAdminService(Path trackingPath) {
        this.trackingPath = trackingPath;
    }

    /** Loads the tracking file from disk every call so concurrent edits remain visible. */
    public DataTracking load() throws IOException {
        if (Files.exists(trackingPath)) return DataTrackingLoader.load(trackingPath);
        // Fall back to the classpath copy when the source tree isn't available (rare in dev).
        return DataTrackingLoader.loadFromClasspath();
    }

    /**
     * Returns count-by-status across every tracking dimension for the dashboard.
     */
    public Summary summarize() throws IOException {
        var tracking = load();
        int totalTables = tracking.tables() == null ? 0 : tracking.tables().size();
        int transferTracked = 0, transferIgnored = 0, transferUnverified = 0;
        int gdprExportTracked = 0, gdprExportIgnored = 0, gdprExportUnverified = 0;
        int gdprDeletionTracked = 0, gdprDeletionIgnored = 0, gdprDeletionUnverified = 0;
        int totalColumns = 0, verifiedColumns = 0;
        if (tracking.tables() != null) {
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
                if (t.columns() != null) {
                    for (var c : t.columns()) {
                        totalColumns++;
                        if (c.verified()) verifiedColumns++;
                    }
                }
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
     * (only verification flags, statuses, rationales, ignoredColumns, etc. may change).
     */
    public TableEntry updateTable(String tableName, TableUpdate update) throws IOException {
        var tracking = load();
        var existing = tracking.tables() == null ? null : tracking.tables().get(tableName);
        if (existing == null) throw new BadRequestResponse("Unknown table: " + tableName);

        // Build the new column list — only the verified flags can change here. Descriptions are
        // mirrored from the live schema and preserved verbatim.
        List<ColumnEntry> newColumns = new ArrayList<>();
        Map<String, Boolean> verifiedOverrides = update.columnVerified();
        for (var col : existing.columns()) {
            boolean v = verifiedOverrides != null && verifiedOverrides.containsKey(col.name())
                    ? verifiedOverrides.get(col.name())
                    : col.verified();
            newColumns.add(new ColumnEntry(col.name(), col.type(), col.nullable(), v, col.description()));
        }

        TransferContext newTransfer = update.stationTransfer() != null
                ? new TransferContext(
                        update.stationTransfer().status(),
                        update.stationTransfer().reason(),
                        update.stationTransfer().ignoredColumns() == null
                                ? List.of()
                                : List.copyOf(update.stationTransfer().ignoredColumns()),
                        update.stationTransfer().rationale())
                : existing.stationTransfer();

        GdprExportContext newGdprExport = update.gdprExport() != null
                ? new GdprExportContext(
                        update.gdprExport().status(),
                        update.gdprExport().reason(),
                        update.gdprExport().identityColumns() == null
                                ? List.of()
                                : List.copyOf(update.gdprExport().identityColumns()),
                        update.gdprExport().ignoredColumns() == null
                                ? List.of()
                                : List.copyOf(update.gdprExport().ignoredColumns()))
                : existing.gdprExport();

        GdprDeletionContext newGdprDeletion = update.gdprDeletion() != null
                ? new GdprDeletionContext(
                        update.gdprDeletion().status(),
                        update.gdprDeletion().reason(),
                        update.gdprDeletion().strategies() == null
                                ? List.of()
                                : List.copyOf(update.gdprDeletion().strategies()))
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

    /** Convenience: marks every column of a table as verified. */
    public TableEntry verifyAllColumns(String tableName) throws IOException {
        var tracking = load();
        var existing = tracking.tables() == null ? null : tracking.tables().get(tableName);
        if (existing == null) throw new BadRequestResponse("Unknown table: " + tableName);

        Map<String, Boolean> overrides = new LinkedHashMap<>();
        for (var c : existing.columns()) overrides.put(c.name(), true);
        return updateTable(
                tableName,
                new TableUpdate(
                        null, overrides, existing.stationTransfer(), existing.gdprExport(), existing.gdprDeletion()));
    }

    /**
     * Counts per {@link Status} for one tracking dimension. Tracked + Ignored + Unverified = total.
     */
    public record StatusCounts(int tracked, int ignored, int unverified) {}

    /** Aggregated counts for the admin dashboard overview. */
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
            String feature,
            Map<String, Boolean> columnVerified,
            TransferContext stationTransfer,
            GdprExportContext gdprExport,
            GdprDeletionContext gdprDeletion) {}
}
