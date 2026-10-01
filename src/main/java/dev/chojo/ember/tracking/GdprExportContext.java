/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.tracking;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Coverage status for the GDPR data export workflow.
 *
 * @param status          current verification status
 * @param reason          required when {@code status = IGNORED}
 * @param identityColumns required when {@code status = TRACKED} - columns that link rows to a person
 * @param ignoredColumns  columns excluded from the GDPR export even when the table is TRACKED
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record GdprExportContext(
        TrackingStatus status,
        @Nullable String reason,
        @Nullable List<IdentityColumn> identityColumns,
        @Nullable List<String> ignoredColumns) {
    public GdprExportContext {
        identityColumns = identityColumns == null ? List.of() : identityColumns;
        ignoredColumns = ignoredColumns == null ? List.of() : ignoredColumns;
    }

    public static GdprExportContext unverified() {
        return new GdprExportContext(TrackingStatus.UNVERIFIED, null, List.of(), List.of());
    }

    public static GdprExportContext ignored(String reason) {
        return new GdprExportContext(TrackingStatus.IGNORED, reason, List.of(), List.of());
    }

    public static GdprExportContext tracked(List<IdentityColumn> identityColumns) {
        return new GdprExportContext(TrackingStatus.TRACKED, null, identityColumns, List.of());
    }
}
