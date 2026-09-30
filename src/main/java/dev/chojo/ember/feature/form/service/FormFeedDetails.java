/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.form.service;

import dev.chojo.ember.feature.feed.render.FeedDetails;
import dev.chojo.ember.feature.feed.render.FeedDetailsContributor;
import dev.chojo.ember.feature.notifications.entity.Notification;
import dev.chojo.ember.feature.notifications.entity.NotificationParams;
import jakarta.inject.Singleton;

/**
 * What a feed entry says about a newly published form: its title.
 */
@Singleton
public class FormFeedDetails implements FeedDetailsContributor {

    @Override
    public void contribute(NotificationParams params, Notification notification, FeedDetails details) {
        if (params instanceof NotificationParams.NewForm(String title)) {
            details.putIfPresent(details.label("title", "Title"), title);
        }
    }
}
