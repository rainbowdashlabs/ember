/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.tracking;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Tracking entry for a filesystem-stored data location (e.g. {@code data/kb-files/}).
 *
 * @param path            filesystem path pattern (may include placeholders like {@code {fileId}})
 * @param linkedTable     DB table whose rows correspond to entries here, or {@code null} for orphan stores
 * @param linkColumn      column on {@code linkedTable} that maps to the path placeholder
 * @param feature         feature/module the file store belongs to
 * @param stationTransfer coverage for the station export+import pair
 * @param gdprExport      coverage status for GDPR export
 * @param gdprDeletion    coverage status for GDPR deletion
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record FileStoreEntry(
        String path,
        String linkedTable,
        String linkColumn,
        String feature,
        TransferContext stationTransfer,
        GdprExportContext gdprExport,
        GdprDeletionContext gdprDeletion) {}
