/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.api;

import java.util.Objects;
import java.util.UUID;

/**
 * Unified identity for any member reference (local or federated).
 * Includes optional display metadata resolved at the API boundary.
 *
 * @param stationUid  the station UUID
 * @param memberUid   the member UUID
 * @param name        the resolved display name of the member (null until enriched)
 * @param stationName the station display name (for external badge display, null for local members)
 * @param nameColor   hex color from the highest-priority group (null if no group has a color)
 * @param displayTag  the highest-priority visible tag to show as a badge (null if none)
 */
public record MemberIdentity(
        UUID stationUid, UUID memberUid, String name, String stationName, String nameColor, DisplayTag displayTag) {

    public MemberIdentity(UUID stationUid, UUID memberUid) {
        this(stationUid, memberUid, null, null, null, null);
    }

    public MemberIdentity withDisplay(String name, String stationName, String nameColor, DisplayTag displayTag) {
        return new MemberIdentity(stationUid, memberUid, name, stationName, nameColor, displayTag);
    }

    /**
     * Returns {@code true} if this identity and {@code other} refer to the same member,
     * comparing only the stable identity fields (stationUid + memberUid).
     *
     * <p>Use this for ownership checks instead of {@link #equals(Object)}, which also compares
     * enrichment fields (name, color, tag) that may differ between a bare DB-loaded identity
     * and an enriched session identity even when they point to the same person.
     */
    public boolean sameMember(MemberIdentity other) {
        if (other == null) return false;
        return Objects.equals(stationUid, other.stationUid) && Objects.equals(memberUid, other.memberUid);
    }

    public record DisplayTag(String name, String color) {}
}
