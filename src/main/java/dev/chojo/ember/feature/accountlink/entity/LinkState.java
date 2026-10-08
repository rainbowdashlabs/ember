/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.accountlink.entity;

import org.jspecify.annotations.Nullable;

import java.time.Instant;

/**
 * Where a member's latest link request stands, as the station that asked sees it. The station never
 * learns which account it asked about beyond the address it typed itself.
 *
 * @param status        how the request stands
 * @param origin        how the station came to ask
 * @param sentAt        when it was last sent
 * @param expiresAt     until when the person may answer
 * @param answeredAt    when it was answered or ran out, or null while it waits
 * @param sendAgainFrom from when it may be sent again, or null where it may not be sent again at all
 */
public record LinkState(
        LinkStatus status,
        LinkOrigin origin,
        Instant sentAt,
        Instant expiresAt,
        @Nullable Instant answeredAt,
        @Nullable Instant sendAgainFrom) {}
