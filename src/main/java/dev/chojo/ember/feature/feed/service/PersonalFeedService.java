/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.feed.service;

import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.feed.synd.SyndFeedImpl;
import com.rometools.rome.io.SyndFeedOutput;
import dev.chojo.ember.api.refusal.FeedRefusal;
import dev.chojo.ember.feature.events.entity.EventCategory;
import dev.chojo.ember.feature.events.entity.RegistrationStatus;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.service.EventCategoryService;
import dev.chojo.ember.feature.events.service.EventCrudService;
import dev.chojo.ember.feature.events.service.EventRegistrationService;
import dev.chojo.ember.feature.events.service.OccurrenceCalendar;
import dev.chojo.ember.feature.feed.FeedFingerprint;
import dev.chojo.ember.feature.feed.render.IcalEventRenderer;
import dev.chojo.ember.feature.feed.render.NotificationFeedRenderer;
import dev.chojo.ember.feature.lostandfound.service.LostAndFoundImageService;
import dev.chojo.ember.feature.lostandfound.service.LostAndFoundService;
import dev.chojo.ember.feature.mail.service.EmailService;
import dev.chojo.ember.feature.media.entity.MediaContent;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.members.service.MemberNameResolver;
import dev.chojo.ember.feature.notifications.entity.Notification;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.notifications.entity.Recipient;
import dev.chojo.ember.feature.notifications.service.NotificationInbox;
import dev.chojo.ember.feature.notifications.service.NotificationPreferences;
import dev.chojo.ember.feature.notifications.service.NotificationText;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.repository.StationRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.property.ProdId;
import net.fortuna.ical4j.model.property.XProperty;
import net.fortuna.ical4j.model.property.immutable.ImmutableCalScale;
import net.fortuna.ical4j.model.property.immutable.ImmutableVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * The feeds a member subscribes to with their personal feed link: the household's calendar and
 * the notifications, each fingerprinted first so an unchanged feed is not rendered again.
 *
 * <p>A feed link names one member and everything served through it is that member's, or that of the
 * members they answer for, in their own station.
 */
@Singleton
public class PersonalFeedService {
    private static final Logger log = LoggerFactory.getLogger(PersonalFeedService.class);

    /**
     * Past calendar window: a year back, the same distance the feed reaches forward. A calendar is
     * as much a record of what happened as a plan of what is coming.
     */
    private static final Duration ICAL_WINDOW_PAST = Duration.ofDays(365);

    /** Forward calendar window: covers annual events without unbounded growth on long-running stations. */
    private static final Duration ICAL_WINDOW_FUTURE = Duration.ofDays(365);

    /**
     * Maximum number of notification entries per feed, which is what readers typically surface and
     * keeps the payload bounded for noisy stations.
     */
    private static final int NOTIFICATION_FEED_CAP = 100;

    private final FeedTokenService tokenService;
    private final EventCrudService crudService;
    private final EventCategoryService categoryService;
    private final EventRegistrationService registrationService;
    private final NotificationText notificationText;
    private final NotificationInbox notificationInbox;
    private final NotificationPreferences notificationPreferences;
    private final StationMemberRepository memberRepository;
    private final StationRepository stationRepository;
    private final EmailService emailService;
    private final IcalEventRenderer icalRenderer;
    private final LostAndFoundService lostAndFoundService;
    private final LostAndFoundImageService imageService;
    private final NotificationFeedRenderer notificationRenderer;
    private final MemberNameResolver memberNameResolver;
    private final OccurrenceCalendar occurrenceCalendar;

    @Inject
    public PersonalFeedService(
            FeedTokenService tokenService,
            EventCrudService crudService,
            EventCategoryService categoryService,
            EventRegistrationService registrationService,
            NotificationText notificationText,
            NotificationInbox notificationInbox,
            NotificationPreferences notificationPreferences,
            StationMemberRepository memberRepository,
            StationRepository stationRepository,
            EmailService emailService,
            IcalEventRenderer icalRenderer,
            LostAndFoundService lostAndFoundService,
            LostAndFoundImageService imageService,
            NotificationFeedRenderer notificationRenderer,
            MemberNameResolver memberNameResolver,
            OccurrenceCalendar occurrenceCalendar) {
        this.tokenService = tokenService;
        this.crudService = crudService;
        this.categoryService = categoryService;
        this.registrationService = registrationService;
        this.notificationText = notificationText;
        this.notificationInbox = notificationInbox;
        this.notificationPreferences = notificationPreferences;
        this.memberRepository = memberRepository;
        this.stationRepository = stationRepository;
        this.emailService = emailService;
        this.icalRenderer = icalRenderer;
        this.lostAndFoundService = lostAndFoundService;
        this.imageService = imageService;
        this.notificationRenderer = notificationRenderer;
        this.memberNameResolver = memberNameResolver;
        this.occurrenceCalendar = occurrenceCalendar;
    }

