/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.route;

import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.api.RouteHarness;
import dev.chojo.ember.api.TestSessions;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.attendance.service.AttendanceService;
import dev.chojo.ember.feature.content.entity.BlockAudience;
import dev.chojo.ember.feature.events.entity.EventFieldDraft;
import dev.chojo.ember.feature.events.entity.EventQuestionSettings;
import dev.chojo.ember.feature.events.entity.EventTemplate;
import dev.chojo.ember.feature.events.entity.PickerMode;
import dev.chojo.ember.feature.events.entity.RegistrationFieldDraft;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.service.BatchEventService;
import dev.chojo.ember.feature.events.service.EventBreakService;
import dev.chojo.ember.feature.events.service.EventCategoryService;
import dev.chojo.ember.feature.events.service.EventCrudService;
import dev.chojo.ember.feature.events.service.EventExportService;
import dev.chojo.ember.feature.events.service.EventFieldDefaultService;
import dev.chojo.ember.feature.events.service.EventFieldRegistrationService;
import dev.chojo.ember.feature.events.service.EventFieldService;
import dev.chojo.ember.feature.events.service.EventMemberTableService;
import dev.chojo.ember.feature.events.service.EventOccurrenceService;
import dev.chojo.ember.feature.events.service.EventRegistrationFieldService;
import dev.chojo.ember.feature.events.service.EventRegistrationService;
import dev.chojo.ember.feature.events.service.EventReminderService;
import dev.chojo.ember.feature.events.service.EventRestrictionService;
import dev.chojo.ember.feature.events.service.EventTemplateRestrictionService;
import dev.chojo.ember.feature.events.service.EventTemplateService;
import dev.chojo.ember.feature.events.service.OccurrenceCalendar;
import dev.chojo.ember.feature.events.service.RegistrationAnswerReminder;
import dev.chojo.ember.feature.events.service.RegistrationRowLookups;
import dev.chojo.ember.feature.members.service.GuardianPolicy;
import dev.chojo.ember.feature.members.service.MemberNameResolver;
import dev.chojo.ember.feature.members.service.MemberTableRenderer;
import dev.chojo.ember.feature.members.service.MemberTableService;
import dev.chojo.ember.feature.question.FieldType;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.service.StationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static dev.chojo.ember.api.RouteHarness.PREFIX;
import static dev.chojo.ember.api.RouteHarness.body;
import static dev.chojo.ember.api.RouteHarness.refusalOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The event routes read their stations, the picker mode and the questions they write through
 * services and entity types, and an appointment of another station or none at all is not here.
 */
class EventRouteLookupsTest {
    private static final int STATION_ID = 3;
    private static final UUID STATION_UID = UUID.fromString("00000000-0000-0000-0000-000000000003");

    private EventCrudService crudService;
    private StationService stationService;
    private EventVisibility visibility;

    private static StationEvent eventOf(int stationId) {
        var event = mock(StationEvent.class);
        when(event.stationId()).thenReturn(stationId);
        when(event.name()).thenReturn("Übung");
        return event;
    }

    @BeforeEach
    void setup() {
        crudService = mock(EventCrudService.class);
        stationService = mock(StationService.class);
        visibility = new EventVisibility(crudService, mock(EventRestrictionService.class), mock(GuardianPolicy.class));
        var own = eventOf(STATION_ID);
        var foreign = eventOf(4);
        when(crudService.findById(9)).thenReturn(Optional.of(own));
        when(crudService.findById(8)).thenReturn(Optional.of(foreign));
        when(crudService.findById(7)).thenReturn(Optional.empty());
    }

    @Test
    void thePickerReadsItsModeAndHoldsItsLimit() {
        var routes = new EventRoutes(
                crudService,
                mock(EventOccurrenceService.class),
                mock(EventRestrictionService.class),
                mock(EventReminderService.class),
                mock(BatchEventService.class),
                mock(GuardianPolicy.class),
                mock(EventExportService.class),
                mock(EventTemplateService.class),
                mock(EventFieldRegistrationService.class),
                mock(OccurrenceCalendar.class),
                visibility);
        var harness = RouteHarness.serving(routes);

        harness.run((server, client) -> {
            var editor = harness.as(TestSessions.member(STATION_ID, StationPermission.PAGE_EDIT));
            assertEquals(
                    200,
                    client.get(PREFIX + "/events/search?q=x&mode=past&limit=90", editor)
                            .code());
            assertEquals(
                    200,
                    client.get(PREFIX + "/events/search?mode=sometime", editor).code());
        });

        verify(crudService).searchEventPicker(STATION_ID, BlockAudience.PUBLIC, "x", PickerMode.PAST, 20);
        verify(crudService).searchEventPicker(STATION_ID, BlockAudience.PUBLIC, null, PickerMode.FUTURE, 10);
    }

