/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.event.events;

import dev.chojo.ember.event.DomainEvent;

/**
 * @param preview plain-text snippet of the article body (markdown stripped, length-capped by
 *                the publisher) so the notification feed can surface the news content in the
 *                entry body instead of just the title + author.
 */
public record NewsCreated(int stationId, int newsId, String title, String authorName, String preview)
        implements DomainEvent {}
