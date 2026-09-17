/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.attendance.entity;

import dev.chojo.ember.api.auth.StationUserType;

import java.util.List;
import java.util.Set;

/**
 * Who stands on one sheet, where the template it borrows its fields from is not the answer.
 *
 * <p>A template decides two things at once: which questions a sheet carries and whom it enters. A
 * station wanting only the first had to keep a template with no groups on it for the rest of its
 * life. This says whom to enter for this one sheet instead, and is remembered nowhere afterwards.
 *
 * @param userTypes the kinds of member to enter, empty where none was chosen
 * @param groupIds  the groups to enter, in the order they were chosen, which is the order the sheet
 *     is then written in
 */
public record SessionAudience(Set<StationUserType> userTypes, List<Integer> groupIds) {

    public SessionAudience {
        userTypes = userTypes == null ? Set.of() : Set.copyOf(userTypes);
        groupIds = groupIds == null ? List.of() : List.copyOf(groupIds);
    }

    /** Whether the sheet was told nothing, in which case the template's own groups still decide. */
    public boolean namesNobody() {
        return userTypes.isEmpty() && groupIds.isEmpty();
    }
}
