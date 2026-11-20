/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.events.entity.AwaitingAnswer;
import dev.chojo.ember.feature.events.entity.CancellationCause;
import dev.chojo.ember.feature.events.entity.RegistrationDeadline;
import dev.chojo.ember.feature.events.entity.RegistrationStatus;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The answers a household still owes cover every date of a series: each member is listed at the
 * soonest date they have not answered whose sign-up is still open.
 */
class AwaitingAnswersTest extends RepositoryTestBase {
    private static final RegistrationDeadline TWO_DAYS =
            RegistrationDeadline.relative(2 * 24 * 60, RegistrationDeadline.DeadlineAnchor.EXACT);

    private static Station station;
    private static Account account;
    private static StationMember member;

    @BeforeAll
    static void setup() {
        station = stationRepo.create("Awaiting Answers Station");
        account = accountRepo.create("awaiting-answers@test.com", "Offene", "Antwort");
        member = stationMemberRepo.create(station.id(), account.id());
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
        accountRepo.delete(account.id());
    }

    private static ZoneId zone() {
        return occurrenceCalendar.zoneOf(station.id());
    }

    private static LocalDate today() {
        return LocalDate.now(zone());
    }

    private static StationEvent appointment(
            StationEvent.EventType type, LocalDate first, RegistrationDeadline deadline) {
        Instant start = first.atTime(18, 0).atZone(zone()).toInstant();
        return eventRepo.create(
                station.id(),
                "Dienst " + first,
                "desc",
                type,
                type == StationEvent.EventType.ONE_TIME
                        ? null
                        : first.getDayOfWeek().getValue(),
                start,
                start.plus(Duration.ofHours(2)),
                null,
                true,
                deadline,
                false,
                null,
                null,
                null,
                null);
    }

    private static List<AwaitingAnswer> awaitingFor(StationEvent event) {
        return awaitingAnswers().find(List.of(member.id())).stream()
                .filter(answer -> answer.eventId() == event.id())
                .toList();
    }

    private static List<LocalDate> datesOf(StationEvent event) {
        return awaitingFor(event).stream().map(AwaitingAnswer::eventDate).toList();
    }

    @Test
    void aSeriesListsItsNextOpenDateWithItsTimes() {
        var first = today().plusDays(5);
        var series = appointment(StationEvent.EventType.RECURRING, first, TWO_DAYS);
        try {
            var listed = awaitingFor(series);

            assertEquals(1, listed.size(), "one entry per member, not one per date");
            var entry = listed.getFirst();
            assertEquals(first, entry.eventDate());
            var start = first.atTime(18, 0).atZone(zone()).toInstant();
            assertEquals(start, entry.startTime());
            assertEquals(start.minus(Duration.ofDays(2)), entry.registrationClosesAt());
            assertEquals(member.id(), entry.memberId());
        } finally {
            eventRepo.delete(series.id());
        }
    }

    @Test
    void anAnsweredDateGivesWayToTheNextOne() {
        var first = today().plusDays(5);
        var series = appointment(StationEvent.EventType.RECURRING, first, TWO_DAYS);
        try {
            eventRegistrationRepo.create(series.id(), member.id(), first, RegistrationStatus.DECLINED, null);

            assertEquals(List.of(first.plusWeeks(1)), datesOf(series));
        } finally {
            eventRepo.delete(series.id());
        }
    }

    @Test
    void aDateWhoseSignUpClosedIsNotListed() {
        var first = today().plusDays(1);
        var series = appointment(StationEvent.EventType.RECURRING, first, TWO_DAYS);
        try {
            assertEquals(List.of(first.plusWeeks(1)), datesOf(series));
        } finally {
            eventRepo.delete(series.id());
        }
    }

    @Test
    void aCalledOffDateIsNotListed() {
        var first = today().plusDays(5);
        var series = appointment(StationEvent.EventType.RECURRING, first, TWO_DAYS);
        try {
            eventDateCancellationRepo.cancel(series.id(), first, CancellationCause.MANUAL, null, null);

            assertEquals(List.of(first.plusWeeks(1)), datesOf(series));
        } finally {
            eventRepo.delete(series.id());
        }
    }

    @Test
    void aFixedDeadlineOnASeriesListsTheNextDateTheMemberHasNotAnswered() {
        var first = today().plusDays(5);
        var closes = Instant.now().plus(Duration.ofDays(3)).truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        var series = appointment(StationEvent.EventType.RECURRING, first, RegistrationDeadline.fixed(closes));
        try {
            assertEquals(List.of(first), datesOf(series));

            eventRegistrationRepo.create(series.id(), member.id(), first, RegistrationStatus.DECLINED, null);
            var next = awaitingFor(series);
            assertEquals(
                    List.of(first.plusWeeks(1)),
                    next.stream().map(AwaitingAnswer::eventDate).toList(),
                    "the one instant closes every date, so the following one is still owed");
            assertEquals(closes, next.getFirst().registrationClosesAt());
        } finally {
            eventRepo.delete(series.id());
        }
    }

    @Test
    void aFixedDeadlineThatPassedListsNoDateOfTheSeries() {
        var series = appointment(
                StationEvent.EventType.RECURRING,
                today().plusDays(5),
                RegistrationDeadline.fixed(Instant.now().minus(Duration.ofHours(1))));
        try {
            assertTrue(datesOf(series).isEmpty());
        } finally {
            eventRepo.delete(series.id());
        }
    }

    /** Two members of one household owe different dates of one series and are listed each at their own. */
    @Test
    void membersOfOneHouseholdAreListedAtTheirOwnDates() {
        var siblingAccount = accountRepo.create("awaiting-answers-sibling@test.com", "Zweite", "Antwort");
        var sibling = stationMemberRepo.create(station.id(), siblingAccount.id());
        var first = today().plusDays(5);
        var series = appointment(StationEvent.EventType.RECURRING, first, TWO_DAYS);
        try {
            eventRegistrationRepo.create(series.id(), member.id(), first, RegistrationStatus.ACCEPTED, null);

            var listed = awaitingAnswers().find(List.of(member.id(), sibling.id())).stream()
                    .filter(answer -> answer.eventId() == series.id())
                    .toList();

            assertEquals(2, listed.size());
            assertEquals(sibling.id(), listed.get(0).memberId(), "the soonest close comes first");
            assertEquals(first, listed.get(0).eventDate());
            assertEquals(member.id(), listed.get(1).memberId());
            assertEquals(first.plusWeeks(1), listed.get(1).eventDate());
        } finally {
            eventRepo.delete(series.id());
            accountRepo.delete(siblingAccount.id());
        }
    }

    @Test
    void aOneTimeAppointmentIsListedHoweverFarItsCloseLies() {
        var day = today().plusDays(120);
        var event = appointment(StationEvent.EventType.ONE_TIME, day, TWO_DAYS);
        try {
            assertEquals(List.of(day), datesOf(event));

            eventRegistrationRepo.create(event.id(), member.id(), day, RegistrationStatus.DECLINED, null);
            assertTrue(datesOf(event).isEmpty(), "an answer settles it");
        } finally {
            eventRepo.delete(event.id());
        }
    }

    @Test
    void aSeriesWhoseNextCloseIsFarOffIsNotUrgentYet() {
        var first = today().plusDays(5 + AwaitingAnswers.SERIES_LOOK_AHEAD.toDays());
        var series = appointment(StationEvent.EventType.RECURRING, first, TWO_DAYS);
        try {
            assertTrue(datesOf(series).isEmpty());
        } finally {
            eventRepo.delete(series.id());
        }
    }
}
