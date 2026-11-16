/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.api.refusal.EventRefusal;
import dev.chojo.ember.feature.events.entity.AppointmentField;
import dev.chojo.ember.feature.events.entity.EventFieldDraft;
import dev.chojo.ember.feature.events.entity.EventGroupContent;
import dev.chojo.ember.feature.events.entity.EventGroupSetDraft;
import dev.chojo.ember.feature.events.entity.EventQuestionSettings;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.repository.EventGroupRepository;
import dev.chojo.ember.feature.question.FieldType;
import dev.chojo.ember.feature.restriction.RestrictionAudience;
import org.junit.jupiter.api.Test;

import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static dev.chojo.ember.repository.EventGroupFixtures.part;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The appointment fields of an event group: shown on every part, replaced by a part's own field of the same
 * name, checked like an appointment's and gone from a part that leaves.
 */
class EventGroupServiceFieldsTest extends EventGroupServiceTestBase {
    @Test
    void anAppointmentShowsTheGroupFieldsAndItsOwnFieldOfTheSameNameReplacesOne() {
        var group = service.create(
                station.id(),
                details("Zeltlager", false),
                List.of(new EventGroupSetDraft(null, "Tage", 1)),
                RestrictionAudience.empty(),
                RestrictionAudience.empty(),
                new EventGroupContent(
                        null,
                        List.of(
                                valueField("Ort", FieldType.LOCATION, "Gerätehaus", true),
                                valueField("Mitbringen", FieldType.TEXT, "Schlafsack", false))));
        var part = appointment(station, 30, StationEvent.EventType.ONE_TIME);
        service.addPart(
                station.id(),
                group.id(),
                part.id(),
                service.findSets(group.id()).getFirst().id());
        var fields = new EventFieldService(
                eventFieldRepo,
                stationMemberRepo,
                memberEligibility,
                eventRepo,
                attendanceRepo,
                eventFieldRegistrationService,
                new EventGroupRepository());

        assertEquals(
                List.of("Ort", "Mitbringen"),
                fields.findByEvent(part.id()).stream()
                        .map(AppointmentField::name)
                        .toList());
        assertTrue(fields.findOwnByEvent(part.id()).isEmpty());
        assertTrue(fields.findByEvent(part.id()).stream().allMatch(AppointmentField::inherited));

        fields.replaceFields(part.id(), List.of(valueField("ort", FieldType.LOCATION, "Zeltplatz", false)));

        var shown = fields.findByEvent(part.id());
        assertEquals(
                List.of("Mitbringen", "ort"),
                shown.stream().map(AppointmentField::name).toList());
        assertEquals("Zeltplatz", shown.get(1).value());
        assertEquals(
                Map.of(),
                fields.findOverviewFieldsByEvents(List.of(part.id())).entrySet().stream()
                        .filter(entry -> !entry.getValue().isEmpty())
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
    }

    @Test
    void anEventGroupTakesNoFieldTiedToAnAttendanceSheet() {
        var member = new EventFieldDraft(
                null, "Leitung", FieldType.MEMBER, EventQuestionSettings.empty(), "", false, 4, false);

        assertRefused(
                EventRefusal.APPOINTMENT_FIELD_TYPE_NOT_OFFERED,
                () -> service.create(
                        station.id(),
                        details("Wettbewerb", false),
                        List.of(new EventGroupSetDraft(null, "Tage", 1)),
                        RestrictionAudience.empty(),
                        RestrictionAudience.empty(),
                        new EventGroupContent(null, List.of(member))));
        assertTrue(
                eventGroupRepo.findByStation(station.id()).stream()
                        .noneMatch(eventGroup -> eventGroup.details().name().equals("Wettbewerb")),
                "nothing of the refused event group is kept");
    }

    @Test
    void listsAndDatedReadsShowTheGroupOverviewFields() {
        var group = groupWith("Wettbewerb", valueField("Ort", FieldType.LOCATION, "Sportplatz", true));
        var part = part(group, 31);
        var day = eventRepo
                .findById(part.id())
                .orElseThrow()
                .startTime()
                .atZone(ZoneOffset.UTC)
                .toLocalDate();
        var fields = fieldService();

        assertEquals(
                List.of("Ort"),
                fields.findOverviewFieldsByEvents(List.of(part.id())).get(part.id()).stream()
                        .map(AppointmentField::name)
                        .toList());
        assertEquals(
                List.of("Ort"),
                fields.findOverviewFieldsByEvents(List.of(part.id()), Map.of(part.id(), day)).get(part.id()).stream()
                        .map(AppointmentField::name)
                        .toList());
        assertEquals("Sportplatz", AppointmentField.firstLocation(eventFieldRepo.findShownOn(part.id(), day)));
    }

    @Test
    void savingWithoutFieldsKeepsThemAndSavingWithFieldsReplacesThem() {
        var group = groupWith("Ausbildung", valueField("Ort", FieldType.LOCATION, "Gerätehaus", true));
        var unchanged = details("Ausbildung", false);

        service.save(station.id(), group.id(), unchanged, null, null, null, null);
        assertEquals(
                List.of("Ort"),
                service.findFields(group.id()).stream()
                        .map(AppointmentField::name)
                        .toList());

        service.save(
                station.id(),
                group.id(),
                unchanged,
                null,
                null,
                null,
                new EventGroupContent(null, List.of(valueField("Mitbringen", FieldType.TEXT, "Helm", false))));
        assertEquals(
                List.of("Mitbringen"),
                service.findFields(group.id()).stream()
                        .map(AppointmentField::name)
                        .toList());
    }

    @Test
    void aGroupFieldIsCheckedLikeAnAppointmentField() {
        var choice = new EventFieldDraft(
                null,
                "Kleidung",
                FieldType.CHOICE,
                new EventQuestionSettings(
                        List.of("Uniform", "Zivil"),
                        null,
                        null,
                        null,
                        null,
                        false,
                        false,
                        false,
                        null,
                        null,
                        null,
                        false,
                        null),
                "Badehose",
                false,
                null,
                false);
        var tied = new EventFieldDraft(null, "Ort", FieldType.TEXT, EventQuestionSettings.empty(), "", false, 4, false);

        assertRefused(EventRefusal.APPOINTMENT_FIELD_VALUE_NOT_ACCEPTED, () -> groupWith("Wahl", choice));
        assertRefused(EventRefusal.APPOINTMENT_FIELD_TYPE_NOT_OFFERED, () -> groupWith("Bindung", tied));
        var perDate = new EventFieldDraft(
                null,
                "Thema",
                FieldType.TEXT,
                new EventQuestionSettings(
                        null, null, null, null, null, false, true, false, null, null, null, false, null),
                "",
                false,
                null,
                false);
        assertRefused(EventRefusal.APPOINTMENT_FIELD_TYPE_NOT_OFFERED, () -> groupWith("Datum", perDate));
    }

    @Test
    void aPartLeavingTheGroupNoLongerShowsItsFields() {
        var group = groupWith("Lager", valueField("Ort", FieldType.LOCATION, "Zeltplatz", true));
        var part = part(group, 32);
        var fields = fieldService();
        assertEquals(1, fields.findByEvent(part.id()).size());

        service.removePart(station.id(), group.id(), part.id());

        assertTrue(fields.findByEvent(part.id()).isEmpty());
    }
}
