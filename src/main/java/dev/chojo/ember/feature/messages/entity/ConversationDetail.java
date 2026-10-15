/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.messages.entity;

import java.util.List;

/**
 * A conversation as its header and member list show it.
 *
 * @param conversation the conversation
 * @param members      everybody taking part right now, the longest member first, the reader included
 * @param mine         the reader's own membership
 */
public record ConversationDetail(Conversation conversation, List<Membership> members, Membership mine) {}
