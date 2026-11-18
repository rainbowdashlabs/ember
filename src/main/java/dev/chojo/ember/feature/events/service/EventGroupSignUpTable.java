/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.api.MemberIdentity;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.events.entity.EventGroupPartState;
import dev.chojo.ember.feature.events.entity.EventGroupRegistration;
import dev.chojo.ember.feature.events.entity.EventRegistrationField;
import dev.chojo.ember.feature.events.entity.RegistrationStatus;
import dev.chojo.ember.feature.events.service.EventGroupRegistrationService.EventGroupStanding;
import dev.chojo.ember.feature.members.entity.MemberTable;
import dev.chojo.ember.feature.members.entity.MemberTableCellType;
import dev.chojo.ember.feature.members.entity.MemberTableColumn;
import dev.chojo.ember.feature.members.entity.MemberTableColumnKind;
import dev.chojo.ember.feature.members.entity.MemberTablePeople;
import dev.chojo.ember.feature.members.entity.MemberTableQuestion;
import dev.chojo.ember.feature.members.service.MemberTableService;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.entity.StationFormat;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The sign-ups of an event group as one table: a row per sign-up, the status, the answers to its
 * questions and a column per part marked where the part was chosen.
 *
 * <p>The station's own sign-ups come first, drawn the way an appointment's registration table is,
 * so the file reads the same. Sign-ups from partner stations follow, named with their station; they
 * answered no questions here. A sign-up that was withdrawn or declined is left out, as on the page.
 * Questions kept for organisers are only shown to whoever edits appointments.
 */
@Singleton
public class EventGroupSignUpTable {
    /** What a chosen part reads as in its column. */
    static final String CHOSEN = "x";

    private static final DateTimeFormatter PART_DAY = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final EventGroupService eventGroups;
    private final EventGroupRegistrationService registrations;
    private final EventGroupQuestionService questions;
    private final EventGroupPartnerService partners;
    private final MemberTableService memberTables;

    @Inject
    public EventGroupSignUpTable(
            EventGroupService eventGroups,
            EventGroupRegistrationService registrations,
            EventGroupQuestionService questions,
            EventGroupPartnerService partners,
            MemberTableService memberTables) {
        this.eventGroups = eventGroups;
        this.registrations = registrations;
        this.questions = questions;
        this.partners = partners;
        this.memberTables = memberTables;
    }

    /**
     * Draws the table of an event group's sign-ups.
     *
     * @param station            the station holding the event group
     * @param eventGroupId       the event group
     * @param permissions        the reader's own permissions, already expanded
     * @param readsHiddenAnswers whether the reader may see the questions kept for organisers
     * @return the table
     */
    public MemberTable table(
            Station station, int eventGroupId, Set<StationPermission> permissions, boolean readsHiddenAnswers) {
        eventGroups.requireEventGroup(station.id(), eventGroupId);
        var asked = askedQuestions(eventGroupId, readsHiddenAnswers);
        var signUps = registrations.findByEventGroup(eventGroupId).stream()
                .filter(signUp -> signUp.active() || signUp.status() == RegistrationStatus.DENIED)
                .toList();
        var columns = new ArrayList<MemberTableColumn>();
        columns.add(MemberTableColumn.builtin("name"));
        columns.add(MemberTableColumn.builtin("registrationStatus"));
        asked.keySet().forEach(fieldId -> columns.add(MemberTableColumn.registrationField(fieldId)));
        var local = memberTables.build(station, peopleOf(signUps, asked.keySet()), columns, permissions, asked);

        var parts = registrations.partStates(eventGroupId);
        var chosen =
                registrations
                        .standings(
                                eventGroupId,
                                signUps.stream()
                                        .map(EventGroupRegistration::memberId)
                                        .toList())
                        .stream()
                        .collect(Collectors.toMap(
                                EventGroupStanding::memberId, EventGroupStanding::counted, (a, b) -> a));

        var headers = new ArrayList<>(local.columns());
        parts.forEach(part -> headers.add(partHeader(part, station)));
        var rows = new ArrayList<MemberTable.MemberTableRow>();
        for (var row : local.rows()) {
            rows.add(new MemberTable.MemberTableRow(
                    row.memberId(), marked(row.values(), parts, chosen.getOrDefault(row.memberId(), Set.of()))));
        }
        for (var partner : partners.signUps(station.id(), eventGroupId).stream()
                .filter(signUp -> standing(signUp.standing().status()))
                .toList()) {
            rows.add(partnerRow(partner, local.columns(), parts));
        }
        return new MemberTable(headers, rows);
    }

