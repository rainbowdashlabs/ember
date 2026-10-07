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
 * A complete set of restrictions for an entity, including the mode (AND/OR).
 * Provides {@link #matches} to check if a member satisfies the restrictions.
 */
public record RestrictionSet(List<Restriction> restrictions, RestrictionMode mode) {

    /**
     * Checks whether a member with the given identifiers satisfies these restrictions, by the rule of
     * {@link RestrictionAudience#matches}.
     *
     * @param memberUserType the member's user type, or null where the member has none
     * @param memberGroupIds the member's group IDs
     * @param memberTagIds   the member's tag IDs
     * @param memberId       the member's ID (for per-user restrictions)
     * @return true if the member passes the restrictions
     */
    public boolean matches(
            @Nullable StationUserType memberUserType,
            List<Integer> memberGroupIds,
            List<Integer> memberTagIds,
            int memberId) {
        return RestrictionAudience.of(this).matches(memberUserType, memberGroupIds, memberTagIds, memberId);
    }

    public boolean hasRestrictions() {
        return !restrictions.isEmpty();
    }

    public List<StationUserType> userTypes() {
        return restrictions.stream()
                .map(Restriction::userType)
                .filter(Objects::nonNull)
                .toList();
    }

    public List<Integer> groupIds() {
        return restrictions.stream()
                .map(Restriction::groupId)
                .filter(Objects::nonNull)
                .toList();
    }

    public List<Integer> tagIds() {
        return restrictions.stream()
                .map(Restriction::tagId)
                .filter(Objects::nonNull)
                .toList();
    }

    public List<Integer> memberIds() {
        return restrictions.stream()
                .map(Restriction::memberId)
                .filter(Objects::nonNull)
                .toList();
    }
}
