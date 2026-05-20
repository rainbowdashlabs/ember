/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.notifications.entity;

/**
 * Enumerates the categories of notifications that can be sent to members.
 * Each type can be individually toggled in a member's notification settings.
 */
public enum NotificationType {
    NEW_NEWS,
    NEWS_COMMENT,
    EVENT_REGISTRATION_STATUS,
    EXCHANGE_STATUS_CHANGE,
    EXCHANGE_NEW_REQUEST,
    NEW_EVENT,
    MEMBER_ADDED_TO_GROUP,
    PROFILE_FIELD_CHANGED,
    PROCUREMENT_REQUESTED,
    PROCUREMENT_FULFILLED,
    NEW_FORM,
    LOST_AND_FOUND_NEW,
    LOST_AND_FOUND_CLAIMED
}
