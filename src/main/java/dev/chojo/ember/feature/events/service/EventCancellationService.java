/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.event.events.EventCancelled;
import dev.chojo.ember.event.events.EventDateRestored;
import dev.chojo.ember.feature.equipment.service.EquipmentReleaseService;
import dev.chojo.ember.feature.events.entity.CancellationCause;
import dev.chojo.ember.feature.events.entity.CancellationNotice;
import dev.chojo.ember.feature.events.entity.CancelledEventDate;
import dev.chojo.ember.feature.events.entity.EventDateCancellation;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.repository.EventDateCancellationRepository;
import dev.chojo.ember.feature.events.repository.EventRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Calling appointments off and bringing their dates back.
 *
 * <p>Calling off works on one date. A one-time appointment has exactly one, and a series keeps
 * every other date when one of its dates is called off: that is the whole point of cancelling by
 * date. The places held on a date that is off are kept, so bringing the date back brings everybody
 * back with it. A whole series can still be called off in one go, which is final.
 *
 * <p>Whatever the appointment asked of a partner for a date that is off is withdrawn, since the
 * partner has no other way of learning that the date is not happening.
 */
@Singleton
public class EventCancellationService {
    private static final Logger log = LoggerFactory.getLogger(EventCancellationService.class);

    private final EventRepository eventRepository;
    private final EventDateCancellationRepository cancellationRepository;
    private final OccurrenceCalendar occurrenceCalendar;
    private final EquipmentReleaseService equipmentRelease;
    private final DomainEventBus eventBus;

    @Inject
    public EventCancellationService(
            EventRepository eventRepository,
            EventDateCancellationRepository cancellationRepository,
            OccurrenceCalendar occurrenceCalendar,
            EquipmentReleaseService equipmentRelease,
            DomainEventBus eventBus) {
        this.eventRepository = eventRepository;
        this.cancellationRepository = cancellationRepository;
        this.occurrenceCalendar = occurrenceCalendar;
        this.equipmentRelease = equipmentRelease;
        this.eventBus = eventBus;
    }

    /**
     * Calls a whole series off, and tells everybody holding a place on a date still to come.
     *
     * @param stationId the station the caller acts for
     * @param eventId   the series
     * @param reason    the reason given, or null
     */
    public void cancelSeries(int stationId, int eventId, @Nullable String reason) {
        var event = eventRepository
                .findById(eventId)
                .filter(found -> found.stationId() == stationId)
                .orElseThrow(Refusal.EVENT_NOT_HERE_ON_CANCELLATION::raise);
        if (!event.isRecurring()) throw Refusal.ONE_TIME_EVENT_CANCELLED_AS_SERIES.raise();
        if (event.cancelled()) throw Refusal.SERIES_ALREADY_CANCELLED.raise();

        eventRepository.cancelEvent(eventId, reason);
        equipmentRelease.withdrawRequests(eventId, stationId);
        log.info("Cancelled the series {} of station {}", eventId, stationId);
        eventBus.publish(EventCancelled.series(stationId, eventId, event.name(), reason));
    }

    /**
     * Calls one date of an appointment off, and tells everybody holding a place on it.
     *
     * @param event       the appointment
     * @param date        the date, on the station's calendar
     * @param reason      the reason given, or null
     * @param cancelledBy the manager calling it off, or null where the caller is no member
     */
    public void cancelDate(StationEvent event, LocalDate date, @Nullable String reason, @Nullable Integer cancelledBy) {
        var calendar = occurrenceCalendar.forStation(event.stationId());
        switch (calendar.check(event, date)) {
            case NOT_AN_OCCURRENCE, IN_A_BREAK -> throw Refusal.DATE_TO_CANCEL_NOT_A_DATE_OF_THE_EVENT.raise();
            case CANCELLED -> throw Refusal.DATE_ALREADY_CANCELLED.raise();
            case OCCURRENCE -> {
                if (date.isBefore(calendar.today())) throw Refusal.DATE_TO_CANCEL_IN_THE_PAST.raise();
            }
        }
        if (cancellationRepository.cancel(event.id(), date, CancellationCause.MANUAL, reason, cancelledBy)) {
            announceCancelled(event, date, reason, CancellationCause.MANUAL);
        }
    }

    /**
     * Calls one date off because too few registrations were accepted for it in time.
     *
     * <p>A date that was ever called off is left alone: either it is off already, or a manager brought
     * it back on purpose, and the check does not overrule that.
     *
     * @param event the appointment
     * @param date  the date that fell short
     * @return true where the date is now off and was never off before
     */
    public boolean cancelForTooFewRegistrations(StationEvent event, LocalDate date) {
        if (!cancellationRepository.cancelForTooFewRegistrations(event.id(), date)) return false;
        announceCancelled(event, date, null, CancellationCause.THRESHOLD);
        return true;
    }

    /**
     * Brings a date back that was called off, and tells everybody who kept their place on it.
     *
     * <p>A date of a series that was called off as a whole stays off: that cancellation is final.
     *
     * @param event the appointment
     * @param date  the date, on the station's calendar
     */
    public void restoreDate(StationEvent event, LocalDate date) {
        if (event.cancelled()) throw Refusal.DATE_OF_CANCELLED_SERIES_NOT_RESTORED.raise();
        var calendar = occurrenceCalendar.forStation(event.stationId());
        if (calendar.cancellationOn(event, date).isEmpty()) throw Refusal.DATE_TO_RESTORE_NOT_CANCELLED.raise();
        if (date.isBefore(calendar.today())) throw Refusal.DATE_TO_RESTORE_IN_THE_PAST.raise();

        cancellationRepository.restore(event.id(), date);
        eventRepository.touch(event.id());
        log.info("Restored {} of appointment {}", date, event.id());
        eventBus.publish(new EventDateRestored(event.stationId(), event.id(), event.name(), date));
    }

    /**
     * The dates of an appointment that are off, as a reader is told about them.
     *
     * @param eventId the appointment
     * @return those dates, earliest first
     */
    public List<CancellationNotice> findCancelledDates(int eventId) {
        return cancellationRepository.findActiveByEvent(eventId).stream()
                .map(EventDateCancellation::notice)
                .toList();
    }

    /**
     * The dates of a station's appointments that are off one by one, for the appointments named.
     *
     * @param stationId the station
     * @param eventIds  the appointments the reader may see
     * @return those dates, each with its appointment
     */
    public List<CancelledEventDate> findCancelledDates(int stationId, Collection<Integer> eventIds) {
        var seen = Set.copyOf(eventIds);
        return cancellationRepository.findActiveByStation(stationId).stream()
                .filter(cancellation -> seen.contains(cancellation.eventId()))
                .map(cancellation -> new CancelledEventDate(cancellation.eventId(), cancellation.notice()))
                .toList();
    }

    private void announceCancelled(
            StationEvent event, LocalDate date, @Nullable String reason, CancellationCause cause) {
        eventRepository.touch(event.id());
        equipmentRelease.withdrawRequestsOn(event.id(), event.stationId(), date);
        log.info("Cancelled {} of appointment {} ({})", date, event.id(), cause);
        eventBus.publish(new EventCancelled(event.stationId(), event.id(), event.name(), reason, date, cause));
    }
}
