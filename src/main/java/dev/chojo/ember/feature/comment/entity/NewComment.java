/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.comment.entity;

import org.jspecify.annotations.Nullable;

import java.time.LocalDate;

/**
 * A comment about to be written.
 *
 * @param parentId  the comment it answers, {@code null} for a top-level comment
 * @param eventDate the occurrence of a recurring appointment it is about, {@code null} otherwise
 * @param content   the text
 */
public record NewComment(
        @Nullable Integer parentId, @Nullable LocalDate eventDate, String content) {}
