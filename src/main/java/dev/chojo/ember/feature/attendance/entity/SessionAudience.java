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
 * Who stands on one sheet: the members of its groups and everybody of its user types.
 *
 * <p>A template carries one as the default for its sheets, and a sheet started with one of its own
 * keeps it, so filling the sheet in later and printing it go by the same people it was started with.
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

    /** Whether the sheet was told nothing, in which case the template's own user types and groups decide. */
    public boolean namesNobody() {
        return userTypes.isEmpty() && groupIds.isEmpty();
    }
}
