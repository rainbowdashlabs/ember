/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.feature.events.entity.AwaitingAnswer;
import dev.chojo.ember.feature.events.entity.StationCalendar;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.repository.EventRegistrationRepository;
import dev.chojo.ember.feature.events.repository.EventRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The dates of appointments still waiting on an answer from a household, built from the same dates
 * the closing warnings are about.
 *
 * <p>Each appointment is listed once per member, at the soonest date that member has not answered
 * and whose sign-up is still open. A one-time appointment has one such date, however far its close
 * lies. A series is looked at {@link #SERIES_LOOK_AHEAD} ahead, so a member who answered the next
 * dates is shown the first one after them: for one that closes each date on its own, the dates whose
 * close lies within it; for one with a fixed deadline, which closes every date at the same instant,
 * the dates within it while that instant is ahead. Anything further off is not urgent yet.
 *
 * <p>Eligibility is not decided here: it depends on the reader's permissions, and the caller filters.
 */
@Singleton
public class AwaitingAnswers {
    /** How far ahead the closes of a series that closes each date on its own are looked at. */
    static final Duration SERIES_LOOK_AHEAD = Duration.ofDays(56);

    /** How far ahead a single close is looked at, which is as good as without a limit. */
    private static final Duration SINGLE_LOOK_AHEAD = Duration.ofDays(3650);

    private final EventRepository eventRepository;
    private final EventRegistrationRepository registrationRepository;
    private final OccurrenceCalendar occurrenceCalendar;

    @Inject
    public AwaitingAnswers(
            EventRepository eventRepository,
            EventRegistrationRepository registrationRepository,
            OccurrenceCalendar occurrenceCalendar) {
        this.eventRepository = eventRepository;
        this.registrationRepository = registrationRepository;
        this.occurrenceCalendar = occurrenceCalendar;
    }

    /**
     * The dates still waiting on an answer from any of the given members.
     *
     * @param memberIds the reader and everyone they answer for
     * @return one entry per appointment and member, soonest close first
     */
    public List<AwaitingAnswer> find(List<Integer> memberIds) {
        var stations = registrationRepository.findCurrentStationsOf(memberIds);
        if (stations.isEmpty()) return List.of();
        var now = Instant.now();
        var membersByStation = stations.entrySet().stream()
                .collect(Collectors.groupingBy(
                        Map.Entry::getValue, Collectors.mapping(Map.Entry::getKey, Collectors.toList())));
        var awaiting = new ArrayList<AwaitingAnswer>();
        membersByStation.forEach((stationId, members) -> awaiting.addAll(forStation(stationId, members, now)));
        awaiting.sort(Comparator.comparing(AwaitingAnswer::registrationClosesAt)
                .thenComparingInt(AwaitingAnswer::eventId)
                .thenComparingInt(AwaitingAnswer::memberId));
        return awaiting;
    }

    private List<AwaitingAnswer> forStation(int stationId, List<Integer> members, Instant now) {
        var calendar = occurrenceCalendar.forStation(stationId);
        var answered = registrationRepository.findAnsweredDates(members, calendar.today());
        var awaiting = new ArrayList<AwaitingAnswer>();
        for (var event : eventRepository.findWithDeadline(stationId)) {
            var open = openDates(event, calendar, now);
            for (int memberId : members) {
                open.stream()
                        .filter(date -> !answered.contains(
                                new EventRegistrationRepository.AnsweredDate(event.id(), memberId, date.date())))
                        .findFirst()
                        .flatMap(date -> event.occurrenceOn(date.date(), calendar.zone())
                                .map(span -> new AwaitingAnswer(
                                        event.id(),
                                        event.name(),
                                        date.date(),
                                        span.start(),
                                        date.closes(),
                                        event.categoryId(),
                                        memberId)))
                        .ifPresent(awaiting::add);
            }
        }
        return awaiting;
    }

    private static List<ClosingDates.ClosingDate> openDates(StationEvent event, StationCalendar calendar, Instant now) {
        if (!event.isRecurring()) return ClosingDates.of(event, calendar, now, now.plus(SINGLE_LOOK_AHEAD));
        if (event.closesPerDate()) return ClosingDates.of(event, calendar, now, now.plus(SERIES_LOOK_AHEAD));
        var lastDay = calendar.today().plusDays(SERIES_LOOK_AHEAD.toDays());
        return ClosingDates.openDatesOfFixedSeries(event, calendar, now, lastDay);
    }
}
