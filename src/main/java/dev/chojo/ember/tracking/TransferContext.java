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
 * Coverage status for {@code stationExport} or {@code stationImport}.
 *
 * @param status         current verification status
 * @param reason         required when {@code status = IGNORED}; explains why the table/file is not handled
 * @param ignoredColumns     columns excluded from this context even when the table is otherwise TRACKED
 * @param optionalReferences foreign key columns that arrive empty when the row they name never arrives,
 *                           where any other reference keeps its own row behind. They name rows that may
 *                           belong to somebody else, such as the partner's gear on a lending request.
 * @param rationale          optional informational note for TRACKED contexts where the rationale isn't obvious
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record TransferContext(
        TrackingStatus status,
        @Nullable String reason,
        @Nullable List<String> ignoredColumns,
        @Nullable List<String> optionalReferences,
        @Nullable String rationale) {
    public TransferContext {
        ignoredColumns = ignoredColumns == null ? List.of() : ignoredColumns;
        optionalReferences = optionalReferences == null ? List.of() : optionalReferences;
    }

    public static TransferContext unverified() {
        return new TransferContext(TrackingStatus.UNVERIFIED, null, List.of(), List.of(), null);
    }

    public static TransferContext tracked() {
        return new TransferContext(TrackingStatus.TRACKED, null, List.of(), List.of(), null);
    }

    public static TransferContext ignored(String reason) {
        return new TransferContext(TrackingStatus.IGNORED, reason, List.of(), List.of(), null);
    }
}
