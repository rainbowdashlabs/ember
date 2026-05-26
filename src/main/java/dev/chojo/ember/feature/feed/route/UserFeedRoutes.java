/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.feed.route;

import com.rometools.rome.feed.synd.SyndContent;
import com.rometools.rome.feed.synd.SyndContentImpl;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndEntryImpl;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.feed.synd.SyndFeedImpl;
import com.rometools.rome.io.SyndFeedOutput;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.feature.events.entity.EventCategory;
import dev.chojo.ember.feature.events.entity.EventRegistration;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.service.EventService;
import dev.chojo.ember.feature.feed.service.FeedTokenService;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.notifications.entity.Notification;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.notifications.service.NotificationService;
import dev.chojo.ember.feature.station.repository.StationRepository;
import io.javalin.http.Context;
import io.javalin.http.InternalServerErrorResponse;
import io.javalin.http.NotFoundResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.component.VEvent;
import net.fortuna.ical4j.model.property.Categories;
import net.fortuna.ical4j.model.property.Description;
import net.fortuna.ical4j.model.property.ProdId;
import net.fortuna.ical4j.model.property.RRule;
import net.fortuna.ical4j.model.property.Uid;
import net.fortuna.ical4j.model.property.XProperty;
import net.fortuna.ical4j.model.property.immutable.ImmutableCalScale;
import net.fortuna.ical4j.model.property.immutable.ImmutableVersion;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Singleton
public class UserFeedRoutes implements Routes {
    private final FeedTokenService tokenService;
    private final EventService eventService;
    private final NotificationService notificationService;
    private final StationMemberRepository memberRepository;
    private final StationRepository stationRepository;

    @Inject
    public UserFeedRoutes(
            FeedTokenService tokenService,
            EventService eventService,
            NotificationService notificationService,
            StationMemberRepository memberRepository,
            StationRepository stationRepository) {
        this.tokenService = tokenService;
        this.eventService = eventService;
        this.notificationService = notificationService;
        this.memberRepository = memberRepository;
        this.stationRepository = stationRepository;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/public/feed/{token}/events.ics", this::icalFeed);
        routes.get(prefix + "/public/feed/{token}/notifications.rss", this::rssFeed);
        routes.get(prefix + "/public/feed/{token}/notifications.atom", this::atomFeed);
    }

    private StationMember resolveToken(Context ctx) {
        String token = ctx.pathParam("token");
        var feedToken = tokenService.findByToken(token).orElseThrow(NotFoundResponse::new);
        return memberRepository.findById(feedToken.memberId()).orElseThrow(NotFoundResponse::new);
    }

    private int resolveMemberId(Context ctx) {
        String token = ctx.pathParam("token");
        return tokenService
                .findByToken(token)
                .orElseThrow(NotFoundResponse::new)
                .memberId();
    }

    // -- iCal --

    private void icalFeed(Context ctx) {
        var member = resolveToken(ctx);
        tokenService.recordIcalPoll(member.id());
        var station = stationRepository.findById(member.stationId()).orElseThrow(NotFoundResponse::new);

        var categories = eventService.findCategoriesByStation(station.id());
        var categoryMap = new HashMap<Integer, EventCategory>();
        for (var cat : categories) categoryMap.put(cat.id(), cat);

        var registrations = eventService.findRegistrationsByMember(member.id());
        var declinedEventIds = registrations.stream()
                .filter(r -> r.status() == EventRegistration.RegistrationStatus.DECLINED
                        || r.status() == EventRegistration.RegistrationStatus.DENIED)
                .map(EventRegistration::eventId)
                .collect(Collectors.toSet());
        var registeredEventIds = registrations.stream()
                .filter(r -> r.status() == EventRegistration.RegistrationStatus.ACCEPTED
                        || r.status() == EventRegistration.RegistrationStatus.PENDING)
                .map(EventRegistration::eventId)
                .collect(Collectors.toSet());

        var now = Instant.now();
        var events = eventService.findByStation(station.id()).stream()
                .filter(e -> !declinedEventIds.contains(e.id()))
                .filter(e -> !(e.requiresRegistration()
                        && e.registrationDeadline() != null
                        && e.registrationDeadline().isBefore(now)
                        && !registeredEventIds.contains(e.id())))
                .toList();

        var calendar = new Calendar();
        calendar.add(new ProdId("-//Ember//Personal Calendar//DE"));
        calendar.add(ImmutableVersion.VERSION_2_0);
        calendar.add(ImmutableCalScale.GREGORIAN);
        calendar.add(new XProperty("X-WR-CALNAME", station.name()));
        for (var event : events) {
            calendar.add(buildVEvent(event, categoryMap));
        }

        ctx.contentType("text/calendar; charset=utf-8");
        ctx.header("Cache-Control", "public, max-age=3600");
        ctx.result(calendar.toString());
    }

