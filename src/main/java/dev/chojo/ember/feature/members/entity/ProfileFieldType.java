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
     * A date field carrying the last day something is valid, such as a first aid course or a licence.
     * It holds a day like any date, so a date field turns into one and back without losing an answer;
     * what it adds is that it shows how close the day is and reminds people before it passes, as its
     * {@link ExpirySettings} say.
     */
    EXPIRY_DATE,
    /**
     * A heading between fields rather than a field. It holds no answer, is never asked of anybody
     * and never leaves in an export: it exists so a long list of fields reads as the few groups of
     * things it actually is.
     */
    SECTION,
    /**
     * A gap on a row rather than a field. Like a heading it holds no answer and is asked of nobody,
     * and unlike one it keeps its width instead of taking the whole row: it is there to push what
     * follows it along, so two questions can be made to line up under two others.
     */
    SPACER;

    /**
     * Whether this type holds an answer at all.
     */
    public boolean holdsValue() {
        return this != SECTION && this != SPACER;
    }

    /**
     * Whether the answer is worked out rather than given.
     *
     * <p>An age counts itself from a date of birth, so nobody writes one and nobody has changed one:
     * a change recorded against it is a change nobody made, and somebody was asked to confirm it.
     */
    public boolean isCalculated() {
        return this == AGE;
    }

    /**
     * The shared kind this type is, which is what the one check measures an answer against, or
     * nothing for a type nobody answers.
     *
     * <p>A number reads as one that may carry a fraction, because nothing ever checked these and a
     * station with a shoe size of 42.5 on file is not told today that it may not have one. An age has
     * no kind: it is counted from a date and holds no value of its own.
     *
     * @return the kind, or nothing for the two that lay a form out and for an age
     */
    public Optional<QuestionKind> kind() {
        return switch (this) {
            case TEXT -> Optional.of(QuestionKind.TEXT);
            case NUMBER -> Optional.of(QuestionKind.DECIMAL);
            case DATE, BIRTH_DATE, EXPIRY_DATE -> Optional.of(QuestionKind.DATE);
            case BOOLEAN -> Optional.of(QuestionKind.BOOLEAN);
            case ENUM -> Optional.of(QuestionKind.CHOICE);
            case AGE, SECTION, SPACER -> Optional.empty();
        };
    }
}
