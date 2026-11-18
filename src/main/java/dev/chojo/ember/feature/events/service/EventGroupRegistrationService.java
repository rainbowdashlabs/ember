/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.api.refusal.EventRefusal;
import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.event.events.EventGroupRegistrationStatusChanged;
import dev.chojo.ember.feature.events.entity.EventGroup;
import dev.chojo.ember.feature.events.entity.EventGroupPartState;
import dev.chojo.ember.feature.events.entity.EventGroupRegistration;
import dev.chojo.ember.feature.events.entity.EventRegistration;
import dev.chojo.ember.feature.events.entity.QueueEntry;
import dev.chojo.ember.feature.events.entity.RegistrationStatus;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.entity.WhenFull;
import dev.chojo.ember.feature.events.repository.EventGroupRegistrationRepository;
import dev.chojo.ember.feature.events.repository.EventGroupRepository;
import dev.chojo.ember.feature.events.repository.EventRegistrationRepository;
import dev.chojo.ember.feature.events.repository.RegistrationQueueRepository;
import dev.chojo.ember.feature.events.repository.RegistrationQueueRepository.QueueOwner;
import dev.chojo.ember.feature.events.service.EventGroupRules.SetResult;
import dev.chojo.ember.feature.members.service.MemberNameResolver;
import dev.chojo.ember.util.sql.Transactions;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Signs members up for event groups, changes the parts they chose, takes sign-ups back and decides on
 * them.
 *
 * <p>An event group sign-up writes one ordinary registration per chosen part, with the status of the
 * sign-up and tied to it, so everything that reads registrations per appointment keeps working and
 * the event group always knows which parts were chosen. A part given up is untied. A place an appointment
 * field holds for the member is tied but keeps the status the field gives it.
 *
 * <p>The limit, the deadline and the rules are checked in one transaction that locks the event group and
 * then its parts in ascending order, the same order adding a part takes. Whoever keeps a record of
 * the dates hears of the written registrations once that transaction is kept. Confirming or turning
 * down a sign-up changes every chosen part with it.
 *
 * <p>A part that already took place is left as it is: a member can neither add nor drop it, and
 * withdrawing leaves its record standing. Whoever runs events may sign somebody up, or change their
 * parts, past the deadline, past the limit, with a part that already took place and with a choice
 * that breaks the rules; the event group then shows that person as short. Accepting a sign-up is such a
 * decision too and does not count the limit. Members may withdraw any time, and a member turned down
 * may sign up again, which an event group confirming its sign-ups asks a manager about once more.
 */
@Singleton
public class EventGroupRegistrationService {
    private final EventGroupRepository eventGroups;
    private final EventGroupSignUpRules rules;
    private final EventGroupRegistrationRepository signUps;
    private final EventRegistrationRepository registrations;
    private final EventRegistrationService registrationService;
    private final DomainEventBus eventBus;
    private final MemberNameResolver names;
    private final EventGroupQuestionService questions;
    private final EventGroupQueue queue;
    private final RegistrationQueueRepository queueLists;

    @Inject
    public EventGroupRegistrationService(
            EventGroupRepository eventGroups,
            EventGroupRegistrationRepository signUps,
            EventRegistrationRepository registrations,
            EventRegistrationService registrationService,
            DomainEventBus eventBus,
            MemberNameResolver names,
            EventGroupQuestionService questions,
            EventGroupSignUpRules rules,
            EventGroupQueue queue,
            RegistrationQueueRepository queueLists) {
        this.eventGroups = eventGroups;
        this.rules = rules;
        this.signUps = signUps;
        this.registrations = registrations;
        this.registrationService = registrationService;
        this.eventBus = eventBus;
        this.names = names;
        this.questions = questions;
        this.queue = queue;
        this.queueLists = queueLists;
    }

