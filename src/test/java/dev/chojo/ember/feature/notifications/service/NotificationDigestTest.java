/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.notifications.service;

import dev.chojo.ember.api.auth.ClusterUserType;
import dev.chojo.ember.conf.file.elements.Mailing;
import dev.chojo.ember.feature.mail.service.EmailService;
import dev.chojo.ember.feature.mail.service.MailRecipientService;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.notifications.entity.Delivery;
import dev.chojo.ember.feature.notifications.entity.DigestItem;
import dev.chojo.ember.feature.notifications.entity.NotificationData;
import dev.chojo.ember.feature.notifications.entity.NotificationParams;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.notifications.entity.StationAudience;
import dev.chojo.ember.feature.notifications.repository.NotificationRepository;
import dev.chojo.ember.feature.notifications.repository.NotificationScheduleRepository;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.service.StationLogoService;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The digest as its readers meet it: who is mailed, when, in which language, and what the mail
 * holds, for station members and cluster members through the one loop.
 */
class NotificationDigestTest extends RepositoryTestBase {
    private static final String BASE = "https://ember.example.com";

    private EmailService email;
    private StationLogoService logos;
    private List<String> mails;
    private NotificationDigest digest;

    @BeforeEach
    void setup() {
        mails = new ArrayList<>();
        email = mock(EmailService.class);
        logos = mock(StationLogoService.class);
        when(email.getBaseUrl()).thenReturn(BASE);
        when(email.canStationSend(anyInt())).thenReturn(true);
        when(email.canInstanceSend()).thenReturn(true);
        when(email.loadTemplate(anyString(), anyString(), any())).thenAnswer(invocation -> {
            Map<String, String> vars = invocation.getArgument(2);
            var rendered = new StringBuilder();
            rendered.append("template=")
                    .append((String) invocation.getArgument(0))
                    .append('\n');
            rendered.append("locale=")
                    .append((String) invocation.getArgument(1))
                    .append('\n');
            new TreeMap<>(vars)
                    .forEach((key, value) ->
                            rendered.append(key).append('=').append(value).append('\n'));
            return rendered.toString();
        });
        doAnswer(invocation -> mails.add(invocation.getArgument(3) + "to=" + invocation.getArgument(1) + "\nsubject="
                        + invocation.getArgument(2) + "\n"))
                .when(email)
                .queueStationEmail(anyInt(), anyString(), anyString(), anyString());
        doAnswer(invocation -> mails.add(invocation.getArgument(2) + "to=" + invocation.getArgument(0) + "\nsubject="
                        + invocation.getArgument(1) + "\n"))
                .when(email)
                .queueInstanceEmail(anyString(), anyString(), anyString());
        digest = digestWith(notificationRepo, new Mailing());
        flushWaiting();
    }

    /** The station digest reads exactly as it did before stations and clusters shared one loop. */
    @Test
    void aStationDigestReadsAsItAlwaysHas() throws Exception {
        var station = station("Wache Golden", "de-DE", "Europe/Berlin");
        var greta = memberWithMail(station, "golden@test.com", "Greta", "Golden");
        notificationSettingsRepo.upsert(greta.id(), NotificationType.NEW_NEWS, true, true, true);
        notificationSettingsRepo.upsert(greta.id(), NotificationType.NEWS_COMMENT, true, true, true);
        notify(
                greta,
                NotificationType.NEW_NEWS,
                NotificationData.of(
                        new NotificationParams.NewNews("Dienstplan Oktober", "Anna", "Der neue Plan ist da."),
                        new NotificationData.NotificationLink("news-detail", Map.of("id", 42))));
        notify(
                greta,
                NotificationType.NEWS_COMMENT,
                NotificationData.of(
                        new NotificationParams.NewsComment("Dienstplan Oktober", "Ben", "Passt!"),
                        new NotificationData.NotificationLink("news-detail", Map.of("id", 42), Map.of("comment", 7))));

        digest.sweep(Instant.now());

        String golden;
        try (var in = getClass().getResourceAsStream("/notifications/station-digest.txt")) {
            golden = new String(Objects.requireNonNull(in).readAllBytes(), StandardCharsets.UTF_8);
        }
        assertEquals(List.of(golden.replace("{stationUid}", station.uid().toString())), mails);
        assertTrue(waiting(greta).isEmpty(), "what went out is marked as mailed");
    }

