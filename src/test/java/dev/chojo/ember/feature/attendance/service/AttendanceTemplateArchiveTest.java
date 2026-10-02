/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.attendance.service;

import dev.chojo.ember.api.refusal.RefusalResponse;
import dev.chojo.ember.conf.file.elements.Api;
import dev.chojo.ember.conf.file.elements.Attendance;
import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.attendance.entity.AttendanceEntry;
import dev.chojo.ember.feature.attendance.entity.AttendanceFieldConfig;
import dev.chojo.ember.feature.attendance.entity.AttendanceFieldValueEntry;
import dev.chojo.ember.feature.attendance.entity.AttendanceSession;
import dev.chojo.ember.feature.attendance.entity.TemplateGroup;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.repository.EventTemplateRepository;
import dev.chojo.ember.feature.events.service.EventCrudService;
import dev.chojo.ember.feature.events.service.EventTemplateService;
import dev.chojo.ember.feature.members.entity.MemberGroup;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.question.FieldType;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.statistics.repository.StatisticsRepository;
import dev.chojo.ember.repository.RepositoryTestBase;
import dev.chojo.ember.util.sql.SqlSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Deleting an attendance template archives it, and nothing that was written with it goes.
 *
 * <p>Sheets are history. A station that tidies its templates must still find every sheet made from
 * one, with its people, its answers and its hours, exactly where it was, while the template itself
 * is offered for nothing new.
 */
