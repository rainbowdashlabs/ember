/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.api.refusal.EventRefusal;
import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.event.events.CourseRegistrationStatusChanged;
import dev.chojo.ember.feature.events.entity.EventCourse;
import dev.chojo.ember.feature.events.entity.EventCoursePart;
import dev.chojo.ember.feature.events.entity.EventCoursePartState;
import dev.chojo.ember.feature.events.entity.EventCourseRegistration;
import dev.chojo.ember.feature.events.entity.EventRegistration;
import dev.chojo.ember.feature.events.entity.RegistrationStatus;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.repository.EventCoursePartnerRepository;
import dev.chojo.ember.feature.events.repository.EventCourseRegistrationRepository;
import dev.chojo.ember.feature.events.repository.EventCourseRepository;
import dev.chojo.ember.feature.events.repository.EventRegistrationRepository;
import dev.chojo.ember.feature.events.repository.EventRepository;
import dev.chojo.ember.feature.events.service.CourseRules.SetResult;
import dev.chojo.ember.feature.events.service.CourseRules.SetRule;
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
 * Signs members up for courses, changes the parts they chose, takes sign-ups back and decides on
 * them.
 *
 * <p>A course sign-up writes one ordinary registration per chosen part, with the status of the
 * sign-up and tied to it, so everything that reads registrations per appointment keeps working and
 * the course always knows which parts were chosen. A part given up is untied. A place an appointment
 * field holds for the member is tied but keeps the status the field gives it.
 *
 * <p>The limit, the deadline and the rules are checked in one transaction that locks the course and
 * then its parts in ascending order, the same order adding a part takes. Whoever keeps a record of
 * the dates hears of the written registrations once that transaction is kept. Confirming or turning
 * down a sign-up changes every chosen part with it.
 *
 * <p>A part that already took place is left as it is: a member can neither add nor drop it, and
 * withdrawing leaves its record standing. Whoever runs events may sign somebody up, or change their
 * parts, past the deadline, past the limit, with a part that already took place and with a choice
 * that breaks the rules; the course then shows that person as short. Accepting a sign-up is such a
 * decision too and does not count the limit. Members may withdraw any time, and a member turned down
 * may sign up again, which a course confirming its sign-ups asks a manager about once more.
 */
@Singleton
public class EventCourseRegistrationService {
    private final EventCourseRepository courses;
    private final EventCourseRegistrationRepository signUps;
    private final EventRepository events;
    private final EventRegistrationRepository registrations;
    private final EventRegistrationService registrationService;
    private final OccurrenceCalendar calendar;
    private final DomainEventBus eventBus;
    private final MemberNameResolver names;
    private final EventCoursePartnerRepository partnerSignUps;
    private final CourseQuestionService questions;

