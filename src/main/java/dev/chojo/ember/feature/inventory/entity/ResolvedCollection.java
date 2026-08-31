/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.entity;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;
import java.util.List;

/**
 * A collection read against the stock, with the window it was read over.
 *
 * <p>The window matters: without it two people planning the same weekend both see the same kit
 * fillable, which is the situation the collection exists for. A collection resolved without dates
 * ignores what is promised elsewhere and says only what is in the building today.
 *
 * @param collection the collection itself
 * @param dateFrom   the first day of the window, or {@code null} when it was read undated
 * @param dateTo     the last day of the window, or {@code null} when it was read undated
 * @param lines      one answer per line, in the collection's own order
 */
public record ResolvedCollection(
        InventoryCollection collection, LocalDate dateFrom, LocalDate dateTo, List<ResolvedCollectionLine> lines) {

    /**
     * Whether every line of the collection can be filled.
     *
     * @return {@code true} when nothing is missing
     */
    @JsonProperty("complete")
    public boolean complete() {
        return lines.stream().allMatch(ResolvedCollectionLine::filled);
    }

    /**
     * Whether any of what the collection would gather belongs to the body above the station.
     *
     * @return {@code true} when at least one available piece is owned above
     */
    @JsonProperty("holdsClusterOwned")
    public boolean holdsClusterOwned() {
        return lines.stream().anyMatch(line -> line.clusterOwned() > 0);
    }
}
