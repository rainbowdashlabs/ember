/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.route;

import dev.chojo.ember.api.RouteHarness;
import dev.chojo.ember.api.TestSessions;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.refusal.BodyRefusal;
import dev.chojo.ember.feature.events.entity.BatchRequest;
import dev.chojo.ember.feature.events.service.BatchEventService;
import dev.chojo.ember.feature.events.service.EventChangeNotifier;
import dev.chojo.ember.feature.events.service.EventChangePreviewService;
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
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;

import static dev.chojo.ember.api.RouteHarness.PREFIX;
import static dev.chojo.ember.api.RouteHarness.body;
import static dev.chojo.ember.api.RouteHarness.refusalOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A batch of appointments is read from the very body the batch screen sends, and a registration
 * deadline written as a local date and time without a zone is refused as a body of the wrong shape.
 *
 * <p>The screen used to send the deadline as it stood in its date and time field, so every batch
 * with a deadline was refused as a whole and the reader was left with no field to correct.
 */
class EventBatchCreateRouteTest {
    private static final int STATION_ID = 3;

    private static final String ROWS = """
            [{"name": "Adventszauber Freitag 1", "startTime": "2026-12-04T15:30:00.000Z",
              "endTime": "2026-12-04T18:00:00.000Z", "fieldValues": {}},
             {"name": "Adventszauber Freitag 2", "startTime": "2026-12-04T18:00:00.000Z",
              "endTime": "2026-12-04T20:30:00.000Z", "fieldValues": {}},
             {"name": "Adventszauber Samstag 1", "startTime": "2026-12-05T15:30:00.000Z",
              "endTime": "2026-12-05T18:00:00.000Z", "fieldValues": {}},
             {"name": null, "startTime": "2026-12-05T18:00:00.000Z",
              "endTime": "2026-12-05T20:30:00.000Z", "fieldValues": {}}]""";

    private static final String RESTRICTION = """
            {"userTypes": [], "groupIds": [], "tagIds": [], "memberIds": [], "mode": "AND"}""";

    private BatchEventService batches;
    private RouteHarness harness;

    @BeforeEach
    void serve() {
        batches = mock(BatchEventService.class);
        when(batches.createBatch(anyInt(), any())).thenReturn(List.of());
        var crudService = mock(EventCrudService.class);
        var restrictions = mock(EventRestrictionService.class);
        harness = RouteHarness.serving(new EventRoutes(
                crudService,
                mock(EventOccurrenceService.class),
                restrictions,
                mock(EventReminderService.class),
                batches,
                mock(GuardianPolicy.class),
                mock(EventExportService.class),
                mock(EventTemplateService.class),
                mock(EventFieldRegistrationService.class),
                mock(OccurrenceCalendar.class),
                new EventVisibility(crudService, restrictions, mock(GuardianPolicy.class)),
                mock(EventEditScopeService.class),
                mock(EventChangeNotifier.class),
                mock(EventChangePreviewService.class)));
    }

    private Response send(String deadline) {
        return harness.request(client -> client.post(
                PREFIX + "/events/batch",
                body("""
                        {"name": "Adventszauber", "inlineFields": [], "rows": %s,
                         "requiresRegistration": true, "requiresConfirmation": false,
                         "registrationDeadline": %s,
                         "restriction": %s, "viewRestriction": %s}""".formatted(ROWS, deadline, RESTRICTION, RESTRICTION)),
                harness.as(TestSessions.member(STATION_ID, StationPermission.EVENT_EDIT))));
    }

    @Test
    void theBatchTheScreenSendsIsReadWithItsDeadline() {
        var answer = send("\"2026-12-01T17:00:00.000Z\"");

        assertEquals(200, answer.code());
        var request = ArgumentCaptor.forClass(BatchRequest.class);
        verify(batches).createBatch(eq(STATION_ID), request.capture());
        assertEquals(4, request.getValue().rows().size());
        assertEquals(
                Instant.parse("2026-12-04T15:30:00Z"),
                request.getValue().rows().get(0).startTime());
        assertEquals(Instant.parse("2026-12-01T17:00:00Z"), request.getValue().registrationDeadline());
    }

    @Test
    void aDeadlineWithoutAZoneIsRefusedAsABodyOfTheWrongShape() {
        var answer = send("\"2026-12-01T18:00\"");

        assertEquals(BodyRefusal.BODY_DOES_NOT_MATCH, refusalOf(answer));
        verify(batches, never()).createBatch(anyInt(), any());
    }
}
