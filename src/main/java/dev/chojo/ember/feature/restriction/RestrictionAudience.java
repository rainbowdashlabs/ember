/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.restriction;

import dev.chojo.ember.api.auth.StationUserType;

import java.util.ArrayList;
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

    /**
     * Whether a member belongs to this audience, by the same rule a stored restriction set follows.
     *
     * @param member the member and what a restriction can name of them
     * @return whether the member matches; an audience naming nobody takes in everybody
     */
    public boolean includes(RestrictionMember member) {
        var selection = toSelection();
        var restrictions = new ArrayList<Restriction>();
        selection.userTypes().forEach(type -> restrictions.add(new Restriction(0, type, null, null, null)));
        selection.groupIds().forEach(id -> restrictions.add(new Restriction(0, null, id, null, null)));
        selection.tagIds().forEach(id -> restrictions.add(new Restriction(0, null, null, id, null)));
        selection.memberIds().forEach(id -> restrictions.add(new Restriction(0, null, null, null, id)));
        return new RestrictionSet(restrictions, selection.mode())
                .matches(member.userType(), member.groupIds(), member.tagIds(), member.memberId());
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
