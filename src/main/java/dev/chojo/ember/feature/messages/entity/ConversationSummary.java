/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.messages.entity;

import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * One row of the reader's list of conversations.
 *
 * @param overview the conversation with the reader's own state
 * @param others   the other people taking part, the longest member first
 * @param newest   the newest message or system line, or null where nothing was written yet
 */
public record ConversationSummary(
        ConversationOverview overview,
        List<Person> others,
        @Nullable Message newest) {}
