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
import dev.chojo.ember.feature.attendance.repository.AttendanceRepository;
import dev.chojo.ember.feature.events.entity.EventTemplate;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.repository.EventTemplateRepository;
import dev.chojo.ember.feature.events.service.BatchEventService;
import dev.chojo.ember.feature.events.service.EventCrudService;
import dev.chojo.ember.feature.events.service.EventExportService;
import dev.chojo.ember.feature.events.service.EventFieldRegistrationService;
import dev.chojo.ember.feature.events.service.EventOccurrenceService;
import dev.chojo.ember.feature.events.service.EventReminderService;
import dev.chojo.ember.feature.events.service.EventRestrictionService;
import dev.chojo.ember.feature.events.service.EventTemplateService;
import dev.chojo.ember.feature.events.service.OccurrenceCalendar;
import dev.chojo.ember.feature.members.service.GuardianPolicy;
import dev.chojo.ember.feature.question.MemberEligibility;
import io.javalin.testtools.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static dev.chojo.ember.api.RouteHarness.PREFIX;
import static dev.chojo.ember.api.RouteHarness.body;
import static dev.chojo.ember.api.RouteHarness.refusalOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * An appointment made from an appointment template takes that template's registration questions,
 * the attendance sheet it is taken on has nothing to do with which questions those are, and only a
 * template of the station making the appointment hands any questions over.
 */
class EventTemplateQuestionsRouteTest {
    private static final int STATION_ID = 3;
    private static final int OTHER_STATION_ID = 4;
    private static final int EVENT_ID = 40;
    private static final int SHEET_ID = 5;
    private static final int APPOINTMENT_TEMPLATE_ID = 11;

    private EventCrudService crudService;
    private EventTemplateRepository templateRepository;
    private RouteHarness harness;

    @BeforeEach
    void serve() {
        crudService = mock(EventCrudService.class);
        var created = mock(StationEvent.class);
        when(created.id()).thenReturn(EVENT_ID);
        when(crudService.createWithoutEvent(
                        eq(STATION_ID),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        anyBoolean(),
                        any(),
                        anyBoolean(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any()))
                .thenReturn(created);
        templateRepository = mock(EventTemplateRepository.class);
        var templates = new EventTemplateService(
                templateRepository, mock(AttendanceRepository.class), mock(MemberEligibility.class));
        var restrictions = mock(EventRestrictionService.class);
        var routes = new EventRoutes(
                crudService,
                mock(EventOccurrenceService.class),
                restrictions,
                mock(EventReminderService.class),
                mock(BatchEventService.class),
                mock(GuardianPolicy.class),
                mock(EventExportService.class),
                templates,
                mock(EventFieldRegistrationService.class),
                mock(OccurrenceCalendar.class),
                new EventVisibility(crudService, restrictions, mock(GuardianPolicy.class)));
        harness = RouteHarness.serving(routes);
    }

    private void templateOf(int stationId) {
        var template = mock(EventTemplate.class);
        when(template.id()).thenReturn(APPOINTMENT_TEMPLATE_ID);
        when(template.stationId()).thenReturn(stationId);
        when(templateRepository.findById(APPOINTMENT_TEMPLATE_ID)).thenReturn(Optional.of(template));
    }

    private Response create() {
        return harness.request(client -> client.post(
                PREFIX + "/events",
                body("""
                        {"name": "Grundausbildung", "eventType": "ONE_TIME",
                         "startTime": "2027-03-01T17:00:00Z", "endTime": "2027-03-01T19:00:00Z",
                         "templateId": %d, "eventTemplateId": %d}""".formatted(SHEET_ID, APPOINTMENT_TEMPLATE_ID)),
                harness.as(TestSessions.member(STATION_ID, StationPermission.EVENT_EDIT))));
    }

    @Test
    void theQuestionsComeFromTheAppointmentTemplateAndNotFromTheSheet() {
        templateOf(STATION_ID);

        var answer = create();

        assertEquals(201, answer.code());
        verify(templateRepository).copyRegistrationFields(APPOINTMENT_TEMPLATE_ID, EVENT_ID);
        verify(templateRepository, never()).copyRegistrationFields(eq(SHEET_ID), anyInt());
    }

    @Test
    void anotherStationsTemplateHandsNothingOverAndMakesNoAppointment() {
        templateOf(OTHER_STATION_ID);

        var answer = create();

        assertEquals(Refusal.EVENT_TEMPLATE_TO_APPLY_NOT_HERE, refusalOf(answer));
        verify(templateRepository, never()).copyRegistrationFields(anyInt(), anyInt());
        verify(crudService, never())
                .createWithoutEvent(
                        anyInt(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        anyBoolean(),
                        any(),
                        anyBoolean(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any());
    }

    @Test
    void aTemplateThatIsGoneReadsTheSameAsAnotherStations() {
        when(templateRepository.findById(APPOINTMENT_TEMPLATE_ID)).thenReturn(Optional.empty());

        assertEquals(Refusal.EVENT_TEMPLATE_TO_APPLY_NOT_HERE, refusalOf(create()));
    }
}
