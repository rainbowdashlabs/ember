/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.messages.entity;

import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * A message as one reader sees it in a conversation.
 *
 * @param message   the message
 * @param replyTo   the message it answers, where that is still there
 * @param reactions the reactions to it, those of people the reader blocked left out
 */
public record MessageView(Message message, @Nullable Message replyTo, List<Reaction> reactions) {}
