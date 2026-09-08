/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.entity;

import dev.chojo.ember.feature.question.QuestionKind;

import java.util.Optional;

/**
 * Supported data types for custom profile fields.
 */
public enum ProfileFieldType {
    TEXT,
    NUMBER,
    DATE,
    BOOLEAN,
    ENUM,
    AGE,
    /**
     * A date field carrying the member's date of birth. A station may declare at most one, which is
     * what lets anything needing a birthday find it without being told which field holds it.
     */
    BIRTH_DATE,
    /**
     * A heading between fields rather than a field. It holds no answer, is never asked of anybody
     * and never leaves in an export: it exists so a long list of fields reads as the few groups of
     * things it actually is.
     */
    SECTION;

    /**
     * Whether this type holds an answer at all.
     */
    public boolean holdsValue() {
        return this != SECTION;
    }

    /**
     * The shared kind this type is, which is what the one check measures an answer against, or
     * nothing for a heading, which asks nobody anything.
     *
     * <p>A number reads as one that may carry a fraction, because nothing ever checked these and a
     * station with a shoe size of 42.5 on file is not told today that it may not have one.
     */
    public Optional<QuestionKind> kind() {
        return switch (this) {
            case TEXT -> Optional.of(QuestionKind.TEXT);
            case NUMBER, AGE -> Optional.of(QuestionKind.DECIMAL);
            case DATE, BIRTH_DATE -> Optional.of(QuestionKind.DATE);
            case BOOLEAN -> Optional.of(QuestionKind.BOOLEAN);
            case ENUM -> Optional.of(QuestionKind.CHOICE);
            case SECTION -> Optional.empty();
        };
    }
}
