/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.mail.service;

import dev.chojo.ember.feature.mail.entity.MailChainEntry;
import dev.chojo.ember.feature.mail.entity.SmtpEncryption;
import dev.chojo.ember.feature.mail.entity.StationPoolUse;
import dev.chojo.ember.feature.mail.repository.EmailQueueRepository;
import dev.chojo.ember.feature.mail.repository.InstanceMailGrantRepository;
import dev.chojo.ember.feature.mail.repository.MailProviderBlockRepository;
import dev.chojo.ember.feature.station.entity.MailProviderType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MailDashboardServiceTest {
    private static final int STATION = 4;
    private static final UUID NORD = UUID.fromString("00000000-0000-0000-0000-000000000004");

    private final EmailQueueRepository queue = mock(EmailQueueRepository.class);
    private final MailProviderBlockRepository blocks = mock(MailProviderBlockRepository.class);
    private final MailChainService chains = mock(MailChainService.class);
    private final MailAllowance allowance = mock(MailAllowance.class);
    private final InstanceMailGrantRepository grants = mock(InstanceMailGrantRepository.class);
    private final MailDashboardService service = new MailDashboardService(queue, chains, blocks, allowance, grants);

    private static MailChainEntry entry(int position, int dailyLimit) {
        return new MailChainEntry(
                position,
                MailProviderType.BREVO,
                "",
                587,
                SmtpEncryption.STARTTLS,
                "user",
                "secret",
                "key",
                "post@instance.test",
                "Ember",
                2,
                dailyLimit,
                "",
                "");
    }

    @BeforeEach
    void setup() {
        when(queue.summary(any())).thenReturn(new EmailQueueRepository.QueueSummary(1, 0, 3, 0, 0, null));
        when(queue.pendingByProvider(any())).thenReturn(Map.of());
        when(queue.recent(any(), anyInt())).thenReturn(List.of());
        when(queue.stuck(any(), anyInt())).thenReturn(List.of());
        when(blocks.list(any())).thenReturn(List.of());
        when(allowance.stationPool(100)).thenReturn(OptionalInt.of(50));
        when(allowance.stationPool(0)).thenReturn(OptionalInt.empty());
        when(allowance.sharePercent()).thenReturn(50);
        when(allowance.hasRoomToday(any(), any())).thenReturn(true);
    }

    /**
     * The instance's overview counts everything a provider sent, its own mail and the stations',
     * and says how much of the stations' share is used and by whom.
     */
    @Test
    void theInstanceSeesEveryProvidersShareAndWhoUsedIt() {
        var limited = entry(0, 100).asInstanceProvider(0);
        var unlimited = entry(1, 0).asInstanceProvider(1);
        when(chains.forInstance()).thenReturn(List.of(limited, unlimited));
        when(allowance.sentToday(null, limited)).thenReturn(60);
        when(allowance.stationShareSentToday(0)).thenReturn(20);
        when(queue.stationPoolUse(any())).thenReturn(List.of(new StationPoolUse(0, NORD, "Nord", 20)));
        when(grants.countGranted()).thenReturn(2);

        var dashboard = service.forOwner(null);

        var first = dashboard.providers().getFirst();
        assertEquals(60, first.sentToday(), "everything the provider sent");
        assertFalse(first.viaInstance());
        assertEquals(50, first.pool().limit(), "half of 100");
        assertEquals(20, first.pool().sentToday());
        assertEquals("Nord", first.pool().stations().getFirst().name());
        assertNull(dashboard.providers().get(1).pool().limit(), "no limit, no share");
        assertTrue(dashboard.providers().get(1).pool().stations().isEmpty());
        assertEquals(50, dashboard.pool().sharePercent());
        assertEquals(2, dashboard.pool().grantedStations());
    }

    /**
     * A station's overview marks the instance's providers at the end of its list, counts only its
     * own mail there and names no other station.
     */
    @Test
    void aStationSeesTheInstancesProvidersAfterItsOwnWithoutTheOthers() {
        var own = entry(0, 0);
        var lent = entry(1, 100).asInstanceProvider(0);
        when(chains.forStation(STATION)).thenReturn(List.of(own, lent));
        when(allowance.sentToday(STATION, lent)).thenReturn(4);
        when(allowance.hasRoomToday(eq(STATION), eq(lent))).thenReturn(false);
        when(allowance.stationShareSentToday(0)).thenReturn(50);

        var dashboard = service.forOwner(STATION);

        assertNull(dashboard.providers().getFirst().pool(), "its own provider has no share");
        assertFalse(dashboard.providers().getFirst().viaInstance());
        var standing = dashboard.providers().get(1);
        assertTrue(standing.viaInstance());
        assertEquals(4, standing.sentToday(), "its own mail only");
        assertTrue(standing.exhausted(), "the share is spent");
        assertEquals(50, standing.pool().sentToday());
        assertTrue(standing.pool().stations().isEmpty(), "no other station is named");
        assertNull(dashboard.pool(), "the overview of the lending is the instance's");
    }

    @Test
    void aBlockIsLiftedForTheOwnerItWasPutOn() {
        service.liftBlock(4, MailProviderType.BREVO, "example.org");

        verify(blocks).lift(4, MailProviderType.BREVO, "example.org");
    }

    @Test
    void stuckMailsAreQueuedAgain() {
        when(queue.requeueStuck(null, 9)).thenReturn(1);

        assertEquals(1, service.requeueStuck(null, 9).requeued());
    }
}