    /**
     * A cluster's mail is written in its home station's language, goes out through the instance's
     * chain, and reaches only the people who switched their mail on.
     */
    @Test
    void aClusterWritesInItsHomeLanguageAndOnlyToWhoAskedForMail() {
        var home = station("Kreisverband Heim", "de-DE", "Europe/Berlin");
        var cluster = clusterRepo.create("Kreisverband Nord", "hears about its partners", home.id());
        var wantsMail = clusterRepo.addMember(
                cluster.id(),
                accountRepo.create("folgt@test.com", "Fritz", "Folger").id(),
                ClusterUserType.CLUSTER_USER);
        var noMail = clusterRepo.addMember(
                cluster.id(),
                accountRepo.create("still@test.com", "Stella", "Still").id(),
                ClusterUserType.CLUSTER_USER);
        var preferences = new NotificationPreferences(notificationSettingsRepo, clusterRepo, email);
        assertFalse(preferences.clusterMailOf(wantsMail.id()).emailEnabled(), "mail is off until asked for");
        assertTrue(preferences.setClusterMail(wantsMail.id(), true).emailEnabled());
        assertTrue(preferences.clusterMailOf(wantsMail.id()).mailAvailable());
        notificationRepo.insertForCluster(
                List.of(wantsMail.id(), noMail.id()),
                List.of(),
                NotificationType.CLUSTER_APPLICATION_SUBMITTED,
                NotificationData.of(
                        new NotificationParams.ClusterApplicationSubmitted("Wache Süd"),
                        new NotificationData.NotificationLink("cluster-applications")),
                Delivery.EVERY_TIME);

        digest.sweep(Instant.now());

        verify(email, times(1)).queueInstanceEmail(eq("folgt@test.com"), anyString(), anyString());
        verify(email, never()).queueInstanceEmail(eq("still@test.com"), anyString(), anyString());
        verify(email, never()).queueStationEmail(anyInt(), anyString(), anyString(), anyString());
        assertTrue(mails.getFirst().contains("locale=de"));
        assertTrue(mails.getFirst().contains("actionUrl=" + BASE + "/cluster?cluster=" + cluster.uid()));
        assertTrue(mails.getFirst()
                .contains("preferencesUrl=" + BASE + "/cluster/notifications?cluster=" + cluster.uid()));
        assertTrue(
                mails.getFirst().contains("href=\"" + BASE + "/cluster/applications?cluster=" + cluster.uid() + "\""),
                "the item opens the association's applications, not a station's dashboard");
        assertTrue(mails.getFirst().contains("subject=Kreisverband Nord: 1 neue Benachrichtigung"));
        assertTrue(notificationRepo.findWaitingForDigest().isEmpty(), "both are marked, mailed or not");
        clusterRepo.delete(cluster.id());
    }

    /** A member who has not switched mail on is not mailed, and their notification is not tried again. */
    @Test
    void aMemberWithoutMailIsNotWrittenToButMarked() {
        var station = station("Wache Still", "de-DE", "Europe/Berlin");
        var quiet = member(station, "quiet@test.com");
        notify(quiet, NotificationType.NEW_NEWS, news("Ruhe"));

        digest.sweep(Instant.now());

        verify(email, never()).queueStationEmail(anyInt(), anyString(), anyString(), anyString());
        assertTrue(waiting(quiet).isEmpty());
    }

    /** What arrives between two of a station's times waits for the next one, unmailed and unmarked. */
    @Test
    void aStationWhoseMomentHasNotComeKeepsItsNotifications() {
        var station = station("Wache Später", "de-DE", "Europe/Berlin");
        var member = memberWithMail(station, "later@test.com", "Lea", "Later");
        notificationSettingsRepo.upsert(member.id(), NotificationType.NEW_NEWS, true, true, true);
        var schedules = new NotificationScheduleRepository();
        var now = Instant.parse("2026-09-30T10:00:00Z");
        schedules.markStationSent(station.id(), now.minus(Duration.ofHours(2)));
        schedules.setStationSendTimes(station.id(), List.of(LocalTime.of(20, 0)));
        notify(member, NotificationType.NEW_NEWS, news("Warten"));

        digest.sweep(now);

        verify(email, never()).queueStationEmail(anyInt(), anyString(), anyString(), anyString());
        assertFalse(waiting(member).isEmpty());
    }

