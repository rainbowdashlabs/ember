/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.tracking;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Coverage status for {@code stationExport} or {@code stationImport}.
 *
 * @param status         current verification status
 * @param reason         required when {@code status = IGNORED}; explains why the table/file is not handled
 * @param ignoredColumns columns excluded from this context even when the table is otherwise TRACKED
 * @param rationale      optional informational note for TRACKED contexts where the rationale isn't obvious
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record TransferContext(Status status, String reason, List<String> ignoredColumns, String rationale) {
    public static TransferContext unverified() {
        return new TransferContext(Status.UNVERIFIED, null, List.of(), null);
    }

    public static TransferContext tracked() {
        return new TransferContext(Status.TRACKED, null, List.of(), null);
    }

    public static TransferContext ignored(String reason) {
        return new TransferContext(Status.IGNORED, reason, List.of(), null);
    }
}