    /**
     * Signs a member up for an event group with a choice of its parts. Whoever runs events signing up
     * here goes beyond every limit without being asked; routes use the form that takes their choice.
     *
     * @param stationId    the station asking
     * @param eventGroupId the event group
     * @param memberId     the member
     * @param partIds      the chosen parts
     * @param answers      the answers to the event group's questions, keyed by question id
     * @param createdBy    the member who signs somebody else up, or null
     * @param override     whether whoever runs events signs up, which passes the deadline, the limit,
     *                     parts already past and the rules
     * @return the sign-up
     */
    public EventGroupRegistration register(
            int stationId,
            int eventGroupId,
            int memberId,
            Set<Integer> partIds,
            Map<Integer, String> answers,
            @Nullable Integer createdBy,
            boolean override) {
        return register(
                stationId,
                eventGroupId,
                memberId,
                partIds,
                answers,
                createdBy,
                override,
                override ? WhenFull.OVER_LIMIT : null);
    }

    /**
     * Signs a member up for an event group with a choice of its parts. A choice that does not fit waits on
     * the waiting list; whoever runs events signing somebody up has to choose between the list and going
     * beyond the limit.
     *
     * @param stationId    the station asking
     * @param eventGroupId the event group
     * @param memberId     the member
     * @param partIds      the chosen parts
     * @param answers      the answers to the event group's questions, keyed by question id
     * @param createdBy    the member who signs somebody else up, or null
     * @param override     whether whoever runs events signs up, which passes the deadline, parts already
     *                     past and the rules
     * @param choice       what whoever runs events chose for a choice that does not fit, or null
     * @return the sign-up
     */
    public EventGroupRegistration register(
            int stationId,
            int eventGroupId,
            int memberId,
            Set<Integer> partIds,
            Map<Integer, String> answers,
            @Nullable Integer createdBy,
            boolean override,
            @Nullable WhenFull choice) {
        var written = new ArrayList<EventRegistration>();
        var signUp = Transactions.call(() -> {
            var eventGroup = rules.lockOpen(stationId, eventGroupId, override);
            var existing = signUps.find(eventGroupId, memberId);
            EventGroupSignUpRules.requireNoActiveSignUp(
                    existing.filter(EventGroupRegistration::active).isPresent());
            var parts = rules.lockParts(eventGroupId);
            var standing = rules.standing(stationId, parts);
            EventGroupSignUpRules.requireChoosable(partIds, parts.keySet(), standing, Set.of(), override);
            rules.requireRulesMet(eventGroupId, partIds, standing, override);
            var resolved = questions.resolveAnswers(eventGroupId, answers);
            var status = queue.statusFor(eventGroup, partIds, parts, standing, override, choice);
            existing.ifPresent(earlier -> registrations.findLinkedToEventGroup(earlier.id()).stream()
                    .filter(registration -> !partIds.contains(registration.eventId()))
                    .forEach(registration ->
                            written.add(registrationService.releaseFromEventGroup(registration, null))));
            var stored = signUps.signUp(eventGroupId, memberId, status, createdBy);
            questions.writeAnswers(stored.id(), resolved);
            for (int partId : EventGroupSignUpRules.ascending(partIds)) {
                written.add(place(parts.get(partId), memberId, status, createdBy, stored.id()));
            }
            if (status == RegistrationStatus.QUEUED) queue.enqueue(eventGroup, QueueOwner.local(stored.id()), memberId);
            return stored;
        });
        registrationService.recordedAll(written);
        return signUp;
    }

