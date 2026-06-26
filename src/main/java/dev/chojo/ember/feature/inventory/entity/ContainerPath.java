/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.entity;

import java.util.List;

/**
 * User-facing path representation for a container: a root-first chain of
 * segments joined by " / " for display, plus the raw id chain so the UI can
 * make each segment clickable.
 *
 * @param segments root-first list of container names along the path
 * @param ids      root-first list of container ids matching {@code segments}
 */
public record ContainerPath(List<String> segments, List<Integer> ids) {

    /**
     * The slash separator used between path segments.
     */
    public static final String SEPARATOR = " / ";

    /**
     * Returns an empty path for an unlocated item.
     */
    public static ContainerPath empty() {
        return new ContainerPath(List.of(), List.of());
    }

    /**
     * Normalises the segment and id lists to immutable copies.
     */
    public ContainerPath {
        segments = segments == null ? List.of() : List.copyOf(segments);
        ids = ids == null ? List.of() : List.copyOf(ids);
    }

    /**
     * Returns the path joined as a single display string.
     */
    public String display() {
        return String.join(SEPARATOR, segments);
    }
}