    /** A station whose clock cannot be read is written to on UTC rather than never. */
    @Test
    void aStationWhoseClockCannotBeReadIsStillWrittenTo() {
        var station = station("Wache Nirgends", "de-DE", "Nirgendwo/Nirgends");
        var member = memberWithMail(station, "nowhere@test.com", "Nora", "Nirgends");
        notificationSettingsRepo.upsert(member.id(), NotificationType.NEW_NEWS, true, true, true);
        new NotificationScheduleRepository().setStationSendTimes(station.id(), List.of(LocalTime.of(0, 0)));
        notify(member, NotificationType.NEW_NEWS, news("Irgendwo"));

        digest.sweep(Instant.now().plus(Duration.ofDays(1)));

        verify(email).queueStationEmail(eq(station.id()), eq("nowhere@test.com"), anyString(), anyString());
    }

    /** A station logo goes into the mail, a mail that fails is survived, and the rest still goes out. */
    @Test
    void aFailingMailDoesNotStopTheOthers() {
        var station = station("Wache Logo", "en-GB", "Europe/Berlin");
        when(logos.exists(station.id())).thenReturn(true);
        var failing = memberWithMail(station, "failing@test.com", "Fay", "Failing");
        var fine = memberWithMail(station, "fine@test.com", "Finn", "Fine");
        for (var member : List.of(failing, fine)) {
            notificationSettingsRepo.upsert(member.id(), NotificationType.NEW_NEWS, true, true, true);
            notify(member, NotificationType.NEW_NEWS, news("Logo"));
        }
        doThrow(new IllegalStateException("queue away"))
                .when(email)
                .queueStationEmail(anyInt(), eq("failing@test.com"), anyString(), anyString());

        assertDoesNotThrow(() -> digest.sweep(Instant.now()));

        assertEquals(1, mails.size());
        assertTrue(
                mails.getFirst().contains(BASE + "/api/v1/public/stations/" + station.uid() + "/logo?size=128"),
                "the logo is loaded from the address that needs no sign-in");
        assertTrue(mails.getFirst().contains("locale=en"));
        assertTrue(waiting(failing).isEmpty());
    }

    /** A station that cannot send, and a member without an account, are marked and not mailed. */
    @Test
    void nobodyIsMailedWhereNothingCanBeSent() {
        var station = station("Wache Stumm", "de-DE", "Europe/Berlin");
        when(email.canStationSend(station.id())).thenReturn(false);
        var member = memberWithMail(station, "mute@test.com", "Mia", "Mute");
        notificationSettingsRepo.upsert(member.id(), NotificationType.NEW_NEWS, true, true, true);
        notify(member, NotificationType.NEW_NEWS, news("Stumm"));
        var elsewhere = station("Wache Namenlos", "de-DE", "Europe/Berlin");
        var nameless = memberWithMail(elsewhere, "nameless@test.com", "Nils", "Namenlos");
        notificationSettingsRepo.upsert(nameless.id(), NotificationType.NEW_NEWS, true, true, true);
        notify(nameless, NotificationType.NEW_NEWS, news("Namenlos"));
        stationMemberRepo.setDisplayNameAndClearAccount(nameless.id(), "Nils Namenlos");

        digest.sweep(Instant.now());

        verify(email, never()).queueStationEmail(anyInt(), anyString(), anyString(), anyString());
        assertTrue(notificationRepo.findWaitingForDigest().isEmpty());
    }

    /** Reading everything a sweep needs costs the same whatever the number of people. */
    @Test
    void aSweepCostsTheSameForTwentyPeopleAsForOne() {
        var station = station("Wache Zwanzig", "de-DE", "Europe/Berlin");
        for (int i = 0; i < 20; i++) {
            var member = memberWithMail(station, "twenty-" + i + "@test.com", "Zwanzig", "No" + i);
            notificationSettingsRepo.upsert(member.id(), NotificationType.NEW_NEWS, true, true, true);
        }
        for (int n = 0; n < 5; n++) {
            notificationRepo.insertForStation(
                    StationAudience.wholeStation(station.id()),
                    NotificationType.NEW_NEWS,
                    news("Runde " + n),
                    Delivery.EVERY_TIME);
        }

        int statements = countStatements(() -> digest.sweep(Instant.now()));

        assertEquals(20, mails.size());
        assertEquals(6, statements, "read waiting, groups, accounts; mark mailed, mark sent; prune");
    }

