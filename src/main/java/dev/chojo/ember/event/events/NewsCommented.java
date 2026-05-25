/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.event.events;

import dev.chojo.ember.event.DomainEvent;

public record NewsCommented(int stationId, int newsId, String newsTitle, int authorMemberId, String authorName)
        implements DomainEvent {}
