/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.event.events.EventRegistrationStatusChanged;
import dev.chojo.ember.feature.events.entity.EventRegistration;
import dev.chojo.ember.feature.events.entity.MemberRegistrationStats;
import dev.chojo.ember.feature.events.entity.RegistrationCount;
import dev.chojo.ember.feature.events.entity.RegistrationStatus;
import dev.chojo.ember.feature.events.repository.EventRegistrationRepository;
import dev.chojo.ember.feature.events.repository.EventRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Owns who is signed up for which event occurrence, including the status changes that notify
 * members.
 */
@Singleton
public class EventRegistrationService {
    private static final Logger log = LoggerFactory.getLogger(EventRegistrationService.class);

    private final EventRegistrationRepository registrationRepository;
    private final EventRepository eventRepository;
    private final DomainEventBus eventBus;

    @Inject
    public EventRegistrationService(
            EventRegistrationRepository registrationRepository,
            EventRepository eventRepository,
            DomainEventBus eventBus) {
        this.registrationRepository = registrationRepository;
        this.eventRepository = eventRepository;
        this.eventBus = eventBus;
    }

    /**
     * Retrieves all pending registrations for events in a station.
     *
     * @param stationId the station ID
     * @return the list of pending registrations
     */
    public List<EventRegistration> findPendingByStation(int stationId) {
        return registrationRepository.findPendingByStation(stationId);
    }

    /**
     * Retrieves all registrations for an event on a specific date.
     *
     * @param eventId   the event ID
     * @param eventDate the event occurrence date
     * @return the list of registrations
     */
    public List<EventRegistration> findByEventAndDate(int eventId, LocalDate eventDate) {
        return registrationRepository.findByEventAndDate(eventId, eventDate);
    }

    /**
     * Retrieves all registrations for an event across all dates.
     *
     * @param eventId the event ID
     * @return the list of registrations
     */
    public List<EventRegistration> findByEvent(int eventId) {
        return registrationRepository.findByEvent(eventId);
    }

    /**
     * Retrieves all registrations for a specific member.
     *
     * @param memberId the member ID
     * @return the list of registrations
     */
    public List<EventRegistration> findByMember(int memberId) {
        return registrationRepository.findByMember(memberId);
    }

    /**
     * Retrieves upcoming registrations for any member in the given collection in one query.
     * Used by the personal iCal feed to load owner + managed-member registrations together.
     *
     * @param memberIds the member IDs to fetch registrations for
     * @return the list of registrations
     */
    public List<EventRegistration> findByMembers(Collection<Integer> memberIds) {
        return registrationRepository.findByMembers(memberIds);
    }

    /**
     * Returns the most recent registration time across the given members, for feed cache invalidation.
     */
    public Instant findMaxCreatedAt(Collection<Integer> memberIds) {
        return registrationRepository.findMaxCreatedAt(memberIds);
    }

    /**
     * Registers a member for an event. If {@code autoAccept} is true, the registration is immediately accepted.
     *
     * @param eventId    the event ID
     * @param memberId   the member ID
     * @param eventDate  the event occurrence date
     * @param autoAccept whether to automatically accept the registration
     * @param createdBy  the member ID of the creator, or null if self-registered
     * @return the created registration
     */
    public EventRegistration register(
            int eventId, int memberId, LocalDate eventDate, boolean autoAccept, Integer createdBy) {
        var registration =
                registrationRepository.create(eventId, memberId, eventDate, RegistrationStatus.PENDING, createdBy);
        log.info(
                "Registered member {} for event {} on {} (status={})",
                memberId,
                eventId,
                eventDate,
                autoAccept ? RegistrationStatus.ACCEPTED : RegistrationStatus.PENDING);
        if (autoAccept) {
            registrationRepository.updateStatus(registration.id(), RegistrationStatus.ACCEPTED);
            return registrationRepository.findById(registration.id()).orElse(registration);
        }
        return registration;
    }

