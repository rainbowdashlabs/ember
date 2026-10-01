/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.service;

import dev.chojo.ember.feature.federation.entity.LendingStatus;
import dev.chojo.ember.feature.feed.render.FeedDetails;
import dev.chojo.ember.feature.feed.render.FeedDetailsContributor;
import dev.chojo.ember.feature.notifications.entity.Notification;
import dev.chojo.ember.feature.notifications.entity.NotificationParams;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

/**
 * What a feed entry says about a lending between stations: the other station, the request, its
 * status or who wrote, and from the request itself when the items are needed.
 */
@Singleton
public class LendingFeedDetails implements FeedDetailsContributor {
    private final LendingService lendingService;

    @Inject
    public LendingFeedDetails(LendingService lendingService) {
        this.lendingService = lendingService;
    }

    @Override
    public void contribute(NotificationParams params, Notification notification, FeedDetails details) {
        switch (params) {
            case NotificationParams.LendingNewRequest(String stationName, String itemSummary) -> {
                details.putIfPresent(details.label("station", "Station"), stationName);
                details.putIfPresent(details.label("itemSummary"), itemSummary);
                addNeeded(details);
            }
            case NotificationParams.LendingStatusChange(String stationName, LendingStatus status) -> {
                details.putIfPresent(details.label("status", "Status"), details.statusWithSymbol(status.name()));
                details.putIfPresent(details.label("station", "Station"), stationName);
                addNeeded(details);
            }
            case NotificationParams.LendingNewMessage(String stationName, String senderName) -> {
                details.putIfPresent(details.label("by"), senderName);
                details.putIfPresent(details.label("station", "Station"), stationName);
                addNeeded(details);
            }
            default -> {}
        }
    }

    @Override
    public @Nullable String author(NotificationParams params) {
        return params instanceof NotificationParams.LendingNewMessage p ? p.senderName() : null;
    }

    /**
     * Adds when the linked request needs the items, as one range so the reader reads one fact. A
     * missing link, missing dates, a deleted request or a failed lookup adds nothing.
     */
    private void addNeeded(FeedDetails details) {
        Integer requestId = details.linkId();
        if (requestId == null) return;
        try {
            var request = lendingService.findRequest(requestId).orElse(null);
            if (request == null) return;
            var from = request.requestedDateFrom();
            var to = request.requestedDateTo();
            if (from != null && to != null) {
                details.put(details.label("needed", "Needed"), details.dateRange(from, to));
            } else if (from != null) {
                details.put(details.label("needed", "Needed"), details.date(from));
            }
        } catch (Exception ignored) {
        }
    }
}
