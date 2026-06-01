/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.entity;

import java.util.UUID;

/**
 * Lightweight member representation for autocomplete with unified identity.
 *
 * @param id         the internal member ID (still used for legacy operations)
 * @param name       the display name
 * @param stationUid the station UUID
 * @param memberUid  the member UUID
 */
public record MemberCompletion(int id, String name, UUID stationUid, UUID memberUid) {}
