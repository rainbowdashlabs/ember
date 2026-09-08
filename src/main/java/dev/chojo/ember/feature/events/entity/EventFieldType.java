/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.entity;

import dev.chojo.ember.feature.question.QuestionKind;

/**
 * Supported data types for event custom fields.
 */
public enum EventFieldType {
    STRING,
    NUMBER,
    DATE,
    TIME,
    BOOLEAN,
    ENUM,
    URL,
    TEXTAREA,
    /**
     * Free-text field whose value is treated as the event's location. The personal iCal
     * feed picks the first {@code LOCATION} field with a non-empty value to populate the
     * iCal {@code LOCATION} property, which clients render as a tap-to-navigate link.
     */
    LOCATION,
    MEMBER,
    MEMBER_LIST,
    MEMBER_OF_GROUP,
    MEMBER_LIST_OF_GROUP,
    MEMBER_OF_TYPE,
    MEMBER_LIST_OF_TYPE,
    MEMBER_OF_TAG,
    MEMBER_LIST_OF_TAG;

    /**
     * The shared kind this type is, which is what the one check measures an answer against.
     *
     * <p>Seven ways of naming members read as two kinds: one member or several. Which members may be
     * named is the appointment's own business and stays here, in the constraint beside the type.
     */
    public QuestionKind kind() {
        if (isMemberListField()) return QuestionKind.MEMBER_LIST;
        if (isMemberField()) return QuestionKind.MEMBER;
        return switch (this) {
            case NUMBER -> QuestionKind.NUMBER;
            case DATE -> QuestionKind.DATE;
            case TIME -> QuestionKind.TIME;
            case BOOLEAN -> QuestionKind.BOOLEAN;
            case ENUM -> QuestionKind.CHOICE;
            case URL -> QuestionKind.URL;
            case TEXTAREA -> QuestionKind.LONG_TEXT;
            default -> QuestionKind.TEXT;
        };
    }

    public boolean isMemberField() {
        return this == MEMBER
                || this == MEMBER_LIST
                || this == MEMBER_OF_GROUP
                || this == MEMBER_LIST_OF_GROUP
                || this == MEMBER_OF_TYPE
                || this == MEMBER_LIST_OF_TYPE
                || this == MEMBER_OF_TAG
                || this == MEMBER_LIST_OF_TAG;
    }

    public boolean isMemberListField() {
        return this == MEMBER_LIST
                || this == MEMBER_LIST_OF_GROUP
                || this == MEMBER_LIST_OF_TYPE
                || this == MEMBER_LIST_OF_TAG;
    }

    public MemberFieldConstraint constraint() {
        return switch (this) {
            case MEMBER_OF_GROUP, MEMBER_LIST_OF_GROUP -> MemberFieldConstraint.GROUP;
            case MEMBER_OF_TYPE, MEMBER_LIST_OF_TYPE -> MemberFieldConstraint.USER_TYPE;
            case MEMBER_OF_TAG, MEMBER_LIST_OF_TAG -> MemberFieldConstraint.TAG;
            default -> MemberFieldConstraint.NONE;
        };
    }

    public enum MemberFieldConstraint {
        NONE,
        GROUP,
        USER_TYPE,
        TAG
    }
}
