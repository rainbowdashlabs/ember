/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.feed.service;

import dev.chojo.ember.api.refusal.FeedRefusal;
import dev.chojo.ember.feature.events.entity.EventCategory;
import dev.chojo.ember.feature.events.service.EventCategoryService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Checks the categories a feed is to leave out against the station's own categories, for the station
 * feeds and the personal feed alike.
 */
@Singleton
public class FeedCategoryFilter {
    private final EventCategoryService categoryService;

    @Inject
    public FeedCategoryFilter(EventCategoryService categoryService) {
        this.categoryService = categoryService;
    }

    /**
     * The categories to leave out, without duplicates and in id order, once every one of them is a
     * category of the station.
     *
     * @param stationId   the station of the feed
     * @param categoryIds the categories asked for
     * @return the checked ids
     * @throws dev.chojo.ember.api.refusal.RefusalResponse with {@link FeedRefusal#FEED_CATEGORY_NOT_HERE}
     *     when one of them is gone or belongs to another station
     */
    public List<Integer> checked(int stationId, List<Integer> categoryIds) {
        Set<Integer> own = categoryService.findByStation(stationId).stream()
                .map(EventCategory::id)
                .collect(Collectors.toSet());
        if (!own.containsAll(categoryIds)) throw FeedRefusal.FEED_CATEGORY_NOT_HERE.raise();
        return categoryIds.stream().distinct().sorted().toList();
    }
}