    private VEvent buildVEvent(StationEvent event, Map<Integer, EventCategory> categoryMap) {
        var start = event.startTime() != null ? event.startTime() : Instant.now();
        var end = event.endTime() != null ? event.endTime() : start;
        var vevent = new VEvent(start, end, event.name());
        vevent.add(new Uid("event-" + event.id() + "@ember"));
        if (event.description() != null && !event.description().isBlank()) {
            vevent.add(new Description(event.description()));
        }
        if (event.categoryId() != null) {
            var cat = categoryMap.get(event.categoryId());
            if (cat != null) vevent.add(new Categories(cat.name()));
        }
        if (event.isRecurring() && event.dayOfWeek() != null) {
            String[] days = {"", "MO", "TU", "WE", "TH", "FR", "SA", "SU"};
            String day = days[event.dayOfWeek()];
            String rrule =
                    switch (event.eventType()) {
                        case RECURRING -> "FREQ=WEEKLY;BYDAY=" + day;
                        case MONTHLY_FIRST -> "FREQ=MONTHLY;BYDAY=1" + day;
                        case QUARTERLY -> "FREQ=MONTHLY;INTERVAL=3;BYDAY=1" + day;
                        case YEARLY -> "FREQ=YEARLY";
                        default -> null;
                    };
            if (rrule != null) vevent.add(new RRule<>(rrule));
        }
        return vevent;
    }

    // -- RSS --

    private void rssFeed(Context ctx) {
        var member = resolveToken(ctx);
        tokenService.recordNotificationPoll(member.id());
        var station = stationRepository.findById(member.stationId()).orElseThrow(NotFoundResponse::new);
        var notifications = getFeedNotifications(member);

        SyndFeed feed = new SyndFeedImpl();
        feed.setFeedType("rss_2.0");
        feed.setTitle(station.name() + " — Benachrichtigungen");
        feed.setDescription("Ember Benachrichtigungen");
        feed.setLanguage("de");
        feed.setLink("urn:ember:station:" + station.uid());
        feed.setEntries(buildSyndEntries(notifications));

        outputFeed(ctx, feed, "application/rss+xml; charset=utf-8");
    }

    // -- Atom --

    private void atomFeed(Context ctx) {
        var member = resolveToken(ctx);
        tokenService.recordNotificationPoll(member.id());
        var station = stationRepository.findById(member.stationId()).orElseThrow(NotFoundResponse::new);
        var notifications = getFeedNotifications(member);

        SyndFeed feed = new SyndFeedImpl();
        feed.setFeedType("atom_1.0");
        feed.setTitle(station.name() + " — Benachrichtigungen");
        feed.setUri("urn:ember:notifications:" + member.id());
        feed.setEntries(buildSyndEntries(notifications));

        outputFeed(ctx, feed, "application/atom+xml; charset=utf-8");
    }

    // -- Helpers --

    private List<SyndEntry> buildSyndEntries(List<Notification> notifications) {
        var entries = new ArrayList<SyndEntry>();
        for (var n : notifications) {
            SyndEntry entry = new SyndEntryImpl();
            entry.setTitle(n.type().localeKey());
            entry.setUri("urn:ember:notification:" + n.id());
            entry.setPublishedDate(Date.from(n.createdAt()));
            entry.setUpdatedDate(Date.from(n.createdAt()));
            SyndContent content = new SyndContentImpl();
            content.setType("text/plain");
            content.setValue(feedDescription(n));
            entry.setDescription(content);
            entries.add(entry);
        }
        return entries;
    }

    private void outputFeed(Context ctx, SyndFeed feed, String contentType) {
        try {
            var output = new SyndFeedOutput();
            ctx.contentType(contentType);
            ctx.header("Cache-Control", "public, max-age=3600");
            ctx.result(output.outputString(feed));
        } catch (Exception e) {
            throw new InternalServerErrorResponse("Failed to generate feed");
        }
    }

    private List<Notification> getFeedNotifications(StationMember member) {
        var settings = notificationService.getNotificationSettings(member.id());
        var enabledTypes = Set.of(NotificationType.values()).stream()
                .filter(t -> {
                    var s = settings.get(t);
                    return s == null || s.feedEnabled();
                })
                .collect(Collectors.toSet());

        return notificationService.findAll(member.id()).stream()
                .filter(n -> enabledTypes.contains(n.type()))
                .toList();
    }

    private String feedDescription(Notification n) {
        var params = n.data().paramsAsMap();
        return params.values().stream().filter(v -> v != null && !v.isBlank()).collect(Collectors.joining(" — "));
    }
}
