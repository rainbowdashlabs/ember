/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.api.StationSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.events.entity.AwaitingMember;
import dev.chojo.ember.feature.events.entity.EventGroup;
import dev.chojo.ember.feature.events.entity.EventGroupAwaiting;
import dev.chojo.ember.feature.events.entity.EventGroupDetail;
import dev.chojo.ember.feature.events.entity.EventGroupRegistration;
import dev.chojo.ember.feature.events.entity.EventGroupSignUp;
import dev.chojo.ember.feature.events.entity.EventGroupSummary;
import dev.chojo.ember.feature.events.entity.EventGroupTally;
import dev.chojo.ember.feature.events.entity.EventRegistrationField;
import dev.chojo.ember.feature.events.entity.EventRegistrationFieldValue;
import dev.chojo.ember.feature.events.entity.RegistrationFieldValue;
import dev.chojo.ember.feature.events.entity.RegistrationStatus;
import dev.chojo.ember.feature.events.service.EventGroupRegistrationService.EventGroupStanding;
import dev.chojo.ember.feature.members.service.GuardianPolicy;
import dev.chojo.ember.feature.members.service.MemberIdentityFactory;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Event groups as the reader is shown them: the list, the ones still owed an answer, an event group's
 * page and its sign-ups. What a reader is shown of a sign-up depends on who they are: answers kept for
 * organisers only reach whoever edits appointments, the member and whoever looks after them.
 */
@Singleton
public class EventGroupViewService {
    private final EventGroupService eventGroups;
    private final EventGroupRegistrationService signUps;
    private final GuardianPolicy guardianPolicy;
    private final MemberIdentityFactory identities;
    private final EventGroupQuestionService questions;
    private final EventGroupGuards guards;
    private final EventFieldService fields;

    /**
     * Creates the view.
     *
     * @param eventGroups    the event groups
     * @param signUps        their sign-ups
     * @param guardianPolicy who looks after whom
     * @param identities     how members are named
     * @param questions      what event groups ask
     * @param guards         who may open an event group
     * @param fields         what appointments show
     */
    @Inject
    public EventGroupViewService(
            EventGroupService eventGroups,
            EventGroupRegistrationService signUps,
            GuardianPolicy guardianPolicy,
            MemberIdentityFactory identities,
            EventGroupQuestionService questions,
            EventGroupGuards guards,
            EventFieldService fields) {
        this.eventGroups = eventGroups;
        this.signUps = signUps;
        this.guardianPolicy = guardianPolicy;
        this.identities = identities;
        this.questions = questions;
        this.guards = guards;
        this.fields = fields;
    }

    /**
     * The event groups of the reader's station they may open, as lists show them.
     *
     * @param session the reader
     * @return the event groups
     */
    public List<EventGroupSummary> list(StationSession session) {
        var tallies = eventGroups.tallies(session.stationId());
        return eventGroups.findByStation(session.stationId()).stream()
                .filter(eventGroup -> guards.mayOpen(session, eventGroup.id()))
                .map(eventGroup ->
                        EventGroupSummary.of(eventGroup, tallies.getOrDefault(eventGroup.id(), EventGroupTally.NONE)))
                .toList();
    }

    /**
     * The event groups still open with a deadline that the reader or somebody they look after may sign
     * up for and has not answered yet, each with who still owes an answer.
     *
     * @param session the reader
     * @return the event groups still owed an answer
     */
    public List<EventGroupAwaiting> awaiting(StationSession session) {
        var household = guardianPolicy.household(session.user());
        var open = eventGroups.findOpenWithDeadline(session.stationId());
        var answered = signUps.answeredBy(household);
        var mayJoin = eventGroups.whoMaySignUp(
                open.stream().map(EventGroup::id).toList(),
                household,
                session.user().permissions());
        var names = new HashMap<Integer, String>();
        var awaiting = new ArrayList<EventGroupAwaiting>();
        for (var eventGroup : open) {
            var gave = answered.getOrDefault(eventGroup.id(), Set.of());
            var owing = mayJoin.getOrDefault(eventGroup.id(), List.of()).stream()
                    .filter(memberId -> !gave.contains(memberId))
                    .map(memberId -> new AwaitingMember(
                            memberId,
                            names.computeIfAbsent(
                                    memberId,
                                    id -> Objects.requireNonNullElse(
                                            identities
                                                    .local(session.stationId(), id)
                                                    .name(),
                                            ""))))
                    .toList();
            if (!owing.isEmpty()) {
                awaiting.add(new EventGroupAwaiting(
                        eventGroup.id(), eventGroup.details().name(), eventGroup.closesAt(), owing));
            }
        }
        return awaiting;
    }

    /**
     * Every sign-up of an event group that holds, waits for or was turned down for a place, for whoever
     * handles registrations.
     *
     * @param session    the reader
     * @param eventGroup the event group, of the reader's station
     * @return the sign-ups
     */
    public List<EventGroupSignUp> signUpsOf(StationSession session, EventGroup eventGroup) {
        var standing = signUps.findByEventGroup(eventGroup.id()).stream()
                .filter(signUp -> signUp.active() || signUp.status() == RegistrationStatus.DENIED)
                .map(EventGroupRegistration::memberId)
                .toList();
        return signUpsOf(
                session,
                questions.findByEventGroup(eventGroup.id()),
                signUps.standings(eventGroup.id(), standing),
                entry -> true);
    }

