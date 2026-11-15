/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.api.refusal.EventRefusal;
import dev.chojo.ember.feature.events.entity.EventGroup;
import dev.chojo.ember.feature.events.entity.EventGroupPart;
import dev.chojo.ember.feature.events.entity.RegistrationStatus;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.repository.EventGroupRepository;
import dev.chojo.ember.feature.events.repository.EventRepository;
import dev.chojo.ember.feature.events.service.EventGroupRules.SetResult;
import dev.chojo.ember.feature.events.service.EventGroupRules.SetRule;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * What a sign-up for an event group has to pass, the same for a member of this station and for a
 * partner's member: the event group open and not full, the chosen parts choosable, nothing that took
 * place given up, the sets' rules met, and the status a new sign-up or a decision gives.
 *
 * <p>Both sign-up services hold their own records and call these checks in the same order, so a rule
 * changed here changes for both. It is public only because services outside this package are built
 * by hand in tests.
 */
@Singleton
public class EventGroupSignUpRules {
    /** The statuses of a sign-up that holds or waits for a place. */
    static final Set<RegistrationStatus> HOLDING = Set.of(RegistrationStatus.PENDING, RegistrationStatus.ACCEPTED);

    private final EventGroupService eventGroupService;
    private final EventGroupRepository eventGroups;
    private final EventRepository events;
    private final OccurrenceCalendar calendar;

    /**
     * Creates the rules.
     *
     * @param eventGroupService the event groups, for locking one
     * @param eventGroups       the event groups' parts, sets and sign-up counts
     * @param events            the appointments that are parts
     * @param calendar          the occurrence calendar, for what took place or is called off
     */
    @Inject
    public EventGroupSignUpRules(
            EventGroupService eventGroupService,
            EventGroupRepository eventGroups,
            EventRepository events,
            OccurrenceCalendar calendar) {
        this.eventGroupService = eventGroupService;
        this.eventGroups = eventGroups;
        this.events = events;
        this.calendar = calendar;
    }

    /** Locks an event group of the station for a change to its sign-ups. */
    EventGroup lock(int stationId, int eventGroupId) {
        return eventGroupService.lockEventGroup(stationId, eventGroupId);
    }

    /**
     * Locks an event group that still takes sign-ups: not called off, and before its deadline unless
     * whoever runs events overrides it.
     */
    EventGroup lockOpen(int stationId, int eventGroupId, boolean override) {
        var eventGroup = eventGroupService.lockEventGroup(stationId, eventGroupId);
        if (eventGroup.cancelled()) throw EventRefusal.EVENT_GROUP_CANCELLED.raise();
        if (!override && eventGroup.deadlinePassed(Instant.now()))
            throw EventRefusal.EVENT_GROUP_REGISTRATION_CLOSED.raise();
        return eventGroup;
    }

    /** Locks an event group for a decision on a sign-up; a called-off event group accepts nobody. */
    EventGroup lockForDecision(int stationId, int eventGroupId, RegistrationStatus decision) {
        requireDecision(decision);
        var eventGroup = eventGroupService.lockEventGroup(stationId, eventGroupId);
        if (decision == RegistrationStatus.ACCEPTED && eventGroup.cancelled()) {
            throw EventRefusal.EVENT_GROUP_CANCELLED.raise();
        }
        return eventGroup;
    }

    /** Refuses anything but accepting or turning down as a decision. */
    private static void requireDecision(RegistrationStatus decision) {
        if (decision != RegistrationStatus.ACCEPTED && decision != RegistrationStatus.DENIED) {
            throw EventRefusal.EVENT_GROUP_DECISION_UNKNOWN.raise();
        }
    }

    /** Refuses a second sign-up while one still holds or waits for a place. */
    static void requireNoActiveSignUp(boolean active) {
        if (active) throw EventRefusal.EVENT_GROUP_ALREADY_SIGNED_UP.raise();
    }

    /** Refuses a sign-up for an event group whose limit is reached, unless whoever runs events overrides it. */
    void requireRoom(EventGroup eventGroup, boolean override) {
        Integer limit = eventGroup.details().registrationLimit();
        if (!override && limit != null && holding(eventGroup.id()) >= limit) {
            throw EventRefusal.EVENT_GROUP_FULL.raise();
        }
    }

    /**
     * How many sign-ups hold or wait for a place in an event group, those of partners' members included.
     *
     * @param eventGroupId the event group
     * @return the pending and accepted sign-ups
     */
    int holding(int eventGroupId) {
        return eventGroups.countSignUps(eventGroupId, HOLDING);
    }

    /** The status a new sign-up starts with: waiting where a manager confirms each one. */
    static RegistrationStatus initialStatus(EventGroup eventGroup) {
        return eventGroup.details().requiresConfirmation() ? RegistrationStatus.PENDING : RegistrationStatus.ACCEPTED;
    }

