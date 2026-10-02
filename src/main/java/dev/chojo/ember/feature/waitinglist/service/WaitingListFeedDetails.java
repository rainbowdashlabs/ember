/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.waitinglist.service;

import dev.chojo.ember.feature.feed.render.FeedDetails;
import dev.chojo.ember.feature.feed.render.FeedDetailsContributor;
import dev.chojo.ember.feature.notifications.entity.Notification;
import dev.chojo.ember.feature.notifications.entity.NotificationParams;
import jakarta.inject.Singleton;

/**
 * What a feed entry says about a new entry on a waiting list: the child and the list.
 */
@Singleton
public class WaitingListFeedDetails implements FeedDetailsContributor {

    @Override
    public void contribute(NotificationParams params, Notification notification, FeedDetails details) {
        switch (params) {
            case NotificationParams.WaitlistNewEntry(String childName, String listName) ->
                addChildAndList(details, childName, listName);
            case NotificationParams.WaitlistPublicRegistration(String childName, String listName) ->
                addChildAndList(details, childName, listName);
            default -> {}
        }
    }

    private static void addChildAndList(FeedDetails details, String childName, String listName) {
        details.putIfPresent(details.label("child", "Child"), childName);
        details.putIfPresent(details.label("list", "List"), listName);
    }
}
