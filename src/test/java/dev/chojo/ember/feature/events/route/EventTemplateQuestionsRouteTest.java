/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.route;

import dev.chojo.ember.api.RouteHarness;
import dev.chojo.ember.api.TestSessions;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.service.BatchEventService;
import dev.chojo.ember.feature.events.service.EventCrudService;
import dev.chojo.ember.feature.events.service.EventExportService;
import dev.chojo.ember.feature.events.service.EventFieldRegistrationService;
import dev.chojo.ember.feature.events.service.EventOccurrenceService;
import dev.chojo.ember.feature.events.service.EventRegistrationFieldService;
import dev.chojo.ember.feature.events.service.EventReminderService;
import dev.chojo.ember.feature.events.service.EventRestrictionService;
import dev.chojo.ember.feature.events.service.OccurrenceCalendar;
import dev.chojo.ember.feature.members.service.GuardianPolicy;
import org.junit.jupiter.api.Test;

import static dev.chojo.ember.api.RouteHarness.PREFIX;
import static dev.chojo.ember.api.RouteHarness.body;
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
 * and the attendance sheet it is taken on has nothing to do with which questions those are.
 */
class EventTemplateQuestionsRouteTest {
    private static final int STATION_ID = 3;
    private static final int EVENT_ID = 40;
    private static final int SHEET_ID = 5;
    private static final int APPOINTMENT_TEMPLATE_ID = 11;

    @Test
    void theQuestionsComeFromTheAppointmentTemplateAndNotFromTheSheet() {
        var crudService = mock(EventCrudService.class);
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
        var registrationFields = mock(EventRegistrationFieldService.class);
        var restrictions = mock(EventRestrictionService.class);
        var routes = new EventRoutes(
                crudService,
                mock(EventOccurrenceService.class),
                restrictions,
                mock(EventReminderService.class),
                mock(BatchEventService.class),
                mock(GuardianPolicy.class),
                mock(EventExportService.class),
                registrationFields,
                mock(EventFieldRegistrationService.class),
                mock(OccurrenceCalendar.class),
                new EventVisibility(crudService, restrictions, mock(GuardianPolicy.class)));
        var harness = RouteHarness.serving(routes);

        var answer = harness.request(client -> client.post(
                PREFIX + "/events",
                body("""
                        {"name": "Grundausbildung", "eventType": "ONE_TIME",
                         "startTime": "2027-03-01T17:00:00Z", "endTime": "2027-03-01T19:00:00Z",
                         "templateId": %d, "eventTemplateId": %d}""".formatted(SHEET_ID, APPOINTMENT_TEMPLATE_ID)),
                harness.as(TestSessions.member(STATION_ID, StationPermission.EVENT_EDIT))));

        assertEquals(201, answer.code());
        verify(registrationFields).copyTemplateFields(APPOINTMENT_TEMPLATE_ID, EVENT_ID);
        verify(registrationFields, never()).copyTemplateFields(eq(SHEET_ID), anyInt());
    }
}
