/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.attendance.entity;

/**
 * Supported data types for attendance template fields.
 */
public enum AttendanceFieldType {
    MEMBER,
    MEMBER_LIST,
    STRING,
    TIME,
    DATE,
    MEMBER_OF_GROUP,
    MEMBER_LIST_OF_GROUP
}
