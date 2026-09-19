/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.events.entity.EventFieldType;
import dev.chojo.ember.feature.events.entity.EventRegistration;
import dev.chojo.ember.feature.events.repository.EventRegistrationRepository;
import dev.chojo.ember.feature.members.entity.MemberTable;
import dev.chojo.ember.feature.members.entity.MemberTableCellType;
import dev.chojo.ember.feature.members.entity.MemberTableColumn;
import dev.chojo.ember.feature.members.entity.MemberTablePeople;
import dev.chojo.ember.feature.members.entity.MemberTableQuestion;
import dev.chojo.ember.feature.members.service.MemberTableService;
import dev.chojo.ember.feature.station.entity.Station;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Names the people an appointment's table is about, and hands them to the table.
 *
 * <p>One of the two ways of naming people. Everything about drawing the table itself is the same
 * wherever it is drawn, which is the point: this says who, and nothing about what may be shown.
 *
 * <p>Except for one thing that only an appointment has. A question can be kept for whoever runs the
 * appointment, never asked of the member and never shown to the room, and a table naming that
 * question would be the softer door to what the registration list already keeps shut. So those are
 * taken out before the table ever sees them.
 */
@Singleton
public class EventMemberTableService {
    private final EventRegistrationRepository registrationRepository;
    private final EventRegistrationFieldService registrationFieldService;
    private final MemberTableService memberTableService;

    @Inject
    public EventMemberTableService(
            EventRegistrationRepository registrationRepository,
            EventRegistrationFieldService registrationFieldService,
            MemberTableService memberTableService) {
        this.registrationRepository = registrationRepository;
        this.registrationFieldService = registrationFieldService;
        this.memberTableService = memberTableService;
    }

    /**
     * The questions of this appointment a reader may choose as columns.
     *
     * @param eventId            the appointment
     * @param readsHiddenAnswers whether this reader may see the questions kept for whoever runs it
     * @return the questions, by id
     */
    public Map<Integer, MemberTableQuestion> offerableQuestions(int eventId, boolean readsHiddenAnswers) {
        var hidden = registrationFieldService.hiddenFieldIds(eventId, readsHiddenAnswers);
        var questions = new LinkedHashMap<Integer, MemberTableQuestion>();
        for (var field : registrationFieldService.findByEvent(eventId)) {
            if (hidden.contains(field.id())) continue;
            questions.put(field.id(), new MemberTableQuestion(field.name(), cellTypeOf(field.fieldType())));
        }
        return questions;
    }

    /**
     * What an answer to a question of this kind holds in the table.
     *
     * <p>A choice and a named member stay text: the table writes the answer as it was given.
     */
    static MemberTableCellType cellTypeOf(EventFieldType type) {
        if (type == null) return MemberTableCellType.TEXT;
        return switch (type) {
            case NUMBER -> MemberTableCellType.NUMBER;
            case DATE -> MemberTableCellType.DATE;
            case BOOLEAN -> MemberTableCellType.BOOLEAN;
            default -> MemberTableCellType.TEXT;
        };
    }

    /**
     * Draws the table of everybody standing on this appointment's list on one day.
     *
     * <p>Every answer given for that day comes back, whatever it was. Which of them to show is the
     * screen's to decide and it opens on the confirmed ones, because somebody chasing a missing answer
     * wants the same list for the opposite reason: a table that only ever held the people who are
     * coming could not be asked who is not.
     *
     * @param station            the station holding the appointment
     * @param eventId            the appointment
     * @param date               the day in question, because a registration belongs to one
     * @param columns            the columns asked for
     * @param permissions        the reader's own permissions, already expanded
     * @param readsHiddenAnswers whether this reader may see the questions kept for whoever runs it
     * @return the drawn table
     */
    public MemberTable table(
            Station station,
            int eventId,
            LocalDate date,
            List<MemberTableColumn> columns,
            Set<StationPermission> permissions,
            boolean readsHiddenAnswers) {
        var standing = registrationRepository.findByEventAndDate(eventId, date);
        var questions = offerableQuestions(eventId, readsHiddenAnswers);
        var people = peopleOf(standing, questions.keySet());
        return memberTableService.build(station, people, columns, permissions, questions);
    }

    private MemberTablePeople peopleOf(List<EventRegistration> registrations, Set<Integer> askableQuestions) {
        var answersByRegistration = registrationFieldService.findValuesByRegistration(
                registrations.stream().map(EventRegistration::id).toList());

        var memberIds = new ArrayList<Integer>(registrations.size());
        var answers = new HashMap<Integer, Map<Integer, String>>();
        var statuses = new HashMap<Integer, String>();
        for (var registration : registrations) {
            memberIds.add(registration.memberId());
            statuses.put(registration.memberId(), registration.status().name());
            var given = new HashMap<Integer, String>();
            for (var value : answersByRegistration.getOrDefault(registration.id(), List.of())) {
                if (!askableQuestions.contains(value.fieldId())) continue;
                given.put(value.fieldId(), value.value());
            }
            answers.put(registration.memberId(), given);
        }
        return new MemberTablePeople(memberIds, answers, statuses);
    }
}
