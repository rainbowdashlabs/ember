/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.messages.service;

import dev.chojo.ember.MovableClock;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.mail.service.EmailService;
import dev.chojo.ember.feature.mail.service.MailLocaleService;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.messages.entity.ChatAudience;
import dev.chojo.ember.feature.messages.entity.Conversation;
import dev.chojo.ember.feature.messages.entity.StationDigest;
import dev.chojo.ember.feature.messages.repository.ConversationRepository;
import dev.chojo.ember.feature.messages.repository.MessageRepository;
import dev.chojo.ember.feature.messages.repository.StationChatRepository;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.system.repository.ApplicationSettingRepository;
import dev.chojo.ember.lifecycle.Schedule;
import dev.chojo.ember.lifecycle.ScheduledTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * The daily mail about unread messages: once a day at the instance's hour, everything new of
 * private conversations, mentions or everything of station chats, and nothing for a muted
 * conversation, a blocked writer, a reader who turned it off or cannot use messages, a chat somebody
 * is no longer in, or when nothing is new.
 */
class MessageDigestMailTest extends MessageTestBase {
    private final ApplicationSettingRepository settings = new ApplicationSettingRepository();
    private MessageFixtures services;
    private EmailService email;
    private MovableClock clock;
    private MessageDigestMail mail;
    private Station station;
    private Account anna;
    private Account ben;
    private Account mia;
    private Conversation direct;

    @BeforeEach
    void people() {
        services = MessageFixtures.build();
        email = mock(EmailService.class);
        clock = new MovableClock(Instant.now());
        settings.set(MessageDigestMail.LAST_RUN, Instant.EPOCH.toString());
        mail = digestAt(clock.instant().atZone(ZoneOffset.UTC).getHour(), clock);
        station = station();
        anna = memberOf(station, "Anna");
        ben = memberOf(station, "Ben");
        mia = memberOf(station, "Mia");
        direct = services.conversations().openDirect(anna.id(), ben.uid());
    }

    private MessageDigestMail digestAt(int hour, Clock on) {
        return new MessageDigestMail(
                new MessageRepository(),
                new ConversationRepository(),
                new StationChatRepository(),
                accountRepo,
                settings,
                new MailLocaleService(accountRepo, settings),
                email,
                hour,
                ZoneOffset.UTC,
                on);
    }

    @Test
    void privateMessagesGoIntoOneMailADay() {
        services.messages().send(anna.id(), direct.uid(), "Kommst du morgen?", null);
        services.messages().send(anna.id(), direct.uid(), "Sag kurz Bescheid", null);

        mail.sweep();
        mail.sweep();

        var sections = ArgumentCaptor.forClass(String.class);
        verify(email, times(1)).sendMessageDigest(eq(ben.email()), anyString(), sections.capture(), eq(2), anyString());
        assertTrue(sections.getValue().contains("Kommst du morgen?"));
        assertTrue(sections.getValue().contains("Anna Muster"));
        verify(email, never()).sendMessageDigest(eq(anna.email()), anyString(), anyString(), anyInt(), anyString());
    }

    @Test
    void theNextDayTakesOnlyWhatIsNew() {
        services.messages().send(anna.id(), direct.uid(), "Erste", null);
        mail.sweep();

        clock.advance(Duration.ofDays(1));
        services.messages().send(anna.id(), direct.uid(), "Zweite", null);
        mail.sweep();

        var sections = ArgumentCaptor.forClass(String.class);
        verify(email, times(2))
                .sendMessageDigest(eq(ben.email()), anyString(), sections.capture(), anyInt(), anyString());
        assertTrue(sections.getAllValues().getLast().contains("Zweite"));
        assertFalse(sections.getAllValues().getLast().contains("Erste"));
        assertEquals(
                clock.instant().toString(),
                settings.get(MessageDigestMail.LAST_RUN).orElseThrow());
    }

    @Test
    void beforeTheHourNothingIsSent() {
        services.messages().send(anna.id(), direct.uid(), "Hallo", null);
        Instant early = Instant.now().truncatedTo(ChronoUnit.DAYS).plus(Duration.ofHours(5));

        digestAt(6, Clock.fixed(early, ZoneOffset.UTC)).sweep();

        verify(email, never()).sendMessageDigest(eq(ben.email()), anyString(), anyString(), anyInt(), anyString());
        assertEquals(
                Instant.EPOCH.toString(),
                settings.get(MessageDigestMail.LAST_RUN).orElseThrow());
    }

    @Test
    void movingTheHourLaterSendsNoSecondMailThatDay() {
        Instant today = Instant.now().truncatedTo(ChronoUnit.DAYS);
        services.messages().send(anna.id(), direct.uid(), "Erste", null);
        digestAt(8, Clock.fixed(today.plus(Duration.ofHours(10)), ZoneOffset.UTC))
                .sweep();
        services.messages().send(anna.id(), direct.uid(), "Zweite", null);

        digestAt(11, Clock.fixed(today.plus(Duration.ofHours(12)), ZoneOffset.UTC))
                .sweep();

        verify(email, times(1)).sendMessageDigest(eq(ben.email()), anyString(), anyString(), anyInt(), anyString());
    }

