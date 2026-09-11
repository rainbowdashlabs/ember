/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.event.events.EventRegistrationStatusChanged;
import dev.chojo.ember.feature.events.entity.EventRegistration;
import dev.chojo.ember.feature.events.entity.EventRegistrationField;
import dev.chojo.ember.feature.events.entity.MemberRegistrationStats;
import dev.chojo.ember.feature.events.entity.RegistrationCount;
import dev.chojo.ember.feature.events.entity.RegistrationFieldValue;
import dev.chojo.ember.feature.events.entity.RegistrationStatus;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.repository.EventRegistrationFieldRepository;
import dev.chojo.ember.feature.events.repository.EventRegistrationRepository;
import dev.chojo.ember.feature.events.repository.EventRepository;
import dev.chojo.ember.feature.members.service.MemberNameResolver;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Owns who is signed up for which event occurrence, including the status changes that notify
 * members.
 */
@Singleton
public class EventRegistrationService {
    private static final Logger log = LoggerFactory.getLogger(EventRegistrationService.class);

    private final EventRegistrationRepository registrationRepository;
    private final EventRegistrationFieldRepository fieldRepository;
    private final EventRepository eventRepository;
    private final DomainEventBus eventBus;
    private final MemberNameResolver nameResolver;

    @Inject
    public EventRegistrationService(
            EventRegistrationRepository registrationRepository,
            EventRegistrationFieldRepository fieldRepository,
            EventRepository eventRepository,
            DomainEventBus eventBus,
            MemberNameResolver nameResolver) {
        this.registrationRepository = registrationRepository;
        this.fieldRepository = fieldRepository;
        this.eventRepository = eventRepository;
        this.eventBus = eventBus;
        this.nameResolver = nameResolver;
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
     * The registrations of the given members that are short of an answer somebody still has to
     * give.
     *
     * <p>A question added to an appointment after somebody signed up leaves their registration
     * without an answer to it. The notification about it marks the moment of the change; this is
     * the state itself, read fresh for the screens that list what is still owed. Only upcoming
     * occurrences take part: a question on a day gone by is no longer anybody's to answer.
     *
     * @param memberIds the reader and everyone they answer for
     * @return one entry per registration still short of an answer, soonest occurrence first
     */
    public List<RegistrationShortOfAnswer> findShortOfAnswer(Collection<Integer> memberIds) {
        var registrations = registrationRepository.findByMembers(memberIds);
        if (registrations.isEmpty()) return List.of();

        var requiredByEvent = new HashMap<Integer, Set<Integer>>();
        var candidates = registrations.stream()
                .filter(registration -> !requiredByEvent
                        .computeIfAbsent(registration.eventId(), this::requiredFieldIds)
                        .isEmpty())
                .toList();
        if (candidates.isEmpty()) return List.of();

        var answers =
                fieldRepository
                        .findValuesForRegistrations(
                                candidates.stream().map(EventRegistration::id).toList())
                        .stream()
                        .collect(Collectors.groupingBy(RegistrationFieldValue::registrationId));
        var eventNames = new HashMap<Integer, String>();
        var owing = new ArrayList<RegistrationShortOfAnswer>();
        for (var registration : candidates) {
            boolean owes = EventRegistrationFieldService.owesAnswer(
                    registration.status(),
                    requiredByEvent.get(registration.eventId()),
                    answers.getOrDefault(registration.id(), List.of()));
            if (!owes) continue;
            var eventName = eventNames.computeIfAbsent(
                    registration.eventId(),
                    id -> eventRepository.findById(id).map(StationEvent::name).orElse(""));
            owing.add(new RegistrationShortOfAnswer(
                    registration.id(),
                    registration.eventId(),
                    eventName,
                    registration.eventDate(),
                    registration.memberId()));
        }
        return owing;
    }

    /** The ids of an appointment's required questions, empty where it requires none. */
    private Set<Integer> requiredFieldIds(int eventId) {
        return fieldRepository.findByEvent(eventId).stream()
                .filter(field -> field.config().required())
                .map(EventRegistrationField::id)
                .collect(Collectors.toSet());
    }

    /** One registration short of an answer, and whose it is. */
    public record RegistrationShortOfAnswer(
            int registrationId, int eventId, String eventName, LocalDate eventDate, int memberId) {}

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
        registrationRepository
                .findById(id)
                .ifPresent(registration -> announce(registration.eventId(), registration.memberId(), status));
        return true;
    }

