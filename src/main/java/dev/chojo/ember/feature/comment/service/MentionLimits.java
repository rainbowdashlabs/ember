/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.comment.service;

/**
 * How far one comment may reach.
 *
 * <p>Every mention raises a notification, and nothing about writing a comment costs the author
 * anything, so a comment carrying hundreds of mentions is a way to put a message in front of a
 * whole station as often as the author likes. These ceilings bound one comment; they are
 * deliberately far above what somebody writing to colleagues reaches.
 */
public final class MentionLimits {

    /** Members one comment may mention by name. */
    public static final int MAX_MEMBER_MENTIONS = 25;

    /** Groups, events and registration audiences one comment may address at once. */
    public static final int MAX_BULK_MENTIONS = 5;

    private MentionLimits() {}
}
