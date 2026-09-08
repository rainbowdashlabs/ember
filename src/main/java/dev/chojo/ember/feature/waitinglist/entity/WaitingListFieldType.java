/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.waitinglist.entity;

import dev.chojo.ember.feature.question.QuestionKind;

public enum WaitingListFieldType {
    TEXT,
    NUMBER,
    DATE,
    BOOLEAN,
    ENUM,
    /**
     * A date field carrying the date of birth. A list may declare at most one, which is what lets
     * the list work out an age without being told which field holds it. It is stored and answered
     * exactly like a {@link #DATE} field, so an ordinary date field becomes one without losing the
     * answers already given.
     */
    BIRTH_DATE;

    /**
     * The shared kind this type is, which is what the one check measures an answer against.
     *
     * <p>A number reads as one that may carry a fraction, because nothing ever checked these and a
     * list that has been taking them is not told today that it may not.
     */
    public QuestionKind kind() {
        return switch (this) {
            case TEXT -> QuestionKind.TEXT;
            case NUMBER -> QuestionKind.DECIMAL;
            case DATE, BIRTH_DATE -> QuestionKind.DATE;
            case BOOLEAN -> QuestionKind.BOOLEAN;
            case ENUM -> QuestionKind.CHOICE;
        };
    }
}
