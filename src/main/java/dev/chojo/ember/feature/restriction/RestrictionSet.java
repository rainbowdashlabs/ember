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
 *
 * <p>Member restrictions are always OR-connected — any member_id match grants access
 * regardless of the mode setting for role/group/tag.</p>
 */
public record RestrictionSet(List<Restriction> restrictions, RestrictionMode mode) {

    /**
     * Checks whether a member with the given identifiers satisfies these restrictions.
     *
     * @param memberRoleIds  the member's role IDs
     * @param memberGroupIds the member's group IDs
     * @param memberTagIds   the member's tag IDs
     * @param memberId       the member's ID (for per-user restrictions)
     * @return true if the member passes the restrictions
     */
    public boolean matches(
            List<Integer> memberRoleIds, List<Integer> memberGroupIds, List<Integer> memberTagIds, int memberId) {
        if (restrictions.isEmpty()) return true;

        // Per-user restrictions are always OR — any member match grants immediate access
        boolean hasMemberRestrictions = restrictions.stream().anyMatch(r -> r.memberId() != null);
        if (hasMemberRestrictions) {
            boolean memberMatch = restrictions.stream().anyMatch(r -> r.memberId() != null && r.memberId() == memberId);
            if (memberMatch) return true;
            // If only member restrictions exist and none match, deny
            boolean hasOtherRestrictions =
                    restrictions.stream().anyMatch(r -> r.roleId() != null || r.groupId() != null || r.tagId() != null);
            if (!hasOtherRestrictions) return false;
        }

        var roleRestrictions = restrictions.stream()
                .filter(r -> r.roleId() != null)
                .map(Restriction::roleId)
                .toList();
        var groupRestrictions = restrictions.stream()
                .filter(r -> r.groupId() != null)
                .map(Restriction::groupId)
                .toList();
        var tagRestrictions = restrictions.stream()
                .filter(r -> r.tagId() != null)
                .map(Restriction::tagId)
                .toList();

        if (mode == OR) {
            // Any match across any type returns true
            for (int rId : roleRestrictions) {
                if (memberRoleIds.contains(rId)) return true;
            }
            for (int gId : groupRestrictions) {
                if (memberGroupIds.contains(gId)) return true;
            }
            for (int tId : tagRestrictions) {
                if (memberTagIds.contains(tId)) return true;
            }
            return false;
        } else {
            // AND: each non-empty type must have at least one match
            if (!roleRestrictions.isEmpty() && roleRestrictions.stream().noneMatch(memberRoleIds::contains))
                return false;
            if (!groupRestrictions.isEmpty() && groupRestrictions.stream().noneMatch(memberGroupIds::contains))
                return false;
            return tagRestrictions.isEmpty() || tagRestrictions.stream().anyMatch(memberTagIds::contains);
        }
    }

    /**
     * @return true if any restrictions are defined
     */
    public boolean hasRestrictions() {
        return !restrictions.isEmpty();
    }

    public List<Integer> roleIds() {
        return restrictions.stream()
                .filter(r -> r.roleId() != null)
                .map(Restriction::roleId)
                .toList();
    }

    public List<Integer> groupIds() {
        return restrictions.stream()
                .filter(r -> r.groupId() != null)
                .map(Restriction::groupId)
                .toList();
    }

    public List<Integer> tagIds() {
        return restrictions.stream()
                .filter(r -> r.tagId() != null)
                .map(Restriction::tagId)
                .toList();
    }

    public List<Integer> memberIds() {
        return restrictions.stream()
                .filter(r -> r.memberId() != null)
                .map(Restriction::memberId)
                .toList();
    }
}
