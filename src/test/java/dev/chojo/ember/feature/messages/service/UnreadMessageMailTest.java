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
import dev.chojo.ember.feature.messages.entity.Conversation;
import dev.chojo.ember.feature.messages.repository.ConversationRepository;
import dev.chojo.ember.feature.messages.repository.MessageRepository;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.system.repository.ApplicationSettingRepository;
import dev.chojo.ember.lifecycle.ScheduledTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * The mail about unread messages: after 15 minutes, once per unread stretch, quoting the first
 * lines, and never for a muted conversation, a blocked writer or a reader who turned it off.
 */
class UnreadMessageMailTest extends MessageTestBase {
    private MessageFixtures services;
    private EmailService email;
    private MovableClock clock;
    private UnreadMessageMail mail;
    private Station station;
    private Account anna;
    private Account ben;
    private Conversation direct;

    @BeforeEach
    void people() {
        services = MessageFixtures.build();
        email = mock(EmailService.class);
        clock = new MovableClock(Instant.now());
        mail = new UnreadMessageMail(
                new MessageRepository(),
                new ConversationRepository(),
                accountRepo,
                new MailLocaleService(accountRepo, new ApplicationSettingRepository()),
                email,
                clock);
        station = station();
        anna = memberOf(station, "Anna");
        ben = memberOf(station, "Ben");
        direct = services.conversations().openDirect(anna.id(), ben.uid());
    }

    private void sweepLater() {
        clock.advance(UnreadMessageMail.WAIT.plusMinutes(1));
        mail.sweep();
    }

    private void verifyBenMailed(int times) {
        verify(email, times(times)).sendUnreadMessages(eq(ben.email()), any(), any(), any(), any(), any(), any());
    }

    @Test
    void aMessageLeftUnreadEarnsOneMailUntilItIsRead() {
        services.messages().send(anna.id(), direct.uid(), "Hallo @[account/%s:Ben]".formatted(ben.uid()), null);

        mail.sweep();
        verifyBenMailed(0);

        sweepLater();
        sweepLater();
        verify(email, times(1))
                .sendUnreadMessages(
                        eq(ben.email()),
                        eq("Ben"),
                        eq("Anna Muster"),
                        eq(""),
                        eq(List.of("Hallo @Ben")),
                        eq("/messages/" + direct.uid()),
                        anyString());

        int next = services.messages()
                .send(anna.id(), direct.uid(), "Noch da?", null)
                .message()
                .id();
        services.conversations().read(ben.id(), direct.uid(), next);
        services.messages().send(anna.id(), direct.uid(), "Und jetzt?", null);
        sweepLater();
        verifyBenMailed(2);
    }

    @Test
    void aMutedConversationAReaderWhoSaidNoAndABlockedWriterAreLeftOut() {
        services.messages().send(anna.id(), direct.uid(), "Hallo", null);
        services.conversations().mute(ben.id(), direct.uid(), true);
        sweepLater();

        services.conversations().mute(ben.id(), direct.uid(), false);
        services.settings().setMail(ben.id(), false);
        sweepLater();

        services.settings().setMail(ben.id(), true);
        services.blocks().block(ben.id(), anna.uid());
        sweepLater();

        verifyBenMailed(0);
    }

    @Test
    void aGroupWithoutANameIsCalledAfterTheOthers() {
        var cora = memberOf(station, "Cora");
        var group = services.groups().create(anna.id(), List.of(ben.uid(), cora.uid()), null);
        services.messages().send(anna.id(), group.uid(), "Treffen?", null);

        sweepLater();

        verify(email).sendUnreadMessages(eq(ben.email()), any(), any(), eq("Anna, Cora"), any(), any(), anyString());
    }

    @Test
    void aNamedGroupIsCalledByItsName() {
        var group = services.groups().create(anna.id(), List.of(ben.uid()), "Jugend");
        services.messages().send(anna.id(), group.uid(), "Treffen?", null);

        sweepLater();

        verify(email).sendUnreadMessages(eq(ben.email()), any(), any(), eq("Jugend"), any(), any(), anyString());
    }

    @Test
    void anAccountWithoutAnAddressOfItsOwnIsNotMailed() {
        String synthetic = "ben-%d@managed.local".formatted(System.nanoTime());
        accountRepo.updateEmail(ben.id(), synthetic);
        services.messages().send(anna.id(), direct.uid(), "Hallo", null);

        sweepLater();

        verify(email, never()).sendUnreadMessages(eq(synthetic), any(), any(), any(), any(), any(), any());
    }

    @Test
    void theSweepRunsEveryMinute() {
        ScheduledTask task = mail.scheduledTasks().getFirst();

        assertEquals("unread-message-mail", task.name());
        assertEquals(Duration.ofSeconds(60), task.schedule().period());
        task.work().run();
    }
}
