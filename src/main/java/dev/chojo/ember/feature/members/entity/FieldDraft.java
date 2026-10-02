/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.entity;

import dev.chojo.ember.feature.question.FieldType;
import org.jspecify.annotations.Nullable;

/**
 * A profile question as an owner is about to write it down, before the shared checks have passed it.
 *
 * @param name     what it is to be called, which a spacer may leave empty
 * @param type     what kind of answer it takes
 * @param config   its settings
 * @param required whether an answer is expected
 */
public record FieldDraft(@Nullable String name, FieldType type, ProfileFieldConfig config, boolean required) {}
