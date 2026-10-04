/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.restriction;

import dev.chojo.ember.api.auth.StationUserType;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * One named audience on the wire, as the shared audience editor reads and writes it.
 *
 * <p>{@link RestrictionSet} is what a read produces and {@link RestrictionSelection} is what a write
 * consumes; this is the single shape that travels between them and the browser, so a feature holding
 * more than one audience does not need a record per audience.
 *
 * @param userTypes the kinds of member named, empty where none are
 * @param groupIds  the groups named
 * @param tagIds    the tags named
 * @param memberIds members named one by one, who always match regardless of the mode
 * @param mode      whether every kind named has to match or any one of them
 */
public record RestrictionAudience(
        List<StationUserType> userTypes,
        List<Integer> groupIds,
        List<Integer> tagIds,
        List<Integer> memberIds,
        RestrictionMode mode) {

    /** An audience naming nobody, which every member passes. */
    public static RestrictionAudience empty() {
        return new RestrictionAudience(List.of(), List.of(), List.of(), List.of(), RestrictionMode.AND);
    }

    /** @return whether the audience names nobody, which every member passes */
    public boolean namesNobody() {
        var selection = toSelection();
        return selection.userTypes().isEmpty()
                && selection.groupIds().isEmpty()
                && selection.tagIds().isEmpty()
                && selection.memberIds().isEmpty();
    }

    /**
     * Whether a member belongs to this audience.
     *
     * @param member the member and what a restriction can name of them
     * @return whether the member matches; an audience naming nobody takes in everybody
     */
    public boolean includes(RestrictionMember member) {
        return matches(member.userType(), member.groupIds(), member.tagIds(), member.memberId());
    }

    /**
     * Whether a member belongs to this audience. A member named one by one always does, whatever the
     * mode; an audience that names members only takes in nobody else. The kinds named (user types,
     * groups, tags) then combine by the mode: with {@link RestrictionMode#OR} one match of any kind is
     * enough, with {@link RestrictionMode#AND} each kind named needs one match.
     *
     * @param userType the member's user type, or null where the member has none
     * @param groupIds the member's groups
     * @param tagIds   the member's tags
     * @param memberId the member
     * @return whether the member matches; an audience naming nobody takes in everybody
     */
    public boolean matches(
            @Nullable StationUserType userType, List<Integer> groupIds, List<Integer> tagIds, int memberId) {
        var selection = toSelection();
        boolean typeNamed = !selection.userTypes().isEmpty();
        boolean groupNamed = !selection.groupIds().isEmpty();
        boolean tagNamed = !selection.tagIds().isEmpty();
        boolean kindNamed = typeNamed || groupNamed || tagNamed;
        if (!kindNamed && selection.memberIds().isEmpty()) return true;
        if (selection.memberIds().contains(memberId)) return true;
        if (!kindNamed) return false;
        boolean typeMatches = userType != null && selection.userTypes().contains(userType);
        boolean groupMatches = selection.groupIds().stream().anyMatch(groupIds::contains);
        boolean tagMatches = selection.tagIds().stream().anyMatch(tagIds::contains);
        if (selection.mode() == RestrictionMode.OR) return typeMatches || groupMatches || tagMatches;
        return (!typeNamed || typeMatches) && (!groupNamed || groupMatches) && (!tagNamed || tagMatches);
    }

    public static RestrictionAudience of(RestrictionSet set) {
        return new RestrictionAudience(set.userTypes(), set.groupIds(), set.tagIds(), set.memberIds(), set.mode());
    }

    /**
     * Reads this audience as a selection to persist. An audience that arrived without any part set
     * still writes: an empty selection is how an audience is cleared.
     */
    public RestrictionSelection toSelection() {
        return new RestrictionSelection(
                userTypes == null ? List.of() : userTypes,
                groupIds == null ? List.of() : groupIds,
                tagIds == null ? List.of() : tagIds,
                memberIds == null ? List.of() : memberIds,
                mode);
    }
}