    @Test
    void theQuestionsOfAnAppointmentAreWrittenOnlyForTheStationsOwn() {
        var fields = mock(EventFieldService.class);
        var routes = new EventStructureRoutes(
                crudService,
                mock(EventCategoryService.class),
                mock(EventBreakService.class),
                mock(EventFieldDefaultService.class),
                fields,
                mock(OccurrenceCalendar.class),
                visibility);
        var harness = RouteHarness.serving(routes);
        var request = body("""
                {"fields": [{"id": null, "name": "Größe", "fieldType": "STRING", "config": null, "value": null,
                             "overview": null, "attendanceFieldId": null, "isPublic": true}]}""");

        harness.run((server, client) -> {
            var editor = harness.as(TestSessions.member(STATION_ID, StationPermission.EVENT_EDIT));
            assertEquals(
                    200,
                    client.put(PREFIX + "/events/9/fields", request, editor).code());
            assertEquals(Refusal.EVENT_NOT_HERE, refusalOf(client.put(PREFIX + "/events/7/fields", request, editor)));
            assertEquals(
                    Refusal.NOT_YOURS_TO_OPEN, refusalOf(client.put(PREFIX + "/events/8/fields", request, editor)));
        });

        verify(fields)
                .replaceFields(
                        9,
                        List.of(new EventFieldDraft(
                                null, "Größe", FieldType.TEXT, EventQuestionSettings.empty(), "", false, null, true)));
        verify(fields, never()).replaceFields(eq(7), any());
        verify(fields, never()).replaceFields(eq(8), any());
    }

    @Test
    void theRegistrationQuestionsOfATemplateAndAnAppointmentAreWrittenAsDrafts() {
        var templates = mock(EventTemplateService.class);
        var template = mock(EventTemplate.class);
        when(template.stationId()).thenReturn(STATION_ID);
        when(templates.findById(5)).thenReturn(Optional.of(template));
        var reminder = mock(RegistrationAnswerReminder.class);
        var harness = RouteHarness.serving(
                new EventTemplateRoutes(templates, mock(EventTemplateRestrictionService.class)),
                registrationRoutes(reminder));
        var request = body("""
                {"fields": [{"name": "Shirt", "fieldType": "STRING", "config": null, "overview": true}]}""");
        var draft = new RegistrationFieldDraft("Shirt", FieldType.TEXT, EventQuestionSettings.empty(), true);

        harness.run((server, client) -> {
            var manager = harness.as(TestSessions.member(
                    STATION_ID, StationPermission.EVENT_MANAGE_TEMPLATE, StationPermission.EVENT_EDIT));
            assertEquals(
                    200,
                    client.put(PREFIX + "/event-templates/5/registration-fields", request, manager)
                            .code());
            assertEquals(
                    200,
                    client.put(PREFIX + "/events/9/registration-fields", request, manager)
                            .code());
        });

        verify(templates).replaceRegistrationFields(5, List.of(draft));
        verify(reminder).replaceQuestions(9, List.of(draft));
    }

    @Test
    void aRegistrationTableOfAStationThatIsGoneIsRefusedByName() {
        when(stationService.findById(STATION_ID)).thenReturn(Optional.empty());
        var harness = RouteHarness.serving(registrationRoutes(mock(RegistrationAnswerReminder.class)));

        var answer = harness.request(client -> client.post(
                PREFIX + "/events/9/registration-table/export.csv",
                body("{\"date\": \"2026-09-02\", \"columns\": []}"),
                harness.as(TestSessions.member(STATION_ID, StationPermission.EVENT_REGISTRATION))));

        assertEquals(Refusal.STATION_NOT_HERE_FOR_REGISTRATION_CSV, refusalOf(answer));
    }

    @Test
    void thePublicCalendarNeedsAStationThatPublishesOne() {
        var closed = mock(Station.class);
        when(stationService.findByUid(STATION_UID)).thenReturn(Optional.of(closed));
        var harness = RouteHarness.serving(new PublicEventRoutes(
                crudService,
                mock(EventCategoryService.class),
                mock(EventFieldService.class),
                mock(OccurrenceCalendar.class),
                stationService));

        harness.run((server, client) -> {
            assertEquals(
                    Refusal.PUBLIC_CALENDAR_SWITCHED_OFF,
                    refusalOf(client.get(PREFIX + "/public/events/" + STATION_UID)));
            assertEquals(
                    Refusal.STATION_NOT_HERE_BEHIND_PUBLIC_CALENDAR,
                    refusalOf(client.get(PREFIX + "/public/events/" + UUID.randomUUID())));
        });
    }

    private EventRegistrationRoutes registrationRoutes(RegistrationAnswerReminder reminder) {
        return new EventRegistrationRoutes(
                crudService,
                mock(EventRegistrationService.class),
                mock(EventRestrictionService.class),
                mock(MemberNameResolver.class),
                mock(GuardianPolicy.class),
                mock(RegistrationRowLookups.Reader.class),
                mock(AttendanceService.class),
                mock(EventRegistrationFieldService.class),
                reminder,
                mock(EventMemberTableService.class),
                mock(MemberTableService.class),
                mock(MemberTableRenderer.class),
                stationService,
                mock(OccurrenceCalendar.class),
                visibility);
    }
}