class AttendanceTemplateArchiveTest extends RepositoryTestBase {
    private static final Instant START = Instant.now().plus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.HOURS);
    private static final Instant END = START.plus(3, ChronoUnit.HOURS);

    private static AttendanceService service;
    private static AttendanceReportService reportService;
    private static EventCrudService eventCrud;
    private static EventTemplateService eventTemplates;
    private static Station station;
    private static Account account;
    private static StationMember member;
    private static MemberGroup group;

    private int templateId;
    private int fieldId;
    private AttendanceSession sheet;

    @BeforeAll
    static void setup() {
        service = new AttendanceService(
                attendanceRepo,
                eventRepo,
                eventFieldRepo,
                eventFieldDefaultRepo,
                eventRegistrationRepo,
                stationMemberRepo,
                memberGroupRepo,
                new Attendance(),
                stationRepo,
                eventDateCancellationRepo,
                new AttendanceAudienceService(attendanceRepo),
                memberEligibility,
                new AttendanceTemplateGuards(attendanceRepo));
        reportService = new AttendanceReportService(
                attendanceRepo,
                stationMemberRepo,
                accountRepo,
                stationRepo,
                memberGroupRepo,
                new Api(),
                newStationLogoService());
        eventCrud = newEventServices(new DomainEventBus(Set.of())).crud();
        eventTemplates = new EventTemplateService(
                new EventTemplateRepository(),
                attendanceRepo,
                memberEligibility,
                new AttendanceTemplateGuards(attendanceRepo));
        station = stationRepo.create("Template Archive Station");
        account = accountRepo.create("template-archive@test.com", "Tilda", "Template");
        member = stationMemberRepo.create(station.id(), account.id());
        group = memberGroupRepo.create(station.id(), "Zeltlager");
        memberGroupRepo.addMember(group.id(), member.id());
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
        accountRepo.delete(account.id());
    }

    /** A template with a field and a group, one sheet made from it, an answer and a member present for its whole span. */
    @BeforeEach
    void sheetFromATemplate() {
        templateId = service.createTemplate(station.id(), "Sommerlager " + System.nanoTime())
                .id();
        fieldId = service.createTemplateField(
                        templateId, "Leitung", FieldType.TEXT, AttendanceFieldConfig.parse("{}"), 0)
                .getFirst()
                .id();
        service.setTemplateGroups(templateId, List.of(new TemplateGroup(group.id(), 0)));
        sheet = service.createSession(templateId, START, END, null, null, null);
        service.setSessionFields(sheet.id(), List.of(new AttendanceFieldValueEntry(fieldId, "\"Greta\"")));
        var entry = attendanceRepo.findEntry(sheet.id(), member.id()).orElseThrow();
        attendanceRepo.updateEntryStatus(entry.id(), AttendanceEntry.AttendanceStatus.PRESENT);
    }

    @Test
    void deletingKeepsTheTemplateItsSheetsAndTheirEntries() {
        assertTrue(service.archiveTemplate(templateId));

        var template = service.findTemplateById(templateId).orElseThrow();
        assertEquals(1, service.findTemplateFields(templateId).size());
        assertEquals(
                List.of(group.id()),
                service.findTemplateGroups(templateId).stream()
                        .map(TemplateGroup::groupId)
                        .toList());
        var kept = service.findSessionById(sheet.id()).orElseThrow();
        assertEquals(templateId, kept.templateId());
        assertEquals(template.name(), kept.title());
        assertEquals(
                AttendanceEntry.AttendanceStatus.PRESENT,
                service.findEntries(sheet.id()).getFirst().status());
        assertEquals(
                "\"Greta\"", service.findSessionFields(sheet.id()).getFirst().value());
        assertEquals(List.of(group.id()), service.audienceOf(kept).groupIds());
    }

    @Test
    void theSheetsOfADeletedTemplateStillCountInTheListTheHoursAndTheStatistics() {
        service.archiveTemplate(templateId);

        assertTrue(service.findSessionSummaries(station.id()).stream().anyMatch(s -> s.id() == sheet.id()));
        var report = reportService.buildReport(
                station.id(), List.of(), List.of(group.id()), START.minus(1, ChronoUnit.DAYS), END, "exact");
        var line = report.sessions().stream()
                .filter(s -> s.sessionId() == sheet.id())
                .findFirst()
                .orElseThrow();
        assertEquals(3.0, line.entries().getFirst().hours());
        var months = new StatisticsRepository().stationStatistics(station.id()).attendanceByMonth();
        assertTrue(months.stream().mapToInt(month -> month.present()).sum() >= 1);
    }

    @Test
    void aDeletedTemplateIsNoLongerListed() {
        service.archiveTemplate(templateId);

        assertTrue(service.findTemplatesByStation(station.id()).stream().noneMatch(t -> t.id() == templateId));
    }

    /** The name of a deleted template is free again, as it was when a template was really deleted. */
    @Test
    void aDeletedTemplatesNameIsFreeAgain() {
        String name = service.findTemplateById(templateId).orElseThrow().name();
        service.archiveTemplate(templateId);

        var successor = service.createTemplate(station.id(), name);

        assertEquals(name, successor.name());
        assertTrue(service.findTemplatesByStation(station.id()).stream().anyMatch(t -> t.id() == successor.id()));
    }

    @Test
    void aDeletedTemplateTakesNoNewSheet() {
        service.archiveTemplate(templateId);

        var refused = assertThrows(
                RefusalResponse.class,
                () -> service.createSession(templateId, START.plus(1, ChronoUnit.DAYS), null, null, null, null));
        assertEquals("AT-058", refused.refusal().code());
    }

    @Test
    void aDeletedTemplateIsChosenForNoAppointment() {
        service.archiveTemplate(templateId);

        var refused = assertThrows(
                RefusalResponse.class,
                () -> eventCrud.create(
                        station.id(),
                        "Nachtwanderung",
                        null,
                        StationEvent.EventType.ONE_TIME,
                        null,
                        START,
                        END,
                        templateId,
                        false,
                        null,
                        false,
                        null,
                        null,
                        null,
                        null,
                        null));
        assertEquals("AT-058", refused.refusal().code());

        int eventTemplate = eventTemplates.create(station.id(), "Lagerabend").id();
        var refusedForTemplate = assertThrows(
                RefusalResponse.class,
                () -> eventTemplates.update(
                        eventTemplate, "Lagerabend", null, null, null, null, null, null, null, null, templateId, null));
        assertEquals("AT-058", refusedForTemplate.refusal().code());
    }

    /**
     * An appointment and an appointment template that took their sheets from the template let go of
     * it, as they did when a template was really deleted, so neither offers a template nobody can
     * choose any more.
     */
    @Test
    void appointmentsLetGoOfADeletedTemplate() {
        var event = eventCrud.create(
                station.id(),
                "Lagerfeuer",
                null,
                StationEvent.EventType.ONE_TIME,
                null,
                START,
                END,
                templateId,
                false,
                null,
                false,
                null,
                null,
                null,
                null,
                null);
        int eventTemplate = eventTemplates.create(station.id(), "Lagerfeuer").id();
        eventTemplates.update(
                eventTemplate, "Lagerfeuer", null, null, null, null, null, null, null, null, templateId, null);

        service.archiveTemplate(templateId);

        assertNull(eventRepo.findById(event.id()).orElseThrow().templateId());
        assertNull(eventTemplates.findById(eventTemplate).orElseThrow().attendanceTemplateId());
    }

    @Test
    void theDatabaseRefusesToDeleteATemplateThatHasSheets() {
        assertFalse(deletesQuietly(templateId));

        assertTrue(service.findTemplateById(templateId).isPresent());
        assertTrue(service.findSessionById(sheet.id()).isPresent());
        assertEquals(1, service.findEntries(sheet.id()).size());
    }

    /** A station that is deleted still takes its templates and their sheets with it. */
    @Test
    void aDeletedStationTakesItsSheetsWithIt() {
        var leaving = stationRepo.create("Template Archive Leaving Station");
        int template = service.createTemplate(leaving.id(), "Abschied").id();
        int leavingSheet =
                service.createSession(template, START, END, null, null, null).id();

        assertTrue(stationRepo.delete(leaving.id()));

        assertTrue(service.findSessionById(leavingSheet).isEmpty());
        assertTrue(service.findTemplateById(template).isEmpty());
    }

    /** Deletes the template row outright, the way only a mistake would, and says whether it went. */
    private static boolean deletesQuietly(int id) {
        try {
            return SqlSupport.deleteById("attendance_template", id);
        } catch (RuntimeException refused) {
            return false;
        }
    }
}
