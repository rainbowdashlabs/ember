/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.station.transfer;

/**
 * Imports the wire payload of one tracked table into the destination station.
 *
 * <p>Implementations are collected through a Guice multibinding and looked up by {@link #table()}.
 * A table without a dedicated importer falls through to the metadata-driven generic engine. The
 * sequence in which tables are imported is not decided here — it comes from the foreign-key
 * topology of {@code data_tracking.json}, so the set of importers may be iterated in any order.
 */
public interface TableImporter {
    /**
     * The tracked table name this importer claims.
     *
     * @return the table name as it appears in the tracking metadata and on the wire
     */
    String table();

    /**
     * Imports one payload for this table.
     *
     * @param context the run context carrying the target station and the id remapping
     * @param payload the wire payload for this table, in the shape declared by the tracking metadata
     * @return the number of rows applied to the destination
     */
    int importRows(StationImportContext context, Object payload);
}
