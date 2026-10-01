/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.entity;

import dev.chojo.ember.feature.question.FieldType;

/**
 * The type of an appointment's question under the names screens and partner stations read.
 *
 * <p>Only what crosses the wire speaks these names. Everything the server decides about a question
 * goes by its {@link FieldType}, and the two map onto each other one to one. The names stay because a
 * partner station compares them, constant by constant, before it shares an appointment.
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
    LOCATION,
    MEMBER,
    MEMBER_LIST,
    MEMBER_OF_GROUP,
    MEMBER_LIST_OF_GROUP,
    MEMBER_OF_TYPE,
    MEMBER_LIST_OF_TYPE,
    MEMBER_OF_TAG,
    MEMBER_LIST_OF_TAG;

    /** The shared type this name stands for. */
    public FieldType fieldType() {
        return switch (this) {
            case STRING -> FieldType.TEXT;
            case TEXTAREA -> FieldType.LONG_TEXT;
            case ENUM -> FieldType.CHOICE;
            default -> FieldType.valueOf(name());
        };
    }

    /**
     * The name a shared type is sent under.
     *
     * @param type a type an appointment's question takes
     * @throws IllegalArgumentException for a type no appointment offers
     */
    public static EventFieldType of(FieldType type) {
        return FieldType.featureType(EventFieldType.class, type.name(), EventFieldType::fieldType);
    }
}
