/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.attendance.entity.AttendanceFieldConfig;
import dev.chojo.ember.feature.attendance.entity.AttendanceFieldType;
import dev.chojo.ember.feature.events.entity.EventFieldConfig;
import dev.chojo.ember.feature.events.entity.EventFieldType;
import dev.chojo.ember.feature.events.entity.EventTemplateFieldData;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.repository.EventTemplateRepository;
import dev.chojo.ember.feature.restriction.RestrictionMode;
import dev.chojo.ember.feature.restriction.RestrictionSelection;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class EventTemplateServiceTest extends RepositoryTestBase {

    private static EventTemplateService service;
    private static EventTemplateRestrictionService restrictions;
    private static Station station;
    private static int templateId;

    @BeforeAll
    static void setup() {
        var repository = new EventTemplateRepository();
        service = new EventTemplateService(repository, attendanceRepo);
        restrictions = new EventTemplateRestrictionService(repository, restrictionService);
        station = stationRepo.create("EventTemplateServiceStation");
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
    }

    @Test
    @Order(1)
    void createTemplate() {
        var template = service.create(station.id(), "Training Template");
        assertNotNull(template);
        assertEquals("Training Template", template.name());
        assertEquals(station.id(), template.stationId());
        templateId = template.id();
    }

    @Test
    @Order(2)
    void findById() {
        var found = service.findById(templateId);
        assertTrue(found.isPresent());
        assertEquals(templateId, found.get().id());
        assertEquals("Training Template", found.get().name());
    }

    @Test
    @Order(3)
    void findByIdMissing() {
        assertTrue(service.findById(999999).isEmpty());
    }

    @Test
    @Order(4)
    void findByStation() {
        var templates = service.findByStation(station.id());
        assertTrue(templates.stream().anyMatch(t -> t.id() == templateId));
    }

    @Test
    @Order(5)
    void updateTemplate() {
        boolean updated = service.update(
                templateId,
                "Updated Template",
                "Weekly Training",
                "Regular training session",
                null,
                StationEvent.EventType.RECURRING,
                true,
                "1 day",
                false,
                RestrictionMode.AND,
                null,
                null);
        assertTrue(updated);
        var found = service.findById(templateId).orElseThrow();
        assertEquals("Updated Template", found.name());
        assertEquals("Weekly Training", found.title());
    }

    @Test
    @Order(10)
    void replaceAndFindFields() {
        var fields = List.of(
                new EventTemplateFieldData(
                        "Location",
                        EventFieldType.STRING,
                        EventFieldConfig.parse("{}"),
                        0,
                        true,
                        false,
                        null,
                        "Gerätehaus"),
                new EventTemplateFieldData(
                        "Notes", EventFieldType.STRING, EventFieldConfig.parse("{}"), 1, false, true, null, null));
        service.replaceFields(templateId, fields);

        var found = service.findFields(templateId);
        assertEquals(2, found.size());
        assertEquals(
                "Gerätehaus",
                found.stream()
                        .filter(f -> f.name().equals("Location"))
                        .findFirst()
                        .orElseThrow()
                        .defaultValue(),
                "a question keeps what it starts an appointment off with");
        assertTrue(found.stream().anyMatch(f -> f.name().equals("Location") && f.overview()));
        assertTrue(found.stream().anyMatch(f -> f.name().equals("Notes") && f.isPublic()));
    }

    @Test
    @Order(11)
    void replaceFieldsClearsOld() {
        service.replaceFields(
                templateId,
                List.of(new EventTemplateFieldData(
                        "OnlyField",
                        EventFieldType.STRING,
                        EventFieldConfig.parse("{}"),
                        0,
                        false,
                        false,
                        null,
                        null)));

        var found = service.findFields(templateId);
        assertEquals(1, found.size());
        assertEquals("OnlyField", found.getFirst().name());
    }

    /**
     * A question can only be tied to a field of the sheet the template actually names.
     *
     * <p>The reported fault, in the shape it was found in: two attendance sheets each carried a field
     * called "Ausbilder Anfänger", the picker offered every sheet's fields at once with nothing to
     * tell them apart, and the template ended up tied to a field of a sheet it does not use. Nothing
     * complained, and the answer would have been written into a sheet nobody opens.
     */
    @Test
    @Order(13)
    void aTieToAnotherSheetIsNotKept() {
        var ours = attendanceRepo.createTemplate(station.id(), "Unser Bogen");
        attendanceRepo.createTemplateField(
                ours.id(), "Ausbilder", AttendanceFieldType.STRING, AttendanceFieldConfig.parse("{}"), 0);
        int mine = attendanceRepo.findTemplateFields(ours.id()).getFirst().id();

        var theirs = attendanceRepo.createTemplate(station.id(), "Fremder Bogen");
        attendanceRepo.createTemplateField(
                theirs.id(), "Ausbilder", AttendanceFieldType.STRING, AttendanceFieldConfig.parse("{}"), 0);
        int foreign = attendanceRepo.findTemplateFields(theirs.id()).getFirst().id();

        service.update(templateId, "Bogenprobe", null, null, null, null, null, null, null, null, ours.id(), null);
        service.replaceFields(
                templateId,
                List.of(
                        new EventTemplateFieldData(
                                "Eigene",
                                EventFieldType.STRING,
                                EventFieldConfig.parse("{}"),
                                0,
                                false,
                                false,
                                mine,
                                null),
                        new EventTemplateFieldData(
                                "Fremde",
                                EventFieldType.STRING,
                                EventFieldConfig.parse("{}"),
                                1,
                                false,
                                false,
                                foreign,
                                null)));

        var stored = service.findFields(templateId);
        assertEquals(
                mine,
                stored.stream()
                        .filter(f -> f.name().equals("Eigene"))
                        .findFirst()
                        .orElseThrow()
                        .attendanceFieldId(),
                "the tie into our own sheet is kept");
        assertNull(
                stored.stream()
                        .filter(f -> f.name().equals("Fremde"))
                        .findFirst()
                        .orElseThrow()
                        .attendanceFieldId(),
                "and the one into somebody else's sheet is not");

        attendanceRepo.deleteTemplate(ours.id());
        attendanceRepo.deleteTemplate(theirs.id());
    }

    @Test
    @Order(12)
    void replaceFieldsWithEmpty() {
        service.replaceFields(templateId, List.of());
        assertTrue(service.findFields(templateId).isEmpty());
    }

    /** Who a template hands its appointments to, in every kind it can be said. */
    @Test
    @Order(20)
    void setAndFindRestrictions() {
        restrictions.setRestrictions(
                templateId,
                new RestrictionSelection(
                        List.of(StationUserType.MEMBER, StationUserType.TEAM, StationUserType.MANAGER),
                        List.of(),
                        List.of(),
                        List.of(),
                        RestrictionMode.OR));

        var set = restrictions.findRestrictions(templateId);
        assertEquals(3, set.userTypes().size());
        assertTrue(set.userTypes().containsAll(List.of(StationUserType.MEMBER, StationUserType.TEAM)));
    }

    /**
     * A group is an audience a template can name, which it could not before.
     *
     * <p>The point of the whole thing: a station running one evening for the youngest group used to
     * pick that group again on every date of the year, because a template could only say what kind
     * of member somebody is.
     */
    @Test
    @Order(21)
    void aTemplateCanNameAGroup() {
        var group = memberGroupRepo.create(station.id(), "Vorlagengruppe");

        restrictions.setRestrictions(
                templateId,
                new RestrictionSelection(List.of(), List.of(group.id()), List.of(), List.of(), RestrictionMode.AND));

        var set = restrictions.findRestrictions(templateId);
        assertEquals(List.of(group.id()), set.groupIds());
        assertTrue(set.userTypes().isEmpty(), "and the kinds it named before are gone");
        assertEquals(RestrictionMode.AND, set.mode(), "the mode is kept on the template itself");
    }

    /**
     * How the named audiences combine lives on the template, not among them.
     *
     * <p>Which is why it survives the audience being rewritten, and why setting it is its own step.
     */
    @Test
    @Order(22)
    void theModeIsKeptOnTheTemplateItself() {
        restrictions.updateRestrictionMode(templateId, RestrictionMode.OR);
        assertEquals(
                RestrictionMode.OR, restrictions.findRestrictions(templateId).mode());

        restrictions.updateRestrictionMode(templateId, RestrictionMode.AND);
        assertEquals(
                RestrictionMode.AND, restrictions.findRestrictions(templateId).mode());
    }

    /**
     * A template carries both audiences an appointment does, and keeps them apart.
     *
     * <p>Without the second one, whoever applies the template would have to say again on every date
     * of the year who may know about it, which is the one thing a template is for.
     */
    @Test
    @Order(24)
    void aTemplateCarriesBothAudiencesApart() {
        restrictions.setRestrictions(
                templateId,
                new RestrictionSelection(
                        List.of(StationUserType.MEMBER), List.of(), List.of(), List.of(), RestrictionMode.AND));
        restrictions.setViewRestrictions(
                templateId,
                new RestrictionSelection(
                        List.of(StationUserType.TEAM), List.of(), List.of(), List.of(), RestrictionMode.AND));

        assertEquals(
                List.of(StationUserType.MEMBER),
                restrictions.findRestrictions(templateId).userTypes());
        assertEquals(
                List.of(StationUserType.TEAM),
                restrictions.findViewRestrictions(templateId).userTypes());
    }

    /** The two modes sit in columns of their own, so setting one leaves the other alone. */
    @Test
    @Order(25)
    void theTwoModesAreKeptApart() {
        restrictions.updateRestrictionMode(templateId, RestrictionMode.OR);
        restrictions.updateViewRestrictionMode(templateId, RestrictionMode.AND);

        assertEquals(
                RestrictionMode.OR, restrictions.findRestrictions(templateId).mode());
        assertEquals(
                RestrictionMode.AND,
                restrictions.findViewRestrictions(templateId).mode());

        restrictions.updateViewRestrictionMode(templateId, RestrictionMode.OR);
        assertEquals(
                RestrictionMode.OR,
                restrictions.findViewRestrictions(templateId).mode());
        assertEquals(
                RestrictionMode.OR,
                restrictions.findRestrictions(templateId).mode(),
                "setting the view mode leaves the registration mode where it was");
    }

    /** A template that is not there names nobody, rather than falling over. */
    @Test
    @Order(23)
    void anUnknownTemplateNamesNobody() {
        assertFalse(restrictions.findViewRestrictions(999999).hasRestrictions());
        assertFalse(restrictions.findRestrictions(999999).hasRestrictions());
    }

    @Test
    @Order(24)
    void setRestrictionsEmpty() {
        restrictions.setRestrictions(
                templateId, new RestrictionSelection(List.of(), List.of(), List.of(), List.of(), null));
        assertFalse(restrictions.findRestrictions(templateId).hasRestrictions());
    }

    @Test
    @Order(30)
    void setAndFindReminders() {
        service.setReminders(templateId, List.of(1, 3, 7));
        var days = service.findReminderDays(templateId);
        assertEquals(3, days.size());
        assertEquals(List.of(1, 3, 7), days);
    }

    @Test
    @Order(31)
    void setRemindersReplaces() {
        service.setReminders(templateId, List.of(2));
        var days = service.findReminderDays(templateId);
        assertEquals(1, days.size());
        assertEquals(2, days.getFirst());
    }

    @Test
    @Order(32)
    void setRemindersEmpty() {
        service.setReminders(templateId, List.of());
        assertTrue(service.findReminderDays(templateId).isEmpty());
    }

    @Test
    @Order(40)
    void updateNotFound() {
        boolean updated = service.update(
                999999,
                "Nope",
                "Nope",
                "Nope",
                null,
                StationEvent.EventType.ONE_TIME,
                false,
                null,
                false,
                RestrictionMode.AND,
                null,
                null);
        assertFalse(updated);
    }

    @Test
    @Order(41)
    void deleteNotFound() {
        assertFalse(service.delete(999999));
    }

    @Test
    @Order(99)
    void deleteTemplate() {
        assertTrue(service.delete(templateId));
        assertTrue(service.findById(templateId).isEmpty());
    }
}