    @Test
    void stationChatsGiveMentionsByDefaultAndEverythingOnRequest() {
        Conversation chat =
                services.stationChats().open(station.id(), mia.id(), "Alle", ChatAudience.STATION, null, null, false);
        services.messages().send(anna.id(), chat.uid(), "Allgemeines", null);
        services.messages().send(anna.id(), chat.uid(), "Frage an @[account/%s:Ben Muster]".formatted(ben.uid()), null);
        services.settings().update(mia.id(), null, StationDigest.ALL);

        mail.sweep();

        var forBen = ArgumentCaptor.forClass(String.class);
        verify(email).sendMessageDigest(eq(ben.email()), anyString(), forBen.capture(), eq(1), anyString());
        assertTrue(forBen.getValue().contains("Frage an @Ben Muster"));
        assertTrue(forBen.getValue().contains("Alle (" + station.name() + ")"));
        verify(email).sendMessageDigest(eq(mia.email()), anyString(), anyString(), eq(2), anyString());
        assertEquals(StationDigest.ALL, services.settings().status(mia.id()).stationDigest());
    }

    @Test
    void mutedConversationsBlockedWritersAndTheSwitchLeaveThingsOut() {
        services.conversations().mute(ben.id(), direct.uid(), true);
        services.messages().send(anna.id(), direct.uid(), "Gedämpft", null);
        var cora = memberOf(station, "Cora");
        var round = services.groups().create(cora.id(), List.of(mia.uid(), anna.uid()), "Runde");
        services.blocks().block(mia.id(), cora.uid());
        services.messages().send(cora.id(), round.uid(), "Blockiert", null);
        var withMia = services.conversations().openDirect(mia.id(), anna.uid());
        services.messages().send(mia.id(), withMia.uid(), "Abgeschaltet", null);
        services.settings().update(anna.id(), false, null);

        mail.sweep();

        verify(email, never()).sendMessageDigest(eq(ben.email()), anyString(), anyString(), anyInt(), anyString());
        verify(email, never()).sendMessageDigest(eq(mia.email()), anyString(), anyString(), anyInt(), anyString());
        verify(email, never()).sendMessageDigest(eq(anna.email()), anyString(), anyString(), anyInt(), anyString());
    }

    @Test
    void somebodyWhoseGuardianTurnedMessagesOffGetsNoMail() {
        var child = memberOf(station, "Kim");
        var parent = memberOf(station, "Paula");
        guard(member(parent), member(child));
        services.settings().setGuardianAllowed(child.id(), true);
        var withChild = services.conversations().openDirect(anna.id(), child.uid());
        services.messages().send(anna.id(), withChild.uid(), "Hallo Kim", null);
        services.settings().setGuardianAllowed(child.id(), false);

        mail.sweep();

        verify(email, never()).sendMessageDigest(eq(child.email()), anyString(), anyString(), anyInt(), anyString());
    }

    @Test
    void aStationChatSomebodyWasTakenOutOfSendsThemNothing() {
        Conversation chat =
                services.stationChats().open(station.id(), mia.id(), "Alle", ChatAudience.STATION, null, null, false);
        services.settings().update(ben.id(), null, StationDigest.ALL);
        stationMemberRepo.setFormer(member(ben).id(), true);
        services.membership().reconcile(station.id());
        services.messages().send(anna.id(), chat.uid(), "Nach Bens Abschied", null);

        mail.sweep();

        verify(email, never()).sendMessageDigest(eq(ben.email()), anyString(), anyString(), anyInt(), anyString());
    }

    @Test
    void namesAndTextsArriveEscaped() {
        var eve = memberOf(station, "<Eve>");
        var group = services.groups().create(eve.id(), List.of(ben.uid()), "A & \"B\"");
        services.messages().send(eve.id(), group.uid(), "<script>alert(1)</script>", null);

        mail.sweep();

        var sections = ArgumentCaptor.forClass(String.class);
        verify(email).sendMessageDigest(eq(ben.email()), anyString(), sections.capture(), anyInt(), anyString());
        String html = sections.getValue();
        assertTrue(html.contains("A &amp; &quot;B&quot;"));
        assertTrue(html.contains("&lt;Eve&gt; Muster"));
        assertTrue(html.contains("&lt;script&gt;"));
        assertFalse(html.contains("<script>"));
    }

    @Test
    void readMessagesAndNothingNewSendNothing() {
        var sent = services.messages().send(anna.id(), direct.uid(), "Gelesen", null);
        services.conversations().read(ben.id(), direct.uid(), sent.message().id());

        mail.sweep();

        verify(email, never()).sendMessageDigest(eq(ben.email()), anyString(), anyString(), anyInt(), anyString());
    }

    @Test
    void theSweepLooksInEveryQuarterHour() {
        ScheduledTask task = mail.scheduledTasks().getFirst();

        task.work().run();

        assertEquals("message-digest-mail", task.name());
        assertEquals(Schedule.fixedDelay(Duration.ofMinutes(15), Duration.ofMinutes(15)), task.schedule());
    }

    private StationMember member(Account account) {
        return stationMemberRepo
                .findByStationAndAccount(station.id(), account.id())
                .orElseThrow();
    }
}
