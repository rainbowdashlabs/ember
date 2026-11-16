/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.messages.entity;

import dev.chojo.ember.feature.restriction.RestrictionAudience;

/**
 * One of a station's chats as its managers see it in their list.
 *
 * @param conversation the chat
 * @param audience     whom it is for; one naming nobody is the whole station without trial members
 * @param members      how many take part in it right now
 */
public record StationChatSummary(Conversation conversation, RestrictionAudience audience, int members) {}
