/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.events.entity.RegistrationStatus;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.storage.service.StationReadOnlyGuard;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** The sign-up deadline answers for whoever waits for a decision, never for whoever waits for a place. */
class WaitingThroughTheDeadlineTest extends RepositoryTestBase {
    private static Station station;
    private static Account pendingAccount;
    private static Account waitingAccount;
    private static StationMember pending;
    private static StationMember waiting;

    @BeforeAll
    static void setup() {
        station = stationRepo.create("Waiting Through Deadline Station");
        pendingAccount = accountRepo.create("deadline-pending@test.com", "Offen", "Ist");
        waitingAccount = accountRepo.create("deadline-waiting@test.com", "Warte", "Nd");
        pending = stationMemberRepo.create(station.id(), pendingAccount.id());
        waiting = stationMemberRepo.create(station.id(), waitingAccount.id());
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
        accountRepo.delete(pendingAccount.id());
        accountRepo.delete(waitingAccount.id());
    }

    @Test
    void theDeadlineDeclinesWhoWaitsForADecisionAndLeavesTheWaitingListAlone() {
        var start = Instant.now().plus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);
        var zone = occurrenceCalendar.zoneOf(station.id());
        var event = eventRepo.create(
                station.id(),
                "Wochendienst",
                "desc",
                StationEvent.EventType.RECURRING,
                start.atZone(zone).getDayOfWeek().getValue(),
                start,
                start.plus(2, ChronoUnit.HOURS),
                null,
                true,
                null,
                true,
                null,
                1,
                null,
                null,
                30);
        try {
            var date = start.atZone(zone).toLocalDate();
            query("UPDATE station_event SET registration_close_days = 30 WHERE id = :id;")
                    .single(call().bind("id", event.id()))
                    .update();
            var asked = eventRegistrationRepo.create(event.id(), pending.id(), date, RegistrationStatus.PENDING, null);
            var inLine = eventRegistrationRepo.create(event.id(), waiting.id(), date, RegistrationStatus.QUEUED, null);
            var guard = mock(StationReadOnlyGuard.class);
            when(guard.isWritable(anyInt())).thenReturn(true);
            var checker = new RegistrationDeadlineChecker(
                    eventRepo,
                    eventRegistrationRepo,
                    newEventServices(new DomainEventBus(Set.of())).registration(),
                    new DomainEventBus(Set.of()),
                    occurrenceCalendar,
                    guard);

            checker.scheduledTasks().getFirst().work().run();

            assertEquals(
                    RegistrationStatus.DECLINED,
                    eventRegistrationRepo.findById(asked.id()).orElseThrow().status());
            assertEquals(
                    RegistrationStatus.QUEUED,
                    eventRegistrationRepo.findById(inLine.id()).orElseThrow().status());
        } finally {
            eventRepo.delete(event.id());
        }
    }
}