    /**
     * An event group with everything its page shows the reader: its sets, parts, audiences, questions,
     * fields and the sign-up of the reader and of everybody they look after who may see it.
     *
     * @param session    the reader
     * @param eventGroup the event group
     * @return its page
     */
    public EventGroupDetail detailOf(StationSession session, EventGroup eventGroup) {
        var permissions = session.user().permissions();
        var asked = questions.findByEventGroup(eventGroup.id());
        var household = guardianPolicy.household(session.user()).stream()
                .filter(memberId -> eventGroups.canSee(eventGroup.id(), memberId, permissions))
                .toList();
        return new EventGroupDetail(
                EventGroupSummary.of(eventGroup, eventGroups.tally(eventGroup.id())),
                eventGroups.findSets(eventGroup.id()),
                signUps.partStates(eventGroup.id()),
                eventGroups.registrationAudience(eventGroup.id()),
                eventGroups.viewAudience(eventGroup.id()),
                asked,
                eventGroups.findFields(eventGroup.id()).stream()
                        .map(field -> fields.asSeenOnEventGroup(
                                eventGroup.id(), field, session.member().id(), runsEventGroups(session)))
                        .toList(),
                signUpsOf(
                        session,
                        asked,
                        signUps.standings(eventGroup.id(), household),
                        entry -> eventGroups.canSignUp(eventGroup.id(), entry.memberId(), permissions)),
                null);
    }

    /**
     * One member's sign-up as the reader is shown it.
     *
     * @param session      the reader
     * @param eventGroupId the event group
     * @param memberId     the member
     * @return the member's standing, with no status where they are not signed up
     */
    public EventGroupSignUp signUpOf(StationSession session, int eventGroupId, int memberId) {
        var permissions = session.user().permissions();
        return signUpsOf(
                        session,
                        questions.findByEventGroup(eventGroupId),
                        signUps.standings(eventGroupId, List.of(memberId)),
                        entry -> eventGroups.canSignUp(eventGroupId, entry.memberId(), permissions))
                .getFirst();
    }

    /**
     * Turns standings into what a response shows, reading the answers of all of them at once. Answers
     * kept for organisers are left out unless the reader runs events or the answers are about them or
     * somebody they look after. Whether a required answer is still owed is told either way.
     */
    private List<EventGroupSignUp> signUpsOf(
            StationSession session,
            List<EventRegistrationField> asked,
            List<EventGroupStanding> entries,
            Predicate<EventGroupStanding> mayRegister) {
        var answers = questions.answersBySignUp(entries.stream()
                .map(EventGroupStanding::signUp)
                .filter(Objects::nonNull)
                .map(EventGroupRegistration::id)
                .toList());
        var required = EventGroupQuestionService.requiredIds(asked);
        var hidden = asked.stream()
                .filter(field -> field.config().managersOnly())
                .map(EventRegistrationField::id)
                .collect(Collectors.toSet());
        return entries.stream()
                .map(entry -> {
                    var signUp = entry.signUp();
                    boolean readsHidden = readsAnswersOf(session, entry.memberId());
                    var stored = signUp == null
                            ? List.<RegistrationFieldValue>of()
                            : answers.getOrDefault(signUp.id(), List.of());
                    var given = stored.stream()
                            .filter(value -> readsHidden || !hidden.contains(value.fieldId()))
                            .map(value -> new EventRegistrationFieldValue(value.fieldId(), value.value()))
                            .toList();
                    return new EventGroupSignUp(
                            entry.memberId(),
                            identities.local(session.stationId(), entry.memberId()),
                            signUp == null ? null : signUp.status(),
                            signUp == null ? null : signUp.registeredAt(),
                            entry.counted().stream().sorted().toList(),
                            entry.sets(),
                            given,
                            signUp != null
                                    && EventRegistrationFieldService.owesAnswer(signUp.status(), required, stored),
                            mayRegister.test(entry),
                            entry.queuePosition());
                })
                .toList();
    }

    /**
     * Whether the reader may read the answers kept for organisers of a member: they edit appointments,
     * the answers are their own or somebody's they look after.
     *
     * @param session  the reader
     * @param memberId the member
     * @return true where the reader reads them
     */
    public boolean readsAnswersOf(StationSession session, int memberId) {
        return session.hasPermission(StationPermission.EVENT_EDIT)
                || memberId == session.member().id()
                || guardianPolicy.mayActFor(session.user(), memberId);
    }

    /**
     * Whether the reader acts for somebody else as whoever runs events, which passes the deadline, the
     * limit, parts already past and the rules.
     *
     * @param session  the reader
     * @param memberId the member acted for
     * @return true for whoever runs events acting for another member
     */
    public static boolean overrides(StationSession session, int memberId) {
        return memberId != session.member().id() && session.hasPermission(StationPermission.EVENT_MANAGER);
    }

    /**
     * Whether the reader runs the station's event groups and keeps their lists.
     *
     * @param session the reader
     * @return true for whoever runs events
     */
    public static boolean runsEventGroups(StationSession session) {
        return session.hasPermission(StationPermission.EVENT_MANAGER);
    }
}
