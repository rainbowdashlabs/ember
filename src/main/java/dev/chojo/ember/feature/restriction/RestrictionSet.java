/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.restriction;

import java.util.List;

import static dev.chojo.ember.feature.restriction.RestrictionMode.OR;

/**
 * A complete set of restrictions for an entity, including the mode (AND/OR).
 * Provides {@link #matches} to check if a member satisfies the restrictions.
 */
public record RestrictionSet(List<Restriction> restrictions, RestrictionMode mode) {

    /**
     * Checks whether a member with the given identifiers satisfies these restrictions.
     *
     * @param memberUserType the member's user type name
     * @param memberGroupIds the member's group IDs
     * @param memberTagIds   the member's tag IDs
     * @param memberId       the member's ID (for per-user restrictions)
     * @return true if the member passes the restrictions
     */
    public boolean matches(
            String memberUserType, List<Integer> memberGroupIds, List<Integer> memberTagIds, int memberId) {
        if (restrictions.isEmpty()) return true;

        // Per-user restrictions are always OR — any member match grants immediate access
        boolean hasMemberRestrictions = restrictions.stream().anyMatch(r -> r.memberId() != null);
        if (hasMemberRestrictions) {
            boolean memberMatch = restrictions.stream().anyMatch(r -> r.memberId() != null && r.memberId() == memberId);
            if (memberMatch) return true;
            boolean hasOtherRestrictions = restrictions.stream()
                    .anyMatch(r -> r.userType() != null || r.groupId() != null || r.tagId() != null);
            if (!hasOtherRestrictions) return false;
        }

        var userTypeRestrictions = restrictions.stream()
                .map(Restriction::userType)
                .filter(ut -> ut != null)
                .toList();
        var groupRestrictions = restrictions.stream()
                .map(Restriction::groupId)
                .filter(id -> id != null)
                .toList();
        var tagRestrictions = restrictions.stream()
                .map(Restriction::tagId)
                .filter(id -> id != null)
                .toList();

        if (mode == OR) {
            if (userTypeRestrictions.contains(memberUserType)) return true;
            for (int gId : groupRestrictions) {
                if (memberGroupIds.contains(gId)) return true;
            }
            for (int tId : tagRestrictions) {
                if (memberTagIds.contains(tId)) return true;
            }
            return false;
        } else {
            if (!userTypeRestrictions.isEmpty() && !userTypeRestrictions.contains(memberUserType)) return false;
            if (!groupRestrictions.isEmpty() && groupRestrictions.stream().noneMatch(memberGroupIds::contains))
                return false;
            return tagRestrictions.isEmpty() || tagRestrictions.stream().anyMatch(memberTagIds::contains);
        }
    }

    public boolean hasRestrictions() {
        return !restrictions.isEmpty();
    }

    public List<String> userTypes() {
        return restrictions.stream()
                .map(Restriction::userType)
                .filter(ut -> ut != null)
                .toList();
    }

    public List<Integer> groupIds() {
        return restrictions.stream()
                .map(Restriction::groupId)
                .filter(id -> id != null)
                .toList();
    }

    public List<Integer> tagIds() {
        return restrictions.stream()
                .map(Restriction::tagId)
                .filter(id -> id != null)
                .toList();
    }

    public List<Integer> memberIds() {
        return restrictions.stream()
                .map(Restriction::memberId)
                .filter(id -> id != null)
                .toList();
    }
}
