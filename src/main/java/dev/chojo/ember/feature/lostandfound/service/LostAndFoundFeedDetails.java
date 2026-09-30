/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.lostandfound.service;

import dev.chojo.ember.feature.feed.render.FeedDetails;
import dev.chojo.ember.feature.feed.render.FeedDetailsContributor;
import dev.chojo.ember.feature.feed.render.FeedImage;
import dev.chojo.ember.feature.notifications.entity.Notification;
import dev.chojo.ember.feature.notifications.entity.NotificationParams;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * What a feed entry says about a found item: its description, when it was found and, once claimed,
 * when that was, together with its picture.
 */
@Singleton
public class LostAndFoundFeedDetails implements FeedDetailsContributor {
    private final LostAndFoundService lostAndFoundService;

    @Inject
    public LostAndFoundFeedDetails(LostAndFoundService lostAndFoundService) {
        this.lostAndFoundService = lostAndFoundService;
    }

    @Override
    public void contribute(NotificationParams params, Notification notification, FeedDetails details) {
        switch (params) {
            case NotificationParams.LostAndFoundNew(String description) -> {
                details.putSnippetIfPresent(details.label("description", "Description"), description);
                addDates(details, false);
            }
            case NotificationParams.LostAndFoundClaimed(String name, String description) -> {
                details.putIfPresent(details.label("by"), name);
                details.putSnippetIfPresent(details.label("description", "Description"), description);
                addDates(details, true);
            }
            default -> {}
        }
    }

    /**
     * The item's picture, served below the feed token so a reader fetches it without signing in, and
     * described by the item's own description.
     */
    @Override
    public FeedImage image(NotificationParams params, Notification notification) {
        String alt;
        switch (params) {
            case NotificationParams.LostAndFoundNew(String description) -> alt = description;
            case NotificationParams.LostAndFoundClaimed p -> alt = p.description();
            default -> {
                return null;
            }
        }
        var link = notification.data().link();
        if (link == null || link.routeParams() == null) return null;
        Object id = link.routeParams().get("id");
        if (id == null) return null;
        return new FeedImage("lost-and-found/" + id + "/image", alt);
    }

    /**
     * Adds when the linked item was found and, for a claim, when it was claimed. A missing link, a
     * deleted item or a failed lookup adds nothing.
     */
    private void addDates(FeedDetails details, boolean includeClaim) {
        Integer itemId = details.linkId();
        if (itemId == null) return;
        try {
            var item = lostAndFoundService.findById(itemId).orElse(null);
            if (item == null) return;
            if (item.foundAt() != null) {
                details.put(details.label("foundOn", "Found on"), details.date(item.foundAt()));
            }
            if (includeClaim && item.claimedAt() != null) {
                details.put(
                        details.label("claimedOn", "Claimed on"),
                        details.moment(item.claimedAt(), details.zoneOf(item.stationId())));
            }
        } catch (Exception ignored) {
        }
    }
}
