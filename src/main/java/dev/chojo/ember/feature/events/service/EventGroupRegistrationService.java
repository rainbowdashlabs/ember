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
import dev.chojo.ember.feature.events.entity.EventGroupPart;
import dev.chojo.ember.feature.events.entity.EventGroupPartState;
import dev.chojo.ember.feature.events.entity.EventGroupRegistration;
import dev.chojo.ember.feature.events.entity.EventRegistration;
import dev.chojo.ember.feature.events.entity.RegistrationStatus;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.repository.EventGroupPartnerRepository;
import dev.chojo.ember.feature.events.repository.EventGroupRegistrationRepository;
import dev.chojo.ember.feature.events.repository.EventGroupRepository;
import dev.chojo.ember.feature.events.repository.EventRegistrationRepository;
import dev.chojo.ember.feature.events.repository.EventRepository;
import dev.chojo.ember.feature.events.service.EventGroupRules.SetResult;
import dev.chojo.ember.feature.events.service.EventGroupRules.SetRule;
import dev.chojo.ember.feature.members.service.MemberNameResolver;
import dev.chojo.ember.util.sql.Transactions;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
    private final EventGroupRegistrationRepository signUps;
    private final EventRepository events;
    private final EventRegistrationRepository registrations;
    private final EventRegistrationService registrationService;
    private final OccurrenceCalendar calendar;
    private final DomainEventBus eventBus;
    private final MemberNameResolver names;
    private final EventGroupPartnerRepository partnerSignUps;
    private final EventGroupQuestionService questions;

    @Inject
    public EventGroupRegistrationService(
            EventGroupRepository eventGroups,
            EventGroupRegistrationRepository signUps,
            EventRepository events,
            EventRegistrationRepository registrations,
            EventRegistrationService registrationService,
            OccurrenceCalendar calendar,
            DomainEventBus eventBus,
            MemberNameResolver names,
            EventGroupQuestionService questions,
            EventGroupPartnerRepository partnerSignUps) {
        this.eventGroups = eventGroups;
        this.signUps = signUps;
        this.events = events;
        this.registrations = registrations;
        this.registrationService = registrationService;
        this.calendar = calendar;
        this.eventBus = eventBus;
        this.names = names;
        this.questions = questions;
        this.partnerSignUps = partnerSignUps;
    }

    /**
     * Signs a member up for an event group with a choice of its parts.
     *
     * @param stationId the station asking
     * @param eventGroupId  the event group
     * @param memberId  the member
     * @param partIds   the chosen parts
     * @param answers   the answers to the event group's questions, keyed by question id
     * @param createdBy the member who signs somebody else up, or null
     * @param override  whether whoever runs events signs up, which passes the deadline, the limit,
     *                  parts already past and the rules
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
        var written = new ArrayList<EventRegistration>();
        var signUp = Transactions.call(() -> {
            var eventGroup = lockOpenEventGroup(stationId, eventGroupId, override);
            var existing = signUps.find(eventGroupId, memberId);
            if (existing.filter(EventGroupRegistration::active).isPresent()) {
                throw EventRefusal.EVENT_GROUP_ALREADY_SIGNED_UP.raise();
            }
            Integer limit = eventGroup.registrationLimit();
            if (!override && limit != null && holding(eventGroupId) >= limit) {
                throw EventRefusal.EVENT_GROUP_FULL.raise();
            }
            var parts = lockParts(eventGroupId);
            var standing = Standing.of(stationId, parts, calendar);
            requireChoosable(partIds, parts.keySet(), standing, Set.of(), override);
            requireRulesMet(eventGroupId, partIds, standing, override);
            var resolved = questions.resolveAnswers(eventGroupId, answers);
            var status = eventGroup.requiresConfirmation() ? RegistrationStatus.PENDING : RegistrationStatus.ACCEPTED;
            existing.ifPresent(earlier -> registrations.findLinkedToEventGroup(earlier.id()).stream()
                    .filter(registration -> !partIds.contains(registration.eventId()))
                    .forEach(registration ->
                            written.add(registrationService.releaseFromEventGroup(registration, null))));
            var stored = signUps.signUp(eventGroupId, memberId, status, createdBy);
            questions.writeAnswers(stored.id(), resolved);
            for (int partId : ascending(partIds)) {
                written.add(place(parts.get(partId), memberId, status, createdBy, stored.id()));
            }
            return stored;
        });
        registrationService.recordedAll(written);
        return signUp;
    }

    /**
     * Changes the parts a member chose. Parts added take the status of the sign-up, parts dropped
     * are given up. A called-off part the member already held may stay, and still counts.
     *
     * @param stationId the station asking
     * @param eventGroupId  the event group
     * @param memberId  the member
     * @param partIds   the parts chosen from now on
     * @param createdBy the member who changes somebody else's choice, or null
     * @param override  whether whoever runs events changes it, which passes the deadline, parts
     *                  already past and the rules
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
            lockOpenEventGroup(stationId, eventGroupId, override);
            var current = requireActive(eventGroupId, memberId);
            var parts = lockParts(eventGroupId);
            var standing = Standing.of(stationId, parts, calendar);
            var held = chosenRegistrations(current);
            requireChoosable(partIds, parts.keySet(), standing, held.keySet(), override);
            if (!override) {
                var dropped = new HashSet<>(held.keySet());
                dropped.removeAll(partIds);
                if (dropped.stream().anyMatch(standing::tookPlace))
                    throw EventRefusal.EVENT_GROUP_PART_TOOK_PLACE.raise();
            }
            requireRulesMet(eventGroupId, partIds, standing, override);
            for (int partId : ascending(partIds)) {
                if (!held.containsKey(partId)) {
                    written.add(place(parts.get(partId), memberId, current.status(), createdBy, current.id()));
                }
            }
            for (var registration : held.values()) {
                if (!partIds.contains(registration.eventId())) {
                    written.add(
                            registrationService.releaseFromEventGroup(registration, givenUp(registration.status())));
                }
            }
            return current;
        });
        registrationService.recordedAll(written);
        return signUp;
    }

    /**
     * Changes the answers a standing sign-up gives to the event group's questions. The deadline holds for
     * the member as it does for a change of parts; whoever runs events may correct them later.
     *
     * @param stationId          the station asking
     * @param eventGroupId           the event group
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
            lockOpenEventGroup(stationId, eventGroupId, override);
            var current = requireActive(eventGroupId, memberId);
            questions.replaceAnswers(eventGroupId, current.id(), answers, readsHiddenAnswers);
            return current;
        });
    }

    /**
     * Takes a member's sign-up back, with every part they chose. A confirmed place is withdrawn, a
     * sign-up still waiting is declined. Parts that already took place keep their record.
     *
     * @param stationId the station asking
     * @param eventGroupId  the event group
     * @param memberId  the member
     */
    public void withdraw(int stationId, int eventGroupId, int memberId) {
        var written = new ArrayList<EventRegistration>();
        var freed = Transactions.call(() -> {
            var eventGroup = lockEventGroup(stationId, eventGroupId);
            var signUp = requireActive(eventGroupId, memberId);
            var parts = lockParts(eventGroupId);
            var standing = Standing.of(stationId, parts, calendar);
            signUps.updateStatus(signUp.id(), givenUp(signUp.status()));
            questions.forgetAnswers(signUp.id());
            for (var registration : registrations.findLinkedToEventGroup(signUp.id())) {
                var endsWith = standing.tookPlace(registration.eventId()) ? null : givenUp(registration.status());
                written.add(registrationService.releaseFromEventGroup(registration, endsWith));
            }
            return signUp.status() == RegistrationStatus.ACCEPTED ? eventGroup : null;
        });
        registrationService.recordedAll(written);
        if (freed != null) announce(freed, memberId, RegistrationStatus.WITHDRAWN);
    }

    /**
     * Accepts or turns down a sign-up, and every part chosen with it. A called-off event group accepts
     * nobody.
     *
     * @param stationId the station asking
     * @param eventGroupId  the event group
     * @param memberId  the member
     * @param decision  {@link RegistrationStatus#ACCEPTED} or {@link RegistrationStatus#DENIED}
     * @return the sign-up as it is now
     */
    public EventGroupRegistration decide(int stationId, int eventGroupId, int memberId, RegistrationStatus decision) {
        if (decision != RegistrationStatus.ACCEPTED && decision != RegistrationStatus.DENIED) {
            throw EventRefusal.EVENT_GROUP_DECISION_UNKNOWN.raise();
        }
        var written = new ArrayList<EventRegistration>();
        var decided = new ArrayList<EventGroup>();
        var signUp = Transactions.call(() -> {
            var eventGroup = lockEventGroup(stationId, eventGroupId);
            decided.add(eventGroup);
            if (decision == RegistrationStatus.ACCEPTED && eventGroup.cancelled()) {
                throw EventRefusal.EVENT_GROUP_CANCELLED.raise();
            }
            var current = signUps.find(eventGroupId, memberId)
                    .filter(found -> found.active() || found.status() == RegistrationStatus.DENIED)
                    .orElseThrow(EventRefusal.EVENT_GROUP_NOT_SIGNED_UP::raise);
            lockParts(eventGroupId);
            signUps.updateStatus(current.id(), decision);
            for (var registration : registrations.findLinkedToEventGroup(current.id())) {
                written.add(registrationService.restateForEventGroup(registration, decision));
            }
            return signUps.find(eventGroupId, memberId).orElseThrow();
        });
        registrationService.recordedAll(written);
        announce(decided.getFirst(), memberId, decision);
        return signUp;
    }

    /** Tells the member and whoever runs events what became of the sign-up, once for the event group. */
    private void announce(EventGroup eventGroup, int memberId, RegistrationStatus status) {
        eventBus.publish(new EventGroupRegistrationStatusChanged(
                eventGroup.stationId(), eventGroup.id(), eventGroup.name(), memberId, names.called(memberId), status));
    }

    /**
     * A member's sign-up for an event group.
     *
     * @param eventGroupId the event group
     * @param memberId the member
     * @return the sign-up, or null where the member never signed up
     */
    public @Nullable EventGroupRegistration find(int eventGroupId, int memberId) {
        return signUps.find(eventGroupId, memberId).orElse(null);
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

    /**
     * The parts a member chose with their sign-up.
     *
     * @param eventGroupId the event group
     * @param memberId the member
     * @return the chosen parts, empty where the member is not signed up
     */
    public Set<Integer> chosenParts(int eventGroupId, int memberId) {
        return signUps.find(eventGroupId, memberId)
                .map(signUp -> chosenRegistrations(signUp).keySet())
                .orElse(Set.of());
    }

    /**
     * The parts a sign-up counts for the event group's rules: the tied places that still hold or wait,
     * and while the sign-up is turned down, the places turned down with it. A place an appointment
     * field gave and took back again does not count.
     *
     * @param eventGroupId the event group
     * @param memberId the member
     * @return the counted parts, empty where the member is not signed up
     */
    public Set<Integer> countedParts(int eventGroupId, int memberId) {
        return signUps.find(eventGroupId, memberId)
                .map(signUp -> counted(signUp, registrations.findLinkedToEventGroup(signUp.id())))
                .orElse(Set.of());
    }

    private static Set<Integer> counted(EventGroupRegistration signUp, List<EventRegistration> tied) {
        return tied.stream()
                .filter(registration -> registration.status() == RegistrationStatus.PENDING
                        || registration.status() == RegistrationStatus.ACCEPTED
                        || (registration.status() == RegistrationStatus.DENIED
                                && signUp.status() == RegistrationStatus.DENIED))
                .map(EventRegistration::eventId)
                .collect(Collectors.toSet());
    }

    /**
     * Where each of the given members stands in an event group: their sign-up, the parts that count and how
     * those meet every set. The event group's rules and cancellations are read once for all of them.
     *
     * @param eventGroupId  the event group
     * @param memberIds the members asked about
     * @return one standing per member, in the order asked
     */
    public List<EventGroupStanding> standings(int eventGroupId, List<Integer> memberIds) {
        if (memberIds.isEmpty()) return List.of();
        var eventGroup = eventGroups.findById(eventGroupId).orElseThrow(EventRefusal.EVENT_GROUP_NOT_HERE::raise);
        var bySignUp = signUps.findByEventGroup(eventGroupId).stream()
                .collect(Collectors.toMap(EventGroupRegistration::memberId, Function.identity()));
        var tiedByMember = registrations.findLinkedToEventGroupOf(eventGroupId).stream()
                .collect(Collectors.groupingBy(EventRegistration::memberId));
        var parts = eventGroups.findParts(eventGroupId).stream()
                .map(part -> events.findById(part.eventId()))
                .flatMap(Optional::stream)
                .collect(Collectors.toMap(StationEvent::id, Function.identity()));
        var calledOff = Standing.of(eventGroup.stationId(), parts, calendar).calledOff();
        var rules = rulesOf(eventGroupId);
        return memberIds.stream()
                .map(memberId -> {
                    var signUp = bySignUp.get(memberId);
                    if (signUp == null) return new EventGroupStanding(memberId, null, Set.of(), List.of());
                    var counted = counted(signUp, tiedByMember.getOrDefault(memberId, List.of()));
                    return new EventGroupStanding(
                            memberId, signUp, counted, EventGroupRules.evaluate(rules, counted, calledOff));
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
     */
    public record EventGroupStanding(
            int memberId, @Nullable EventGroupRegistration signUp, Set<Integer> counted, List<SetResult> sets) {}

    /**
     * How many sign-ups hold or wait for a place in an event group, those of partners' members included.
     *
     * @param eventGroupId the event group
     * @return the pending and accepted sign-ups
     */
    public int holding(int eventGroupId) {
        return signUps.countHolding(eventGroupId) + partnerSignUps.countHolding(eventGroupId);
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
        var events = parts.stream()
                .map(part -> this.events.findById(part.eventId()))
                .flatMap(Optional::stream)
                .collect(Collectors.toMap(StationEvent::id, Function.identity()));
        var standing = Standing.of(eventGroup.stationId(), events, calendar);
        return parts.stream()
                .filter(part -> events.containsKey(part.eventId()))
                .map(part -> {
                    var event = events.get(part.eventId());
                    return new EventGroupPartState(
                            event.id(),
                            part.setId(),
                            event.name(),
                            event.startTime(),
                            event.endTime(),
                            standing.calledOff().contains(event.id()),
                            standing.tookPlace(event.id()));
                })
                .toList();
    }

    /**
     * How a choice stands against every set of an event group, after its cancellations.
     *
     * @param eventGroupId the event group
     * @param chosen   the chosen parts
     * @return one result per set
     */
    public List<SetResult> evaluate(int eventGroupId, Set<Integer> chosen) {
        return evaluator(eventGroupId).apply(chosen);
    }

    /**
     * Measures many choices against one event group, reading its rules and cancellations once.
     *
     * @param eventGroupId the event group
     * @return what gives every set's result for a choice
     */
    public Function<Set<Integer>, List<SetResult>> evaluator(int eventGroupId) {
        var eventGroup = eventGroups.findById(eventGroupId).orElseThrow(EventRefusal.EVENT_GROUP_NOT_HERE::raise);
        var parts = eventGroups.findParts(eventGroupId).stream()
                .map(part -> events.findById(part.eventId()))
                .flatMap(Optional::stream)
                .collect(Collectors.toMap(StationEvent::id, Function.identity()));
        var calledOff = Standing.of(eventGroup.stationId(), parts, calendar).calledOff();
        var rules = rulesOf(eventGroupId);
        return chosen -> EventGroupRules.evaluate(rules, chosen, calledOff);
    }

    EventGroup lockEventGroup(int stationId, int eventGroupId) {
        eventGroups.lockEventGroup(eventGroupId);
        return eventGroups
                .findById(eventGroupId)
                .filter(eventGroup -> eventGroup.stationId() == stationId)
                .orElseThrow(EventRefusal.EVENT_GROUP_NOT_HERE::raise);
    }

    EventGroup lockOpenEventGroup(int stationId, int eventGroupId, boolean override) {
        var eventGroup = lockEventGroup(stationId, eventGroupId);
        if (eventGroup.cancelled()) throw EventRefusal.EVENT_GROUP_CANCELLED.raise();
        if (!override && eventGroup.deadlinePassed(Instant.now()))
            throw EventRefusal.EVENT_GROUP_REGISTRATION_CLOSED.raise();
        return eventGroup;
    }

    private EventGroupRegistration requireActive(int eventGroupId, int memberId) {
        return signUps.find(eventGroupId, memberId)
                .filter(EventGroupRegistration::active)
                .orElseThrow(EventRefusal.EVENT_GROUP_NOT_SIGNED_UP::raise);
    }

    /** Locks the parts of an event group in ascending order and reads them by id. */
    Map<Integer, StationEvent> lockParts(int eventGroupId) {
        var ids = eventGroups.findParts(eventGroupId).stream()
                .map(EventGroupPart::eventId)
                .sorted()
                .toList();
        ids.forEach(events::lockForSignUp);
        return ids.stream()
                .map(events::findById)
                .flatMap(Optional::stream)
                .collect(Collectors.toMap(StationEvent::id, Function.identity()));
    }

    List<SetRule> rulesOf(int eventGroupId) {
        var partsBySet = eventGroups.findParts(eventGroupId).stream()
                .collect(Collectors.groupingBy(
                        EventGroupPart::setId, Collectors.mapping(EventGroupPart::eventId, Collectors.toSet())));
        return eventGroups.findSets(eventGroupId).stream()
                .map(set ->
                        new SetRule(set.id(), set.name(), set.minimum(), partsBySet.getOrDefault(set.id(), Set.of())))
                .toList();
    }

    /** The registrations of the parts a sign-up chose, by part: every one still tied to it. */
    private Map<Integer, EventRegistration> chosenRegistrations(EventGroupRegistration signUp) {
        return registrations.findLinkedToEventGroup(signUp.id()).stream()
                .collect(Collectors.toMap(EventRegistration::eventId, Function.identity(), (first, second) -> first));
    }

    void requireRulesMet(int eventGroupId, Set<Integer> chosen, Standing standing, boolean override) {
        if (!override && !EventGroupRules.met(rulesOf(eventGroupId), chosen, standing.calledOff())) {
            throw EventRefusal.EVENT_GROUP_CHOICE_SHORT.raise();
        }
    }

    static void requireChoosable(
            Set<Integer> chosen, Set<Integer> parts, Standing standing, Set<Integer> alreadyHeld, boolean override) {
        if (chosen.isEmpty()) throw EventRefusal.EVENT_GROUP_CHOICE_EMPTY.raise();
        if (!parts.containsAll(chosen)) throw EventRefusal.EVENT_GROUP_CHOICE_NOT_A_PART.raise();
        var added = new HashSet<>(chosen);
        added.removeAll(alreadyHeld);
        if (added.stream().anyMatch(standing.calledOff()::contains))
            throw EventRefusal.EVENT_GROUP_CHOICE_CALLED_OFF.raise();
        if (!override && added.stream().anyMatch(standing::tookPlace)) {
            throw EventRefusal.EVENT_GROUP_PART_TOOK_PLACE.raise();
        }
    }

    /**
     * The one day a part falls on.
     *
     * @param part the part
     * @return its day
     */
    LocalDate dayOf(StationEvent part) {
        return calendar.forStation(part.stationId())
                .ruleOf(part)
                .orElseThrow(EventRefusal.EVENT_HAS_NO_START_TIME::raise)
                .first();
    }

    private EventRegistration place(
            StationEvent part,
            int memberId,
            RegistrationStatus status,
            @Nullable Integer createdBy,
            int eventGroupRegistrationId) {
        return registrationService.placeForEventGroup(
                part.id(), memberId, dayOf(part), status, createdBy, eventGroupRegistrationId);
    }

    static RegistrationStatus givenUp(RegistrationStatus status) {
        return status == RegistrationStatus.ACCEPTED ? RegistrationStatus.WITHDRAWN : RegistrationStatus.DECLINED;
    }

    static List<Integer> ascending(Set<Integer> ids) {
        return ids.stream().sorted().toList();
    }

    /**
     * Which parts of an event group are called off and which already took place, as the station's
     * calendar says.
     *
     * @param calledOff the parts called off
     * @param past      the parts whose day lies before today
     */
    record Standing(Set<Integer> calledOff, Set<Integer> past) {
        static Standing of(int stationId, Map<Integer, StationEvent> parts, OccurrenceCalendar calendar) {
            var stationCalendar = calendar.forStation(stationId);
            var today = LocalDate.now(calendar.zoneOf(stationId));
            var calledOff = new HashSet<Integer>();
            var past = new HashSet<Integer>();
            for (var part : parts.values()) {
                if (stationCalendar.cancelledAltogether(part)) calledOff.add(part.id());
                stationCalendar
                        .ruleOf(part)
                        .filter(rule -> rule.first().isBefore(today))
                        .ifPresent(rule -> past.add(part.id()));
            }
            return new Standing(Set.copyOf(calledOff), Set.copyOf(past));
        }

        boolean tookPlace(int partId) {
            return past.contains(partId);
        }
    }
}
