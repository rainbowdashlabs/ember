/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.api.refusal.EventRefusal;
import dev.chojo.ember.api.refusal.Refusal;
import dev.chojo.ember.api.refusal.RefusalResponse;
import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.event.events.EventAnswerRecorded;
import dev.chojo.ember.event.events.EventRegistrationStatusChanged;
import dev.chojo.ember.feature.events.entity.EventRegistration;
import dev.chojo.ember.feature.events.entity.EventRegistrationField;
import dev.chojo.ember.feature.events.entity.MemberRegistrationStats;
import dev.chojo.ember.feature.events.entity.Places;
import dev.chojo.ember.feature.events.entity.RegistrationCount;
import dev.chojo.ember.feature.events.entity.RegistrationFieldValue;
import dev.chojo.ember.feature.events.entity.RegistrationStatus;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.entity.WhenFull;
import dev.chojo.ember.feature.events.repository.EventRegistrationFieldRepository;
import dev.chojo.ember.feature.events.repository.EventRegistrationRepository;
import dev.chojo.ember.feature.events.repository.EventRepository;
import dev.chojo.ember.feature.events.repository.RegistrationQueueRepository.QueueOwner;
import dev.chojo.ember.feature.members.service.MemberNameResolver;
import dev.chojo.ember.util.sql.Transactions;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Owns who is signed up for which event occurrence, including the status changes that notify
 * members.
 */
@Singleton
public class EventRegistrationService {
    private static final Logger log = LoggerFactory.getLogger(EventRegistrationService.class);

    /**
     * How long a refusal can be taken back.
     *
     * <p>Long enough to notice the wrong row, short enough that a list somebody is planning from does
     * not lie for long. Not a setting: a station has nothing to gain by choosing a different number
     * and something to lose by choosing badly.
     */
    public static final Duration UNDO_WINDOW = Duration.ofMinutes(5);

    private final EventRegistrationRepository registrationRepository;
    private final EventRegistrationFieldRepository fieldRepository;
    private final EventRepository eventRepository;
    private final DomainEventBus eventBus;
    private final MemberNameResolver nameResolver;
    private final EventPlaces places;