    /**
     * Changes the parts a member chose. Parts added take the status of the sign-up, parts dropped
     * are given up. A called-off part the member already held may stay, and still counts.
     *
     * @param stationId    the station asking
     * @param eventGroupId the event group
     * @param memberId     the member
     * @param partIds      the parts chosen from now on
     * @param createdBy    the member who changes somebody else's choice, or null
     * @param override     whether whoever runs events changes it, which passes the deadline, parts
     *                     already past and the rules
     * @return the sign-up
     */
    public EventGroupRegistration changeParts(
            int stationId,
            int eventGroupId,
            int memberId,
            Set<Integer> partIds,
            @Nullable Integer createdBy,
            boolean override) {
        var written = new ArrayList<EventRegistration>();
        var signUp = Transactions.call(() -> {
            var eventGroup = rules.lockOpen(stationId, eventGroupId, override);
            var current = requireActive(eventGroupId, memberId);
            var parts = rules.lockParts(eventGroupId);
            var standing = rules.standing(stationId, parts);
            var held = chosenRegistrations(current);
            EventGroupSignUpRules.requireChoosable(partIds, parts.keySet(), standing, held.keySet(), override);
            EventGroupSignUpRules.requireDroppable(held.keySet(), partIds, standing, override);
            rules.requireRulesMet(eventGroupId, partIds, standing, override);
            if (current.status() == RegistrationStatus.ACCEPTED) {
                var added = new HashSet<>(partIds);
                added.removeAll(held.keySet());
                rules.requireRoomOnAdded(added, parts, standing, override);
            }
            for (int partId : EventGroupSignUpRules.ascending(partIds)) {
                if (!held.containsKey(partId)) {
                    written.add(place(parts.get(partId), memberId, current.status(), createdBy, current.id()));
                }
            }
            for (var registration : held.values()) {
                if (!partIds.contains(registration.eventId())) {
                    written.add(registrationService.releaseFromEventGroup(
                            registration, EventGroupSignUpRules.givenUp(registration.status())));
                }
            }
            queue.fill(eventGroup, parts, standing);
            return signUps.find(eventGroupId, memberId).orElse(current);
        });
        registrationService.recordedAll(written);
        return signUp;
    }

    /**
     * Changes the answers a standing sign-up gives to the event group's questions. The deadline holds for
     * the member as it does for a change of parts; whoever runs events may correct them later.
     *
     * @param stationId          the station asking
     * @param eventGroupId       the event group
     * @param memberId           the member
     * @param answers            the answers keyed by question id, as submitted
     * @param readsHiddenAnswers whether the caller may read the answers kept for organisers
     * @param override           whether whoever runs events changes them, which passes the deadline
     * @return the sign-up
     */
    public EventGroupRegistration changeAnswers(
            int stationId,
            int eventGroupId,
            int memberId,
            Map<Integer, String> answers,
            boolean readsHiddenAnswers,
            boolean override) {
        return Transactions.call(() -> {
            rules.lockOpen(stationId, eventGroupId, override);
            var current = requireActive(eventGroupId, memberId);
            questions.replaceAnswers(eventGroupId, current.id(), answers, readsHiddenAnswers);
            return current;
        });
    }

    /**
     * Takes a member's sign-up back, with every part they chose. A confirmed place is withdrawn, a
     * sign-up still waiting is declined. Parts that already took place keep their record.
     *
     * @param stationId    the station asking
     * @param eventGroupId the event group
     * @param memberId     the member
     */
    public void withdraw(int stationId, int eventGroupId, int memberId) {
        var written = new ArrayList<EventRegistration>();
        var freed = Transactions.call(() -> {
            var eventGroup = rules.lock(stationId, eventGroupId);
            var signUp = requireActive(eventGroupId, memberId);
            var parts = rules.lockParts(eventGroupId);
            var standing = rules.standing(stationId, parts);
            signUps.updateStatus(signUp.id(), EventGroupSignUpRules.givenUp(signUp.status()));
            questions.forgetAnswers(signUp.id());
            for (var registration : registrations.findLinkedToEventGroup(signUp.id())) {
                var endsWith = standing.tookPlace(registration.eventId())
                        ? null
                        : EventGroupSignUpRules.givenUp(registration.status());
                written.add(registrationService.releaseFromEventGroup(registration, endsWith));
            }
            if (signUp.status() != RegistrationStatus.ACCEPTED) return null;
            queue.fill(eventGroup, parts, standing);
            return eventGroup;
        });
        registrationService.recordedAll(written);
        if (freed != null) announce(freed, memberId, RegistrationStatus.WITHDRAWN);
    }

    /**
     * Accepts or turns down a sign-up, and every part chosen with it, an acceptance going beyond the limit
     * without being asked; routes use the form that takes the choice. A called-off event group accepts
     * nobody.
     *
     * @param stationId    the station asking
     * @param eventGroupId the event group
     * @param memberId     the member
     * @param decision     {@link RegistrationStatus#ACCEPTED} or {@link RegistrationStatus#DENIED}
     * @return the sign-up as it is now
     */
    public EventGroupRegistration decide(int stationId, int eventGroupId, int memberId, RegistrationStatus decision) {
        return decide(stationId, eventGroupId, memberId, decision, true);
    }

