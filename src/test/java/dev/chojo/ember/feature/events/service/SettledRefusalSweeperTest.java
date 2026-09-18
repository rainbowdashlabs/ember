/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.events.entity.EventFieldType;
import dev.chojo.ember.feature.events.entity.EventRegistrationFieldConfig;
import dev.chojo.ember.feature.events.entity.RegistrationStatus;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.repository.EventRegistrationFieldRepository;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * What the sweep lets go of, and what it leaves alone.
 *
 * <p>Both halves matter. Keeping too much holds somebody's answers to an appointment they are not
 * going to, for no reason anybody could name; taking too much empties a registration that could still
 * be put back, which would make the undo worse than no undo at all.
 */
class SettledRefusalSweeperTest extends RepositoryTestBase {

    private static Station station;
    private static Account account;
    private static StationMember member;
    private static StationEvent event;
    private static int fieldId;

    private final EventRegistrationFieldRepository fields = new EventRegistrationFieldRepository();

    @BeforeAll
    static void setup() {
        station = stationRepo.create("SettledRefusalStation");
        account = accountRepo.create("settled-refusal@test.com", "Settled", "Refusal");
        member = stationMemberRepo.create(station.id(), account.id());
        event = eventRepo.create(
                station.id(),
                "Aufräumen",
                "desc",
                StationEvent.EventType.ONE_TIME,
                null,
                Instant.parse("2029-03-01T09:00:00Z"),
                Instant.parse("2029-03-01T11:00:00Z"),
                null,
                true,
                null,
                false,
                null,
                null,
                null,
                null,
                null);
        var field = new EventRegistrationFieldRepository()
                .create(event.id(), "Shirtgröße", EventFieldType.STRING, EventRegistrationFieldConfig.empty(), 0, true);
        fieldId = field.id();
    }

    @AfterAll
    static void cleanup() {
        eventRepo.delete(event.id());
        stationRepo.delete(station.id());
        accountRepo.delete(account.id());
    }

    /** Backdates the moment the answer was given, which is what the sweep measures from. */
    private static void refusedAgo(int registrationId, Duration ago) {
        query("UPDATE event_registration SET status_changed_at = now() - CAST(:ago AS INTERVAL) WHERE id = :id;")
                .single(call().bind("id", registrationId).bind("ago", ago.toSeconds() + " seconds"))
                .update();
    }

    @Test
    void theSweepClearsSettledRefusalsAndLeavesTheRestAlone() {
        var sweeper = new SettledRefusalSweeper(
                newEventServices(new DomainEventBus(Set.of())).registration());
        LocalDate day = LocalDate.of(2029, 3, 1);

        var justNow = eventRegistrationRepo.create(event.id(), member.id(), day, RegistrationStatus.WITHDRAWN, null);
        fields.setValue(justNow.id(), fieldId, "\"M\"");
        assertFalse(fields.findValues(justNow.id()).isEmpty(), "the answers are there to begin with");

        sweeper.sweep();
        assertFalse(
                fields.findValues(justNow.id()).isEmpty(),
                "a refusal that can still be taken back keeps what it would give back");

        refusedAgo(justNow.id(), Duration.ofMinutes(30));
        sweeper.sweep();
        assertTrue(
                fields.findValues(justNow.id()).isEmpty(),
                "and once it can no longer be taken back there is nothing left to hold them for");
    }

    /**
     * A sweep that throws must not take its scheduler down with it.
     *
     * <p>It runs every few minutes for the life of the process, so a database that is briefly away has
     * to cost one round and not all of them. Swallowing the failure is the point, and a test that did
     * not check it would leave the one line that matters here unproven.
     */
    @Test
    void aSweepThatFailsIsSurvived() {
        var brokenService = mock(EventRegistrationService.class);
        when(brokenService.sweepAnswersOfSettledRefusals()).thenThrow(new IllegalStateException("database away"));

        var sweeper = new SettledRefusalSweeper(brokenService);

        assertDoesNotThrow(sweeper::sweep, "the round is lost, the sweeper is not");
    }
}
