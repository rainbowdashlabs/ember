/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.messages.entity;

/** What the daily mail about messages takes from station chats. */
public enum StationDigest {
    /** Only the unread messages that mention the reader. */
    MENTIONS,
    /** Every unread message. */
    ALL
}