    /**
     * Accepts or turns down a sign-up, and every part chosen with it. Accepting a choice that does not fit
     * refuses unless whoever decides goes beyond the limit. A called-off event group accepts nobody.
     *
     * @param stationId    the station asking
     * @param eventGroupId the event group
     * @param memberId     the member
     * @param decision     {@link RegistrationStatus#ACCEPTED} or {@link RegistrationStatus#DENIED}
     * @param overLimit    whether an acceptance may go beyond the limit
     * @return the sign-up as it is now
     */
    public EventGroupRegistration decide(
            int stationId, int eventGroupId, int memberId, RegistrationStatus decision, boolean overLimit) {
        var written = new ArrayList<EventRegistration>();
        var decided = new ArrayList<EventGroup>();
        var signUp = Transactions.call(() -> {
            var eventGroup = rules.lockForDecision(stationId, eventGroupId, decision);
            decided.add(eventGroup);
            var current = signUps.find(eventGroupId, memberId)
                    .filter(found -> found.active() || found.status() == RegistrationStatus.DENIED)
                    .orElseThrow(EventRefusal.EVENT_GROUP_NOT_SIGNED_UP::raise);
            var parts = rules.lockParts(eventGroupId);
            var tied = registrations.findLinkedToEventGroup(current.id());
            if (decision == RegistrationStatus.ACCEPTED && current.status() != RegistrationStatus.ACCEPTED) {
                queue.requireRoomToAccept(
                        eventGroup,
                        tied.stream().map(EventRegistration::eventId).collect(Collectors.toSet()),
                        parts,
                        rules.standing(stationId, parts),
                        overLimit);
            }
            signUps.updateStatus(current.id(), decision);
            for (var registration : tied) {
                written.add(registrationService.restateForEventGroup(registration, decision));
            }
            if (current.status() == RegistrationStatus.ACCEPTED && decision != RegistrationStatus.ACCEPTED) {
                queue.fill(eventGroup, parts, rules.standing(stationId, parts));
            }
            return signUps.find(eventGroupId, memberId).orElseThrow();
        });
        registrationService.recordedAll(written);
        announce(decided.getFirst(), memberId, decision);
        return signUp;
    }

    /**
     * Moves people up from the event group's waiting list into places that are free now, such as after
     * its limit on people or its places per part were raised.
     *
     * @param stationId    the station asking
     * @param eventGroupId the event group
     * @return this station's members who moved up
     */
    public List<Integer> moveUp(int stationId, int eventGroupId) {
        return Transactions.call(() -> {
            var eventGroup = rules.lock(stationId, eventGroupId);
            var parts = rules.lockParts(eventGroupId);
            return queue.fill(eventGroup, parts, rules.standing(stationId, parts));
        });
    }

    /**
     * Puts sign-ups waiting for a decision onto the event group's waiting list, the way whoever runs an
     * event group that confirms its sign-ups turns down the ones it cannot take yet without turning them
     * away. They join in sign-up order, oldest first, placed by the order the list keeps. Nothing
     * changes unless every one of them waits for a decision and the event group limits its places.
     *
     * @param stationId    the station asking
     * @param eventGroupId the event group
     * @param memberIds    the members whose sign-ups go onto the list
     */
    public void moveToQueue(int stationId, int eventGroupId, Collection<Integer> memberIds) {
        var written = new ArrayList<EventRegistration>();
        Transactions.run(() -> {
            var eventGroup = rules.lock(stationId, eventGroupId);
            var parts = rules.lockParts(eventGroupId);
            queue.requireLimit(eventGroup, parts);
            var pending = memberIds.stream()
                    .distinct()
                    .map(memberId -> signUps.find(eventGroupId, memberId)
                            .filter(found -> found.status() == RegistrationStatus.PENDING)
                            .orElseThrow(EventRefusal.EVENT_GROUP_NOT_PENDING_FOR_QUEUE::raise))
                    .sorted(Comparator.comparing(EventGroupRegistration::registeredAt)
                            .thenComparing(EventGroupRegistration::id))
                    .toList();
            for (var signUp : pending) {
                signUps.updateStatus(signUp.id(), RegistrationStatus.QUEUED);
                for (var registration : registrations.findLinkedToEventGroup(signUp.id())) {
                    written.add(registrationService.restateForEventGroup(registration, RegistrationStatus.QUEUED));
                }
                queue.enqueue(eventGroup, QueueOwner.local(signUp.id()), signUp.memberId());
            }
        });
        registrationService.recordedAll(written);
    }

