/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.feed.service;

import dev.chojo.ember.api.refusal.FeedRefusal;
import dev.chojo.ember.api.refusal.GeneralRefusal;
import dev.chojo.ember.feature.events.entity.EventCategory;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.service.EventCategoryService;
import dev.chojo.ember.feature.events.service.EventCrudService;
import dev.chojo.ember.feature.events.service.OccurrenceCalendar;
import dev.chojo.ember.feature.events.service.PublicCalendar;
import dev.chojo.ember.feature.feed.FeedFingerprint;
import dev.chojo.ember.feature.feed.entity.StationCalendarFeed;
import dev.chojo.ember.feature.feed.render.CalendarFeeds;
import dev.chojo.ember.feature.feed.render.IcalEventRenderer;
import dev.chojo.ember.feature.feed.service.PersonalFeedService.Feed;
import dev.chojo.ember.feature.feed.service.PersonalFeedService.Rendered;
import dev.chojo.ember.feature.mail.service.EmailService;
import dev.chojo.ember.feature.notifications.service.NotificationText;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.entity.StationModule;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.feature.station.service.ModuleAvailability;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The calendar behind a station calendar feed: every appointment of the station up to the feed's
 * level, within a year either way of now, without anybody's sign-up state.
 */
@Singleton
public class StationFeedCalendar {
    private static final Logger log = LoggerFactory.getLogger(StationFeedCalendar.class);

    private final StationCalendarFeedService feeds;
    private final EventCrudService crudService;
    private final EventCategoryService categoryService;
    private final StationRepository stationRepository;
    private final NotificationText notificationText;
    private final EmailService emailService;
    private final IcalEventRenderer icalRenderer;
    private final OccurrenceCalendar occurrenceCalendar;
    private final ModuleAvailability modules;

    @Inject
    public StationFeedCalendar(
            StationCalendarFeedService feeds,
            EventCrudService crudService,
            EventCategoryService categoryService,
            StationRepository stationRepository,
            NotificationText notificationText,
            EmailService emailService,
            IcalEventRenderer icalRenderer,
            OccurrenceCalendar occurrenceCalendar,
            ModuleAvailability modules) {
        this.feeds = feeds;
        this.crudService = crudService;
        this.categoryService = categoryService;
        this.stationRepository = stationRepository;
        this.notificationText = notificationText;
        this.emailService = emailService;
        this.icalRenderer = icalRenderer;
        this.occurrenceCalendar = occurrenceCalendar;
        this.modules = modules;
    }

    /**
     * The feed behind an address.
     *
     * @throws dev.chojo.ember.api.refusal.RefusalResponse with {@link FeedRefusal#FEED_LINK_NOT_GOOD} for an
     *     address that leads to no feed
     */
    public StationCalendarFeed feed(String token) {
        return feeds.findByToken(token).orElseThrow(FeedRefusal.FEED_LINK_NOT_GOOD::raise);
    }

    /**
     * The calendar of a station feed, answered from its fingerprint where the caller holds it already.
     *
     * <p>The fingerprint covers which appointments the feed carries and when any of them last changed,
     * the station's categories and everything the feed itself decides. So an appointment deleted or
     * moved out of reach, a category renamed or made public, and a feed renamed all reach the next
     * fetch.
     *
     * @param verbose whether descriptions carry everything rather than the essentials
     */
    public Feed calendar(StationCalendarFeed feed, boolean verbose) {
        if (StationModule.EVENTS.isOffIn(modules.disabledAt(feed.stationId()))) {
            throw GeneralRefusal.MODULE_SWITCHED_OFF.raise();
        }
        feeds.recordPoll(feed.id());
        var station =
                stationRepository.findById(feed.stationId()).orElseThrow(FeedRefusal.FEED_STATION_NOT_HERE::raise);
        String locale = notificationText.resolveLocale(station.locale());
        var categoryList = categoryService.findByStation(station.id());
        var categories = new HashMap<Integer, EventCategory>();
        categoryList.forEach(category -> categories.put(category.id(), category));
        var events = selected(feed, station.id(), categories);
        var fingerprint = FeedFingerprint.compute(
                crudService.findMaxEventUpdatedAt(station.id()),
                "station-ics",
                station.id(),
                feed.id(),
                feed.name(),
                feed.level(),
                feed.hiddenCategoryIds(),
                events.stream().map(StationEvent::id).toList(),
                categoryList,
                locale,
                verbose);
        return new Feed(fingerprint, () -> render(feed, station, categories, events, locale, verbose));
    }

    private Rendered render(
            StationCalendarFeed feed,
            Station station,
            Map<Integer, EventCategory> categories,
            List<StationEvent> events,
            String locale,
            boolean verbose) {
        var context = IcalEventRenderer.Context.withoutMember(
                station,
                locale,
                emailService.getBaseUrl(),
                verbose,
                categories,
                occurrenceCalendar.forStation(station.id()),
                IcalEventRenderer.Reader.STATION);
        var calendar = CalendarFeeds.frame(
                "Station Calendar",
                station.name() + " - " + feed.name(),
                context.calendar().zone());
        for (var event : events) {
            try {
                icalRenderer.render(event, context).forEach(calendar::add);
            } catch (Exception e) {
                log.warn("Failed to render event {} for station calendar feed {}", event.id(), feed.id(), e);
            }
        }
        return new Rendered(calendar.toString(), events.size());
    }

    /** The appointments a feed shows: those its level reaches, within the window every calendar feed carries. */
    private List<StationEvent> selected(
            StationCalendarFeed feed, int stationId, Map<Integer, EventCategory> categories) {
        var now = Instant.now();
        return crudService.findByStation(stationId).stream()
                .filter(event -> reaches(feed, event, categories))
                .filter(event -> CalendarFeeds.inWindow(event, now))
                .toList();
    }

    private static boolean reaches(
            StationCalendarFeed feed, StationEvent event, Map<Integer, EventCategory> categories) {
        return switch (feed.level()) {
            case PUBLIC -> PublicCalendar.shows(event, categories);
            case MEMBERS -> !event.restricted();
            case ALL -> true;
        };
    }
}
