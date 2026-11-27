/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.feed.service;

import dev.chojo.ember.feature.feed.repository.PersonalFeedFilterRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;

/**
 * The categories a member's personal calendar feed leaves out. The choice belongs to the member, not
 * to the address, so renewing the address keeps it.
 */
@Singleton
public class PersonalFeedFilterService {
    private final PersonalFeedFilterRepository repository;
    private final FeedCategoryFilter categoryFilter;

    @Inject
    public PersonalFeedFilterService(PersonalFeedFilterRepository repository, FeedCategoryFilter categoryFilter) {
        this.repository = repository;
        this.categoryFilter = categoryFilter;
    }

    /**
     * The categories the member's personal feed leaves out, in id order.
     */
    public List<Integer> hiddenCategories(int memberId) {
        return repository.findHiddenCategories(memberId);
    }

    /**
     * Sets the categories the member's personal feed leaves out.
     *
     * @param memberId    the member
     * @param stationId   the member's station, whose categories alone may be chosen
     * @param categoryIds the categories to leave out from now on
     * @return the categories it leaves out now, in id order
     */
    public List<Integer> setHiddenCategories(int memberId, int stationId, List<Integer> categoryIds) {
        List<Integer> checked = categoryFilter.checked(stationId, categoryIds);
        repository.replaceHiddenCategories(memberId, checked);
        return checked;
    }
}
