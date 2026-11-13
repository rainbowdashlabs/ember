/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.refusal.EventRefusal;
import dev.chojo.ember.feature.events.entity.AppointmentField;
import dev.chojo.ember.feature.events.entity.EventGroup;
import dev.chojo.ember.feature.events.entity.EventGroupContent;
import dev.chojo.ember.feature.events.entity.EventGroupDetails;
import dev.chojo.ember.feature.events.entity.EventGroupPart;
import dev.chojo.ember.feature.events.entity.EventGroupPartSummary;
import dev.chojo.ember.feature.events.entity.EventGroupSet;
import dev.chojo.ember.feature.events.entity.EventGroupSetDraft;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.repository.EventFieldRepository;
import dev.chojo.ember.feature.events.repository.EventGroupRepository;
import dev.chojo.ember.feature.events.repository.EventRepository;
import dev.chojo.ember.feature.question.FieldTypes;
import dev.chojo.ember.feature.restriction.RestrictionAudience;
import dev.chojo.ember.feature.restriction.RestrictionType;
import dev.chojo.ember.feature.restriction.service.RestrictionService;
import dev.chojo.ember.util.sql.Transactions;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Creates and shapes event groups: their details, sets, parts and who may see and join them.
 *
 * <p>An event group hands its sign-up and its audiences down to every part. The parts take sign-ups,
 * confirm and close as the event group does and carry no limit or minimum of their own, and they are seen
 * and joined by exactly the people the event group names. That keeps every place that reads a single
 * appointment (lists, the calendar, reminders, attendance) right without knowing about event groups.
 *
 * <p>Only a one-time appointment of the same station can become a part, and only while it is not
 * called off, nobody is signed up for it on its own, it is not shared with partners on its own, and
 * it is meant either for everybody or for the same people as the event group. Handing the event group's
 * audience down therefore never shows an appointment to anybody it was kept from.
 *
 * <p>Every change runs in one transaction that locks the event group first, and adding a part also locks
 * the appointment, so two managers working on the same event group or appointment take turns. Deleting a
 * event group makes its parts appointments of their own again; who was signed up for one stays signed up.
 */
@Singleton
public class EventGroupService {
    private static final Pattern HEX_COLOR = Pattern.compile("#[0-9a-fA-F]{6}");

    private final EventGroupRepository eventGroups;
    private final EventRepository events;
    private final RestrictionService restrictions;
    private final OccurrenceCalendar calendar;

    private final EventGroupQuestionService questions;
    private final EventFieldRepository fields;

    @Inject
    public EventGroupService(
            EventGroupRepository eventGroups,
            EventRepository events,
            RestrictionService restrictions,
            OccurrenceCalendar calendar,
            EventGroupQuestionService questions,
            EventFieldRepository fields) {
        this.questions = questions;
        this.fields = fields;
        this.eventGroups = eventGroups;
        this.events = events;
        this.restrictions = restrictions;
        this.calendar = calendar;
    }

    /**
     * Creates an event group with its sets and audiences.
     *
     * @param stationId    the station
     * @param details      what the event group holds
     * @param sets         its sets in order, at least one
     * @param registration who may sign up
     * @param view         who may see it
     * @param content      the questions and fields it carries for all its appointments, or null for
     *                     none
     * @return the event group
     */
    public EventGroup create(
            int stationId,
            EventGroupDetails details,
            List<EventGroupSetDraft> sets,
            RestrictionAudience registration,
            RestrictionAudience view,
            @Nullable EventGroupContent content) {
        var checked = checked(details);
        requireGroupFieldTypes(content);
        requireValidSets(sets);
        if (sets.stream().anyMatch(set -> set.id() != null))
            throw EventRefusal.EVENT_GROUP_SET_NOT_IN_EVENT_GROUP.raise();
        requireOfStation(stationId, registration, view);
        return Transactions.call(() -> {
            var eventGroup = eventGroups.create(stationId, checked);
            for (int position = 0; position < sets.size(); position++) {
                var set = sets.get(position);
                eventGroups.createSet(eventGroup.id(), set.name().strip(), set.minimum(), position);
            }
            restrictions.setAudience(RestrictionType.EVENT_GROUP, eventGroup.id(), registration);
            restrictions.setAudience(RestrictionType.EVENT_GROUP_VIEW, eventGroup.id(), view);
            writeContent(eventGroup.id(), content);
            return eventGroups.findById(eventGroup.id()).orElseThrow();
        });
    }

