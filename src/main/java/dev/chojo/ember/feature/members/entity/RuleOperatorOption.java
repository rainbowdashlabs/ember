/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.entity;

import dev.chojo.ember.feature.question.FieldType;
import org.jspecify.annotations.Nullable;

/**
 * One operator a check of a rule can use, as the builder offers it.
 *
 * @param subject   what the check looks at
 * @param fieldType the type of the field it reads, null for a subject that reads none
 * @param operator  how it compares
 * @param need      what its value has to carry
 */
public record RuleOperatorOption(
        RuleSubject subject, @Nullable FieldType fieldType, RuleOperator operator, RuleNeed need) {}
