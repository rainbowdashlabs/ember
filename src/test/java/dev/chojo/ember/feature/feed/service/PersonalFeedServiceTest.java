/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.feed.service;

import com.rometools.rome.feed.synd.SyndEntryImpl;
import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.api.RefusalResponse;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.events.entity.EventRegistration;
import dev.chojo.ember.feature.events.entity.RegistrationStatus;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.service.EventCategoryService;
import dev.chojo.ember.feature.events.service.EventCrudService;
import dev.chojo.ember.feature.events.service.EventRegistrationService;
import dev.chojo.ember.feature.events.service.OccurrenceCalendar;
import dev.chojo.ember.feature.feed.entity.FeedToken;
import dev.chojo.ember.feature.feed.render.IcalEventRenderer;
import dev.chojo.ember.feature.feed.render.NotificationFeedRenderer;
import dev.chojo.ember.feature.feed.service.PersonalFeedService.FeedFormat;
import dev.chojo.ember.feature.lostandfound.entity.LostAndFoundItem;
import dev.chojo.ember.feature.lostandfound.service.LostAndFoundImageService;
import dev.chojo.ember.feature.lostandfound.service.LostAndFoundService;
import dev.chojo.ember.feature.mail.service.EmailService;
import dev.chojo.ember.feature.media.entity.MediaContent;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.members.service.MemberNameResolver;
import dev.chojo.ember.feature.notifications.entity.Notification;
import dev.chojo.ember.feature.notifications.entity.NotificationSetting;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.notifications.entity.Recipient;
import dev.chojo.ember.feature.notifications.repository.NotificationRepository;
import dev.chojo.ember.feature.notifications.service.NotificationInbox;
import dev.chojo.ember.feature.notifications.service.NotificationPreferences;
import dev.chojo.ember.feature.notifications.service.NotificationText;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.repository.StationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PersonalFeedServiceTest {
    private static final int STATION = 3;
    private static final StationMember OWNER =
            new StationMember(7, STATION, null, 1, false, null, "Mara", StationUserType.GUARDIAN, null);
    private static final StationMember CHILD =
            new StationMember(8, STATION, null, null, false, null, "Kim", StationUserType.MEMBER, null);

    private FeedTokenService tokens;
    private EventCrudService events;
    private EventRegistrationService registrations;
    private NotificationText text;
    private NotificationInbox inbox;
    private NotificationPreferences preferences;
    private StationMemberRepository members;
    private IcalEventRenderer icalRenderer;
    private LostAndFoundService lostAndFound;
    private LostAndFoundImageService images;
    private NotificationFeedRenderer notificationRenderer;
    private MemberNameResolver names;
    private PersonalFeedService service;

    private static StationEvent event(int id, boolean recurring, Instant start) {
        var event = mock(StationEvent.class);
        when(event.id()).thenReturn(id);
        when(event.isRecurring()).thenReturn(recurring);
        when(event.startTime()).thenReturn(start);
        return event;
    }

    private static EventRegistration registration(int eventId, int memberId, RegistrationStatus status) {
        var registration = mock(EventRegistration.class);
        when(registration.eventId()).thenReturn(eventId);
        when(registration.memberId()).thenReturn(memberId);
        when(registration.status()).thenReturn(status);
        return registration;
    }

    @BeforeEach
    void setup() {
        tokens = mock(FeedTokenService.class);
        events = mock(EventCrudService.class);
        registrations = mock(EventRegistrationService.class);
        text = mock(NotificationText.class);
        inbox = mock(NotificationInbox.class);
        preferences = mock(NotificationPreferences.class);
        members = mock(StationMemberRepository.class);
        var stations = mock(StationRepository.class);
        icalRenderer = mock(IcalEventRenderer.class);
        lostAndFound = mock(LostAndFoundService.class);
        images = mock(LostAndFoundImageService.class);
        notificationRenderer = mock(NotificationFeedRenderer.class);
        names = mock(MemberNameResolver.class);
        var station = mock(Station.class);
        when(station.id()).thenReturn(STATION);
        when(station.name()).thenReturn("Wache");
        when(station.uid()).thenReturn(UUID.fromString("00000000-0000-0000-0000-000000000003"));
        when(stations.findById(STATION)).thenReturn(Optional.of(station));
        when(text.resolveLocale(any())).thenReturn("de");
        when(text.resolveLocalized(any(), any(), any(), any())).thenReturn("text");
        service = new PersonalFeedService(
                tokens,
                events,
                mock(EventCategoryService.class),
                registrations,
                text,
                inbox,
                preferences,
                members,
                stations,
                mock(EmailService.class),
                icalRenderer,
                lostAndFound,
                images,
                notificationRenderer,
                names,
                mock(OccurrenceCalendar.class));
    }

    @Test
    void aFeedLinkNamesItsMemberAndAnUnknownOneIsNotGood() {
        when(tokens.findByToken("t")).thenReturn(Optional.of(new FeedToken(7, "t", Instant.EPOCH, null, null)));
        when(members.findById(7)).thenReturn(Optional.of(OWNER));

        assertSame(OWNER, service.member("t"));
        assertEquals(
                Refusal.FEED_LINK_NOT_GOOD,
                assertThrows(RefusalResponse.class, () -> service.member("other"))
                        .refusal());
    }

    @Test
    void theCalendarCarriesTheHouseholdsAppointmentsWithinAYearAndSkipsWhatCannotBeRendered() {
        var now = Instant.now();
        when(members.findManaged(7)).thenReturn(List.of(CHILD));
        when(events.findMaxEventUpdatedAt(STATION)).thenReturn(now.minusSeconds(60));
        when(registrations.findMaxCreatedAt(List.of(7, 8))).thenReturn(now);
        var household = List.of(
                registration(1, 7, RegistrationStatus.values()[0]), registration(1, 8, RegistrationStatus.values()[0]));
        when(registrations.findByMembers(List.of(7, 8))).thenReturn(household);
        var series = event(1, true, null);
        var soon = event(2, false, now.plus(Duration.ofDays(10)));
        var longAgo = event(3, false, now.minus(Duration.ofDays(800)));
        var undated = event(4, false, null);
        when(events.findFilteredForMembers(STATION, List.of(7, 8), null, null))
                .thenReturn(List.of(series, soon, longAgo, undated));
        when(icalRenderer.isVisibleForFeed(any(), any())).thenReturn(true);
        when(icalRenderer.render(eq(series), any())).thenReturn(List.of());
        when(icalRenderer.render(eq(soon), any())).thenThrow(new IllegalStateException("broken"));

        var feed = service.calendar(OWNER, true);
        var rendered = feed.render().get();

        assertEquals(2, rendered.entries());
        assertTrue(rendered.body().contains("X-WR-CALNAME:Wache"));
        verify(tokens).recordIcalPoll(7);
        verify(names).called(8);
        assertNotEquals(feed.fingerprint(), service.calendar(OWNER, false).fingerprint());
    }

    @Test
    void theNotificationsCarryOnlyTheKindsLeftInTheFeed() {
        when(inbox.latestStamp(7)).thenReturn(new NotificationRepository.Stamp(4, Instant.EPOCH));
        var shown = NotificationType.values()[0];
        var hidden = NotificationType.values()[1];
        var off = mock(NotificationSetting.class);
        when(preferences.settingsOf(7)).thenReturn(Map.of(hidden, off));
        var kept = mock(Notification.class);
        when(kept.type()).thenReturn(shown);
        var broken = mock(Notification.class);
        when(broken.type()).thenReturn(shown);
        var dropped = mock(Notification.class);
        when(dropped.type()).thenReturn(hidden);
        when(inbox.recent(Recipient.stationMember(7))).thenReturn(List.of(kept, broken, dropped));
        when(notificationRenderer.render(eq(kept), any())).thenReturn(new SyndEntryImpl());
        when(notificationRenderer.render(eq(broken), any())).thenThrow(new IllegalStateException("broken"));

        var rendered = service.notifications(OWNER, "t", FeedFormat.ATOM, true, false)
                .render()
                .get();

        assertEquals(1, rendered.entries());
        assertTrue(rendered.body().contains("urn:ember:notifications:7"));
        verify(tokens).recordNotificationPoll(7);
        assertEquals(
                1,
                service.notifications(OWNER, "t", FeedFormat.RSS, false, true)
                        .render()
                        .get()
                        .entries());
    }

    @Test
    void aLostItemPictureIsServedOnlyForTheMembersStation() {
        var own = mock(LostAndFoundItem.class);
        when(own.stationId()).thenReturn(STATION);
        var foreign = mock(LostAndFoundItem.class);
        when(foreign.stationId()).thenReturn(9);
        when(lostAndFound.findById(1)).thenReturn(Optional.of(own));
        when(lostAndFound.findById(2)).thenReturn(Optional.of(foreign));
        when(lostAndFound.findById(4)).thenReturn(Optional.of(own));
        var picture = new MediaContent(new byte[] {1}, "image/png");
        when(images.read(STATION, 1, 0)).thenReturn(Optional.of(picture));
        when(images.read(eq(STATION), eq(4), anyInt())).thenReturn(Optional.empty());

        assertSame(picture, service.lostAndFoundImage(STATION, 1, 0));
        assertEquals(
                Refusal.FEED_ITEM_NOT_HERE,
                assertThrows(RefusalResponse.class, () -> service.lostAndFoundImage(STATION, 2, 0))
                        .refusal());
        assertEquals(
                Refusal.FEED_ITEM_NOT_HERE,
                assertThrows(RefusalResponse.class, () -> service.lostAndFoundImage(STATION, 3, 0))
                        .refusal());
        assertEquals(
                Refusal.FEED_ITEM_PICTURE_NOT_HERE,
                assertThrows(RefusalResponse.class, () -> service.lostAndFoundImage(STATION, 4, 0))
                        .refusal());
    }
}