    /**
     * Creates an event group and, where one is named, takes an appointment into its first set, all or
     * nothing: an appointment that cannot join leaves no group behind.
     *
     * @param stationId     the station
     * @param details       what the event group holds
     * @param sets          its sets in order, at least one
     * @param registration  who may sign up
     * @param view          who may see it
     * @param content       the questions and fields it carries, or null for none
     * @param appointmentId the appointment to take in, or null
     * @return the event group
     */
    public EventGroup createWith(
            int stationId,
            EventGroupDetails details,
            List<EventGroupSetDraft> sets,
            RestrictionAudience registration,
            RestrictionAudience view,
            @Nullable EventGroupContent content,
            @Nullable Integer appointmentId) {
        return Transactions.call(() -> {
            var eventGroup = create(stationId, details, sets, registration, view, content);
            if (appointmentId != null) {
                addPart(
                        stationId,
                        eventGroup.id(),
                        appointmentId,
                        findSets(eventGroup.id()).getFirst().id());
            }
            return eventGroup;
        });
    }

    /**
     * Changes what an event group holds and hands the sign-up down to its parts.
     *
     * @param stationId the station asking
     * @param eventGroupId  the event group
     * @param details   what it holds now
     * @return the event group as it is now
     */
    public EventGroup update(int stationId, int eventGroupId, EventGroupDetails details) {
        var checked = checked(details);
        return Transactions.call(() -> {
            var before = lockEventGroup(stationId, eventGroupId);
            eventGroups.update(eventGroupId, checked);
            if (!Objects.equals(before.registrationDeadline(), checked.registrationDeadline())) {
                eventGroups.clearClosingWarnings(eventGroupId);
            }
            eventGroups.alignParts(eventGroupId, checked.requiresConfirmation(), checked.registrationDeadline());
            return eventGroups.findById(eventGroupId).orElseThrow();
        });
    }

    /**
     * Saves everything the event group editor holds in one go: details, sets, audiences, questions and
     * fields. Either
     * all of it is kept or, where any part is refused, none of it.
     *
     * @param stationId    the station asking
     * @param eventGroupId     the event group
     * @param details      what the event group holds
     * @param sets         its sets in their new order, or null to leave them
     * @param registration who may sign up, or null to leave it
     * @param view         who may see it, or null to leave it
     * @param content      the questions and fields it carries, or null to leave them
     * @return the event group as it is now
     */
    public EventGroup save(
            int stationId,
            int eventGroupId,
            EventGroupDetails details,
            @Nullable List<EventGroupSetDraft> sets,
            @Nullable RestrictionAudience registration,
            @Nullable RestrictionAudience view,
            @Nullable EventGroupContent content) {
        requireGroupFieldTypes(content);
        return Transactions.call(() -> {
            var eventGroup = update(stationId, eventGroupId, details);
            if (sets != null) replaceSets(stationId, eventGroupId, sets);
            if (registration != null && view != null) setAudiences(stationId, eventGroupId, registration, view);
            writeContent(eventGroupId, content);
            return eventGroups.findById(eventGroupId).orElse(eventGroup);
        });
    }

    private void writeContent(int eventGroupId, @Nullable EventGroupContent content) {
        if (content == null) return;
        var askedQuestions = content.questions();
        if (askedQuestions != null) questions.replaceQuestions(eventGroupId, askedQuestions);
        var handedDown = content.fields();
        if (handedDown != null) fields.replaceGroupFields(eventGroupId, questions.checkedFields(handedDown));
    }

