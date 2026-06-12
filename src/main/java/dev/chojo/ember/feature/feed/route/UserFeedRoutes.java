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
import dev.chojo.ember.api.ErrorResponseWrapper;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.feature.events.entity.EventCategory;
import dev.chojo.ember.feature.events.entity.EventRegistration;
import dev.chojo.ember.feature.events.entity.RegistrationStatus;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.service.EventService;
import dev.chojo.ember.feature.feed.service.FeedTokenService;
import dev.chojo.ember.feature.mail.service.EmailService;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.notifications.entity.Notification;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.notifications.service.NotificationService;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.repository.StationRepository;
import io.javalin.http.Context;
import io.javalin.http.InternalServerErrorResponse;
import io.javalin.http.NotFoundResponse;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiParam;
import io.javalin.openapi.OpenApiResponse;
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
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@SuppressWarnings("DefaultAnnotationParam")
@Singleton
public class UserFeedRoutes implements Routes {
    private final FeedTokenService tokenService;
    private final EventService eventService;
    private final NotificationService notificationService;
    private final StationMemberRepository memberRepository;
    private final StationRepository stationRepository;
    private final EmailService emailService;

    @Inject
    public UserFeedRoutes(
            FeedTokenService tokenService,
            EventService eventService,
            NotificationService notificationService,
            StationMemberRepository memberRepository,
            StationRepository stationRepository,
            EmailService emailService) {
        this.tokenService = tokenService;
        this.eventService = eventService;
        this.notificationService = notificationService;
        this.memberRepository = memberRepository;
        this.stationRepository = stationRepository;
        this.emailService = emailService;
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

    @OpenApi(
            path = "/api/v1/public/feed/{token}/events.ics",
            methods = HttpMethod.GET,
            summary = "Get personal iCal event feed",
            tags = {"User Feed"},
            pathParams = @OpenApiParam(name = "token", type = String.class, required = true),
            responses = {
                @OpenApiResponse(
                        status = "200",
                        description = "iCal calendar. Cache-Control: public, max-age=3600",
                        content = @OpenApiContent(type = "text/calendar")),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void icalFeed(Context ctx) {
        var member = resolveToken(ctx);
        tokenService.recordIcalPoll(member.id());
        var station = stationRepository.findById(member.stationId()).orElseThrow(NotFoundResponse::new);
        String locale = notificationService.resolveLocale(station.locale());

        var categories = eventService.findCategoriesByStation(station.id());
        var categoryMap = new HashMap<Integer, EventCategory>();
        for (var cat : categories) categoryMap.put(cat.id(), cat);

        var registrations = eventService.findRegistrationsByMember(member.id());
        var declinedEventIds = registrations.stream()
                .filter(r -> r.status() == RegistrationStatus.DECLINED || r.status() == RegistrationStatus.DENIED)
                .map(EventRegistration::eventId)
                .collect(Collectors.toSet());
        var registeredEventIds = registrations.stream()
                .filter(r -> r.status() == RegistrationStatus.ACCEPTED || r.status() == RegistrationStatus.PENDING)
                .map(EventRegistration::eventId)
                .collect(Collectors.toSet());
        var memberStatusByEvent = new HashMap<Integer, RegistrationStatus>();
        for (var r : registrations) memberStatusByEvent.put(r.eventId(), r.status());

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
            calendar.add(buildVEvent(event, categoryMap, memberStatusByEvent, locale));
        }

        ctx.contentType("text/calendar; charset=utf-8");
        ctx.header("Cache-Control", "public, max-age=3600");
        ctx.result(calendar.toString());
    }

    private VEvent buildVEvent(
            StationEvent event,
            Map<Integer, EventCategory> categoryMap,
            Map<Integer, RegistrationStatus> memberStatusByEvent,
            String locale) {
        var start = event.startTime() != null ? event.startTime() : Instant.now();
        var end = event.endTime() != null ? event.endTime() : start;
        var vevent = new VEvent(start, end, event.name());
        vevent.add(new Uid("event-" + event.id() + "@ember"));

        if (event.categoryId() != null) {
            var cat = categoryMap.get(event.categoryId());
            if (cat != null) vevent.add(new Categories(cat.name()));
        }

        // Build a human-readable, localised description with event metadata so calendar clients
        // (which only show name + description in compact views) carry the same info as the web UI.
        String description = buildIcalDescription(event, categoryMap, memberStatusByEvent, locale);
        if (!description.isBlank()) vevent.add(new Description(description));

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

    private String buildIcalDescription(
            StationEvent event,
            Map<Integer, EventCategory> categoryMap,
            Map<Integer, RegistrationStatus> memberStatusByEvent,
            String locale) {
        var sb = new StringBuilder();

        // Free-text description first so it leads the body when present.
        if (event.description() != null && !event.description().isBlank()) {
            sb.append(event.description().trim()).append("\n\n");
        }

        if (event.categoryId() != null) {
            var cat = categoryMap.get(event.categoryId());
            if (cat != null) {
                appendIcalLine(sb, locale, "label.category", cat.name());
            }
        }

        String typeLabel = notificationService.resolveLocalized(
                locale, "ical", "eventType." + event.eventType().name(), null);
        appendIcalLine(sb, locale, "label.eventType", typeLabel);

        if (event.cancelled()) {
            String cancelled =
                    event.cancelReason() != null && !event.cancelReason().isBlank()
                            ? notificationService.resolveLocalized(
                                    locale, "ical", "cancelledWithReason", Map.of("reason", event.cancelReason()))
                            : notificationService.resolveLocalized(locale, "ical", "cancelled", null);
            sb.append(cancelled).append("\n");
        }

        if (event.requiresRegistration()) {
            sb.append(notificationService.resolveLocalized(locale, "ical", "registrationRequired", null))
                    .append("\n");
            if (event.registrationDeadline() != null) {
                appendIcalLine(sb, locale, "label.deadline", formatInstant(event.registrationDeadline(), locale));
            }
            if (event.registrationLimit() != null) {
                appendIcalLine(
                        sb, locale, "label.limit", event.registrationLimit().toString());
            }
            var status = memberStatusByEvent.get(event.id());
            String statusLabel = notificationService.resolveLocalized(
                    locale, "ical", "status." + (status != null ? status.name() : "NONE"), null);
            appendIcalLine(sb, locale, "label.status", statusLabel);
        }

        return sb.toString().stripTrailing();
    }

    private void appendIcalLine(StringBuilder sb, String locale, String labelKey, String value) {
        String label = notificationService.resolveLocalized(locale, "ical", labelKey, null);
        sb.append(label).append(": ").append(value).append("\n");
    }

    private String formatInstant(Instant instant, String locale) {
        var fmt = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
                .withLocale(Locale.forLanguageTag(locale))
                .withZone(ZoneId.systemDefault());
        return fmt.format(instant);
    }

    // -- RSS --

    @OpenApi(
            path = "/api/v1/public/feed/{token}/notifications.rss",
            methods = HttpMethod.GET,
            summary = "Get personal RSS notification feed",
            tags = {"User Feed"},
            pathParams = @OpenApiParam(name = "token", type = String.class, required = true),
            responses = {
                @OpenApiResponse(
                        status = "200",
                        description = "RSS feed. Cache-Control: public, max-age=3600",
                        content = @OpenApiContent(type = "application/rss+xml")),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void rssFeed(Context ctx) {
        var member = resolveToken(ctx);
        tokenService.recordNotificationPoll(member.id());
        var station = stationRepository.findById(member.stationId()).orElseThrow(NotFoundResponse::new);
        var notifications = getFeedNotifications(member);
        String locale = notificationService.resolveLocale(station.locale());
        String baseUrl = emailService.getBaseUrl();

        SyndFeed feed = new SyndFeedImpl();
        feed.setFeedType("rss_2.0");
        feed.setTitle(localizedFeedTitle(locale, station));
        feed.setDescription(notificationService.resolveLocalized(locale, "feed", "description", null));
        feed.setLanguage(locale);
        feed.setLink(baseUrl + "/station/dashboard/overview");
        feed.setEntries(buildSyndEntries(notifications, locale, baseUrl));

        outputFeed(ctx, feed, "application/rss+xml; charset=utf-8");
    }

    // -- Atom --

    @OpenApi(
            path = "/api/v1/public/feed/{token}/notifications.atom",
            methods = HttpMethod.GET,
            summary = "Get personal Atom notification feed",
            tags = {"User Feed"},
            pathParams = @OpenApiParam(name = "token", type = String.class, required = true),
            responses = {
                @OpenApiResponse(
                        status = "200",
                        description = "Atom feed. Cache-Control: public, max-age=3600",
                        content = @OpenApiContent(type = "application/atom+xml")),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void atomFeed(Context ctx) {
        var member = resolveToken(ctx);
        tokenService.recordNotificationPoll(member.id());
        var station = stationRepository.findById(member.stationId()).orElseThrow(NotFoundResponse::new);
        var notifications = getFeedNotifications(member);
        String locale = notificationService.resolveLocale(station.locale());
        String baseUrl = emailService.getBaseUrl();

        SyndFeed feed = new SyndFeedImpl();
        feed.setFeedType("atom_1.0");
        feed.setTitle(localizedFeedTitle(locale, station));
        feed.setDescription(notificationService.resolveLocalized(locale, "feed", "description", null));
        feed.setLanguage(locale);
        // Atom requires an alternate link, plus a stable self-identifying URI per-feed.
        feed.setLink(baseUrl + "/station/dashboard/overview");
        feed.setUri("urn:ember:notifications:" + member.id());
        feed.setEntries(buildSyndEntries(notifications, locale, baseUrl));

        outputFeed(ctx, feed, "application/atom+xml; charset=utf-8");
    }

    private String localizedFeedTitle(String locale, Station station) {
        return notificationService.resolveLocalized(locale, "feed", "title", Map.of("stationName", station.name()));
    }

    // -- Helpers --

    private List<SyndEntry> buildSyndEntries(List<Notification> notifications, String locale, String baseUrl) {
        var entries = new ArrayList<SyndEntry>();
        for (var n : notifications) {
            SyndEntry entry = new SyndEntryImpl();
            entry.setTitle(notificationService.resolveCategory(locale, n.type()));
            entry.setUri("urn:ember:notification:" + n.id());
            entry.setPublishedDate(Date.from(n.createdAt()));
            entry.setUpdatedDate(Date.from(n.createdAt()));

            // Deep-link the entry to the target entity when the notification carries link metadata.
            String link = notificationService.resolveNotificationUrl(baseUrl, n.data());
            if (link != null) entry.setLink(link);

            SyndContent content = new SyndContentImpl();
            content.setType("text/plain");
            content.setValue(notificationService.resolveFeedBody(locale, n));
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
}
