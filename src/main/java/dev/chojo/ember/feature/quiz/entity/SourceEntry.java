/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.quiz.entity;

import org.jspecify.annotations.Nullable;

public record SourceEntry(int catalogId, @Nullable Integer categoryId, int questionCount) {}
