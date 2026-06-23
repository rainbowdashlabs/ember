/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.tracking;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Map;

/**
 * Root of the data tracking file. Records which tables and file stores are
 * covered by station export/import and GDPR export/deletion workflows.
 *
 * @param version     file format version
 * @param schemaHash  SHA-256 combining all table hashes — included in export bundles so import can verify schema parity
 * @param generatedAt timestamp of last refresh
 * @param tables      per-table tracking entries, keyed by table name
 * @param fileStores  per-file-store tracking entries, keyed by store name
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DataTracking(
        int version,
        String schemaHash,
        Instant generatedAt,
        Map<String, TableEntry> tables,
        Map<String, FileStoreEntry> fileStores) {

    public static final int CURRENT_VERSION = 1;
    public static final String RESOURCE_PATH = "/data_tracking.json";
}
