/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.tracking;

import com.fasterxml.jackson.annotation.JsonInclude;

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
        Status status, String reason, List<IdentityColumn> identityColumns, List<String> ignoredColumns) {
    public static GdprExportContext unverified() {
        return new GdprExportContext(Status.UNVERIFIED, null, List.of(), List.of());
    }

    public static GdprExportContext ignored(String reason) {
        return new GdprExportContext(Status.IGNORED, reason, List.of(), List.of());
    }

    public static GdprExportContext tracked(List<IdentityColumn> identityColumns) {
        return new GdprExportContext(Status.TRACKED, null, identityColumns, List.of());
    }
}