    /**
     * The member a feed link belongs to.
     */
    public StationMember member(String token) {
        var feedToken = tokenService.findByToken(token).orElseThrow(FeedRefusal.FEED_LINK_NOT_GOOD::raise);
        return memberRepository.findById(feedToken.memberId()).orElseThrow(FeedRefusal.FEED_LINK_NOT_GOOD::raise);
    }

    /**
     * The household's calendar: the member's and those of everyone they answer for, restricted to
     * the appointments the household may know about. A subscribed calendar is a copy of the
     * station's appointments leaving the application, so one hidden from these members must not be
     * in it.
     *
     * <p>The fingerprint covers event changes and registration changes across the household, which
     * is what a conditional request is answered by.
     *
     * @param verbose whether descriptions carry everything rather than the essentials
     */
    public Feed calendar(StationMember member, boolean verbose) {
        tokenService.recordIcalPoll(member.id());
        var station =
                stationRepository.findById(member.stationId()).orElseThrow(FeedRefusal.FEED_STATION_NOT_HERE::raise);
        String locale = notificationText.resolveLocale(station.locale());
        var managed = memberRepository.findManaged(member.id());
        var memberIds = new ArrayList<Integer>(managed.size() + 1);
        memberIds.add(member.id());
        managed.forEach(m -> memberIds.add(m.id()));
        var eventLatest = crudService.findMaxEventUpdatedAt(station.id());
        var registrationLatest = registrationService.findMaxCreatedAt(memberIds);
        var lastModified = eventLatest.isAfter(registrationLatest) ? eventLatest : registrationLatest;
        var fingerprint = FeedFingerprint.compute(lastModified, "ics", station.id(), locale, verbose);
        return new Feed(fingerprint, () -> renderCalendar(member, managed, memberIds, station, locale, verbose));
    }

    private Rendered renderCalendar(
            StationMember member,
            List<StationMember> managed,
            List<Integer> memberIds,
            Station station,
            String locale,
            boolean verbose) {
        var categories = new HashMap<Integer, EventCategory>();
        categoryService.findByStation(station.id()).forEach(category -> categories.put(category.id(), category));
        var ownerStatusByEvent = new HashMap<Integer, RegistrationStatus>();
        var managedByEvent = managedRegistrations(member, managed, memberIds, ownerStatusByEvent);
        var renderContext = new IcalEventRenderer.Context(
                station,
                locale,
                emailService.getBaseUrl(),
                verbose,
                categories,
                ownerStatusByEvent,
                managedByEvent,
                occurrenceCalendar.forStation(station.id()));
        var events = eventsInWindow(station.id(), memberIds).stream()
                .filter(event -> icalRenderer.isVisibleForFeed(event, renderContext))
                .toList();
        var calendar = new Calendar();
        calendar.add(new ProdId("-//Ember//Personal Calendar//DE"));
        calendar.add(ImmutableVersion.VERSION_2_0);
        calendar.add(ImmutableCalScale.GREGORIAN);
        calendar.add(new XProperty("X-WR-CALNAME", station.name()));
        for (var event : events) {
            try {
                icalRenderer.render(event, renderContext).forEach(calendar::add);
            } catch (Exception e) {
                log.warn("Failed to render event {} for ical feed", event.id(), e);
            }
        }
        return new Rendered(calendar.toString(), events.size());
    }

    /**
     * The registrations of the household, the member's own by appointment into the given map and
     * those of the members they answer for, by name and in a stable order, as the result.
     */
    private Map<Integer, List<IcalEventRenderer.ManagedRegistration>> managedRegistrations(
            StationMember member,
            List<StationMember> managed,
            List<Integer> memberIds,
            Map<Integer, RegistrationStatus> ownerStatusByEvent) {
        var nameById = new HashMap<Integer, String>();
        managed.forEach(m -> nameById.put(m.id(), displayName(m.id())));
        var managedByEvent = new HashMap<Integer, List<IcalEventRenderer.ManagedRegistration>>();
        for (var registration : registrationService.findByMembers(memberIds)) {
            if (registration.memberId() == member.id()) {
                ownerStatusByEvent.put(registration.eventId(), registration.status());
            } else {
                managedByEvent
                        .computeIfAbsent(registration.eventId(), _ -> new ArrayList<>())
                        .add(new IcalEventRenderer.ManagedRegistration(
                                nameById.getOrDefault(registration.memberId(), "Member #" + registration.memberId()),
                                registration.status()));
            }
        }
        managedByEvent
                .values()
                .forEach(list -> list.sort(Comparator.comparing(IcalEventRenderer.ManagedRegistration::memberName)));
        return managedByEvent;
    }

    /**
     * The household's appointments within a year either way of now, so feeds stay small for
     * stations with thousands of past entries. A series is kept by its anchor: its rule expands
     * across the window in the client.
     */
    private List<StationEvent> eventsInWindow(int stationId, List<Integer> memberIds) {
        var now = Instant.now();
        var windowStart = now.minus(ICAL_WINDOW_PAST);
        var windowEnd = now.plus(ICAL_WINDOW_FUTURE);
        return crudService.findFilteredForMembers(stationId, memberIds, null, null).stream()
                .filter(e -> e.isRecurring()
                        || (e.startTime() != null
                                && !e.startTime().isBefore(windowStart)
                                && !e.startTime().isAfter(windowEnd)))
                .toList();
    }

