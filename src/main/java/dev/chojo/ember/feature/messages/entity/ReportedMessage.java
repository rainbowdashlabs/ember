/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.messages.entity;

import java.util.List;

/**
 * An open report with the message in its context, as the people handling a station's chats look at
 * it.
 *
 * @param report  the report
 * @param context the reported message with a few messages before and after it, oldest first
 */
public record ReportedMessage(MessageReport report, List<MessageView> context) {}