    private Map<Integer, MemberTableQuestion> askedQuestions(int eventGroupId, boolean readsHiddenAnswers) {
        var asked = new LinkedHashMap<Integer, MemberTableQuestion>();
        for (EventRegistrationField field : questions.findByEventGroup(eventGroupId)) {
            if (!readsHiddenAnswers && field.config().managersOnly()) continue;
            asked.put(field.id(), new MemberTableQuestion(field.name(), field.fieldType()));
        }
        return asked;
    }

    private MemberTablePeople peopleOf(List<EventGroupRegistration> signUps, Set<Integer> asked) {
        var answers = questions.answersBySignUp(
                signUps.stream().map(EventGroupRegistration::id).toList());
        var memberIds = new ArrayList<Integer>();
        var given = new HashMap<Integer, Map<Integer, String>>();
        var statuses = new HashMap<Integer, String>();
        for (var signUp : signUps) {
            memberIds.add(signUp.memberId());
            statuses.put(signUp.memberId(), statusCell(signUp.status()));
            given.put(
                    signUp.memberId(),
                    answers.getOrDefault(signUp.id(), List.of()).stream()
                            .filter(value -> asked.contains(value.fieldId()))
                            .collect(Collectors.toMap(value -> value.fieldId(), value -> value.value(), (a, b) -> a)));
        }
        return new MemberTablePeople(memberIds, given, statuses);
    }

    private static MemberTable.MemberTableHeader partHeader(EventGroupPartState part, Station station) {
        String day = PART_DAY.format(part.startTime().atZone(StationFormat.timezoneOf(station)));
        return new MemberTable.MemberTableHeader(
                part.name() + " " + day,
                MemberTableColumnKind.BUILTIN,
                "part:" + part.eventId(),
                null,
                MemberTableCellType.TEXT);
    }

    private static List<String> marked(List<String> values, List<EventGroupPartState> parts, Set<Integer> chosen) {
        var line = new ArrayList<>(values);
        parts.forEach(part -> line.add(chosen.contains(part.eventId()) ? CHOSEN : ""));
        return line;
    }

    /**
     * A partner's sign-up: the name with its station, the status, nothing for the questions it was
     * never asked here, and its parts.
     */
    private static MemberTable.MemberTableRow partnerRow(
            EventGroupPartnerService.PartnerSignUp signUp,
            List<MemberTable.MemberTableHeader> localColumns,
            List<EventGroupPartState> parts) {
        var status = signUp.standing().status();
        var values = new ArrayList<String>();
        for (var column : localColumns) {
            String key = column.key();
            if ("name".equals(key)) values.add(nameOf(signUp.member()));
            else if ("registrationStatus".equals(key)) values.add(status == null ? "" : statusCell(status));
            else values.add("");
        }
        return new MemberTable.MemberTableRow(
                0, marked(values, parts, Set.copyOf(signUp.standing().parts())));
    }

    /**
     * A sign-up the table lists: waiting for a decision, accepted, on the waiting list or turned down;
     * withdrawn and declined ones are left out.
     */
    private static boolean standing(@Nullable RegistrationStatus status) {
        return status != null && (status.standing() || status == RegistrationStatus.DENIED);
    }

    /** The status as the table's status column holds it, for the station's own sign-ups and partners' alike. */
    private static String statusCell(RegistrationStatus status) {
        return status.name();
    }

    private static String nameOf(@Nullable MemberIdentity member) {
        if (member == null) return "";
        String name = member.name() == null ? "" : member.name();
        String station = member.stationName();
        return station == null || station.isBlank() ? name : name + " (" + station + ")";
    }
}
