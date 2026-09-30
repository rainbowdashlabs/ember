/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.route;

import dev.chojo.ember.api.MemberIdentity;
import dev.chojo.ember.feature.events.entity.EventField;
import dev.chojo.ember.feature.events.entity.EventRegistration;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.service.EventCrudService;
import dev.chojo.ember.feature.events.service.EventFieldService;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.members.service.MemberIdentityFactory;
import dev.chojo.ember.feature.members.service.MemberNameResolver;
import dev.chojo.ember.feature.question.QuestionValues;

import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * What the rows of one list of people share, read once for the list rather than once per row.
 *
 * <p>A list of registrations names the same appointment on most of its rows, and the same members
 * and guardians again and again. The members are read in one statement up front, an appointment and
 * the questions it asked on a date are read the first time a row needs them and kept for the rest,
 * and names come from {@link MemberNameResolver}, whose cache answers a name it has already read.
 * One instance serves one response and is thrown away with it, so nothing here outlives a change.
 */
final class RegistrationRowLookups {
    private final EventCrudService crudService;
    private final EventFieldService eventFieldService;
    private final MemberNameResolver nameResolver;
    private final MemberIdentityFactory identityFactory;
    private final Map<Integer, StationMember> members;
    private final Map<Integer, MemberDisplay> displays = new HashMap<>();
    private final Map<Integer, Optional<String>> eventNames = new HashMap<>();
    private final Map<EventDay, List<EventField>> questions = new HashMap<>();

    private RegistrationRowLookups(
            EventCrudService crudService,
            EventFieldService eventFieldService,
            MemberNameResolver nameResolver,
            MemberIdentityFactory identityFactory,
            Map<Integer, StationMember> members) {
        this.crudService = crudService;
        this.eventFieldService = eventFieldService;
        this.nameResolver = nameResolver;
        this.identityFactory = identityFactory;
        this.members = members;
    }

    /**
     * Reads the members a list is about in one statement and keeps the rest to be read on demand.
     *
     * @param memberIds the members the rows of the list are about, repeats allowed
     */
    static RegistrationRowLookups forMembers(
            Collection<Integer> memberIds,
            StationMemberRepository memberRepository,
            EventCrudService crudService,
            EventFieldService eventFieldService,
            MemberNameResolver nameResolver,
            MemberIdentityFactory identityFactory) {
        var members = memberRepository.findByIds(memberIds.stream().distinct().toList()).stream()
                .collect(Collectors.toMap(StationMember::id, Function.identity()));
        return new RegistrationRowLookups(crudService, eventFieldService, nameResolver, identityFactory, members);
    }

    /**
     * A member's name and identity, an empty name and no identity where the member is not known.
     *
     * @param memberId one of the members the lookups were made for
     */
    MemberDisplay member(int memberId) {
        return displays.computeIfAbsent(memberId, this::readMember);
    }

    private MemberDisplay readMember(int memberId) {
        var member = members.get(memberId);
        if (member == null) return new MemberDisplay("", null);
        return new MemberDisplay(
                Objects.requireNonNullElse(nameResolver.called(memberId), ""),
                identityFactory.local(member.stationId(), memberId));
    }

    /**
     * The name of whoever filed a registration for somebody else.
     *
     * @return the name, or null where the member filed it themselves or is not known
     */
    String createdByName(Integer createdBy) {
        return createdBy == null ? null : nameResolver.called(createdBy);
    }

    /**
     * What an appointment is called.
     *
     * @return the name, or null where the appointment is gone
     */
    String eventName(int eventId) {
        return eventNames
                .computeIfAbsent(eventId, id -> crudService.findById(id).map(StationEvent::name))
                .orElse(null);
    }

    /**
     * The question that put this member on the list, named so the reader knows where to go to come
     * off it again.
     *
     * <p>Several questions may name the same member on the same date, and the first of them is
     * enough: what the line beside the entry has to say is that a question holds the place, not how
     * many do.
     *
     * @return the question's name, or null where none of them names the member
     */
    String namingField(EventRegistration registration) {
        return questions
                .computeIfAbsent(
                        new EventDay(registration.eventId(), registration.eventDate()),
                        day -> eventFieldService.findByEvent(day.eventId(), day.date()))
                .stream()
                .filter(field -> field.fieldType().isMemberField())
                .filter(field -> QuestionValues.memberIds(field.value()).contains(registration.memberId()))
                .map(EventField::name)
                .findFirst()
                .orElse(null);
    }

    /** A member's display name, empty when unknown, and their identity, {@code null} when unknown. */
    record MemberDisplay(String name, MemberIdentity identity) {}

    private record EventDay(int eventId, LocalDate date) {}
}
