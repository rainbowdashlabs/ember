/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.entity;

import org.jspecify.annotations.Nullable;

import java.time.Instant;

/**
 * The guardian link an act went through, as it stood at signing: the evidence of guardianship is this
 * link, so it is kept with the act rather than read again later, when it may be gone.
 *
 * @param position     the guardian's place in the member's order, counted from 0
 * @param linkedAt     when the guardian was linked to the member, or null where that was not recorded
 * @param linkedByName the official name of the member who made the link, or null where nobody did or
 *                     they are gone
 */
public record GuardianLink(
        int position, @Nullable Instant linkedAt, @Nullable String linkedByName) {

    /**
     * The link as the station keeps it, naming who made it by id.
     *
     * @param position the guardian's place in the member's order, counted from 0
     * @param linkedAt when the guardian was linked, or null where that was not recorded
     * @param linkedBy the member who made the link, or null where nobody did or they are gone
     */
    public record Stored(
            int position,
            @Nullable Instant linkedAt,
            @Nullable Integer linkedBy) {}
}
