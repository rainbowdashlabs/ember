/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.util;

import dev.chojo.ember.api.refusal.Refusal;
import org.jspecify.annotations.Nullable;

import java.util.Collection;

/**
 * The one rule for naming a group, whoever owns it: a station's member groups, an association's member
 * groups and an association's groups of stations.
 *
 * <p>A name is trimmed, may not be blank, and may not be one another group of the same owner already has,
 * whatever the case: two groups called "Jugend" and "jugend" are one group spelled twice to a reader.
 * The refusals stay the owner's own, because their codes are public.
 *
 * <p>A group that keeps its name is never refused for it. Names given before this rule held may differ
 * from another group's in case alone, and saving such a group's colour must not fail over a name nobody
 * changed.
 */
public final class GroupNames {
    private GroupNames() {}

    /**
     * Checks a name for a group and gives it back trimmed.
     *
     * @param name    the name asked for
     * @param current the group's present name, or {@code null} for a group being created
     * @param others  the names of the owner's other groups
     * @param missing the refusal for a blank name
     * @param taken   the refusal for a name another group has, raised with the trimmed name
     * @return the name to store
     */
    public static String require(
            @Nullable String name,
            @Nullable String current,
            Collection<String> others,
            Refusal missing,
            Refusal taken) {
        if (name == null || name.isBlank()) throw missing.raise();
        String trimmed = name.trim();
        if (trimmed.equals(current)) return trimmed;
        if (others.stream().anyMatch(other -> other.trim().equalsIgnoreCase(trimmed))) {
            throw taken.raise(trimmed);
        }
        return trimmed;
    }
}