    /**
     * Refuses a field an event group cannot hand down: one that names members, is answered per date,
     * takes self-registration or feeds an attendance sheet is about one date and stays on the
     * appointment.
     */
    private static void requireGroupFieldTypes(@Nullable EventGroupContent content) {
        var handedDown = content == null ? null : content.fields();
        if (handedDown == null) return;
        for (var field : handedDown) {
            if (!FieldTypes.EVENT_GROUP.contains(field.fieldType()) || field.attendanceFieldId() != null) {
                throw EventRefusal.APPOINTMENT_FIELD_TYPE_NOT_OFFERED.raise();
            }
        }
    }

    /**
     * The fields an event group hands down to its appointments.
     *
     * @param eventGroupId the event group
     * @return its fields in order
     */
    public List<AppointmentField> findFields(int eventGroupId) {
        return fields.findByGroup(eventGroupId);
    }

    /**
     * Replaces the sets of an event group: drafts with an id change that set, drafts without one add a
     * set, and sets left out are removed, which a set still holding parts refuses.
     *
     * @param stationId the station asking
     * @param eventGroupId  the event group
     * @param drafts    the sets in their new order, at least one
     * @return the sets as they are now
     */
    public List<EventGroupSet> replaceSets(int stationId, int eventGroupId, List<EventGroupSetDraft> drafts) {
        requireValidSets(drafts);
        return Transactions.call(() -> {
            lockEventGroup(stationId, eventGroupId);
            var existing = eventGroups.findSets(eventGroupId).stream()
                    .map(EventGroupSet::id)
                    .collect(Collectors.toSet());
            var kept = new HashSet<Integer>();
            for (var draft : drafts) {
                if (draft.id() == null) continue;
                if (!existing.contains(draft.id()) || !kept.add(draft.id())) {
                    throw EventRefusal.EVENT_GROUP_SET_NOT_IN_EVENT_GROUP.raise();
                }
            }
            var removed = existing.stream().filter(id -> !kept.contains(id)).toList();
            if (removed.stream().anyMatch(id -> eventGroups.countPartsInSet(id) > 0)) {
                throw EventRefusal.EVENT_GROUP_SET_HOLDS_PARTS.raise();
            }
            removed.forEach(eventGroups::deleteSet);
            for (int position = 0; position < drafts.size(); position++) {
                var draft = drafts.get(position);
                Integer setId = draft.id();
                if (setId == null) {
                    eventGroups.createSet(eventGroupId, draft.name().strip(), draft.minimum(), position);
                } else {
                    eventGroups.updateSet(setId, draft.name().strip(), draft.minimum(), position);
                }
            }
            return eventGroups.findSets(eventGroupId);
        });
    }

    /**
     * Makes an appointment a part of an event group, in one of its sets. The part takes the event group's
     * sign-up, questions, audiences and partner share from then on, and the questions it asked of its
     * own go.
     *
     * @param stationId the station asking
     * @param eventGroupId  the event group
     * @param eventId   the appointment
     * @param setId     the set it goes into
     * @return the part
     */
    public EventGroupPart addPart(int stationId, int eventGroupId, int eventId, int setId) {
        return Transactions.call(() -> {
            var eventGroup = lockEventGroup(stationId, eventGroupId);
            if (eventGroup.cancelled()) throw EventRefusal.EVENT_GROUP_CANCELLED.raise();
            eventGroups.lockEvent(eventId);
            var event = requireEvent(stationId, eventId);
            requireSetOf(eventGroupId, setId);
            if (event.eventType() != StationEvent.EventType.ONE_TIME) {
                throw EventRefusal.EVENT_GROUP_PART_NOT_ONE_TIME.raise();
            }
            if (calendar.forStation(stationId).cancelledAltogether(event)) {
                throw EventRefusal.EVENT_GROUP_PART_CANCELLED.raise();
            }
            if (eventGroups.findPart(eventId).isPresent()) throw EventRefusal.EVENT_GROUP_PART_TAKEN.raise();
            if (eventGroups.hasOpenSignUps(eventId)) throw EventRefusal.EVENT_GROUP_PART_HAS_SIGN_UPS.raise();
            if (eventGroups.isSharedWithPartners(eventId)) throw EventRefusal.EVENT_GROUP_PART_SHARED.raise();
            requireCompatibleAudiences(eventGroupId, eventId);
            if (!eventGroups.claimPart(eventId, setId)) throw EventRefusal.EVENT_GROUP_PART_TAKEN.raise();
            questions.dropQuestionsOfPart(eventId);
            eventGroups.alignParts(eventGroupId, eventGroup.requiresConfirmation(), eventGroup.registrationDeadline());
            handDownAudiences(eventGroupId, List.of(eventId));
            eventGroups.handDownShare(eventGroupId, eventId);
            return eventGroups.findPart(eventId).orElseThrow();
        });
    }

