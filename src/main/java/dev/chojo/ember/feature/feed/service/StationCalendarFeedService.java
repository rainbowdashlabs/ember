/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.feed.service;

import dev.chojo.ember.api.refusal.FeedRefusal;
import dev.chojo.ember.feature.feed.entity.StationCalendarFeed;
import dev.chojo.ember.feature.feed.entity.StationFeedLevel;
import dev.chojo.ember.feature.feed.repository.StationCalendarFeedRepository;
import dev.chojo.ember.util.RandomTokens;
import dev.chojo.ember.util.sql.Transactions;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

/**
 * Manages the calendar feeds of a station: subscriptions for shared calendars that belong to no member.
 */
@Singleton
public class StationCalendarFeedService {
    /** The longest name a feed may carry. */
    public static final int MAX_NAME_LENGTH = 100;

    private static final Logger log = LoggerFactory.getLogger(StationCalendarFeedService.class);

    private final StationCalendarFeedRepository repository;
    private final FeedCategoryFilter categoryFilter;

    @Inject
    public StationCalendarFeedService(StationCalendarFeedRepository repository, FeedCategoryFilter categoryFilter) {
        this.repository = repository;
        this.categoryFilter = categoryFilter;
    }

    /**
     * Every calendar feed of a station, oldest first.
     */
    public List<StationCalendarFeed> list(int stationId) {
        return repository.findByStation(stationId);
    }

    /**
     * The calendar feed behind a feed address, for serving it.
     */
    public Optional<StationCalendarFeed> findByToken(String token) {
        return repository.findByToken(token);
    }

    /**
     * Creates a calendar feed with a fresh address.
     *
     * @param stationId           the station
     * @param name                the feed's name
     * @param level               up to which visibility it shows appointments
     * @param hiddenCategoryIds   the categories it leaves out
     * @param createdBy           the member creating it
     * @return the new feed
     */
    public StationCalendarFeed create(
            int stationId,
            @Nullable String name,
            @Nullable StationFeedLevel level,
            List<Integer> hiddenCategoryIds,
            int createdBy) {
        String checkedName = checkedName(name);
        StationFeedLevel checkedLevel = checkedLevel(level);
        List<Integer> hidden = categoryFilter.checked(stationId, hiddenCategoryIds);
        int id = Transactions.call(() -> {
            int created = repository.create(stationId, checkedName, checkedLevel, RandomTokens.urlSafe(32), createdBy);
            repository.replaceHiddenCategories(created, stationId, hidden);
            return created;
        });
        log.info("Created station calendar feed {} at station {}", id, stationId);
        return get(id, stationId);
    }

    /**
     * Changes a calendar feed's name, level and the categories it leaves out. The address stays.
     *
     * @return the feed as it is now
     */
    public StationCalendarFeed update(
            int id,
            int stationId,
            @Nullable String name,
            @Nullable StationFeedLevel level,
            List<Integer> hiddenCategoryIds) {
        get(id, stationId);
        String checkedName = checkedName(name);
        StationFeedLevel checkedLevel = checkedLevel(level);
        List<Integer> hidden = categoryFilter.checked(stationId, hiddenCategoryIds);
        Transactions.run(() -> {
            if (!repository.update(id, stationId, checkedName, checkedLevel)) {
                throw FeedRefusal.FEED_STATION_FEED_NOT_HERE.raise();
            }
            repository.replaceHiddenCategories(id, stationId, hidden);
        });
        return get(id, stationId);
    }

    /**
     * Gives a calendar feed a new address. Calendars holding the old one stop receiving it.
     *
     * @return the feed with its new token
     */
    public StationCalendarFeed regenerate(int id, int stationId) {
        if (!repository.replaceToken(id, stationId, RandomTokens.urlSafe(32))) {
            throw FeedRefusal.FEED_STATION_FEED_NOT_HERE.raise();
        }
        log.info("Renewed the address of station calendar feed {} at station {}", id, stationId);
        return get(id, stationId);
    }

    /**
     * Deletes a calendar feed, which ends its address.
     */
    public void delete(int id, int stationId) {
        if (!repository.delete(id, stationId)) throw FeedRefusal.FEED_STATION_FEED_NOT_HERE.raise();
        log.info("Deleted station calendar feed {} at station {}", id, stationId);
    }

    /**
     * Records that a calendar fetched the feed.
     */
    public void recordPoll(int id) {
        repository.recordPoll(id);
    }

    private StationCalendarFeed get(int id, int stationId) {
        return repository.findInStation(id, stationId).orElseThrow(FeedRefusal.FEED_STATION_FEED_NOT_HERE::raise);
    }

    private static String checkedName(@Nullable String name) {
        String trimmed = name == null ? "" : name.strip();
        if (trimmed.isEmpty()) throw FeedRefusal.FEED_STATION_FEED_NEEDS_A_NAME.raise();
        if (trimmed.length() > MAX_NAME_LENGTH) throw FeedRefusal.FEED_STATION_FEED_NAME_TOO_LONG.raise();
        return trimmed;
    }

    private static StationFeedLevel checkedLevel(@Nullable StationFeedLevel level) {
        if (level == null) throw FeedRefusal.FEED_STATION_FEED_NEEDS_A_LEVEL.raise();
        return level;
    }
}
