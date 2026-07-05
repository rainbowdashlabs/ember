/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.restriction;

import dev.chojo.ember.api.auth.StationUserType;

import java.util.List;

/**
 * A reusable bundle of the values that make up an access restriction: the selected user
 * types, groups, tags and members, plus the mode used to combine them. Mirrors the frontend
 * restriction picker so a single value flows through routes, services and the repository
 * instead of the four lists (and a separate mode) being threaded individually.
 *
 * <p>Null lists are normalised to empty and a null mode to {@link RestrictionMode#AND}, so
 * callers can pass request values through without null guards. The write path persists the
 * four lists; {@link #mode()} is stored on the owning entity by callers that support it.
 */
public record RestrictionSelection(
        List<StationUserType> userTypes,
        List<Integer> groupIds,
        List<Integer> tagIds,
        List<Integer> memberIds,
        RestrictionMode mode) {

    public RestrictionSelection {
        userTypes = userTypes != null ? userTypes : List.of();
        groupIds = groupIds != null ? groupIds : List.of();
        tagIds = tagIds != null ? tagIds : List.of();
        memberIds = memberIds != null ? memberIds : List.of();
        mode = mode != null ? mode : RestrictionMode.AND;
    }

    /**
     * An empty selection with no restrictions and the default {@link RestrictionMode#AND}.
     */
    public static RestrictionSelection empty() {
        return new RestrictionSelection(List.of(), List.of(), List.of(), List.of(), RestrictionMode.AND);
    }
}
