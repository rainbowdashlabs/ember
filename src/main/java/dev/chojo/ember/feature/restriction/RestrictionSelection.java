/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.restriction;

import dev.chojo.ember.api.auth.StationUserType;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/**
 * A reusable bundle of the values that make up an access restriction: the selected user
 * types, groups, tags and members, plus the mode used to combine them. Mirrors the frontend
 * restriction picker so a single value flows through routes, services and the repository
 * instead of the four lists (and a separate mode) being threaded individually.
 *
 * <p>Null lists are normalised to empty and a null mode to {@link RestrictionMode#AND}, so
 * callers can pass request values through without null guards. The write path persists the
 * four lists; {@link #mode()} is stored on the owning entity by callers that support it.
 *
 * <p>The components are nullable because a request may leave any of them out; the accessors are
 * not, because the constructor has already filled in what was missing.
 */
public record RestrictionSelection(
        @Nullable List<StationUserType> userTypes,
        @Nullable List<Integer> groupIds,
        @Nullable List<Integer> tagIds,
        @Nullable List<Integer> memberIds,
        @Nullable RestrictionMode mode) {

    public RestrictionSelection {
        userTypes = userTypes != null ? userTypes : List.of();
        groupIds = groupIds != null ? groupIds : List.of();
        tagIds = tagIds != null ? tagIds : List.of();
        memberIds = memberIds != null ? memberIds : List.of();
        mode = mode != null ? mode : RestrictionMode.AND;
    }

    /** The selected user types, empty where none were named. */
    @Override
    public List<StationUserType> userTypes() {
        return Objects.requireNonNull(userTypes, "the constructor fills in a missing list");
    }

    /** The selected groups, empty where none were named. */
    @Override
    public List<Integer> groupIds() {
        return Objects.requireNonNull(groupIds, "the constructor fills in a missing list");
    }

    /** The selected tags, empty where none were named. */
    @Override
    public List<Integer> tagIds() {
        return Objects.requireNonNull(tagIds, "the constructor fills in a missing list");
    }

    /** The selected members, empty where none were named. */
    @Override
    public List<Integer> memberIds() {
        return Objects.requireNonNull(memberIds, "the constructor fills in a missing list");
    }

    /** How the selected values combine, {@link RestrictionMode#AND} where the caller named none. */
    @Override
    public RestrictionMode mode() {
        return Objects.requireNonNull(mode, "the constructor fills in a missing mode");
    }

    /**
     * An empty selection with no restrictions and the default {@link RestrictionMode#AND}.
     */
    public static RestrictionSelection empty() {
        return new RestrictionSelection(List.of(), List.of(), List.of(), List.of(), RestrictionMode.AND);
    }
}
