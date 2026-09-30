/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.feed.render;

import dev.chojo.ember.feature.notifications.entity.Notification;
import dev.chojo.ember.feature.notifications.entity.NotificationParams;

/**
 * What a feature adds to the feed entries of its own notifications.
 *
 * <p>The feed renderer writes the parts every entry has: title, link, categories, summary and the
 * frame of the body. What a notification is about, and what can be looked up to say more about it,
 * only its feature knows, so each feature contributes that here. Every contributor is asked about
 * every notification and answers only for its own parameter records, which is why each ignores the
 * rest rather than refusing them.
 */
public interface FeedDetailsContributor {

    /**
     * Adds the detail rows of a notification of this feature, and nothing for any other.
     *
     * <p>Whatever is looked up to enrich the rows must never fail the feed: a thing deleted since
     * the notification was written simply adds no row.
     */
    void contribute(NotificationParams params, Notification notification, FeedDetails details);

    /** The person who acted, which readers show as the entry's author, or null where there is none. */
    default String author(NotificationParams params) {
        return null;
    }

    /** The picture the entry shows, or null where it has none. */
    default FeedImage image(NotificationParams params, Notification notification) {
        return null;
    }
}
