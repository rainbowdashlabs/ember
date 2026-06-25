/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.entity;

import java.time.Instant;

/**
 * One row in an item's check history. Returned newest-first so a UI can show "this item has been
 * checked X times" without further sorting.
 *
 * @param checkId       parent check id; useful for cross-linking into the full check detail
 * @param result        what the operator recorded for this item in that check
 * @param checkedAt     when the check ran
 * @param checkerName   display name of the member who performed the check
 * @param containerName name of the container the check covered; {@code null} for member-scope checks
 * @param scope         {@code CONTAINER} or {@code MEMBER}
 * @param note          free-text note recorded for this item, or empty
 */
public record ItemCheckHistoryEntry(
        int checkId,
        CheckResult result,
        Instant checkedAt,
        String checkerName,
        String containerName,
        String scope,
        String note) {}
