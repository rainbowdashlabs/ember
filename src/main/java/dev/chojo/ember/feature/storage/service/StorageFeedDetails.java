/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.service;

import dev.chojo.ember.feature.feed.render.FeedDetails;
import dev.chojo.ember.feature.feed.render.FeedDetailsContributor;
import dev.chojo.ember.feature.notifications.entity.Notification;
import dev.chojo.ember.feature.notifications.entity.NotificationParams;
import dev.chojo.ember.feature.storage.entity.StorageUsage;
import dev.chojo.ember.util.SizeParser;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * What a feed entry says about a station running out of storage: how full it is, and which
 * categories take the most.
 */
@Singleton
public class StorageFeedDetails implements FeedDetailsContributor {
    private static final int LARGEST_SHOWN = 5;

    private final StorageQuotaService storageQuotaService;

    @Inject
    public StorageFeedDetails(StorageQuotaService storageQuotaService) {
        this.storageQuotaService = storageQuotaService;
    }

    @Override
    public void contribute(NotificationParams params, Notification notification, FeedDetails details) {
        if (params
                instanceof
                NotificationParams.StorageWarning(int usedPercent, String usedFormatted, String quotaFormatted)) {
            details.put(details.label("usedPercent"), usedPercent + "%");
            details.putIfPresent(details.label("used"), usedFormatted);
            details.putIfPresent(details.label("quota"), quotaFormatted);
            addLargestCategories(details);
        }
    }

    /**
     * Adds the largest categories of the station the link names by {@code stationId}, biggest first,
     * as one bulleted row. Empty categories are left out, so a station that does not use a feature is
     * not told about it. A missing id, no usage or a failed lookup adds nothing.
     */
    private void addLargestCategories(FeedDetails details) {
        Integer stationId = details.linkParam("stationId");
        if (stationId == null) return;
        try {
            var top = storageQuotaService.getUsage(stationId).stream()
                    .filter(u -> u.totalBytes() > 0)
                    .sorted((a, b) -> Long.compare(b.totalBytes(), a.totalBytes()))
                    .limit(LARGEST_SHOWN)
                    .toList();
            if (top.isEmpty()) return;
            var sb = new StringBuilder();
            for (StorageUsage u : top) {
                if (!sb.isEmpty()) sb.append("\n");
                sb.append("• ")
                        .append(details.localized(
                                "storageCategory", u.category().name(), null))
                        .append(": ")
                        .append(SizeParser.formatBytes(u.totalBytes()));
            }
            details.put(details.label("largestCategories", "Largest categories"), sb.toString());
        } catch (Exception ignored) {
        }
    }
}
