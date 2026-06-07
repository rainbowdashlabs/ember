/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.entity;

import dev.chojo.ember.api.MemberIdentity;

import java.util.UUID;

/**
 * Lightweight member representation for autocomplete with unified identity.
 * Includes display metadata (nameColor, displayTag) so the frontend can
 * render member names with proper styling without extra lookups.
 *
 * @param id         the internal member ID (still used for legacy operations)
 * @param name       the display name
 * @param stationUid the station UUID
 * @param memberUid  the member UUID
 * @param stationName the station name (for external badge display)
 * @param nameColor  hex color from highest-priority group
 * @param displayTag visible tag badge
 */
public record MemberCompletion(
        int id,
        String name,
        UUID stationUid,
        UUID memberUid,
        String stationName,
        String nameColor,
        MemberIdentity.DisplayTag displayTag) {}
