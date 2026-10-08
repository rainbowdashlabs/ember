/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.mail.service;

import dev.chojo.ember.conf.file.elements.Mailing;
import dev.chojo.ember.feature.mail.entity.InstanceMailGrant;
import dev.chojo.ember.feature.mail.entity.MailChainEntry;
import dev.chojo.ember.feature.mail.entity.SmtpEncryption;
import dev.chojo.ember.feature.mail.repository.EmailQueueRepository;
import dev.chojo.ember.feature.mail.repository.InstanceMailGrantRepository;
import dev.chojo.ember.feature.station.entity.MailProviderType;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * How much a provider may still send today. The instance's own mail answers to a provider's daily
 * limit alone; a station's mail through an instance provider also answers to the share all stations
 * may use of it and to the station's own daily limit there.
 */
class MailAllowanceTest {
    private static final int STATION = 7;

    private final EmailQueueRepository queue = mock(EmailQueueRepository.class);
    private final InstanceMailGrantRepository grants = mock(InstanceMailGrantRepository.class);
    private final Mailing mailing = new Mailing();
    private MailAllowance allowance;

    @BeforeEach
    void setup() {
        mailing.stationShare(50);
        allowance = new MailAllowance(mailing, queue, grants);
    }

    private static MailChainEntry instanceEntry(int dailyLimit) {
        return own(dailyLimit).withPosition(2).asInstanceProvider(0);
    }

    private static MailChainEntry own(int dailyLimit) {
        return new MailChainEntry(
                0,
                MailProviderType.SMTP,
                "smtp.example",
                587,
                SmtpEncryption.STARTTLS,
                "user",
                "secret",
                "",
                "post@example",
                "Wache",
                2,
                dailyLimit,
                "",
                "");
    }

    private void granted(@Nullable Integer dailyLimit) {
        when(grants.find(STATION)).thenReturn(Optional.of(new InstanceMailGrant(STATION, Instant.EPOCH, dailyLimit)));
    }

    private void sent(int throughProvider, int byStations, int byThisStation) {
        when(queue.instanceProviderDailyCount(any(), eq(0))).thenReturn(throughProvider);
        when(queue.stationShareDailyCount(any(), eq(0))).thenReturn(byStations);
        when(queue.stationViaInstanceDailyCount(any(), eq(STATION), isNull())).thenReturn(byThisStation);
    }

    @Test
    void theStationsShareIsRoundedDown() {
        assertEquals(OptionalInt.of(50), MailAllowance.stationPool(100, 50));
        assertEquals(OptionalInt.of(50), MailAllowance.stationPool(101, 50));
        assertEquals(OptionalInt.of(0), MailAllowance.stationPool(1, 50), "half of one mail is none");
        assertEquals(OptionalInt.of(0), MailAllowance.stationPool(300, 0));
        assertEquals(OptionalInt.of(300), MailAllowance.stationPool(300, 100));
        assertEquals(OptionalInt.of(300), MailAllowance.stationPool(300, 250), "never more than the whole");
    }

    @Test
    void aProviderWithoutALimitHasNoShareToTakeAPartOf() {
        assertTrue(MailAllowance.stationPool(0, 50).isEmpty());
        assertTrue(allowance.stationPool(0).isEmpty());
        assertEquals(OptionalInt.of(20), allowance.stationPool(40));
    }

    @Test
    void theInstancesOwnMailUsesTheWholeLimitWhateverTheStationsSent() {
        sent(99, 80, 0);

        assertTrue(allowance.hasRoomToday(null, instanceEntry(100)), "the share is no cap on the instance");
        sent(100, 80, 0);
        assertFalse(allowance.hasRoomToday(null, instanceEntry(100)), "but the provider's limit is");
    }

    @Test
    void aStationStopsWhereAllStationsTogetherHaveSpentTheirShare() {
        granted(null);
        sent(70, 49, 3);

        assertTrue(allowance.hasRoomToday(STATION, instanceEntry(100)));
        sent(70, 50, 3);
        assertFalse(allowance.hasRoomToday(STATION, instanceEntry(100)), "the other half is the instance's");
    }

    @Test
    void aStationStopsWhereTheProviderItselfIsSpentEvenWithShareLeft() {
        granted(null);
        sent(100, 10, 3);

        assertFalse(allowance.hasRoomToday(STATION, instanceEntry(100)));
    }

    @Test
    void aStationStopsAtItsOwnDailyLimit() {
        granted(5);
        sent(10, 4, 4);

        assertTrue(allowance.hasRoomToday(STATION, instanceEntry(100)));
        sent(10, 5, 5);
        assertFalse(allowance.hasRoomToday(STATION, instanceEntry(100)), "its own limit is reached");
    }

    @Test
    void onAProviderWithoutALimitOnlyTheStationsOwnLimitApplies() {
        granted(null);
        sent(10_000, 9_000, 9_000);
        assertTrue(allowance.hasRoomToday(STATION, instanceEntry(0)), "no limit, no share");

        granted(9_000);
        assertFalse(allowance.hasRoomToday(STATION, instanceEntry(0)), "the station's own limit still holds");
    }

    @Test
    void aStationWithoutAGrantHasNoRoomOnTheInstancesProviders() {
        when(grants.find(STATION)).thenReturn(Optional.empty());
        sent(0, 0, 0);

        assertFalse(allowance.hasRoomToday(STATION, instanceEntry(100)));
    }

    @Test
    void aStationsOwnProviderAnswersToItsOwnLimitAlone() {
        when(queue.ownProviderDailyCount(any(), eq(STATION), eq(0))).thenReturn(9);

        assertTrue(allowance.hasRoomToday(STATION, own(10)));
        assertFalse(allowance.hasRoomToday(STATION, own(9)));
        assertFalse(allowance.hasRoomToday(null, own(10)), "an own provider belongs to a station");
        assertEquals(9, allowance.sentToday(STATION, own(10)));
        assertEquals(0, allowance.sentToday(null, own(10)));
    }

    @Test
    void anyRoomLooksAtEveryProviderOfTheChain() {
        granted(null);
        when(queue.ownProviderDailyCount(any(), eq(STATION), anyInt())).thenReturn(10);
        sent(0, 0, 0);

        assertTrue(allowance.anyRoomToday(STATION, List.of(own(10), instanceEntry(100))), "the instance takes over");
        assertFalse(allowance.anyRoomToday(STATION, List.of(own(10))), "its own alone is spent");
    }

    @Test
    void whatWasSentTodayIsCountedForWhoeverAsks() {
        sent(40, 25, 6);
        when(queue.stationViaInstanceDailyCount(any(), eq(STATION), eq(0))).thenReturn(4);

        assertEquals(40, allowance.sentToday(null, instanceEntry(100)), "the instance sees everything");
        assertEquals(4, allowance.sentToday(STATION, instanceEntry(100)), "a station sees its own");
        assertEquals(25, allowance.stationShareSentToday(0));
        assertEquals(6, allowance.stationSentViaInstanceToday(STATION));
    }
}
