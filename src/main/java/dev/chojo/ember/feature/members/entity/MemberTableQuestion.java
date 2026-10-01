/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.entity;

import dev.chojo.ember.feature.question.FieldType;

/**
 * One of an appointment's own questions as a table sees it: what it is called and what its answers
 * are.
 *
 * @param label     what the appointment calls the question
 * @param fieldType the question's type, which decides how an answer is printed
 */
public record MemberTableQuestion(String label, FieldType fieldType) {

    /** What the question's answers hold in the table. */
    public MemberTableCellType type() {
        return MemberTableCellType.of(fieldType);
    }
}
