/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.entity;

import org.jspecify.annotations.Nullable;

/**
 * What saving an appointment would tell, as the save dialog shows it before anything is saved.
 *
 * @param noticeAudience how many people would be told about the change if the editor chose to tell them
 * @param startMoves     whether the start moves on a date the save touches
 * @param newStart       the time of day it would start at where the start moves, or null
 * @param moveAudience   how many people holding a place would be told the new time in any case
 * @param everybody      how many people would hear anything at all if the editor chose to tell them
 */
public record ChangePreview(
        int noticeAudience, boolean startMoves, @Nullable String newStart, int moveAudience, int everybody) {}