    /**
     * The events still waiting on an answer from any of the given members.
     *
     * @param memberIds the reader and everyone they answer for
     * @return one entry per event and member still owing an answer
     */
    public List<EventRegistrationRepository.AwaitingAnswer> findAwaitingAnswer(List<Integer> memberIds) {
        return registrationRepository.findAwaitingAnswer(memberIds);
    }

    /**
     * Finds a registration by its ID.
     *
     * @param id the registration ID
     * @return the registration, if found
     */
    public Optional<EventRegistration> findById(int id) {
        return registrationRepository.findById(id);
    }

    /**
     * Moves a registration to a new status and announces the change.
     *
     * @param id     the registration ID
     * @param status the new status
     * @return true if the registration was updated
     */
    public boolean updateStatus(int id, RegistrationStatus status) {
        if (!registrationRepository.updateStatus(id, status)) {
            log.warn("Cannot update registration status: registration {} not found", id);
            return false;
        }
        log.info("Updated registration {} status to {}", id, status);
        registrationRepository.findById(id).ifPresent(registration -> eventRepository
                .findById(registration.eventId())
                .ifPresent(event -> eventBus.publish(new EventRegistrationStatusChanged(
                        event.stationId(), event.id(), event.name(), registration.memberId(), status))));
        return true;
    }

    /**
     * Removes a registration, announcing the withdrawal if the member was already accepted.
     *
     * @param id the registration ID
     * @return true if the registration was removed
     */
    public boolean withdraw(int id) {
        var registration = registrationRepository.findById(id).orElse(null);
        if (!registrationRepository.delete(id)) {
            log.warn("Cannot withdraw registration: registration {} not found", id);
            return false;
        }
        log.info("Withdrew registration {}", id);
        if (registration != null && registration.status() == RegistrationStatus.ACCEPTED) {
            eventRepository
                    .findById(registration.eventId())
                    .ifPresent(event -> eventBus.publish(new EventRegistrationStatusChanged(
                            event.stationId(),
                            event.id(),
                            event.name(),
                            registration.memberId(),
                            RegistrationStatus.WITHDRAWN)));
        }
        return true;
    }

    /**
     * Records that a member will not attend an event occurrence.
     *
     * @param eventId   the event ID
     * @param memberId  the member ID
     * @param eventDate the event occurrence date
     * @param createdBy the member ID of the creator, or null if self-declined
     * @return the stored declination
     */
    public EventRegistration decline(int eventId, int memberId, LocalDate eventDate, Integer createdBy) {
        var existing = registrationRepository.findByEventAndDate(eventId, eventDate).stream()
                .filter(r -> r.memberId() == memberId)
                .findFirst()
                .orElse(null);
        var result =
                registrationRepository.create(eventId, memberId, eventDate, RegistrationStatus.DECLINED, createdBy);
        log.info("Declined registration for member {} on event {} ({})", memberId, eventId, eventDate);
        if (existing != null && existing.status() == RegistrationStatus.ACCEPTED) {
            eventRepository
                    .findById(eventId)
                    .ifPresent(event -> eventBus.publish(new EventRegistrationStatusChanged(
                            event.stationId(), event.id(), event.name(), memberId, RegistrationStatus.DECLINED)));
        }
        return result;
    }

    /**
     * Returns the per-event registration totals of a station.
     *
     * @param stationId the station ID
     * @return the registration counts
     */
    public List<RegistrationCount> findCountsByStation(int stationId) {
        return registrationRepository.findCountsByStation(stationId);
    }

    /**
     * Returns the members who declined a specific event occurrence.
     *
     * @param eventId   the event ID
     * @param eventDate the event occurrence date
     * @return the declined member IDs
     */
    public List<Integer> findDeclinedMemberIds(int eventId, LocalDate eventDate) {
        return registrationRepository.findDeclinedMemberIds(eventId, eventDate);
    }

    /**
     * Returns per-member registration statistics for an event over a number of months.
     *
     * @param eventId    the event ID
     * @param categoryId the optional category filter
     * @param months     the number of months to look back
     * @return the statistics per member
     */
    public List<MemberRegistrationStats> findStatsByEvent(int eventId, Integer categoryId, int months) {
        return registrationRepository.findStatsByEvent(eventId, categoryId, months);
    }
}