    @Inject
    public EventCourseRegistrationService(
            EventCourseRepository courses,
            EventCourseRegistrationRepository signUps,
            EventRepository events,
            EventRegistrationRepository registrations,
            EventRegistrationService registrationService,
            OccurrenceCalendar calendar,
            DomainEventBus eventBus,
            MemberNameResolver names,
            CourseQuestionService questions,
            EventCoursePartnerRepository partnerSignUps) {
        this.courses = courses;
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
     * Signs a member up for a course with a choice of its parts.
     *
     * @param stationId the station asking
     * @param courseId  the course
     * @param memberId  the member
     * @param partIds   the chosen parts
     * @param answers   the answers to the course's questions, keyed by question id
     * @param createdBy the member who signs somebody else up, or null
     * @param override  whether whoever runs events signs up, which passes the deadline, the limit,
     *                  parts already past and the rules
     * @return the sign-up
     */
    public EventCourseRegistration register(
            int stationId,
            int courseId,
            int memberId,
            Set<Integer> partIds,
            Map<Integer, String> answers,
            @Nullable Integer createdBy,
            boolean override) {
        var written = new ArrayList<EventRegistration>();
        var signUp = Transactions.call(() -> {
            var course = lockOpenCourse(stationId, courseId, override);
            var existing = signUps.find(courseId, memberId);
            if (existing.filter(EventCourseRegistration::active).isPresent()) {
                throw EventRefusal.COURSE_ALREADY_SIGNED_UP.raise();
            }
            if (!override && course.registrationLimit() != null && holding(courseId) >= course.registrationLimit()) {
                throw EventRefusal.COURSE_FULL.raise();
            }
            var parts = lockParts(courseId);
            var standing = Standing.of(stationId, parts, calendar);
            requireChoosable(partIds, parts.keySet(), standing, Set.of(), override);
            requireRulesMet(courseId, partIds, standing, override);
            var resolved = questions.resolveAnswers(courseId, answers);
            var status = course.requiresConfirmation() ? RegistrationStatus.PENDING : RegistrationStatus.ACCEPTED;
            existing.ifPresent(earlier -> registrations.findLinkedToCourse(earlier.id()).stream()
                    .filter(registration -> !partIds.contains(registration.eventId()))
                    .forEach(registration -> written.add(registrationService.releaseFromCourse(registration, null))));
            var stored = signUps.signUp(courseId, memberId, status, createdBy);
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
     * @param courseId  the course
     * @param memberId  the member
     * @param partIds   the parts chosen from now on
     * @param createdBy the member who changes somebody else's choice, or null
     * @param override  whether whoever runs events changes it, which passes the deadline, parts
     *                  already past and the rules
     * @return the sign-up
     */
    public EventCourseRegistration changeParts(
            int stationId,
            int courseId,
            int memberId,
            Set<Integer> partIds,
            @Nullable Integer createdBy,
            boolean override) {
        var written = new ArrayList<EventRegistration>();
        var signUp = Transactions.call(() -> {
            lockOpenCourse(stationId, courseId, override);
            var current = requireActive(courseId, memberId);
            var parts = lockParts(courseId);
            var standing = Standing.of(stationId, parts, calendar);
            var held = chosenRegistrations(current);
            requireChoosable(partIds, parts.keySet(), standing, held.keySet(), override);
            if (!override) {
                var dropped = new HashSet<>(held.keySet());
                dropped.removeAll(partIds);
                if (dropped.stream().anyMatch(standing::tookPlace)) throw EventRefusal.COURSE_PART_TOOK_PLACE.raise();
            }
            requireRulesMet(courseId, partIds, standing, override);
            for (int partId : ascending(partIds)) {
                if (!held.containsKey(partId)) {
                    written.add(place(parts.get(partId), memberId, current.status(), createdBy, current.id()));
                }
            }
            for (var registration : held.values()) {
                if (!partIds.contains(registration.eventId())) {
                    written.add(registrationService.releaseFromCourse(registration, givenUp(registration.status())));
                }
            }
            return current;
        });
        registrationService.recordedAll(written);
        return signUp;
    }

    /**
     * Changes the answers a standing sign-up gives to the course's questions. The deadline holds for
     * the member as it does for a change of parts; whoever runs events may correct them later.
     *
     * @param stationId          the station asking
     * @param courseId           the course
     * @param memberId           the member
     * @param answers            the answers keyed by question id, as submitted
     * @param readsHiddenAnswers whether the caller may read the answers kept for organisers
     * @param override           whether whoever runs events changes them, which passes the deadline
     * @return the sign-up
     */
    public EventCourseRegistration changeAnswers(
            int stationId,
            int courseId,
            int memberId,
            Map<Integer, String> answers,
            boolean readsHiddenAnswers,
            boolean override) {
        return Transactions.call(() -> {
            lockOpenCourse(stationId, courseId, override);
            var current = requireActive(courseId, memberId);
            questions.replaceAnswers(courseId, current.id(), answers, readsHiddenAnswers);
            return current;
        });
    }

    /**
     * Takes a member's sign-up back, with every part they chose. A confirmed place is withdrawn, a
     * sign-up still waiting is declined. Parts that already took place keep their record.
     *
     * @param stationId the station asking
     * @param courseId  the course
     * @param memberId  the member
     */
    public void withdraw(int stationId, int courseId, int memberId) {
        var written = new ArrayList<EventRegistration>();
        var freed = Transactions.call(() -> {
            var course = lockCourse(stationId, courseId);
            var signUp = requireActive(courseId, memberId);
            var parts = lockParts(courseId);
            var standing = Standing.of(stationId, parts, calendar);
            signUps.updateStatus(signUp.id(), givenUp(signUp.status()));
            questions.forgetAnswers(signUp.id());
            for (var registration : registrations.findLinkedToCourse(signUp.id())) {
                var endsWith = standing.tookPlace(registration.eventId()) ? null : givenUp(registration.status());
                written.add(registrationService.releaseFromCourse(registration, endsWith));
            }
            return signUp.status() == RegistrationStatus.ACCEPTED ? course : null;
        });
        registrationService.recordedAll(written);
        if (freed != null) announce(freed, memberId, RegistrationStatus.WITHDRAWN);
    }

    /**
     * Accepts or turns down a sign-up, and every part chosen with it. A called-off course accepts
     * nobody.
     *
     * @param stationId the station asking
     * @param courseId  the course
     * @param memberId  the member
     * @param decision  {@link RegistrationStatus#ACCEPTED} or {@link RegistrationStatus#DENIED}
     * @return the sign-up as it is now
     */
    public EventCourseRegistration decide(int stationId, int courseId, int memberId, RegistrationStatus decision) {
        if (decision != RegistrationStatus.ACCEPTED && decision != RegistrationStatus.DENIED) {
            throw EventRefusal.COURSE_DECISION_UNKNOWN.raise();
        }
        var written = new ArrayList<EventRegistration>();
        var decided = new ArrayList<EventCourse>();
        var signUp = Transactions.call(() -> {
            var course = lockCourse(stationId, courseId);
            decided.add(course);
            if (decision == RegistrationStatus.ACCEPTED && course.cancelled()) {
                throw EventRefusal.COURSE_CANCELLED.raise();
            }
            var current = signUps.find(courseId, memberId)
                    .filter(found -> found.active() || found.status() == RegistrationStatus.DENIED)
                    .orElseThrow(EventRefusal.COURSE_NOT_SIGNED_UP::raise);
            lockParts(courseId);
            signUps.updateStatus(current.id(), decision);
            for (var registration : registrations.findLinkedToCourse(current.id())) {
                written.add(registrationService.restateForCourse(registration, decision));
            }
            return signUps.find(courseId, memberId).orElseThrow();
        });
        registrationService.recordedAll(written);
        announce(decided.getFirst(), memberId, decision);
        return signUp;
    }

    /** Tells the member and whoever runs events what became of the sign-up, once for the course. */
    private void announce(EventCourse course, int memberId, RegistrationStatus status) {
        eventBus.publish(new CourseRegistrationStatusChanged(
                course.stationId(), course.id(), course.name(), memberId, names.called(memberId), status));
    }

    /**
     * A member's sign-up for a course.
     *
     * @param courseId the course
     * @param memberId the member
     * @return the sign-up, or null where the member never signed up
     */
    public @Nullable EventCourseRegistration find(int courseId, int memberId) {
        return signUps.find(courseId, memberId).orElse(null);
    }

    /**
     * The sign-ups of a course in every status, oldest first.
     *
     * @param courseId the course
     * @return its sign-ups
     */
    public List<EventCourseRegistration> findByCourse(int courseId) {
        return signUps.findByCourse(courseId);
    }

    /**
     * The parts a member chose with their sign-up.
     *
     * @param courseId the course
     * @param memberId the member
     * @return the chosen parts, empty where the member is not signed up
     */
    public Set<Integer> chosenParts(int courseId, int memberId) {
        return signUps.find(courseId, memberId)
                .map(signUp -> chosenRegistrations(signUp).keySet())
                .orElse(Set.of());
    }

    /**
     * The parts a sign-up counts for the course's rules: the tied places that still hold or wait,
     * and while the sign-up is turned down, the places turned down with it. A place an appointment
     * field gave and took back again does not count.
     *
     * @param courseId the course
     * @param memberId the member
     * @return the counted parts, empty where the member is not signed up
     */
    public Set<Integer> countedParts(int courseId, int memberId) {
        return signUps.find(courseId, memberId)
                .map(signUp -> counted(signUp, registrations.findLinkedToCourse(signUp.id())))
                .orElse(Set.of());
    }

    private static Set<Integer> counted(EventCourseRegistration signUp, List<EventRegistration> tied) {
        return tied.stream()
                .filter(registration -> registration.status() == RegistrationStatus.PENDING
                        || registration.status() == RegistrationStatus.ACCEPTED
                        || (registration.status() == RegistrationStatus.DENIED
                                && signUp.status() == RegistrationStatus.DENIED))
                .map(EventRegistration::eventId)
                .collect(Collectors.toSet());
    }

    /**
     * Where each of the given members stands in a course: their sign-up, the parts that count and how
     * those meet every set. The course's rules and cancellations are read once for all of them.
     *
     * @param courseId  the course
     * @param memberIds the members asked about
     * @return one standing per member, in the order asked
     */
    public List<CourseStanding> standings(int courseId, List<Integer> memberIds) {
        if (memberIds.isEmpty()) return List.of();
        var course = courses.findById(courseId).orElseThrow(EventRefusal.COURSE_NOT_HERE::raise);
        var bySignUp = signUps.findByCourse(courseId).stream()
                .collect(Collectors.toMap(EventCourseRegistration::memberId, Function.identity()));
        var tiedByMember = registrations.findLinkedToCourseOf(courseId).stream()
                .collect(Collectors.groupingBy(EventRegistration::memberId));
        var parts = courses.findParts(courseId).stream()
                .map(part -> events.findById(part.eventId()))
                .flatMap(Optional::stream)
                .collect(Collectors.toMap(StationEvent::id, Function.identity()));
        var calledOff = Standing.of(course.stationId(), parts, calendar).calledOff();
        var rules = rulesOf(courseId);
        return memberIds.stream()
                .map(memberId -> {
                    var signUp = bySignUp.get(memberId);
                    if (signUp == null) return new CourseStanding(memberId, null, Set.of(), List.of());
                    var counted = counted(signUp, tiedByMember.getOrDefault(memberId, List.of()));
                    return new CourseStanding(
                            memberId, signUp, counted, CourseRules.evaluate(rules, counted, calledOff));
                })
                .toList();
    }

    /**
     * Where a member stands in a course.
     *
     * @param memberId the member
     * @param signUp   their sign-up, or null where they are not signed up
     * @param counted  the parts that count for the rules
     * @param sets     how those parts meet every set
     */
    public record CourseStanding(
            int memberId, @Nullable EventCourseRegistration signUp, Set<Integer> counted, List<SetResult> sets) {}

    /**
     * How many sign-ups hold or wait for a place in a course, those of partners' members included.
     *
     * @param courseId the course
     * @return the pending and accepted sign-ups
     */
    public int holding(int courseId) {
        return signUps.countHolding(courseId) + partnerSignUps.countHolding(courseId);
    }

    /**
     * The parts of a course in date order, each saying whether it is called off or already past.
     *
     * @param courseId the course
     * @return its parts
     */
    public List<EventCoursePartState> partStates(int courseId) {
        var course = courses.findById(courseId).orElseThrow(EventRefusal.COURSE_NOT_HERE::raise);
        var parts = courses.findParts(courseId);
        var events = parts.stream()
                .map(part -> this.events.findById(part.eventId()))
                .flatMap(Optional::stream)
                .collect(Collectors.toMap(StationEvent::id, Function.identity()));
        var standing = Standing.of(course.stationId(), events, calendar);
        return parts.stream()
                .filter(part -> events.containsKey(part.eventId()))
                .map(part -> {
                    var event = events.get(part.eventId());
                    return new EventCoursePartState(
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
     * How a choice stands against every set of a course, after its cancellations.
     *
     * @param courseId the course
     * @param chosen   the chosen parts
     * @return one result per set
     */
    public List<SetResult> evaluate(int courseId, Set<Integer> chosen) {
        return evaluator(courseId).apply(chosen);
    }

    /**
     * Measures many choices against one course, reading its rules and cancellations once.
     *
     * @param courseId the course
     * @return what gives every set's result for a choice
     */
    public Function<Set<Integer>, List<SetResult>> evaluator(int courseId) {
        var course = courses.findById(courseId).orElseThrow(EventRefusal.COURSE_NOT_HERE::raise);
        var parts = courses.findParts(courseId).stream()
                .map(part -> events.findById(part.eventId()))
                .flatMap(Optional::stream)
                .collect(Collectors.toMap(StationEvent::id, Function.identity()));
        var calledOff = Standing.of(course.stationId(), parts, calendar).calledOff();
        var rules = rulesOf(courseId);
        return chosen -> CourseRules.evaluate(rules, chosen, calledOff);
    }

    EventCourse lockCourse(int stationId, int courseId) {
        courses.lockCourse(courseId);
        return courses.findById(courseId)
                .filter(course -> course.stationId() == stationId)
                .orElseThrow(EventRefusal.COURSE_NOT_HERE::raise);
    }

    EventCourse lockOpenCourse(int stationId, int courseId, boolean override) {
        var course = lockCourse(stationId, courseId);
        if (course.cancelled()) throw EventRefusal.COURSE_CANCELLED.raise();
        if (!override && course.deadlinePassed(Instant.now())) throw EventRefusal.COURSE_REGISTRATION_CLOSED.raise();
        return course;
    }

    private EventCourseRegistration requireActive(int courseId, int memberId) {
        return signUps.find(courseId, memberId)
                .filter(EventCourseRegistration::active)
                .orElseThrow(EventRefusal.COURSE_NOT_SIGNED_UP::raise);
    }

    /** Locks the parts of a course in ascending order and reads them by id. */
    Map<Integer, StationEvent> lockParts(int courseId) {
        var ids = courses.findParts(courseId).stream()
                .map(EventCoursePart::eventId)
                .sorted()
                .toList();
        ids.forEach(events::lockForSignUp);
        return ids.stream()
                .map(events::findById)
                .flatMap(Optional::stream)
                .collect(Collectors.toMap(StationEvent::id, Function.identity()));
    }

    List<SetRule> rulesOf(int courseId) {
        var partsBySet = courses.findParts(courseId).stream()
                .collect(Collectors.groupingBy(
                        EventCoursePart::setId, Collectors.mapping(EventCoursePart::eventId, Collectors.toSet())));
        return courses.findSets(courseId).stream()
                .map(set ->
                        new SetRule(set.id(), set.name(), set.minimum(), partsBySet.getOrDefault(set.id(), Set.of())))
                .toList();
    }

    /** The registrations of the parts a sign-up chose, by part: every one still tied to it. */
    private Map<Integer, EventRegistration> chosenRegistrations(EventCourseRegistration signUp) {
        return registrations.findLinkedToCourse(signUp.id()).stream()
                .collect(Collectors.toMap(EventRegistration::eventId, Function.identity(), (first, second) -> first));
    }

    void requireRulesMet(int courseId, Set<Integer> chosen, Standing standing, boolean override) {
        if (!override && !CourseRules.met(rulesOf(courseId), chosen, standing.calledOff())) {
            throw EventRefusal.COURSE_CHOICE_SHORT.raise();
        }
    }

    static void requireChoosable(
            Set<Integer> chosen, Set<Integer> parts, Standing standing, Set<Integer> alreadyHeld, boolean override) {
        if (chosen.isEmpty()) throw EventRefusal.COURSE_CHOICE_EMPTY.raise();
        if (!parts.containsAll(chosen)) throw EventRefusal.COURSE_CHOICE_NOT_A_PART.raise();
        var added = new HashSet<>(chosen);
        added.removeAll(alreadyHeld);
        if (added.stream().anyMatch(standing.calledOff()::contains))
            throw EventRefusal.COURSE_CHOICE_CALLED_OFF.raise();
        if (!override && added.stream().anyMatch(standing::tookPlace)) {
            throw EventRefusal.COURSE_PART_TOOK_PLACE.raise();
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
            int courseRegistrationId) {
        return registrationService.placeForCourse(
                part.id(), memberId, dayOf(part), status, createdBy, courseRegistrationId);
    }

    static RegistrationStatus givenUp(RegistrationStatus status) {
        return status == RegistrationStatus.ACCEPTED ? RegistrationStatus.WITHDRAWN : RegistrationStatus.DECLINED;
    }

    static List<Integer> ascending(Set<Integer> ids) {
        return ids.stream().sorted().toList();
    }

    /**
     * Which parts of a course are called off and which already took place, as the station's
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