    /**
     * Moves a member's sign-up to another place in line on the event group's waiting list.
     *
     * @param stationId    the station asking
     * @param eventGroupId the event group
     * @param memberId     the member, whose sign-up waits on the list
     * @param rank         the place in line it takes, 1 moving up next; beyond the end it goes last
     */
    public void moveInQueue(int stationId, int eventGroupId, int memberId, int rank) {
        Transactions.run(() -> {
            var eventGroup = rules.lock(stationId, eventGroupId);
            var signUp = signUps.find(eventGroupId, memberId)
                    .filter(found -> found.status() == RegistrationStatus.QUEUED)
                    .orElseThrow(EventRefusal.EVENT_GROUP_NOT_QUEUED::raise);
            queue.moveTo(eventGroup, QueueOwner.local(signUp.id()), rank);
        });
    }

    /**
     * The places in line of the sign-ups waiting on an event group's list, this station's members and
     * partners' members alike.
     *
     * @param eventGroupId the event group
     * @return the entries in line, first in line first
     */
    public List<QueueEntry> queueOf(int eventGroupId) {
        return queueLists.entries(RegistrationQueueRepository.ofEventGroup(eventGroupId));
    }

    /** Tells the member and whoever runs events what became of the sign-up, once for the event group. */
    private void announce(EventGroup eventGroup, int memberId, RegistrationStatus status) {
        eventBus.publish(new EventGroupRegistrationStatusChanged(
                eventGroup.stationId(),
                eventGroup.id(),
                eventGroup.details().name(),
                memberId,
                names.called(memberId),
                status));
    }

    /**
     * A member's sign-up for an event group.
     *
     * @param eventGroupId the event group
     * @param memberId     the member
     * @return the sign-up, or null where the member never signed up
     */
    public @Nullable EventGroupRegistration find(int eventGroupId, int memberId) {
        return signUps.find(eventGroupId, memberId).orElse(null);
    }

    /**
     * Which of the given members gave any answer to which event group, a sign-up in any status.
     *
     * @param memberIds the members
     * @return the members who answered, by event group
     */
    public Map<Integer, Set<Integer>> answeredBy(Collection<Integer> memberIds) {
        return signUps.findAnsweredBy(memberIds);
    }

    /**
     * The sign-ups of an event group in every status, oldest first.
     *
     * @param eventGroupId the event group
     * @return its sign-ups
     */
    public List<EventGroupRegistration> findByEventGroup(int eventGroupId) {
        return signUps.findByEventGroup(eventGroupId);
    }

    private static Set<Integer> counted(EventGroupRegistration signUp, List<EventRegistration> tied) {
        return tied.stream()
                .filter(registration -> EventGroupSignUpRules.counts(registration.status(), signUp.status()))
                .map(EventRegistration::eventId)
                .collect(Collectors.toSet());
    }

    /**
     * Where each of the given members stands in an event group: their sign-up, the parts that count and how
     * those meet every set. The event group's rules and cancellations are read once for all of them.
     *
     * @param eventGroupId the event group
     * @param memberIds    the members asked about
     * @return one standing per member, in the order asked
     */
    public List<EventGroupStanding> standings(int eventGroupId, List<Integer> memberIds) {
        if (memberIds.isEmpty()) return List.of();
        var eventGroup = eventGroups.findById(eventGroupId).orElseThrow(EventRefusal.EVENT_GROUP_NOT_HERE::raise);
        var bySignUp = signUps.findByEventGroup(eventGroupId).stream()
                .collect(Collectors.toMap(EventGroupRegistration::memberId, Function.identity()));
        var tiedByMember = registrations.findLinkedToEventGroupOf(eventGroupId).stream()
                .collect(Collectors.groupingBy(EventRegistration::memberId));
        var calledOff = rules.standing(eventGroup.stationId(), rules.partEvents(eventGroupId))
                .calledOff();
        var setRules = rules.rulesOf(eventGroupId);
        var ranks = queue.ranks(eventGroupId);
        return memberIds.stream()
                .map(memberId -> {
                    var signUp = bySignUp.get(memberId);
                    if (signUp == null) return new EventGroupStanding(memberId, null, Set.of(), List.of());
                    var counted = counted(signUp, tiedByMember.getOrDefault(memberId, List.of()));
                    return new EventGroupStanding(
                            memberId,
                            signUp,
                            counted,
                            EventGroupRules.evaluate(setRules, counted, calledOff),
                            ranks.get(QueueOwner.local(signUp.id())));
                })
                .toList();
    }

