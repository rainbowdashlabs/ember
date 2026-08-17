/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.tracking;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Tracking entry for a single database table.
 *
 * @param feature         feature/module the table belongs to (e.g. "events", "storage")
 * @param scope           ownership scope (station, instance, or user)
 * @param tableHash       SHA-256 of this table's columns + FK definitions
 * @param columns         list of columns with per-column verification status
 * @param foreignKeys     FK metadata used by the generic export/import engine to derive scope paths
 * @param lookups         join-flattened fields the export engine should add to each row by following a
 *                        foreign key to another table; {@code null} when no lookups are configured
 * @param outputShape     how rows are emitted on the wire ({@code ROWS} by default, {@code SINGLE} for
 *                        one-row-per-station tables, {@code FLAT} for single-column lists)
 * @param flatField       column name to extract when {@code outputShape == FLAT}
 * @param customScope     override for tables that aren't directly scoped by an outgoing FK chain;
 *                        used for tables reached via an incoming FK (e.g. {@code account} through
 *                        {@code station_member.account_id})
 * @param stationTransfer coverage for the station export+import pair - what is exported must also be imported
 * @param gdprExport      coverage status for GDPR export
 * @param gdprDeletion    coverage status for GDPR deletion
 * @param description     {@code COMMENT ON TABLE} text mirrored from the live schema. Intentionally excluded
 *                        from {@link HashComputer} so editing a comment does not flip verification flags.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TableEntry(
        String feature,
        Scope scope,
        String tableHash,
        List<ColumnEntry> columns,
        List<ForeignKey> foreignKeys,
        List<Lookup> lookups,
        OutputShape outputShape,
        String flatField,
        CustomScope customScope,
        TransferContext stationTransfer,
        GdprExportContext gdprExport,
        GdprDeletionContext gdprDeletion,
        String description) {

    /**
     * Backwards-compatible constructor for callers that don't supply a description.
     */
    public TableEntry(
            String feature,
            Scope scope,
            String tableHash,
            List<ColumnEntry> columns,
            List<ForeignKey> foreignKeys,
            List<Lookup> lookups,
            OutputShape outputShape,
            String flatField,
            CustomScope customScope,
            TransferContext stationTransfer,
            GdprExportContext gdprExport,
            GdprDeletionContext gdprDeletion) {
        this(
                feature,
                scope,
                tableHash,
                columns,
                foreignKeys,
                lookups,
                outputShape,
                flatField,
                customScope,
                stationTransfer,
                gdprExport,
                gdprDeletion,
                null);
    }

    public TableEntry withColumns(List<ColumnEntry> newColumns) {
        return new TableEntry(
                feature,
                scope,
                tableHash,
                newColumns,
                foreignKeys,
                lookups,
                outputShape,
                flatField,
                customScope,
                stationTransfer,
                gdprExport,
                gdprDeletion,
                description);
    }

    public TableEntry withTableHash(String newHash) {
        return new TableEntry(
                feature,
                scope,
                newHash,
                columns,
                foreignKeys,
                lookups,
                outputShape,
                flatField,
                customScope,
                stationTransfer,
                gdprExport,
                gdprDeletion,
                description);
    }

    /**
     * Returns the effective output shape, defaulting to {@link OutputShape#ROWS}.
     */
    public OutputShape effectiveShape() {
        return outputShape == null ? OutputShape.ROWS : outputShape;
    }
}
