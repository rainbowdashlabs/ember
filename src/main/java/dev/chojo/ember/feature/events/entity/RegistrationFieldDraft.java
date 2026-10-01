/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.entity;

import dev.chojo.ember.feature.question.FieldType;

/**
 * A question asked at registration as it arrives from the editor, before it has a row of its own.
 */
public record RegistrationFieldDraft(
        String name, FieldType fieldType, EventQuestionSettings config, boolean overview) {}
