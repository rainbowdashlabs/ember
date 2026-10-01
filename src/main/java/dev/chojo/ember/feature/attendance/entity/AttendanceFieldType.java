/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.attendance.entity;

import dev.chojo.ember.feature.question.FieldType;

/**
 * The field types of an attendance template as the station's screens spell them.
 *
 * <p>Only the spelling is this feature's own: what a type means is {@link FieldType}, which each of
 * these names through {@link #fieldType()}.
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

    /** The shared name this type is stored under, which is also what every check and value reads. */
    public FieldType fieldType() {
        return switch (this) {
            case STRING -> FieldType.TEXT;
            case TEXTAREA -> FieldType.LONG_TEXT;
            case ENUM -> FieldType.CHOICE;
            default -> FieldType.valueOf(name());
        };
    }

    /**
     * The type a column holds under its shared name.
     *
     * @param stored the name as the column holds it
     */
    public static AttendanceFieldType stored(String stored) {
        return FieldType.featureType(AttendanceFieldType.class, stored, AttendanceFieldType::fieldType);
    }
}
