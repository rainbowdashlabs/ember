/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.mail.service;

import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetup;
import dev.chojo.ember.conf.file.elements.Api;
import dev.chojo.ember.conf.file.elements.Demo;
import dev.chojo.ember.conf.file.elements.Mailing;
import dev.chojo.ember.feature.mail.entity.MailChainEntry;
import dev.chojo.ember.feature.mail.entity.SmtpEncryption;
import dev.chojo.ember.feature.mail.repository.EmailQueueRepository;
import dev.chojo.ember.feature.mail.repository.EmailQueueRepository.QueuedEmail;
import dev.chojo.ember.feature.mail.repository.MailProviderBlockRepository;
import dev.chojo.ember.feature.station.entity.MailProviderType;
import dev.chojo.ember.feature.storage.service.StationReadOnlyGuard;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * One round of the mail queue: where a mail goes, which provider carries it, and what is recorded
 * about it. The providers are real relays on this machine, so a mail that is sent is a mail that
 * arrived.
 */
class EmailQueueProcessingTest {
    private static final int STATION = 4;

    private static GreenMail relay;

    private EmailQueueRepository queue;
    private MailChainService chains;
    private MailAllowance allowance;
    private EmailService service;

    @BeforeAll
    static void startRelay() {
        relay = new GreenMail(new ServerSetup(0, "127.0.0.1", ServerSetup.PROTOCOL_SMTP));
        relay.start();
        relay.setUser("post@instance.test", "sender", "secret");
    }

    @AfterAll
    static void stopRelay() {
        if (relay != null) relay.stop();
    }

    @BeforeEach
    void setup() throws Exception {
        relay.purgeEmailFromAllMailboxes();
        queue = mock(EmailQueueRepository.class);
        chains = mock(MailChainService.class);
        allowance = mock(MailAllowance.class);
        var guard = mock(StationReadOnlyGuard.class);
        when(guard.isWritable(anyInt())).thenReturn(true);
        var blocks = mock(MailProviderBlockRepository.class);
        when(blocks.blockedFor(any(), any())).thenReturn(Set.of());
        when(chains.forInstance()).thenReturn(List.of(relayEntry(0).asInstanceProvider(0)));
        service = new EmailService(
                mock(Mailing.class),
                mock(Api.class),
                mock(Demo.class),
                queue,
                mock(MailTemplateRenderer.class),
                guard,
                chains,
                blocks,
                mock(MailRetryService.class),
                allowance);
    }

    private static MailChainEntry relayEntry(int position) {
        return new MailChainEntry(
                position,
                MailProviderType.SMTP,
                "127.0.0.1",
                relay.getSmtp().getPort(),
                SmtpEncryption.NONE,
                "sender",
                "secret",
                "",
                "post@instance.test",
                "Ember",
                2,
                100,
                "",
                "");
    }

    private void queued(@Nullable Integer stationId) {
        var mail = new QueuedEmail(11, "reader@example.test", "Digest", "<p>Hi</p>", stationId, 0, 0, Instant.now());
        when(queue.fetchPending(anyInt(), anyBoolean())).thenReturn(List.of(mail));
    }

    private void runQueue() {
        service.scheduledTasks().getFirst().work().run();
    }

    /**
     * A station whose providers have all spent today's allowance, the instance's share included,
     * gets its mail out tomorrow rather than never.
     */
    @Test
    void stationMailWaitsUntilTomorrowWhenNothingHasRoomToday() {
        queued(STATION);
        var chain = List.of(relayEntry(0).asInstanceProvider(0));
        when(chains.forStation(STATION)).thenReturn(chain);
        when(allowance.anyRoomToday(STATION, chain)).thenReturn(false);

        runQueue();

        verify(queue).waitUntil(11, LocalDate.now().plusDays(1));
        verify(queue, never()).markFailed(anyInt());
        assertEquals(0, relay.getReceivedMessages().length);
    }

    @Test
    void stationMailFailsWhereTheStationHasNothingToSendThrough() {
        queued(STATION);
        when(chains.forStation(STATION)).thenReturn(List.of());

        runQueue();

        verify(queue).markFailed(11);
        verify(queue, never()).waitUntil(anyInt(), any());
    }

    /**
     * Its own provider is spent, so the mail walks on to the instance's provider after it and is
     * recorded against that provider's place in the instance's list.
     */
    @Test
    void stationMailMovesOnToTheInstanceAndIsCountedThere() {
        queued(STATION);
        var own = relayEntry(0);
        var lent = relayEntry(1).asInstanceProvider(0);
        var chain = List.of(own, lent);
        when(chains.forStation(STATION)).thenReturn(chain);
        when(allowance.anyRoomToday(STATION, chain)).thenReturn(true);
        when(allowance.hasRoomToday(STATION, own)).thenReturn(false);
        when(allowance.hasRoomToday(STATION, lent)).thenReturn(true);

        runQueue();

        verify(queue).advanceProvider(11);
        verify(queue).markSent(11, 0);
        verify(queue, never()).incrementDailyCount(any());
        assertEquals(1, relay.getReceivedMessages().length);
    }

    @Test
    void instanceMailIsCountedAgainstItsProvider() {
        queued(null);
        var chain = chains.forInstance();
        when(allowance.hasRoomToday(eq(null), eq(chain.getFirst()))).thenReturn(true);

        runQueue();

        verify(queue).markSent(11, 0);
        verify(queue).incrementDailyCount(LocalDate.now());
        assertEquals(1, relay.getReceivedMessages().length);
    }

    @Test
    void instanceMailWithoutRoomIsHeldForTheNextRound() {
        queued(null);

        runQueue();

        verify(queue).requeue(11);
        verify(queue, never()).waitUntil(anyInt(), any());
    }
}