    private String displayName(int memberId) {
        String name = memberNameResolver.called(memberId);
        return name == null || name.isBlank() ? "Member #" + memberId : name;
    }

    /**
     * The member's notifications as a syndication feed, of the kinds they chose to have in it.
     *
     * @param token   the feed link, which the entries' pictures are served through
     * @param format  RSS or Atom
     * @param verbose whether entries carry everything rather than the essentials
     * @param images  whether entries carry pictures
     */
    public Feed notifications(StationMember member, String token, FeedFormat format, boolean verbose, boolean images) {
        tokenService.recordNotificationPoll(member.id());
        var station = stationRepository
                .findById(member.stationId())
                .orElseThrow(FeedRefusal.FEED_STATION_NOT_HERE_FOR_NOTIFICATIONS::raise);
        String locale = notificationText.resolveLocale(station.locale());
        var stamp = notificationInbox.latestStamp(member.id());
        var fingerprint = FeedFingerprint.compute(
                stamp.maxCreatedAt(), format.syndicationType(), stamp.maxId(), locale, verbose, images);
        var render = new NotificationFeedRenderer.RenderContext(
                locale, emailService.getBaseUrl(), token, verbose, images, station.uid());
        return new Feed(fingerprint, () -> renderNotifications(member, station, format, render));
    }

    private Rendered renderNotifications(
            StationMember member, Station station, FeedFormat format, NotificationFeedRenderer.RenderContext render) {
        SyndFeed feed = new SyndFeedImpl();
        feed.setFeedType(format.syndicationType());
        feed.setTitle(notificationText.resolveLocalized(
                render.locale(), "feed", "title", Map.of("stationName", station.name())));
        feed.setDescription(notificationText.resolveLocalized(render.locale(), "feed", "description", null));
        feed.setLanguage(render.locale());
        feed.setLink(render.baseUrl() + "/station/dashboard/overview?station=" + station.uid());
        if (format == FeedFormat.ATOM) {
            feed.setUri("urn:ember:notifications:" + member.id());
        }
        var entries = new ArrayList<SyndEntry>();
        for (var notification : feedNotifications(member)) {
            try {
                entries.add(notificationRenderer.render(notification, render));
            } catch (Exception e) {
                log.warn("Failed to render notification {} of type {}", notification.id(), notification.type(), e);
            }
        }
        feed.setEntries(entries);
        try {
            return new Rendered(new SyndFeedOutput().outputString(feed), entries.size());
        } catch (Exception e) {
            log.warn("Failed to write out the feed", e);
            throw FeedRefusal.FEED_NOT_BUILT.raise();
        }
    }

    /**
     * The newest notifications of the kinds the member left in their feed, capped so a noisy
     * station cannot blow up the payload whatever the store hands back.
     */
    private List<Notification> feedNotifications(StationMember member) {
        var settings = notificationPreferences.settingsOf(member.id());
        Set<NotificationType> enabled = Set.of(NotificationType.values()).stream()
                .filter(type -> settings.get(type) == null || settings.get(type).feedEnabled())
                .collect(Collectors.toSet());
        return notificationInbox.recent(Recipient.stationMember(member.id())).stream()
                .filter(n -> enabled.contains(n.type()))
                .limit(NOTIFICATION_FEED_CAP)
                .toList();
    }

    /**
     * The picture of a lost-and-found item, for a feed reader to embed. An item of another station
     * answers exactly as a missing one, so a link holder cannot probe for pictures elsewhere.
     *
     * @param stationId the station of the member the feed link belongs to
     */
    public MediaContent lostAndFoundImage(int stationId, int itemId, int size) {
        var item = lostAndFoundService.findById(itemId).orElseThrow(FeedRefusal.FEED_ITEM_NOT_HERE::raise);
        if (item.stationId() != stationId) {
            throw FeedRefusal.FEED_ITEM_NOT_HERE.raise();
        }
        return imageService.read(stationId, itemId, size).orElseThrow(FeedRefusal.FEED_ITEM_PICTURE_NOT_HERE::raise);
    }

    /** The two syndication formats the notifications are offered in. */
    public enum FeedFormat {
        RSS("rss_2.0"),
        ATOM("atom_1.0");

        private final String syndicationType;

        FeedFormat(String syndicationType) {
            this.syndicationType = syndicationType;
        }

        public String syndicationType() {
            return syndicationType;
        }
    }

    /**
     * A feed as far as it is known before rendering: what it is fingerprinted by, and how to render
     * it once a conditional request did not settle it.
     */
    public record Feed(FeedFingerprint.Result fingerprint, Supplier<Rendered> render) {}

    /**
     * A rendered feed.
     *
     * @param entries how many entries it carries
     */
    public record Rendered(String body, int entries) {}
}