    /**
     * Moves a part into another set of its event group.
     *
     * @param stationId the station asking
     * @param eventGroupId  the event group
     * @param eventId   the part
     * @param setId     the set it moves into
     * @return the part
     */
    public EventGroupPart movePart(int stationId, int eventGroupId, int eventId, int setId) {
        return Transactions.call(() -> {
            lockEventGroup(stationId, eventGroupId);
            requirePartOf(eventGroupId, eventId);
            requireSetOf(eventGroupId, setId);
            eventGroups.setPartSet(eventId, setId);
            return eventGroups.findPart(eventId).orElseThrow();
        });
    }

    /**
     * Makes a part an appointment of its own again. Who was signed up for it stays signed up, and
     * it keeps the sign-up settings, audiences and partner share it had as a part until somebody
     * changes them, so partners' members signed up for it can still be seen and decided.
     *
     * @param stationId the station asking
     * @param eventGroupId  the event group
     * @param eventId   the part
     */
    public void removePart(int stationId, int eventGroupId, int eventId) {
        Transactions.run(() -> {
            lockEventGroup(stationId, eventGroupId);
            requirePartOf(eventGroupId, eventId);
            eventGroups.setPartSet(eventId, null);
            eventGroups.untieRegistrations(eventId);
        });
    }

    /**
     * Sets who may see and who may sign up for an event group, and hands both down to every part.
     *
     * @param stationId    the station asking
     * @param eventGroupId     the event group
     * @param registration who may sign up
     * @param view         who may see it
     */
    public void setAudiences(
            int stationId, int eventGroupId, RestrictionAudience registration, RestrictionAudience view) {
        requireOfStation(stationId, registration, view);
        Transactions.run(() -> {
            lockEventGroup(stationId, eventGroupId);
            restrictions.setAudience(RestrictionType.EVENT_GROUP, eventGroupId, registration);
            restrictions.setAudience(RestrictionType.EVENT_GROUP_VIEW, eventGroupId, view);
            handDownAudiences(
                    eventGroupId,
                    eventGroups.findParts(eventGroupId).stream()
                            .map(EventGroupPart::eventId)
                            .toList());
        });
    }

    /**
     * Who may sign up for an event group.
     *
     * @param eventGroupId the event group
     * @return the audience, one naming nobody where everybody who sees it may
     */
    public RestrictionAudience registrationAudience(int eventGroupId) {
        return restrictions.audienceOf(RestrictionType.EVENT_GROUP, eventGroupId);
    }

    /**
     * Who may see an event group.
     *
     * @param eventGroupId the event group
     * @return the audience, one naming nobody where everybody may
     */
    public RestrictionAudience viewAudience(int eventGroupId) {
        return restrictions.audienceOf(RestrictionType.EVENT_GROUP_VIEW, eventGroupId);
    }

    /**
     * Deletes an event group with its sets and sign-ups. Its parts become appointments of their own again,
     * and who was signed up for one of them stays signed up for it. A part keeps the partner share
     * it had through the event group.
     *
     * @param stationId the station asking
     * @param eventGroupId  the event group
     */
    public void delete(int stationId, int eventGroupId) {
        Transactions.run(() -> {
            lockEventGroup(stationId, eventGroupId);
            eventGroups.releaseParts(eventGroupId);
            eventGroups.delete(eventGroupId);
        });
    }

    /**
     * An event group of the station.
     *
     * @param stationId the station asking
     * @param eventGroupId  the event group
     * @return the event group
     */
    public EventGroup requireEventGroup(int stationId, int eventGroupId) {
        return eventGroups
                .findById(eventGroupId)
                .filter(eventGroup -> eventGroup.stationId() == stationId)
                .orElseThrow(EventRefusal.EVENT_GROUP_NOT_HERE::raise);
    }