    @Inject
    public EventRegistrationService(
            EventRegistrationRepository registrationRepository,
            EventRegistrationFieldRepository fieldRepository,
            EventRepository eventRepository,
            DomainEventBus eventBus,
            MemberNameResolver nameResolver,
            EventPlaces places) {
        this.registrationRepository = registrationRepository;
        this.fieldRepository = fieldRepository;
        this.eventRepository = eventRepository;
        this.eventBus = eventBus;
        this.nameResolver = nameResolver;
        this.places = places;
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
     * Registers a member for an appointment date the way a member signing up does: accepted while a
     * place is free, onto the waiting list once every place is taken, or waiting for a decision where
     * {@code autoAccept} is false.
     *
     * @param eventId    the event ID
     * @param memberId   the member ID
     * @param eventDate  the event occurrence date
     * @param autoAccept whether the appointment takes sign-ups without a decision
     * @param createdBy  the member ID of the creator, or null if self-registered
     * @return the created registration
     */
    public EventRegistration register(
            int eventId, int memberId, LocalDate eventDate, boolean autoAccept, @Nullable Integer createdBy) {
        return register(eventId, memberId, eventDate, autoAccept, createdBy, false, null);
    }

    /**
     * Registers a member for an appointment date.
     *
     * <p>A member who already holds, waits for or asked for a place keeps it as it is, so signing up
     * again never overtakes anybody on the waiting list. Otherwise the sign-up waits for a decision
     * where {@code autoAccept} is false, is accepted while a place is free and nobody waits, and joins
     * the waiting list once every place is taken. Whoever runs the appointment signing somebody else up
     * to a full date has to choose between the waiting list and going beyond the limit.
     *
     * @param eventId    the event ID
     * @param memberId   the member ID
     * @param eventDate  the event occurrence date
     * @param autoAccept whether the appointment takes sign-ups without a decision
     * @param createdBy  the member ID of the creator, or null if self-registered
     * @param byManager  whether whoever runs the appointment signs somebody else up
     * @param choice     what they chose for a full date, or null
     * @return the registration as it stands
     */
    public EventRegistration register(
            int eventId,
            int memberId,
            LocalDate eventDate,
            boolean autoAccept,
            @Nullable Integer createdBy,
            boolean byManager,
            @Nullable WhenFull choice) {
        var moved = new ArrayList<EventRegistration>();
        var created = new boolean[1];
        var stored = Transactions.call(() -> {
            eventRepository.lockForSignUp(eventId);
            requireOwnSignUp(eventId);
            var standing = standingOf(eventId, memberId, eventDate, autoAccept);
            if (standing != null) return standing;
            created[0] = true;
            var event = eventRepository.findById(eventId).orElseThrow();
            var status = autoAccept
                    ? places.statusForComing(event, eventDate, byManager, choice)
                    : RegistrationStatus.PENDING;
            var registration = registrationRepository.create(eventId, memberId, eventDate, status, createdBy);
            if (status == RegistrationStatus.QUEUED) {
                places.enqueue(event, eventDate, registration.id(), memberId);
                moved.addAll(places.fill(eventId, eventDate));
            }
            log.info("Registered member {} for event {} on {} (status={})", memberId, eventId, eventDate, status);
            return registrationRepository.findById(registration.id()).orElse(registration);
        });
        movedUp(moved.stream()
                .filter(registration -> registration.id() != stored.id())
                .toList());
        if (byManager && created[0] && stored.status() == RegistrationStatus.QUEUED) {
            announceQueued(stored, places.ranksOf(List.of(stored.id())).get(stored.id()));
        }
        recorded(stored);
        return stored;
    }

    /**
     * A member's registration for a date that stands and stays as it is when they sign up again, or
     * null where the sign-up is to be decided anew. A place a question holds becomes the member's own
     * and keeps its status. A sign-up still waiting for a decision is decided anew where the
     * appointment no longer confirms its sign-ups.
     */
    private @Nullable EventRegistration standingOf(int eventId, int memberId, LocalDate eventDate, boolean autoAccept) {
        var existing =
                registrationRepository.findOne(eventId, memberId, eventDate).orElse(null);
        if (existing == null || !existing.status().standing()) return null;
        if (autoAccept && existing.status() == RegistrationStatus.PENDING && !existing.fromField()) return null;
        if (existing.fromField()) {
            registrationRepository.takeOverFromField(existing.id());
            return registrationRepository.findById(existing.id()).orElse(existing);
        }
        return existing;
    }

    /**
     * Records that the member behind a registration is coming after all, the way a sign-up would: a
     * place while one is free and nobody waits, the waiting list once every place is taken, a decision
     * to wait for where the appointment confirms its sign-ups. A registration that already holds or
     * waits for a place keeps it.
     *
     * @param id        the registration
     * @param byManager whether whoever runs the appointment answers for somebody else
     * @param choice    what they chose for a full date, or null
     * @return true where the registration was found
     */
    public boolean answerComing(int id, boolean byManager, @Nullable WhenFull choice) {
        var moved = new ArrayList<EventRegistration>();
        var changed = Transactions.call(() -> {
            var found = registrationRepository.findById(id).orElse(null);
            if (found == null) return null;
            eventRepository.lockForSignUp(found.eventId());
            var registration = registrationRepository.findById(id).orElse(found);
            requireOwnSignUp(registration.eventId());
            requireNotHeldByAField(registration, EventRefusal.REGISTRATION_HELD_BY_A_FIELD_ON_STATUS_CHANGE);
            var current = registration.status();
            if (current == RegistrationStatus.ACCEPTED || current == RegistrationStatus.QUEUED) {
                return new Answered(registration, false);
            }
            var event = eventRepository.findById(registration.eventId()).orElseThrow();
            var status = places.statusForComing(event, registration.eventDate(), byManager, choice);
            registrationRepository.updateStatus(id, status);
            if (status == RegistrationStatus.QUEUED) {
                places.enqueue(event, registration.eventDate(), id, registration.memberId());
                moved.addAll(places.fill(registration.eventId(), registration.eventDate()));
            }
            var after = registrationRepository.findById(id).orElse(registration);
            return new Answered(after, after.status() != current);
        });
        if (changed == null) return false;
        var registration = changed.registration();
        log.info("Registration {} is coming ({})", id, registration.status());
        if (changed.moved() && registration.status() != RegistrationStatus.QUEUED) {
            announce(registration.eventId(), registration.memberId(), registration.status());
        } else if (changed.moved() && byManager) {
            announceQueued(registration, places.ranksOf(List.of(id)).get(id));
        }
        recorded(registration);
        movedUp(moved.stream().filter(other -> other.id() != id).toList());
        return true;
    }

    /** A registration after a "coming", and whether its status moved. */
    private record Answered(EventRegistration registration, boolean moved) {}

    /**
     * Accepts a registration as whoever runs the appointment, which refuses once every place is
     * taken unless they accept beyond the limit.
     *
     * @param id        the registration
     * @param overLimit whether they accept beyond the limit
     * @return true if the registration was updated
     */
    public boolean accept(int id, boolean overLimit) {
        boolean updated = Transactions.call(() -> {
            var found = registrationRepository.findById(id).orElse(null);
            if (found == null) return false;
            eventRepository.lockForSignUp(found.eventId());
            var registration = registrationRepository.findById(id).orElse(found);
            requireOwnSignUp(registration.eventId());
            requireNotHeldByAField(registration, EventRefusal.REGISTRATION_HELD_BY_A_FIELD_ON_STATUS_CHANGE);
            if (registration.status() != RegistrationStatus.ACCEPTED) {
                eventRepository
                        .findById(registration.eventId())
                        .ifPresent(event -> places.requireRoomToAccept(event, registration.eventDate(), overLimit));
            }
            return registrationRepository.updateStatus(id, RegistrationStatus.ACCEPTED);
        });
        if (!updated) return false;
        log.info("Accepted registration {}", id);
        registrationRepository.findById(id).ifPresent(registration -> {
            announce(registration.eventId(), registration.memberId(), RegistrationStatus.ACCEPTED);
            recorded(registration);
        });
        return true;
    }

    /**
     * Puts sign-ups waiting for a decision onto the waiting list of their dates, the way whoever runs
     * an appointment that confirms its sign-ups turns down the ones they cannot take yet without
     * turning them away. They join in sign-up order, oldest first, placed by the order each list keeps.
     * Nothing changes unless every one of them waits for a decision on a date with a participant limit.
     *
     * @param ids the registrations
     * @return the registrations as stored
     */
    public List<EventRegistration> moveToQueue(Collection<Integer> ids) {
        var moved = Transactions.call(() -> {
            var found = ids.stream()
                    .distinct()
                    .map(id -> registrationRepository
                            .findById(id)
                            .orElseThrow(EventRefusal.EVENT_REGISTRATION_NOT_HERE_ON_STATUS_CHANGE::raise))
                    .toList();
            found.stream().map(EventRegistration::eventId).distinct().sorted().forEach(eventRepository::lockForSignUp);
            var current = found.stream()
                    .map(registration ->
                            registrationRepository.findById(registration.id()).orElse(registration))
                    .sorted(Comparator.comparing(EventRegistration::createdAt).thenComparing(EventRegistration::id))
                    .toList();
            for (var registration : current) {
                requireOwnSignUp(registration.eventId());
                requireNotHeldByAField(registration, EventRefusal.REGISTRATION_HELD_BY_A_FIELD_ON_STATUS_CHANGE);
                if (registration.status() != RegistrationStatus.PENDING) {
                    throw EventRefusal.REGISTRATION_NOT_PENDING_FOR_QUEUE.raise();
                }
                places.requireLimit(registration.eventId(), registration.eventDate());
            }
            var queued = new ArrayList<EventRegistration>();
            for (var registration : current) {
                registrationRepository.updateStatus(registration.id(), RegistrationStatus.QUEUED);
                var event = eventRepository.findById(registration.eventId()).orElseThrow();
                places.enqueue(event, registration.eventDate(), registration.id(), registration.memberId());
                registrationRepository.findById(registration.id()).ifPresent(queued::add);
            }
            return queued;
        });
        log.info("Moved {} sign-ups onto the waiting list", moved.size());
        var ranks = places.ranksOf(moved.stream().map(EventRegistration::id).toList());
        moved.forEach(registration -> {
            announceQueued(registration, ranks.get(registration.id()));
            recorded(registration);
        });
        return moved;
    }

    /**
     * Moves a sign-up to another place in line on the waiting list of its date, as whoever runs the
     * appointment decides.
     *
     * @param id   the registration, which waits on the list
     * @param rank the place in line it takes, 1 moving up next; beyond the end it goes last
     */
    public void moveInQueue(int id, int rank) {
        Transactions.run(() -> {
            var found = registrationRepository
                    .findById(id)
                    .orElseThrow(EventRefusal.EVENT_REGISTRATION_NOT_HERE_ON_STATUS_CHANGE::raise);
            eventRepository.lockForSignUp(found.eventId());
            var registration = registrationRepository.findById(id).orElse(found);
            requireOwnSignUp(registration.eventId());
            if (registration.status() != RegistrationStatus.QUEUED) throw EventRefusal.REGISTRATION_NOT_QUEUED.raise();
            places.moveTo(registration.eventId(), registration.eventDate(), QueueOwner.local(id), rank);
        });
        log.info("Moved registration {} to place {} on the waiting list", id, rank);
    }

    /**
     * Runs work on an appointment's sign-ups under its lock, in one transaction, so a partner's member
     * signing up takes a place no member of this station takes at the same moment.
     *
     * @param eventId the appointment
     * @param body    the work
     * @param <T>     what the work gives
     * @return what the work gave
     */
    public <T> T underSignUpLock(int eventId, Supplier<T> body) {
        return Transactions.call(() -> {
            eventRepository.lockForSignUp(eventId);
            return body.get();
        });
    }

    /**
     * The status a partner station's member signing up gets where nobody decides: a place while one is
     * free and nobody waits, otherwise the waiting list. The caller holds the appointment's lock.
     *
     * @param event the appointment
     * @param date  the date
     * @return ACCEPTED or QUEUED
     */
    public RegistrationStatus statusForVisitor(StationEvent event, LocalDate date) {
        return places.statusForComing(event, date, false, null);
    }

    /**
     * Puts a partner station's member on the waiting list of a date. The caller holds the appointment's
     * lock.
     *
     * @param event                    the appointment
     * @param date                     the date
     * @param federationRegistrationId the partner's registration
     * @param first                    whether it goes ahead of everybody waiting
     */
    public void enqueueVisitor(StationEvent event, LocalDate date, int federationRegistrationId, boolean first) {
        places.enqueuePartner(event, date, federationRegistrationId, first);
    }

    /**
     * Refuses to accept somebody on a full date unless it goes beyond the limit. The caller holds the
     * appointment's lock.
     *
     * @param event     the appointment
     * @param date      the date
     * @param overLimit whether the acceptance may go beyond the limit
     */
    public void requireRoomToAccept(StationEvent event, LocalDate date, boolean overLimit) {
        places.requireRoomToAccept(event, date, overLimit);
    }

    /**
     * Fills the free places of a date from its waiting list, after somebody joined it: an entry first in
     * line that cannot move up, such as a partner's member whose partner's places are spent, must not
     * keep a newcomer waiting while a place is free. The caller holds the appointment's lock.
     *
     * @param eventId   the appointment
     * @param eventDate the date
     * @return the registrations of this station's members who moved up, to be notified once the change is kept
     */
    public List<EventRegistration> fillLocked(int eventId, LocalDate eventDate) {
        return places.fill(eventId, eventDate);
    }

    /**
     * Notifies this station's members who moved up, once the change is kept.
     *
     * @param moved the registrations as stored
     */
    public void tellMovedUp(List<EventRegistration> moved) {
        movedUp(moved);
    }

    /**
     * Refuses a waiting list on a date without a participant limit.
     *
     * @param eventId   the appointment
     * @param eventDate the date
     */
    public void requireLimit(int eventId, LocalDate eventDate) {
        places.requireLimit(eventId, eventDate);
    }

    /**
     * Moves a partner's member to another place in line. The caller holds the appointment's lock.
     *
     * @param eventId                  the appointment
     * @param eventDate                the date
     * @param federationRegistrationId the partner's registration
     * @param rank                     the place in line it takes
     */
    public void moveVisitorInQueue(int eventId, LocalDate eventDate, int federationRegistrationId, int rank) {
        places.moveTo(eventId, eventDate, QueueOwner.ofPartner(federationRegistrationId), rank);
    }

    /**
     * Whether every place of a date is taken.
     *
     * @param eventId   the appointment
     * @param eventDate the date
     * @return true where no place is free
     */
    public boolean full(int eventId, LocalDate eventDate) {
        return places.full(eventId, eventDate);
    }

    /**
     * The places in line of the given registrations that wait on a waiting list.
     *
     * @param registrationIds the registrations
     * @return the place in line by registration, only for those waiting
     */
    public Map<Integer, Integer> queueRanksOf(Collection<Integer> registrationIds) {
        return places.ranksOf(registrationIds);
    }

    /**
     * The places in line of the given partners' registrations that wait on a waiting list.
     *
     * @param federationRegistrationIds the partners' registrations
     * @return the place in line by registration, only for those waiting
     */
    public Map<Integer, Integer> partnerQueueRanksOf(Collection<Integer> federationRegistrationIds) {
        return places.partnerRanksOf(federationRegistrationIds);
    }

    /**
     * The places of an appointment date, partners' members included.
     *
     * @param eventId   the appointment
     * @param eventDate the date
     * @return its places
     */
    public Places placesOf(int eventId, LocalDate eventDate) {
        return places.placesOf(eventId, eventDate);
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
        var before = Transactions.call(() -> {
            var found = registrationRepository.findById(id).orElse(null);
            if (found == null) return null;
            eventRepository.lockForSignUp(found.eventId());
            var registration = registrationRepository.findById(id).orElse(found);
            requireOwnSignUp(registration.eventId());
            requireNotHeldByAField(registration, EventRefusal.REGISTRATION_HELD_BY_A_FIELD_ON_STATUS_CHANGE);
            return registrationRepository.updateStatus(id, status) ? registration : null;
        });
        if (before == null) {
            log.warn("Cannot update registration status: registration {} not found", id);
            return false;
        }
        log.info("Updated registration {} status to {}", id, status);
        registrationRepository.findById(id).ifPresent(registration -> {
            announce(registration.eventId(), registration.memberId(), status);
            recorded(registration);
        });
        if (before.status() == RegistrationStatus.ACCEPTED && status != RegistrationStatus.ACCEPTED) {
            moveUp(before.eventId(), before.eventDate());
        }
        return true;
    }

    /**
     * The state a "no" leaves a registration in.
     *
     * <p>Only a confirmed place can be taken back, and taking one back is what whoever runs the
     * event has to react to. Everything else is somebody answering the question they were asked, so
     * it declines. Both mean the member will not be there and every count treats them alike; they
     * are kept apart so the list and the notification can show which of the two happened.
     *
     * <p>An appointment that expects everybody has no place to give up, so a "no" there always
     * declines. A withdrawal on it is a refusal taken back and reads as coming, so writing one for a
     * "no" would have counted the member in.
     *
     * @param current the status the registration holds now, or null where there is none yet
     * @param eventId the appointment the "no" is for
     * @return the status to write
     */
    private RegistrationStatus refusalFor(@Nullable RegistrationStatus current, int eventId) {
        if (current != RegistrationStatus.ACCEPTED) return RegistrationStatus.DECLINED;
        boolean registrationRequired = eventRepository
                .findById(eventId)
                .map(StationEvent::requiresRegistration)
                .orElse(true);
        return registrationRequired ? RegistrationStatus.WITHDRAWN : RegistrationStatus.DECLINED;
    }

    /**
     * Refuses to take back a place that a question of the appointment gives.
     *
     * <p>Such a place is not an answer anybody gave, so there is nothing to take back: it holds for
     * as long as the question names the member, and taking it back here would be undone by the next
     * time the two are compared. The way off the list is out of the question.
     *
     * @param refusal what the caller refuses a place held by a question with
     * @throws RefusalResponse where the registration is held by a question
     */
    private static void requireNotHeldByAField(EventRegistration registration, Refusal refusal) {
        if (registration.fromField()) {
            throw refusal.raise();
        }
    }

    /**
     * Takes a registration back at the member's request.
     *
     * <p>The row is kept and marked, whether or not the place had been confirmed. Taking a place back
     * is a thing somebody did, and whoever runs the appointment has to be able to see that they did
     * it: a removed row leaves the list one shorter than it was with nothing to show who is missing
     * from it, and a place given up before anybody got round to confirming it is still a place given
     * up. Signing up again is unaffected, because a registration is written over its own row where
     * one already stands for that member on that day.
     *
     * <p>What is written is always WITHDRAWN and never a decline, so the list and the notification can
     * show that a place was given up rather than that somebody declined. Either is an answer: whoever
     * gives up a place is not asked again before the deadline. What they had answered outlives the
     * refusal by exactly the window it can be taken back in, because an undo handing back a
     * registration with its questions blank would be worse than none.
     *
     * <p>On an appointment that expects everybody this is how a refusal is taken back. The row stays
     * for the same reasons, and every read of who is coming takes it for no answer at all, so the
     * member is expected again.
     *
     * @param id the registration ID
     * @return true if the registration was withdrawn
     */
    public boolean withdraw(int id) {
        var registration = registrationRepository.findById(id).orElse(null);
        if (registration == null) {
            log.warn("Cannot withdraw registration: registration {} not found", id);
            return false;
        }
        requireOwnSignUp(registration.eventId());
        requireNotHeldByAField(registration, EventRefusal.REGISTRATION_HELD_BY_A_FIELD_ON_WITHDRAWAL);
        if (!registrationRepository.recordAnswer(id, RegistrationStatus.WITHDRAWN)) return false;
        log.info("Withdrew registration {}", id);
        freedPlace(registration.eventId(), registration.memberId(), registration.eventDate(), registration.status());
        recorded(id);
        return true;
    }

    /**
     * Puts a withdrawal back, for as long as it may still be put back.
     *
     * <p>What comes back is the place that was held, not a fresh answer at the end of the queue.
     * Somebody who pressed the wrong button on a confirmed seat gets the seat, because a misclick is
     * not a change of mind and taking it back should not cost them the thing they were taking back.
     *
     * <p>A seat somebody else moved up into in the meantime is not taken from them: the member goes to
     * the front of the waiting list instead and gets the next place that falls free. A place on the
     * waiting list comes back at the end, or by the fairness score where the list is kept so, and
     * moves up at once where a place fell free in the meantime.
     *
     * <p>Past the window this refuses, and the member registers again the ordinary way if the
     * appointment still takes answers. That is the point at which a withdrawal was meant.
     *
     * @param id the registration to restore
     * @return true if the answer was put back
     */
    public boolean undoWithdrawal(int id) {
        var registration = registrationRepository.findById(id).orElse(null);
        if (registration == null) return false;
        var moved = new ArrayList<EventRegistration>();
        var restored = Transactions.call(() -> {
            eventRepository.lockForSignUp(registration.eventId());
            requireOwnSignUp(registration.eventId());
            var held = registration.previousStatus();
            boolean seatTaken = held == RegistrationStatus.ACCEPTED
                    && places.full(registration.eventId(), registration.eventDate());
            boolean back = seatTaken
                    ? registrationRepository.restoreAs(id, RegistrationStatus.QUEUED, UNDO_WINDOW)
                    : registrationRepository.restorePreviousStatus(id, UNDO_WINDOW);
            if (!back) return null;
            var event = eventRepository.findById(registration.eventId()).orElseThrow();
            if (seatTaken) {
                places.enqueueFirst(event, registration.eventDate(), id);
            } else if (held == RegistrationStatus.QUEUED) {
                places.enqueue(event, registration.eventDate(), id, registration.memberId());
                moved.addAll(places.fill(registration.eventId(), registration.eventDate()));
            }
            return registrationRepository.findById(id).orElse(registration);
        });
        if (restored == null) {
            log.info("Registration {} can no longer be taken back", id);
            return false;
        }
        log.info("Took back the withdrawal of registration {} ({})", id, restored.status());
        if (restored.status() == RegistrationStatus.ACCEPTED && held(restored, moved)) {
            announce(registration.eventId(), registration.memberId(), RegistrationStatus.ACCEPTED);
        }
        movedUp(moved);
        recorded(id);
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
        requireOwnSignUp(registration.eventId());
        requireNotHeldByAField(registration, EventRefusal.REGISTRATION_HELD_BY_A_FIELD_ON_REFUSAL);
        var status = refusalFor(registration.status(), registration.eventId());
        if (!registrationRepository.recordAnswer(id, status)) return false;
        log.info("Recorded {} for registration {}", status, id);
        freedPlace(registration.eventId(), registration.memberId(), registration.eventDate(), registration.status());
        recorded(id);
        return true;
    }

    /**
     * Refuses a sign-up, answer or decision on a part of an event group, which goes through the event group so
     * its rules, limit and confirmation hold for every part at once.
     *
     * @param eventId the appointment
     */
    public void requireOwnSignUp(int eventId) {
        if (eventRepository.isEventGroupPart(eventId)) throw EventRefusal.REGISTRATION_THROUGH_EVENT_GROUP.raise();
    }

    /**
     * Writes a member's registration for one part of an event group with the status of their event group
     * sign-up, tied to that sign-up. A place an appointment field already holds for the member is
     * only tied, not rewritten: the field decides it. Nobody is notified here; the event group notifies its
     * people once for all its parts, and whoever keeps a record of the date learns of it through
     * {@link #recordedAll} once the writes are kept.
     *
     * @param eventId              the part
     * @param memberId             the member
     * @param eventDate            the part's date
     * @param status               the status of the event group sign-up
     * @param createdBy            the member who signed somebody else up, or null
     * @param eventGroupRegistrationId the event group sign-up
     * @return the stored registration
     */
    public EventRegistration placeForEventGroup(
            int eventId,
            int memberId,
            LocalDate eventDate,
            RegistrationStatus status,
            @Nullable Integer createdBy,
            int eventGroupRegistrationId) {
        var heldByField = registrationRepository.findByEventAndDate(eventId, eventDate).stream()
                .filter(existing -> existing.memberId() == memberId && existing.fromField())
                .findFirst();
        int id = heldByField.map(EventRegistration::id).orElseGet(() -> registrationRepository
                .create(eventId, memberId, eventDate, status, createdBy)
                .id());
        registrationRepository.linkToEventGroup(id, eventGroupRegistrationId);
        return registrationRepository.findById(id).orElseThrow();
    }

    /**
     * Moves the registration for one part of an event group to a new status, as the event group sign-up moved.
     * A place an appointment field holds keeps its status. Nobody is notified here either.
     *
     * @param registration the registration
     * @param status       the new status
     * @return the registration as stored
     */
    public EventRegistration restateForEventGroup(EventRegistration registration, RegistrationStatus status) {
        if (!registration.fromField()) registrationRepository.updateStatus(registration.id(), status);
        return registrationRepository.findById(registration.id()).orElse(registration);
    }

    /**
     * Lets a part go from an event group sign-up: the registration is untied and, unless an appointment
     * field holds it or the part already took place, given the status it ends with.
     *
     * @param registration the registration
     * @param status       the status it ends with, or null to leave it as it is
     * @return the registration as stored
     */
    public EventRegistration releaseFromEventGroup(
            EventRegistration registration, @Nullable RegistrationStatus status) {
        registrationRepository.linkToEventGroup(registration.id(), null);
        if (status == null)
            return registrationRepository.findById(registration.id()).orElse(registration);
        return restateForEventGroup(registration, status);
    }

    /**
     * Notifies whoever keeps a record of the dates of what registrations written for an event group show now.
     * Called once the writes are kept, so nobody hears of a write that was undone.
     *
     * @param written the registrations as stored
     */
    public void recordedAll(List<EventRegistration> written) {
        written.forEach(this::recorded);
    }

    /**
     * Notifies whoever runs the event that a place has fallen free, where one actually has, and moves the
     * first in line on the waiting list up into it.
     *
     * <p>What is announced is decided by the place that was held and not by the word written over it:
     * only a confirmed place falling free is somebody else's to fill, and an answer that was never a
     * yes frees nothing however it is recorded.
     *
     * <p>The answers stay where they are. They used to go the moment somebody said no, which is right
     * until the no can be undone, so they now outlive the refusal by the window and a named sweep
     * clears the ones that outlive it.
     *
     * @param heldBefore the status the registration held before this refusal was written
     */
    private void freedPlace(int eventId, int memberId, LocalDate eventDate, @Nullable RegistrationStatus heldBefore) {
        if (heldBefore == RegistrationStatus.ACCEPTED) {
            announce(eventId, memberId, RegistrationStatus.WITHDRAWN);
            moveUp(eventId, eventDate);
        }
    }

    /**
     * Moves people up from the waiting list of a date into the places that are free, and notifies them.
     *
     * @param eventId   the appointment
     * @param eventDate the date
     * @return the registrations of this station's members who moved up
     */
    public List<EventRegistration> moveUp(int eventId, LocalDate eventDate) {
        var moved = Transactions.call(() -> {
            eventRepository.lockForSignUp(eventId);
            return places.fill(eventId, eventDate);
        });
        movedUp(moved);
        return moved;
    }

    /**
     * Moves people up on every date of an appointment from one day on that has a waiting list, after
     * its limit was raised or removed, and notifies them.
     *
     * @param eventId the appointment
     * @param from    the first date looked at
     * @return the registrations of this station's members who moved up
     */
    public List<EventRegistration> moveUpFrom(int eventId, LocalDate from) {
        var moved = Transactions.call(() -> {
            eventRepository.lockForSignUp(eventId);
            return places.fillFrom(eventId, from);
        });
        movedUp(moved);
        return moved;
    }

    /** Whether a registration put back holds its place by itself, not by moving up just now. */
    private static boolean held(EventRegistration restored, List<EventRegistration> moved) {
        return moved.stream().noneMatch(registration -> registration.id() == restored.id());
    }

    /**
     * Notifies the members who moved up from the waiting list, with a plain notice where the date starts in
     * less than a day, and whoever keeps a record of the date.
     */
    private void movedUp(List<EventRegistration> moved) {
        for (var registration : moved) {
            eventRepository
                    .findById(registration.eventId())
                    .ifPresent(event -> eventBus.publish(new EventRegistrationStatusChanged(
                            event.stationId(),
                            event.id(),
                            event.name(),
                            registration.memberId(),
                            nameResolver.called(registration.memberId()),
                            RegistrationStatus.ACCEPTED,
                            null,
                            true,
                            places.startsWithinADay(event, registration.eventDate()))));
            recorded(registration);
        }
    }

    /**
     * Throws away the answers behind refusals that can no longer be taken back.
     *
     * <p>They used to go the moment somebody said no, which is right until the no can be undone: an
     * undo that handed back a registration with its questions blank would be worse than none. So they
     * outlive the refusal by exactly the window, and this clears the ones that outlived it.
     *
     * @return how many registrations had their answers cleared
     */
    public int sweepAnswersOfSettledRefusals() {
        int cleared = fieldRepository.deleteValuesOfRefusalsOlderThan(UNDO_WINDOW);
        if (cleared > 0) {
            log.info("Cleared the answers of {} refusals that can no longer be taken back", cleared);
        }
        return cleared;
    }

    /**
     * Notifies whoever cares that a registration changed, naming the member so the message means
     * something to somebody who is not that member.
     */
    private void announce(int eventId, int memberId, RegistrationStatus status) {
        eventRepository
                .findById(eventId)
                .ifPresent(event -> eventBus.publish(new EventRegistrationStatusChanged(
                        event.stationId(), event.id(), event.name(), memberId, nameResolver.called(memberId), status)));
    }

    /**
     * Notifies a member that somebody else put them on the waiting list, with their place in line.
     *
     * @param registration the registration, which waits on the list
     * @param rank         its place in line
     */
    private void announceQueued(EventRegistration registration, @Nullable Integer rank) {
        eventRepository
                .findById(registration.eventId())
                .ifPresent(event -> eventBus.publish(new EventRegistrationStatusChanged(
                        event.stationId(),
                        event.id(),
                        event.name(),
                        registration.memberId(),
                        nameResolver.called(registration.memberId()),
                        RegistrationStatus.QUEUED,
                        rank,
                        false,
                        false)));
    }

    /**
     * Notifies whoever keeps a record of the date of what a registration shows now that it was written,
     * read back as it was stored.
     *
     * @param registrationId the registration just written
     */
    private void recorded(int registrationId) {
        registrationRepository.findById(registrationId).ifPresent(this::recorded);
    }

    /**
     * Notifies whoever keeps a record of the date of what a registration shows now that it was written.
     *
     * <p>Every answer is reported, whoever gave it and whatever it shows, because an attendance sheet
     * opened for the date before the answer came in would otherwise only learn of it when somebody
     * fills it in from the appointment by hand.
     *
     * @param registration the registration as it was stored
     */
    private void recorded(EventRegistration registration) {
        eventRepository
                .findById(registration.eventId())
                .ifPresent(event -> eventBus.publish(new EventAnswerRecorded(
                        event.stationId(),
                        event.id(),
                        registration.memberId(),
                        registration.eventDate(),
                        registration.status())));
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
    public EventRegistration decline(int eventId, int memberId, LocalDate eventDate, @Nullable Integer createdBy) {
        requireOwnSignUp(eventId);
        var existing = registrationRepository.findByEventAndDate(eventId, eventDate).stream()
                .filter(r -> r.memberId() == memberId)
                .findFirst()
                .orElse(null);
        if (existing != null) requireNotHeldByAField(existing, EventRefusal.REGISTRATION_HELD_BY_A_FIELD_ON_DECLINE);
        var heldBefore = existing == null ? null : existing.status();
        var status = refusalFor(heldBefore, eventId);
        var result = registrationRepository.create(eventId, memberId, eventDate, status, createdBy);
        log.info("Recorded {} for member {} on event {} ({})", status, memberId, eventId, eventDate);
        freedPlace(eventId, memberId, eventDate, heldBefore);
        recorded(result);
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
    public List<MemberRegistrationStats> findStatsByEvent(int eventId, @Nullable Integer categoryId, int months) {
        return registrationRepository.findStatsByEvent(eventId, categoryId, months);
    }
}
