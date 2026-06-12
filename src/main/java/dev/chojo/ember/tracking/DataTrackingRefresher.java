/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.tracking;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Merges the live database schema (read via {@link SchemaReader}) into
 * {@link DataTracking}, preserving existing verification statuses but flipping
 * column-level {@code verified} flags when content changes.
 */
public final class DataTrackingRefresher {

    private static final Logger log = LoggerFactory.getLogger(DataTrackingRefresher.class);

    private final SchemaReader schemaReader;

    public DataTrackingRefresher(SchemaReader schemaReader) {
        this.schemaReader = schemaReader;
    }

    /**
     * Refreshes the tracking file at {@code path} against the live schema.
     * Returns a summary of changes.
     */
    public RefreshSummary refresh(Path path) throws IOException, SQLException {
        DataTracking existing = DataTrackingLoader.load(path);
        var schema = schemaReader.readTables();

        var summary = new RefreshSummary();

        Map<String, TableEntry> newTables = new TreeMap<>();

        for (var entry : schema.entrySet()) {
            String name = entry.getKey();
            var rawTable = entry.getValue();
            String newHash = HashComputer.tableHash(rawTable);

            TableEntry oldEntry =
                    existing.tables() == null ? null : existing.tables().get(name);

            if (oldEntry == null) {
                // New table — all UNVERIFIED, all columns unverified.
                // Descriptions from PG flow through here unchanged; they don't affect verification.
                var columns = rawTable.columns.stream()
                        .map(c -> new ColumnEntry(c.name(), c.type(), c.nullable(), false, c.description()))
                        .toList();
                newTables.put(
                        name,
                        new TableEntry(
                                null,
                                null,
                                newHash,
                                columns,
                                toForeignKeys(rawTable),
                                null,
                                null,
                                null,
                                null,
                                TransferContext.unverified(),
                                GdprExportContext.unverified(),
                                GdprDeletionContext.unverified(),
                                rawTable.description));
                summary.tablesAdded.add(name);
                continue;
            }

            // Existing table — merge columns
            var oldByName = oldEntry.columns() == null
                    ? Map.<String, ColumnEntry>of()
                    : oldEntry.columns().stream()
                            .collect(Collectors.toMap(ColumnEntry::name, c -> c, (a, b) -> a, LinkedHashMap::new));

            var mergedColumns = new ArrayList<ColumnEntry>();
            for (var rawCol : rawTable.columns) {
                ColumnEntry existingCol = oldByName.get(rawCol.name());
                if (existingCol == null) {
                    // New column — unverified
                    mergedColumns.add(new ColumnEntry(
                            rawCol.name(), rawCol.type(), rawCol.nullable(), false, rawCol.description()));
                    summary.columnsAdded.add(name + "." + rawCol.name());
                } else if (!existingCol.type().equals(rawCol.type()) || existingCol.nullable() != rawCol.nullable()) {
                    // Type or nullability changed — re-verify. Refresh the description too.
                    mergedColumns.add(new ColumnEntry(
                            rawCol.name(), rawCol.type(), rawCol.nullable(), false, rawCol.description()));
                    summary.columnsChanged.add(
                            name + "." + rawCol.name() + " (" + existingCol.type() + " → " + rawCol.type() + ")");
                } else {
                    // Unchanged column metadata — preserve the verified flag but refresh the description
                    // from the live schema, since descriptions don't participate in verification.
                    mergedColumns.add(new ColumnEntry(
                            existingCol.name(),
                            existingCol.type(),
                            existingCol.nullable(),
                            existingCol.verified(),
                            rawCol.description()));
                }
            }

            // Detect removed columns
            var currentNames =
                    rawTable.columns.stream().map(SchemaReader.RawColumn::name).toList();
            for (var oldCol : oldEntry.columns()) {
                if (!currentNames.contains(oldCol.name())) {
                    summary.columnsRemoved.add(name + "." + oldCol.name());
                }
            }

            // Build merged entry — preserve all context statuses; refresh the table description from
            // the live schema since descriptions don't affect verification.
            var merged = new TableEntry(
                    oldEntry.feature(),
                    oldEntry.scope(),
                    newHash,
                    mergedColumns,
                    toForeignKeys(rawTable),
                    oldEntry.lookups(),
                    oldEntry.outputShape(),
                    oldEntry.flatField(),
                    oldEntry.customScope(),
                    oldEntry.stationTransfer() == null ? TransferContext.unverified() : oldEntry.stationTransfer(),
                    oldEntry.gdprExport() == null ? GdprExportContext.unverified() : oldEntry.gdprExport(),
                    oldEntry.gdprDeletion() == null ? GdprDeletionContext.unverified() : oldEntry.gdprDeletion(),
                    rawTable.description);
            newTables.put(name, merged);
        }

        // Detect removed tables
        if (existing.tables() != null) {
            for (var oldName : existing.tables().keySet()) {
                if (!schema.containsKey(oldName)) {
                    summary.tablesRemoved.add(oldName);
                }
            }
        }

        // Preserve fileStores as-is — fileStores aren't auto-discoverable
        Map<String, FileStoreEntry> fileStores =
                existing.fileStores() == null ? new LinkedHashMap<>() : new LinkedHashMap<>(existing.fileStores());

        String topHash = HashComputer.schemaHash(newTables);

        var refreshed = new DataTracking(DataTracking.CURRENT_VERSION, topHash, Instant.now(), newTables, fileStores);
        DataTrackingLoader.write(path, refreshed);

        return summary;
    }

    private static List<ForeignKey> toForeignKeys(SchemaReader.RawTable rawTable) {
        return rawTable.foreignKeys.stream()
                .map(fk -> new ForeignKey(fk.column(), fk.refTable(), fk.refColumn(), fk.deleteRule()))
                .toList();
    }

    /** Summary of changes detected during a refresh. */
    public static final class RefreshSummary {
        public final List<String> tablesAdded = new ArrayList<>();
        public final List<String> tablesRemoved = new ArrayList<>();
        public final List<String> columnsAdded = new ArrayList<>();
        public final List<String> columnsRemoved = new ArrayList<>();
        public final List<String> columnsChanged = new ArrayList<>();

        public int totalChanges() {
            return tablesAdded.size()
                    + tablesRemoved.size()
                    + columnsAdded.size()
                    + columnsRemoved.size()
                    + columnsChanged.size();
        }

        public void log(Logger log) {
            log.info("✓ data_tracking.json refreshed");
            log.info("  - {} table(s) added{}", tablesAdded.size(), tablesAdded.isEmpty() ? "" : ": " + tablesAdded);
            log.info(
                    "  - {} column(s) added{}", columnsAdded.size(), columnsAdded.isEmpty() ? "" : ": " + columnsAdded);
            log.info(
                    "  - {} column(s) removed{}",
                    columnsRemoved.size(),
                    columnsRemoved.isEmpty() ? "" : ": " + columnsRemoved);
            log.info(
                    "  - {} column(s) changed{}",
                    columnsChanged.size(),
                    columnsChanged.isEmpty() ? "" : ": " + columnsChanged);
            log.info(
                    "  - {} table(s) removed{}",
                    tablesRemoved.size(),
                    tablesRemoved.isEmpty() ? "" : ": " + tablesRemoved);
            if (totalChanges() > 0) {
                log.info("");
                log.info("  Run ./gradlew reviewDataTracking to verify the changes.");
            }
        }
    }
}