    /**
     * The event groups of a station members can still answer before a deadline.
     *
     * @param stationId the station
     * @return those event groups, soonest deadline first
     */
    public List<EventGroup> findOpenWithDeadline(int stationId) {
        return eventGroups.findOpenWithDeadline(stationId);
    }

    /**
     * The event groups of a station.
     *
     * @param stationId the station
     * @return its event groups, by name
     */
    public List<EventGroup> findByStation(int stationId) {
        return eventGroups.findByStation(stationId);
    }

    /**
     * The sets of an event group in order.
     *
     * @param eventGroupId the event group
     * @return its sets
     */
    public List<EventGroupSet> findSets(int eventGroupId) {
        return eventGroups.findSets(eventGroupId);
    }

    /**
     * The parts of an event group in date order.
     *
     * @param eventGroupId the event group
     * @return its parts
     */
    public List<EventGroupPart> findParts(int eventGroupId) {
        return eventGroups.findParts(eventGroupId);
    }

    /**
     * Whether a member may see an event group. Whoever runs events sees every event group.
     *
     * @param eventGroupId    the event group
     * @param memberId    the member
     * @param permissions what the reader may do at the station
     * @return true where the event group is meant for the member
     */
    public boolean canSee(int eventGroupId, int memberId, Set<StationPermission> permissions) {
        return restrictions.checkRestriction(RestrictionType.EVENT_GROUP_VIEW, eventGroupId, memberId, permissions);
    }

    /**
     * Whether an event group is meant for any of the given members, such as a parent and the children
     * they look after.
     *
     * @param eventGroupId    the event group
     * @param memberIds   the members
     * @param permissions what the reader may do at the station
     * @return true where it is meant for at least one of them
     */
    public boolean canSeeAny(int eventGroupId, List<Integer> memberIds, Set<StationPermission> permissions) {
        return memberIds.stream().anyMatch(memberId -> canSee(eventGroupId, memberId, permissions));
    }

    /**
     * Whether somebody is a member of the station, which a sign-up for them requires.
     *
     * @param stationId the station
     * @param memberId  the member
     * @return true for a member of the station
     */
    public boolean isMemberHere(int stationId, int memberId) {
        return !restrictions.membersAt(stationId, List.of(memberId)).isEmpty();
    }

    /**
     * What an appointment says about the event group it is a part of.
     *
     * @param stationId the station
     * @param eventId   the appointment
     * @return the event group, or null for an appointment of its own
     */
    public @Nullable EventGroupPartSummary partSummary(int stationId, int eventId) {
        return eventGroups.findPartSummaries(stationId).get(eventId);
    }

    /**
     * Whether a member may sign up for an event group: see it and be one of the people it takes.
     *
     * @param eventGroupId    the event group
     * @param memberId    the member
     * @param permissions what the reader may do at the station
     * @return true where the member may sign up
     */
    public boolean canSignUp(int eventGroupId, int memberId, Set<StationPermission> permissions) {
        return canSee(eventGroupId, memberId, permissions)
                && restrictions.checkRestriction(RestrictionType.EVENT_GROUP, eventGroupId, memberId, permissions);
    }

    /** Locks an event group of the station for the running transaction and reads it. */
    private EventGroup lockEventGroup(int stationId, int eventGroupId) {
        eventGroups.lockEventGroup(eventGroupId);
        return requireEventGroup(stationId, eventGroupId);
    }

    private void handDownAudiences(int eventGroupId, List<Integer> eventIds) {
        var registration = registrationAudience(eventGroupId);
        var view = viewAudience(eventGroupId);
        for (int eventId : eventIds) {
            restrictions.setAudience(RestrictionType.EVENT, eventId, registration);
            restrictions.setAudience(RestrictionType.EVENT_VIEW, eventId, view);
        }
    }

