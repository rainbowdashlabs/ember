/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.messages.entity;

/**
 * A new message as a push shows it to one reader.
 *
 * @param title    the conversation as the reader knows it
 * @param body     who wrote and what, shortened by the sender to fit one push
 * @param url      where a tap opens the conversation
 * @param topicKey the conversation, so a newer push replaces the older one on the device
 */
public record MessagePush(String title, String body, String url, String topicKey) {}