    /** The status a part or sign-up given up ends with: a held place is withdrawn, a waiting one declined. */
    static RegistrationStatus givenUp(RegistrationStatus status) {
        return status == RegistrationStatus.ACCEPTED ? RegistrationStatus.WITHDRAWN : RegistrationStatus.DECLINED;
    }

    /**
     * Whether a part's registration counts for the rules: it holds or waits for a place, or the whole
     * sign-up was turned down together with it.
     */
    static boolean counts(RegistrationStatus part, RegistrationStatus signUp) {
        return HOLDING.contains(part) || (part == RegistrationStatus.DENIED && signUp == RegistrationStatus.DENIED);
    }

    /** Locks the parts of an event group in ascending order and reads them by id. */
    Map<Integer, StationEvent> lockParts(int eventGroupId) {
        var ids = eventGroups.findParts(eventGroupId).stream()
                .map(EventGroupPart::eventId)
                .sorted()
                .toList();
        ids.forEach(events::lockForSignUp);
        return appointmentsOf(ids);
    }

    /** The appointments that are parts of an event group, by id. */
    Map<Integer, StationEvent> partEvents(int eventGroupId) {
        return appointmentsOf(eventGroups.findParts(eventGroupId).stream()
                .map(EventGroupPart::eventId)
                .toList());
    }

    private Map<Integer, StationEvent> appointmentsOf(List<Integer> eventIds) {
        return eventIds.stream()
                .map(events::findById)
                .flatMap(Optional::stream)
                .collect(Collectors.toMap(StationEvent::id, Function.identity()));
    }

    /** Which of the given parts are called off and which took place, at the station holding them. */
    Standing standing(int stationId, Map<Integer, StationEvent> parts) {
        return Standing.of(stationId, parts, calendar);
    }

    /** The sets of an event group as the rules read them, each with its parts and its required parts. */
    List<SetRule> rulesOf(int eventGroupId) {
        var parts = eventGroups.findParts(eventGroupId);
        var partsBySet = parts.stream()
                .collect(Collectors.groupingBy(
                        EventGroupPart::setId, Collectors.mapping(EventGroupPart::eventId, Collectors.toSet())));
        var requiredBySet = parts.stream()
                .filter(EventGroupPart::required)
                .collect(Collectors.groupingBy(
                        EventGroupPart::setId, Collectors.mapping(EventGroupPart::eventId, Collectors.toSet())));
        return eventGroups.findSets(eventGroupId).stream()
                .map(set -> new SetRule(
                        set.id(),
                        set.name(),
                        set.minimum(),
                        partsBySet.getOrDefault(set.id(), Set.of()),
                        requiredBySet.getOrDefault(set.id(), Set.of())))
                .toList();
    }

    /**
     * Measures many choices against one event group, reading its rules and cancellations once.
     *
     * @param eventGroupId the event group
     * @return what gives every set's result for a choice
     */
    Function<Set<Integer>, List<SetResult>> evaluator(int eventGroupId) {
        var eventGroup = eventGroups.findById(eventGroupId).orElseThrow(EventRefusal.EVENT_GROUP_NOT_HERE::raise);
        var calledOff =
                standing(eventGroup.stationId(), partEvents(eventGroupId)).calledOff();
        var rules = rulesOf(eventGroupId);
        return chosen -> EventGroupRules.evaluate(rules, chosen, calledOff);
    }

    /** Refuses a choice that falls short of a set, unless whoever runs events overrides it. */
    void requireRulesMet(int eventGroupId, Set<Integer> chosen, Standing standing, boolean override) {
        if (!override && !EventGroupRules.met(rulesOf(eventGroupId), chosen, standing.calledOff())) {
            throw EventRefusal.EVENT_GROUP_CHOICE_SHORT.raise();
        }
    }

    /**
     * Refuses a choice that is empty, names something that is no part, or adds a part called off, or a
     * part that took place unless whoever runs events overrides it. Parts already held may stay.
     */
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
     * The held parts a new choice gives up, refusing one that already took place unless whoever runs
     * events overrides it.
     */
    static Set<Integer> requireDroppable(Set<Integer> held, Set<Integer> chosen, Standing standing, boolean override) {
        var dropped = new HashSet<>(held);
        dropped.removeAll(chosen);
        if (!override && dropped.stream().anyMatch(standing::tookPlace))
            throw EventRefusal.EVENT_GROUP_PART_TOOK_PLACE.raise();
        return dropped;
    }

    /**
     * The one day a part falls on.
     *
     * @param part the part
     * @return its day
     */
    LocalDate dayOf(StationEvent part) {
        return EventGroupPartDates.dayOf(calendar, part).orElseThrow(EventRefusal.EVENT_HAS_NO_START_TIME::raise);
    }

    /** The given ids from the lowest up, the order parts are locked and written in. */
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
            var today = stationCalendar.today();
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
