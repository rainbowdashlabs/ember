/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.event.events.PartnerRegistrationStatusChanged;
import dev.chojo.ember.feature.events.entity.EventRegistration;
import dev.chojo.ember.feature.events.entity.QueueEntry;
import dev.chojo.ember.feature.events.entity.RegistrationStatus;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.repository.EventRegistrationRepository;
import dev.chojo.ember.feature.events.repository.EventRepository;
import dev.chojo.ember.feature.events.repository.RegistrationQueueRepository;
import dev.chojo.ember.feature.events.repository.RegistrationQueueRepository.QueueList;
import dev.chojo.ember.feature.events.repository.RegistrationQueueRepository.QueueOwner;
import dev.chojo.ember.util.sql.Transactions;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Moves people up from the waiting list of an appointment date while places are free.
 *
 * <p>The first in line takes the first free place, then the next, until the date is full or nobody
 * waits. Whoever moves up is accepted at once and skips a decision: whoever runs the appointment
 * already looked at them when they put them on the list, or nobody decides on this appointment. A
 * partner's member in line moves up in turn like anybody else, as long as the partner has one of its
 * own places left; otherwise they keep waiting and the next in line takes the place. Moving up runs until the date starts,
 * past the sign-up deadline, and stops there; whoever still waits then did not get a place. A date
 * that is called off, falls into a break or is no longer a date of the series moves nobody up.
 *
 * <p>An entry whose sign-up no longer waits leaves the list without taking a place, so a list that
 * drifted from the statuses never stalls. Callers hold the appointment's lock and notify whoever moved
 * up once the change is kept; a partner's member moving up is announced here, for its station to be notified
 * once the change is kept.
 */
@Singleton
public class QueueAdvancer {
    private static final Logger log = LoggerFactory.getLogger(QueueAdvancer.class);

    private final EventRepository events;
    private final EventCapacity capacity;
    private final RegistrationQueueRepository queue;
    private final EventRegistrationRepository registrations;
    private final OccurrenceCalendar calendar;
    private final DomainEventBus eventBus;
    private final Clock clock;

    /**
     * Creates the advancer on the system clock.
     *
     * @param events        the appointments
     * @param capacity      the places of a date
     * @param queue         the waiting lists
     * @param registrations the registrations moved up
     * @param calendar      the station's clock, for when a date starts
     * @param eventBus      where a partner's member moving up is announced, so the partner is notified
     */
    @Inject
    public QueueAdvancer(
            EventRepository events,
            EventCapacity capacity,
            RegistrationQueueRepository queue,
            EventRegistrationRepository registrations,
            OccurrenceCalendar calendar,
            DomainEventBus eventBus) {
        this(events, capacity, queue, registrations, calendar, eventBus, Clock.systemUTC());
    }

    /**
     * Creates the advancer.
     *
     * @param events        the appointments
     * @param capacity      the places of a date
     * @param queue         the waiting lists
     * @param registrations the registrations moved up
     * @param calendar      the station's clock, for when a date starts
     * @param eventBus      where a partner's member moving up is announced, so the partner is notified
     * @param clock         the current time
     */
    public QueueAdvancer(
            EventRepository events,
            EventCapacity capacity,
            RegistrationQueueRepository queue,
            EventRegistrationRepository registrations,
            OccurrenceCalendar calendar,
            DomainEventBus eventBus,
            Clock clock) {
        this.events = events;
        this.capacity = capacity;
        this.queue = queue;
        this.registrations = registrations;
        this.calendar = calendar;
        this.eventBus = eventBus;
        this.clock = clock;
    }

    /**
     * Fills the free places of a date from its waiting list.
     *
     * @param eventId   the appointment
     * @param eventDate the date
     * @return the registrations of this station's members who moved up, as stored
     */
    public List<EventRegistration> fill(int eventId, LocalDate eventDate) {
        var event = events.findById(eventId).orElse(null);
        if (event == null
                || !calendar.forStation(event.stationId()).takesPlaceOn(event, eventDate)
                || started(event, eventDate)) return List.of();
        var list = RegistrationQueueRepository.ofDate(eventId, eventDate);
        var moved = new ArrayList<EventRegistration>();
        for (var entry : queue.entries(list)) {
            if (capacity.of(eventId, eventDate).full()) break;
            moveUp(event.stationId(), list, entry).ifPresent(moved::add);
        }
        if (!moved.isEmpty()) log.info("Moved {} up on event {} ({})", moved.size(), eventId, eventDate);
        return moved;
    }

    /**
     * Gives one entry of a list the place that is free. An entry whose sign-up no longer waits only
     * leaves the list.
     *
     * @return the registration of this station's member who moved up, as stored
     */
    private Optional<EventRegistration> moveUp(int stationId, QueueList list, QueueEntry entry) {
        Integer local = entry.localId();
        Integer partner = entry.partnerId();
        if (partner != null) {
            if (queue.acceptPartner(partner)) {
                var changedAt = clock.instant();
                Transactions.afterCommit(() -> eventBus.publish(new PartnerRegistrationStatusChanged(
                        stationId, partner, RegistrationStatus.ACCEPTED, null, true, changedAt)));
            } else if (!queue.partnerWaits(partner)) {
                queue.remove(list, QueueOwner.ofPartner(partner));
            }
            return Optional.empty();
        }
        if (local == null) return Optional.empty();
        boolean waiting = registrations
                .findById(local)
                .filter(registration -> registration.status() == RegistrationStatus.QUEUED)
                .isPresent();
        queue.remove(list, QueueOwner.local(local));
        if (!waiting) return Optional.empty();
        registrations.updateStatus(local, RegistrationStatus.ACCEPTED);
        return registrations.findById(local);
    }

    /**
     * Fills the free places of every date of an appointment from one day on that has a waiting list,
     * after its limit was raised or removed.
     *
     * @param eventId the appointment
     * @param from    the first date looked at
     * @return the registrations of this station's members who moved up, as stored
     */
    public List<EventRegistration> fillFrom(int eventId, LocalDate from) {
        var moved = new ArrayList<EventRegistration>();
        for (var date : queue.datesWaitedFor(eventId, from)) moved.addAll(fill(eventId, date));
        return moved;
    }

    /**
     * Whether a date starts in less than a day.
     *
     * @param event     the appointment
     * @param eventDate the date
     * @return true where the start is less than 24 hours away
     */
    public boolean startsWithinADay(StationEvent event, LocalDate eventDate) {
        return withinADay(clock, OccurrenceWindows.startOf(event, eventDate, calendar.zoneOf(event.stationId())));
    }

    /**
     * Whether a start lies less than a day ahead, which a member moving up is notified of plainly.
     *
     * @param clock the current time
     * @param start the start
     * @return true where the start is less than 24 hours away
     */
    static boolean withinADay(Clock clock, Instant start) {
        return start.isBefore(clock.instant().plus(Duration.ofDays(1)));
    }

    private boolean started(StationEvent event, LocalDate date) {
        var start = OccurrenceWindows.startOf(event, date, calendar.zoneOf(event.stationId()));
        return !clock.instant().isBefore(start);
    }
}
