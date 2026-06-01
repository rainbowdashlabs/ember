/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.api;

import java.util.UUID;

/**
 * Unified identity for any member reference (local or federated).
 * The frontend determines locality by comparing stationUid to the current station's UUID.
 */
public record MemberIdentity(UUID stationUid, UUID memberUid) {}
