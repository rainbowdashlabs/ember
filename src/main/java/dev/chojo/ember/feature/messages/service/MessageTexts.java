/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.messages.service;

import dev.chojo.ember.api.refusal.MessageRefusal;
import org.jspecify.annotations.Nullable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The text rules of messages: how long a message and a group name may be, and how a message reads
 * where its mentions cannot be shown as such, as in a mail.
 *
 * <p>A mention is written into the text the way the page's mention completion writes it,
 * {@code @[account/<account uid>:<name>]}, so the name it showed travels with it.
 */
public final class MessageTexts {
    /** The longest a message may be. */
    public static final int MESSAGE_LENGTH = 4000;

    /** The longest a group name may be. */
    public static final int NAME_LENGTH = 100;

    private static final Pattern MENTION = Pattern.compile("@\\[[^/\\]]+/[^:\\]]+:([^\\]]+)]");

    private MessageTexts() {}

    /**
     * A message's text as it is kept: without surrounding blank space, refused when nothing is left
     * or it is too long.
     *
     * @param content the text as written
     * @return the text to keep
     */
    public static String content(@Nullable String content) {
        String text = content == null ? "" : content.strip();
        if (text.isEmpty()) throw MessageRefusal.MESSAGE_EMPTY.raise();
        if (text.length() > MESSAGE_LENGTH) throw MessageRefusal.MESSAGE_TOO_LONG.raise();
        return text;
    }

    /**
     * A group name as it is kept: trimmed, null when blank, refused when too long.
     *
     * @param name the name as given
     * @return the name to keep, or null to call the group after its members
     */
    public static @Nullable String groupName(@Nullable String name) {
        if (name == null || name.isBlank()) return null;
        String trimmed = name.strip();
        if (trimmed.length() > NAME_LENGTH) throw MessageRefusal.GROUP_NAME_TOO_LONG.raise();
        return trimmed;
    }

    /**
     * A message's text with every mention written as an at sign and the name, for a place that
     * shows plain text.
     *
     * @param content the text as kept
     * @return the text as read
     */
    public static String plain(String content) {
        return MENTION.matcher(content).replaceAll(match -> Matcher.quoteReplacement("@" + match.group(1)));
    }

    /**
     * The start of a text, cut at a length with an ellipsis where it was longer.
     *
     * @param text   the text
     * @param length how many characters to keep at most
     * @return the text, shortened
     */
    public static String shortened(String text, int length) {
        if (text.length() <= length) return text;
        return text.substring(0, length).stripTrailing() + "…";
    }
}
