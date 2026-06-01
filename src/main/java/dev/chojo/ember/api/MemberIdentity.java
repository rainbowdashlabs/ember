/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.api;

import java.util.UUID;

/**
 * Unified identity for any member reference (local or federated).
 * Includes optional display metadata resolved at the API boundary.
 *
 * @param stationUid  the station UUID
 * @param memberUid   the member UUID
 * @param stationName the station display name (for external badge display, null for local members)
 * @param nameColor   hex color from the highest-priority group (null if no group has a color)
 * @param displayTag  the highest-priority visible tag to show as a badge (null if none)
 */
public record MemberIdentity(
        UUID stationUid, UUID memberUid, String stationName, String nameColor, DisplayTag displayTag) {

    public MemberIdentity(UUID stationUid, UUID memberUid) {
        this(stationUid, memberUid, null, null, null);
    }

    public MemberIdentity withDisplay(String stationName, String nameColor, DisplayTag displayTag) {
        return new MemberIdentity(stationUid, memberUid, stationName, nameColor, displayTag);
    }

    public record DisplayTag(String name, String color) {}
}