    /** Zero minutes switches the mail off; the sweep still prunes. */
    @Test
    void anInstallationCanTurnTheDigestOff() {
        var station = station("Wache Aus", "de-DE", "Europe/Berlin");
        var member = memberWithMail(station, "off@test.com", "Otto", "Off");
        notificationSettingsRepo.upsert(member.id(), NotificationType.NEW_NEWS, true, true, true);
        notify(member, NotificationType.NEW_NEWS, news("Aus"));
        var silent = digestWith(notificationRepo, mock(Mailing.class));

        silent.sweep(Instant.now());

        verify(email, never()).queueStationEmail(anyInt(), anyString(), anyString(), anyString());
        assertFalse(waiting(member).isEmpty());
    }

    /**
     * Read notifications are pruned once a day, however often the sweep comes round, and a failed
     * attempt is tried again on the next one.
     */
    @Test
    void theSweepPrunesReadNotificationsAtMostOnceADay() {
        var repository = mock(NotificationRepository.class);
        var pruning = digestWith(repository, mock(Mailing.class));
        var start = Instant.parse("2026-09-30T03:00:00Z");

        pruning.pruneAcknowledgedIfDue(start);
        pruning.pruneAcknowledgedIfDue(start.plus(Duration.ofMinutes(15)));
        pruning.pruneAcknowledgedIfDue(start.plus(Duration.ofHours(23)));
        verify(repository, times(1)).deleteOldAcknowledged();

        pruning.pruneAcknowledgedIfDue(start.plus(Duration.ofDays(1)));
        verify(repository, times(2)).deleteOldAcknowledged();

        doThrow(new IllegalStateException("database away")).when(repository).deleteOldAcknowledged();
        assertDoesNotThrow(() -> pruning.pruneAcknowledgedIfDue(start.plus(Duration.ofDays(2))));
        doNothing().when(repository).deleteOldAcknowledged();
        pruning.pruneAcknowledgedIfDue(start.plus(Duration.ofDays(2)).plus(Duration.ofMinutes(15)));
        verify(repository, times(4)).deleteOldAcknowledged();
    }

    /** A sweep whose reading fails is survived, so the scheduled sweep goes on. */
    @Test
    void aSweepThatCannotReadIsSurvived() {
        var repository = mock(NotificationRepository.class);
        when(repository.findWaitingForDigest()).thenThrow(new IllegalStateException("database away"));

        assertDoesNotThrow(() -> digestWith(repository, new Mailing()).sweep(Instant.now()));
    }

    private NotificationDigest digestWith(NotificationRepository repository, Mailing mailing) {
        return new NotificationDigest(
                repository,
                new NotificationScheduleRepository(),
                accountRepo,
                new MailRecipientService(accountRepo, stationMemberRepo),
                email,
                logos,
                new NotificationText(),
                mailing);
    }

    private static void flushWaiting() {
        notificationRepo.markEmailed(notificationRepo.findWaitingForDigest().stream()
                .map(item -> item.notification().id())
                .toList());
    }

    private static Station station(String name, String locale, String timezone) {
        var station = stationRepo.create(name);
        stationRepo.updateLocale(station.id(), locale);
        stationRepo.updateTimezone(station.id(), timezone);
        return station;
    }

    private static StationMember member(Station station, String address) {
        return stationMemberRepo.create(
                station.id(), accountRepo.create(address, "Digest", "Reader").id());
    }

    private static StationMember memberWithMail(Station station, String address, String first, String last) {
        var member = stationMemberRepo.create(
                station.id(), accountRepo.create(address, first, last).id());
        userSettingsRepo.updateEmailEnabled(member.id(), true);
        return member;
    }

    private static void notify(StationMember member, NotificationType type, NotificationData data) {
        notificationRepo.insertForStation(StationAudience.member(member.id()), type, data, Delivery.EVERY_TIME);
    }

    private static NotificationData news(String title) {
        return NotificationData.of(
                new NotificationParams.NewNews(title + " " + UUID.randomUUID(), "Autor", "Vorschau"),
                new NotificationData.NotificationLink("news-list"));
    }

    private static List<DigestItem> waiting(StationMember member) {
        return notificationRepo.findWaitingForDigest().stream()
                .filter(item ->
                        item.recipientId() == member.id() && item.notification().clusterMemberId() == null)
                .toList();
    }
}
