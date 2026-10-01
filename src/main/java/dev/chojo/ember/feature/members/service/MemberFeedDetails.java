/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.service;

import dev.chojo.ember.feature.feed.render.FeedDetails;
import dev.chojo.ember.feature.feed.render.FeedDetailsContributor;
import dev.chojo.ember.feature.notifications.entity.Notification;
import dev.chojo.ember.feature.notifications.entity.NotificationParams;
import jakarta.inject.Singleton;

import java.time.LocalDate;

/**
 * What a feed entry says about members: an expiring profile value, a group somebody was added to,
 * and a profile field that changed.
 */
@Singleton
public class MemberFeedDetails implements FeedDetailsContributor {

    @Override
    public void contribute(NotificationParams params, Notification notification, FeedDetails details) {
        switch (params) {
            case NotificationParams.ExpiryReminder p -> {
                details.putIfPresent(details.label("field", "Field"), p.fieldName());
                details.putIfPresent(details.label("member", "Member"), p.memberName());
                LocalDate expiresOn = p.expiresOn();
                if (expiresOn != null) {
                    details.put(details.label("expiresOn", "Valid until"), details.date(expiresOn));
                }
                details.putIfPresent(details.label("members", "Members"), p.members());
            }
            case NotificationParams.MemberAddedToGroup(String groupName, String addedByName) -> {
                details.putIfPresent(details.label("group", "Group"), groupName);
                details.putIfPresent(details.label("by"), addedByName);
            }
            case NotificationParams.ProfileFieldChanged(String memberName, String fieldName) -> {
                details.putIfPresent(details.label("member", "Member"), memberName);
                details.putIfPresent(details.label("field", "Field"), fieldName);
            }
            default -> {}
        }
    }
}
