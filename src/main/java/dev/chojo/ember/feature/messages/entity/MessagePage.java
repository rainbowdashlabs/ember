/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.messages.entity;

import java.util.List;

/**
 * A stretch of a conversation, oldest first.
 *
 * @param messages the messages
 * @param hasMore  whether there are more in the direction that was asked for
 */
public record MessagePage(List<MessageView> messages, boolean hasMore) {}
