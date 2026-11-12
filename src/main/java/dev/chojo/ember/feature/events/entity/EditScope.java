/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.entity;

import org.jspecify.annotations.Nullable;

import java.time.LocalDate;

/**
 * What an edit of a series applies to.
 *
 * @param kind which dates; a request that leaves it out is refused rather than guessed at
 * @param date the date it applies to, or from which on; not needed for all dates
 */
public record EditScope(
        @Nullable EditScopeKind kind, @Nullable LocalDate date) {

    /** Every date, which is what an edit without a scope means. */
    public static final EditScope ALL = new EditScope(EditScopeKind.ALL, null);
}