    /**
     * Where a member stands in an event group.
     *
     * @param memberId the member
     * @param signUp   their sign-up, or null where they are not signed up
     * @param counted  the parts that count for the rules
     * @param sets     how those parts meet every set
     * @param queuePosition the place in line on the waiting list, or null where the sign-up does not wait
     */
    public record EventGroupStanding(
            int memberId,
            @Nullable EventGroupRegistration signUp,
            Set<Integer> counted,
            List<SetResult> sets,
            @Nullable Integer queuePosition) {
        /** Where a member stands who does not wait on the waiting list. */
        public EventGroupStanding(
                int memberId, @Nullable EventGroupRegistration signUp, Set<Integer> counted, List<SetResult> sets) {
            this(memberId, signUp, counted, sets, null);
        }
    }

    /**
     * How many sign-ups hold a place in an event group, those of partners' members included.
     *
     * @param eventGroupId the event group
     * @return the accepted sign-ups
     */
    public int holding(int eventGroupId) {
        return rules.holding(eventGroupId);
    }

    /**
     * The parts of an event group in date order, each saying whether it is called off or already past.
     *
     * @param eventGroupId the event group
     * @return its parts
     */
    public List<EventGroupPartState> partStates(int eventGroupId) {
        var eventGroup = eventGroups.findById(eventGroupId).orElseThrow(EventRefusal.EVENT_GROUP_NOT_HERE::raise);
        var parts = eventGroups.findParts(eventGroupId);
        var appointments = rules.partEvents(eventGroupId);
        var standing = rules.standing(eventGroup.stationId(), appointments);
        return parts.stream()
                .filter(part -> appointments.containsKey(part.eventId()))
                .map(part -> {
                    var event = appointments.get(part.eventId());
                    var places = rules.placesOf(event);
                    return new EventGroupPartState(
                            event.id(),
                            part.setId(),
                            event.name(),
                            event.startTime(),
                            event.endTime(),
                            standing.calledOff().contains(event.id()),
                            standing.tookPlace(event.id()),
                            part.required(),
                            places.limit(),
                            places.accepted());
                })
                .toList();
    }

    /**
     * Measures many choices against one event group, reading its rules and cancellations once.
     *
     * @param eventGroupId the event group
     * @return what gives every set's result for a choice
     */
    public Function<Set<Integer>, List<SetResult>> evaluator(int eventGroupId) {
        return rules.evaluator(eventGroupId);
    }

    private EventGroupRegistration requireActive(int eventGroupId, int memberId) {
        return signUps.find(eventGroupId, memberId)
                .filter(EventGroupRegistration::active)
                .orElseThrow(EventRefusal.EVENT_GROUP_NOT_SIGNED_UP::raise);
    }

    /** The registrations of the parts a sign-up chose, by part: every one still tied to it. */
    private Map<Integer, EventRegistration> chosenRegistrations(EventGroupRegistration signUp) {
        return registrations.findLinkedToEventGroup(signUp.id()).stream()
                .collect(Collectors.toMap(EventRegistration::eventId, Function.identity(), (first, second) -> first));
    }

    private EventRegistration place(
            StationEvent part,
            int memberId,
            RegistrationStatus status,
            @Nullable Integer createdBy,
            int eventGroupRegistrationId) {
        return registrationService.placeForEventGroup(
                part.id(), memberId, rules.dayOf(part), status, createdBy, eventGroupRegistrationId);
    }
}