    /**
     * Refuses an appointment whose own audiences would be replaced by different ones: an appointment
     * meant for everybody takes the event group's audience, one meant for somebody only the same.
     */
    private void requireCompatibleAudiences(int eventGroupId, int eventId) {
        var ownRegistration = restrictions.audienceOf(RestrictionType.EVENT, eventId);
        var ownView = restrictions.audienceOf(RestrictionType.EVENT_VIEW, eventId);
        if (!fits(ownRegistration, registrationAudience(eventGroupId)) || !fits(ownView, viewAudience(eventGroupId))) {
            throw EventRefusal.EVENT_GROUP_PART_AUDIENCE_DIFFERS.raise();
        }
    }

    private static boolean fits(RestrictionAudience own, RestrictionAudience eventGroup) {
        return own.namesNobody() || sameAudience(own, eventGroup);
    }

    private static boolean sameAudience(RestrictionAudience a, RestrictionAudience b) {
        return Set.copyOf(a.userTypes()).equals(Set.copyOf(b.userTypes()))
                && Set.copyOf(a.groupIds()).equals(Set.copyOf(b.groupIds()))
                && Set.copyOf(a.tagIds()).equals(Set.copyOf(b.tagIds()))
                && Set.copyOf(a.memberIds()).equals(Set.copyOf(b.memberIds()))
                && (combinedEntries(a) < 2 || a.mode() == b.mode());
    }

    /** How many entries an audience combines by its mode; named members always count on their own. */
    private static int combinedEntries(RestrictionAudience audience) {
        return audience.userTypes().size()
                + audience.groupIds().size()
                + audience.tagIds().size();
    }

    private StationEvent requireEvent(int stationId, int eventId) {
        return events.findById(eventId)
                .filter(event -> event.stationId() == stationId)
                .orElseThrow(EventRefusal.EVENT_GROUP_PART_NOT_HERE::raise);
    }

    private void requireSetOf(int eventGroupId, int setId) {
        if (eventGroups.findSets(eventGroupId).stream().noneMatch(set -> set.id() == setId)) {
            throw EventRefusal.EVENT_GROUP_SET_NOT_IN_EVENT_GROUP.raise();
        }
    }

    private void requirePartOf(int eventGroupId, int eventId) {
        eventGroups
                .findPart(eventId)
                .filter(part -> part.eventGroupId() == eventGroupId)
                .orElseThrow(EventRefusal.EVENT_GROUP_PART_NOT_IN_EVENT_GROUP::raise);
    }

    private void requireOfStation(int stationId, RestrictionAudience registration, RestrictionAudience view) {
        if (!restrictions.ofStation(stationId, registration) || !restrictions.ofStation(stationId, view)) {
            throw EventRefusal.EVENT_GROUP_AUDIENCE_NOT_OF_STATION.raise();
        }
    }

    private static EventGroupDetails checked(EventGroupDetails details) {
        String name = details.name();
        Integer limit = details.registrationLimit();
        if (name == null || name.isBlank()) throw EventRefusal.EVENT_GROUP_NAME_MISSING.raise();
        if (limit != null && limit < 1) {
            throw EventRefusal.EVENT_GROUP_LIMIT_NOT_POSITIVE.raise();
        }
        return new EventGroupDetails(
                name.strip(),
                details.description(),
                limit,
                details.registrationDeadline(),
                details.requiresConfirmation(),
                checkedColor(details.color()));
    }

    /** A colour is six hex digits after a hash, the form a colour picker writes; nothing else is stored. */
    private static @Nullable String checkedColor(@Nullable String color) {
        if (color == null || color.isBlank()) return null;
        if (!HEX_COLOR.matcher(color).matches()) throw EventRefusal.EVENT_GROUP_COLOR_INVALID.raise();
        return color.toLowerCase(Locale.ROOT);
    }

    private static void requireValidSets(List<EventGroupSetDraft> sets) {
        if (sets.isEmpty()) throw EventRefusal.EVENT_GROUP_NEEDS_A_SET.raise();
        for (var set : sets) {
            if (set.name() == null || set.name().isBlank()) throw EventRefusal.EVENT_GROUP_NAME_MISSING.raise();
            if (set.minimum() < 0) throw EventRefusal.EVENT_GROUP_SET_MINIMUM_NEGATIVE.raise();
        }
    }
}
