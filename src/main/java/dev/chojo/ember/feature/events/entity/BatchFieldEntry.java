/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.entity;

import dev.chojo.ember.feature.question.FieldType;
import org.jspecify.annotations.Nullable;

/**
 * Field definition copied onto every event created in a batch, as the batch request supplies it.
 */
public record BatchFieldEntry(
        String name,
        FieldType fieldType,
        EventQuestionSettings config,
        boolean overview,
        @Nullable Integer attendanceFieldId) {}
