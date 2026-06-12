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
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.events.entity.EventCategory;
import dev.chojo.ember.feature.events.entity.RegistrationStatus;
import dev.chojo.ember.feature.events.service.EventService;
import dev.chojo.ember.feature.feed.render.IcalEventRenderer;
import dev.chojo.ember.feature.feed.service.FeedTokenService;
import dev.chojo.ember.feature.lostandfound.service.LostAndFoundService;
import dev.chojo.ember.feature.mail.service.EmailService;
import dev.chojo.ember.feature.media.service.ImageCategory;
import dev.chojo.ember.feature.media.service.ImageService;
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
import net.fortuna.ical4j.model.property.ProdId;
import net.fortuna.ical4j.model.property.XProperty;
import net.fortuna.ical4j.model.property.immutable.ImmutableCalScale;
import net.fortuna.ical4j.model.property.immutable.ImmutableVersion;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
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
    private final AccountRepository accountRepository;
    private final IcalEventRenderer icalRenderer;
    private final LostAndFoundService lostAndFoundService;
    private final ImageService imageService;

    @Inject
    public UserFeedRoutes(
            FeedTokenService tokenService,
            EventService eventService,
            NotificationService notificationService,
            StationMemberRepository memberRepository,
            StationRepository stationRepository,
            EmailService emailService,
            AccountRepository accountRepository,
            IcalEventRenderer icalRenderer,
            LostAndFoundService lostAndFoundService,
            ImageService imageService) {
        this.tokenService = tokenService;
        this.eventService = eventService;
        this.notificationService = notificationService;
        this.memberRepository = memberRepository;
        this.stationRepository = stationRepository;
        this.emailService = emailService;
        this.accountRepository = accountRepository;
        this.icalRenderer = icalRenderer;
        this.lostAndFoundService = lostAndFoundService;
        this.imageService = imageService;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/public/feed/{token}/events.ics", this::icalFeed);
        routes.get(prefix + "/public/feed/{token}/notifications.rss", this::rssFeed);
        routes.get(prefix + "/public/feed/{token}/notifications.atom", this::atomFeed);
        routes.get(prefix + "/public/feed/{token}/lost-and-found/{id}/image", this::lostAndFoundImage);
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
        boolean verbose = !"0".equals(ctx.queryParam("verbose"));

        var categories = eventService.findCategoriesByStation(station.id());
        var categoryMap = new HashMap<Integer, EventCategory>();
        for (var cat : categories) categoryMap.put(cat.id(), cat);

        // Collect the owner's registrations and every managed member's registrations in one query.
        var managedMembers = memberRepository.findManaged(member.id());
        var memberIds = new ArrayList<Integer>(managedMembers.size() + 1);
        memberIds.add(member.id());
        for (var m : managedMembers) memberIds.add(m.id());

        var allRegistrations = eventService.findRegistrationsByMembers(memberIds);
        var ownerStatusByEvent = new HashMap<Integer, RegistrationStatus>();
        var ownerRegistered = new java.util.HashSet<Integer>();
        var managedByEvent = new HashMap<Integer, List<IcalEventRenderer.ManagedRegistration>>();
        var managedNameById = new HashMap<Integer, String>();
        for (var managed : managedMembers) {
            managedNameById.put(managed.id(), resolveMemberDisplayName(managed));
        }

        for (var reg : allRegistrations) {
            if (reg.memberId() == member.id()) {
                ownerStatusByEvent.put(reg.eventId(), reg.status());
                if (reg.status() == RegistrationStatus.ACCEPTED || reg.status() == RegistrationStatus.PENDING) {
                    ownerRegistered.add(reg.eventId());
                }
            } else {
                String name = managedNameById.getOrDefault(reg.memberId(), "Member #" + reg.memberId());
                managedByEvent
                        .computeIfAbsent(reg.eventId(), k -> new ArrayList<>())
                        .add(new IcalEventRenderer.ManagedRegistration(name, reg.status()));
            }
        }
        // Stable per-event order so the rendered description is deterministic.
        for (var list : managedByEvent.values()) {
            list.sort(java.util.Comparator.comparing(IcalEventRenderer.ManagedRegistration::memberName));
        }

        var renderCtx = new IcalEventRenderer.Context(
                station,
                locale,
                emailService.getBaseUrl(),
                verbose,
                categoryMap,
                ownerStatusByEvent,
                ownerRegistered,
                managedByEvent);

        var events = eventService.findByStation(station.id()).stream()
                .filter(e -> icalRenderer.isVisibleForFeed(e, renderCtx))
                .toList();

        var calendar = new Calendar();
        calendar.add(new ProdId("-//Ember//Personal Calendar//DE"));
        calendar.add(ImmutableVersion.VERSION_2_0);
        calendar.add(ImmutableCalScale.GREGORIAN);
        calendar.add(new XProperty("X-WR-CALNAME", station.name()));
        for (var event : events) {
            calendar.add(icalRenderer.render(event, renderCtx));
        }

        ctx.contentType("text/calendar; charset=utf-8");
        ctx.header("Cache-Control", "public, max-age=3600");
        ctx.result(calendar.toString());
    }

    // -- Token-scoped lost-and-found image (for feed reader embedding) --

    @OpenApi(
            path = "/api/v1/public/feed/{token}/lost-and-found/{id}/image",
            methods = HttpMethod.GET,
            summary = "Get a lost-and-found image scoped to a feed token",
            tags = {"User Feed"},
            pathParams = {
                @OpenApiParam(name = "token", type = String.class, required = true),
                @OpenApiParam(name = "id", type = Integer.class, required = true)
            },
            responses = {
                @OpenApiResponse(
                        status = "200",
                        description = "Image. Cache-Control: public, max-age=86400. Referrer-Policy: no-referrer."),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void lostAndFoundImage(Context ctx) {
        var member = resolveToken(ctx);
        int itemId = ctx.pathParamAsClass("id", Integer.class).get();

        // Cross-station items must look identical to missing items so token holders cannot probe
        // for the existence of foreign images.
        var item = lostAndFoundService.findById(itemId).orElseThrow(NotFoundResponse::new);
        if (item.stationId() != member.stationId()) {
            throw new NotFoundResponse();
        }

        int size = ctx.queryParamAsClass("size", Integer.class).getOrDefault(0);
        var image = imageService
                .read(ImageCategory.LOST_AND_FOUND, String.valueOf(itemId), size)
                .orElseThrow(NotFoundResponse::new);

        // Privacy: never let the token leak to image hosts via Referer (we serve it ourselves
        // today, but feed readers may proxy through other origins). noindex protects against
        // accidental indexing of leaked token URLs.
        ctx.contentType(image.contentType());
        ctx.header("Cache-Control", "public, max-age=86400");
        ctx.header("Referrer-Policy", "no-referrer");
        ctx.header("X-Robots-Tag", "noindex");
        ctx.result(image.data());
    }

    private String resolveMemberDisplayName(StationMember member) {
        if (member.displayName() != null && !member.displayName().isBlank()) {
            return member.displayName();
        }
        if (member.accountId() != null) {
            var account = accountRepository.findById(member.accountId()).orElse(null);
            if (account != null
                    && account.fullName() != null
                    && !account.fullName().isBlank()) {
                return account.fullName();
            }
        }
        return "Member #" + member.id();
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
