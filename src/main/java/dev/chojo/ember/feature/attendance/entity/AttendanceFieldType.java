/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.attendance.entity;

import dev.chojo.ember.feature.question.QuestionKind;

/**
 * Supported data types for attendance template fields.
 */
public enum AttendanceFieldType {
    STRING,
    NUMBER,
    DATE,
    TIME,
    BOOLEAN,
    ENUM,
    URL,
    TEXTAREA,
    MEMBER,
    MEMBER_LIST,
    MEMBER_OF_GROUP,
    MEMBER_LIST_OF_GROUP;

    /**
     * The shared kind this type is, which is what the one check measures an answer against.
     *
     * <p>A number on a sheet reads as one that may carry a fraction, because nothing ever checked
     * these and a station that has been writing halves into one is not told today that it may not.
     */
    public QuestionKind kind() {
        return switch (this) {
            case NUMBER -> QuestionKind.DECIMAL;
            case DATE -> QuestionKind.DATE;
            case TIME -> QuestionKind.TIME;
            case BOOLEAN -> QuestionKind.BOOLEAN;
            case ENUM -> QuestionKind.CHOICE;
            case URL -> QuestionKind.URL;
            case TEXTAREA -> QuestionKind.LONG_TEXT;
            case MEMBER, MEMBER_OF_GROUP -> QuestionKind.MEMBER;
            case MEMBER_LIST, MEMBER_LIST_OF_GROUP -> QuestionKind.MEMBER_LIST;
            case STRING -> QuestionKind.TEXT;
        };
    }
}