    /**
     * The state a "no" leaves a registration in.
     *
     * <p>Only a confirmed place can be taken back, and taking one back is what whoever runs the
     * event has to react to. Everything else is somebody answering the question they were asked, so
     * it declines. Both mean the member will not be there and every count treats them alike; they
     * are kept apart so the list and the notification can say which of the two happened.
     *
     * @param current the status the registration holds now, or null where there is none yet
     * @return the status to write
     */
    private static RegistrationStatus refusalFor(RegistrationStatus current) {
        return current == RegistrationStatus.ACCEPTED ? RegistrationStatus.WITHDRAWN : RegistrationStatus.DECLINED;
    }

    /**
     * Takes a registration back at the member's request.
     *
     * <p>A confirmed place is kept and marked, because a station that gave somebody a place needs to
     * see that they gave it up. A registration still waiting on an answer is removed outright: nobody
     * had confirmed anything, so there is nothing to record and a row saying so would only stand in
     * the way of signing up again.
     *
     * <p>Either way what the member had answered goes, the same way it goes wherever else somebody
     * says they are not coming.
     *
     * @param id the registration ID
     * @return true if the registration was withdrawn or removed
     */
    public boolean withdraw(int id) {
        var registration = registrationRepository.findById(id).orElse(null);
        if (registration == null) {
            log.warn("Cannot withdraw registration: registration {} not found", id);
            return false;
        }
        if (registration.status() != RegistrationStatus.ACCEPTED) {
            if (!registrationRepository.delete(id)) return false;
            log.info("Removed unconfirmed registration {}", id);
            return true;
        }
        if (!registrationRepository.recordAnswer(id, RegistrationStatus.WITHDRAWN)) return false;
        log.info("Withdrew registration {}", id);
        recordRefusal(id, registration.eventId(), registration.memberId(), RegistrationStatus.WITHDRAWN);
        return true;
    }

    /**
     * Records that the member behind a registration is not coming, keeping a confirmed place apart
     * from an answer that was never a yes.
     *
     * @param id the registration ID
     * @return true if the registration was updated
     */
    public boolean refuse(int id) {
        var registration = registrationRepository.findById(id).orElse(null);
        if (registration == null) {
            log.warn("Cannot refuse registration: registration {} not found", id);
            return false;
        }
        var status = refusalFor(registration.status());
        if (!registrationRepository.recordAnswer(id, status)) return false;
        log.info("Recorded {} for registration {}", status, id);
        recordRefusal(id, registration.eventId(), registration.memberId(), status);
        return true;
    }

    /**
     * Everything a "no" brings with it, whichever of the two it is.
     *
     * <p>The answers go either way. They were given for a place the member is not taking, and
     * nobody has any use for a list of allergies or clothing sizes belonging to somebody who is not
     * coming. Only a place given back is announced, because only a confirmed place falling free is
     * somebody else's to fill.
     */
    private void recordRefusal(int registrationId, int eventId, int memberId, RegistrationStatus status) {
        fieldRepository.deleteValues(registrationId);
        if (status == RegistrationStatus.WITHDRAWN) {
            announce(eventId, memberId, status);
        }
    }

    /**
     * Tells whoever cares that a registration changed, naming the member so the message means
     * something to somebody who is not that member.
     */
    private void announce(int eventId, int memberId, RegistrationStatus status) {
        eventRepository
                .findById(eventId)
                .ifPresent(event -> eventBus.publish(new EventRegistrationStatusChanged(
                        event.stationId(),
                        event.id(),
                        event.name(),
                        memberId,
                        nameResolver.resolveLocal(memberId),
                        status)));
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
        var status = refusalFor(existing == null ? null : existing.status());
        var result = registrationRepository.create(eventId, memberId, eventDate, status, createdBy);
        log.info("Recorded {} for member {} on event {} ({})", status, memberId, eventId, eventDate);
        recordRefusal(result.id(), eventId, memberId, status);
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
     * Returns the members who will not be at a specific event occurrence, whether they declined
     * outright or took back a place they had been given.
     *
     * @param eventId   the event ID
     * @param eventDate the event occurrence date
     * @return the declined member IDs
     */
    public List<Integer> findNotAttendingMemberIds(int eventId, LocalDate eventDate) {
        return registrationRepository.findNotAttendingMemberIds(eventId, eventDate);
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
