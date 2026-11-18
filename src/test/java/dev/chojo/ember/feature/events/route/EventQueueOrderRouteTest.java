/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.route;

import dev.chojo.ember.api.RouteHarness;
import dev.chojo.ember.api.TestSessions;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.events.entity.QueueOrder;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.service.BatchEventService;
import dev.chojo.ember.feature.events.service.EventChangeNotifier;
import dev.chojo.ember.feature.events.service.EventChangePreviewService;
import dev.chojo.ember.feature.events.service.EventCopyService;
import dev.chojo.ember.feature.events.service.EventCrudService;
import dev.chojo.ember.feature.events.service.EventEditScopeService;
import dev.chojo.ember.feature.events.service.EventExportService;
import dev.chojo.ember.feature.events.service.EventFieldRegistrationService;
import dev.chojo.ember.feature.events.service.EventOccurrenceService;
import dev.chojo.ember.feature.events.service.EventReminderService;
import dev.chojo.ember.feature.events.service.EventRestrictionService;
import dev.chojo.ember.feature.events.service.EventTemplateService;
import dev.chojo.ember.feature.events.service.OccurrenceCalendar;
import dev.chojo.ember.feature.members.service.GuardianPolicy;
import io.javalin.testtools.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static dev.chojo.ember.api.RouteHarness.PREFIX;
import static dev.chojo.ember.api.RouteHarness.body;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** How the order of an appointment's waiting lists is set, and by whom. */
class EventQueueOrderRouteTest {
    private static final int STATION_ID = 3;
    private static final int OWN_ID = 40;
    private static final int FOREIGN_ID = 41;

    private EventCrudService crudService;
    private RouteHarness harness;

    @BeforeEach
    void serve() {
        crudService = mock(EventCrudService.class);
        var own = event(OWN_ID, STATION_ID);
        when(crudService.findById(OWN_ID)).thenReturn(Optional.of(own));
        var foreign = event(FOREIGN_ID, STATION_ID + 1);
        when(crudService.findById(FOREIGN_ID)).thenReturn(Optional.of(foreign));
        when(crudService.setQueueOrder(anyInt(), any())).thenReturn(Optional.of(own));
        var restrictions = mock(EventRestrictionService.class);
        var routes = new EventRoutes(
                crudService,
                mock(EventOccurrenceService.class),
                restrictions,
                mock(EventReminderService.class),
                mock(BatchEventService.class),
                mock(GuardianPolicy.class),
                mock(EventExportService.class),
                mock(EventTemplateService.class),
                mock(EventFieldRegistrationService.class),
                mock(OccurrenceCalendar.class),
                new EventVisibility(crudService, restrictions, mock(GuardianPolicy.class)),
                mock(EventEditScopeService.class),
                mock(EventChangeNotifier.class),
                mock(EventChangePreviewService.class),
                mock(EventCopyService.class));
        harness = RouteHarness.serving(routes);
    }

    private static StationEvent event(int id, int stationId) {
        var event = mock(StationEvent.class);
        when(event.id()).thenReturn(id);
        when(event.stationId()).thenReturn(stationId);
        return event;
    }

    private Response setOrder(int eventId, String json, StationPermission... permissions) {
        return harness.request(client -> client.put(
                PREFIX + "/events/" + eventId + "/queue-order",
                body(json),
                harness.as(TestSessions.member(STATION_ID, permissions))));
    }

    @Test
    void whoeverEditsAppointmentsSetsTheOrder() {
        var answer = setOrder(OWN_ID, "{\"queueOrder\": \"FAIRNESS\"}", StationPermission.EVENT_EDIT);

        assertEquals(200, answer.code());
        verify(crudService).setQueueOrder(OWN_ID, QueueOrder.FAIRNESS);
    }

    @Test
    void noOrderReadsAsByTime() {
        var answer = setOrder(OWN_ID, "{}", StationPermission.EVENT_EDIT);

        assertEquals(200, answer.code());
        verify(crudService).setQueueOrder(OWN_ID, QueueOrder.TIME);
    }

    @Test
    void anotherStationsAppointmentIsNotFound() {
        var answer = setOrder(FOREIGN_ID, "{\"queueOrder\": \"FAIRNESS\"}", StationPermission.EVENT_EDIT);

        assertEquals(404, answer.code());
        verify(crudService, never()).setQueueOrder(anyInt(), any());
    }

    @Test
    void aMemberWhoDoesNotEditAppointmentsIsRefused() {
        var answer = setOrder(OWN_ID, "{\"queueOrder\": \"FAIRNESS\"}", StationPermission.USER);

        assertEquals(403, answer.code());
        verify(crudService, never()).setQueueOrder(anyInt(), any());
    }
}
