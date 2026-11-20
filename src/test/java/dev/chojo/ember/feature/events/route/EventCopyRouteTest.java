/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.route;

import dev.chojo.ember.api.RouteHarness;
import dev.chojo.ember.api.TestSessions;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.refusal.EventRefusal;
import dev.chojo.ember.feature.events.entity.RegistrationDeadline;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.repository.EventCopyRepository;
import dev.chojo.ember.feature.events.repository.EventRepository;
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
import static dev.chojo.ember.api.RouteHarness.refusalOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * An appointment copied from another hands over what the editor cannot hold only where the original
 * is one of the station's own, and a copy of anything else is refused before an appointment is made.
 */
class EventCopyRouteTest {
    private static final int STATION_ID = 3;
    private static final int OTHER_STATION_ID = 4;
    private static final int COPY_ID = 40;
    private static final int SOURCE_ID = 12;

    private EventCrudService crudService;
    private EventRepository eventRepository;
    private EventCopyService copyService;
    private RouteHarness harness;

    @BeforeEach
    void serve() {
        crudService = mock(EventCrudService.class);
        var created = mock(StationEvent.class);
        when(created.id()).thenReturn(COPY_ID);
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
                        any()))
                .thenReturn(created);
        eventRepository = mock(EventRepository.class);
        copyService = spy(new EventCopyService(eventRepository, mock(EventCopyRepository.class)));
        doNothing().when(copyService).copyInto(any(), anyInt());
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
                copyService);
        harness = RouteHarness.serving(routes);
    }

    private StationEvent sourceOf(int stationId) {
        var source = mock(StationEvent.class);
        when(source.id()).thenReturn(SOURCE_ID);
        when(source.stationId()).thenReturn(stationId);
        when(eventRepository.findById(SOURCE_ID)).thenReturn(Optional.of(source));
        return source;
    }

    private Response copy() {
        return harness.request(client -> client.post(
                PREFIX + "/events",
                body("""
                        {"name": "Dienstabend", "eventType": "ONE_TIME",
                         "startTime": "2027-03-01T17:00:00Z", "endTime": "2027-03-01T19:00:00Z",
                         "copiedFromEventId": %d}""".formatted(SOURCE_ID)),
                harness.as(TestSessions.member(STATION_ID, StationPermission.EVENT_EDIT))));
    }

    private void verifyNothingWasMade() {
        verify(copyService, never()).copyInto(any(), anyInt());
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
                        any());
    }

    @Test
    void aCopyOfTheStationsOwnAppointmentTakesOverWhatTheEditorCannotHold() {
        var source = sourceOf(STATION_ID);

        var answer = copy();

        assertEquals(201, answer.code());
        verify(copyService).copyInto(source, COPY_ID);
    }

    @Test
    void aCopyOfAnotherStationsAppointmentIsRefusedAndMakesNothing() {
        sourceOf(OTHER_STATION_ID);

        assertEquals(EventRefusal.EVENT_TO_COPY_NOT_HERE, refusalOf(copy()));
        verifyNothingWasMade();
    }

    @Test
    void aCopyOfAnAppointmentThatIsGoneReadsTheSameAsAnotherStations() {
        when(eventRepository.findById(SOURCE_ID)).thenReturn(Optional.empty());

        assertEquals(EventRefusal.EVENT_TO_COPY_NOT_HERE, refusalOf(copy()));
        verifyNothingWasMade();
    }

    /**
     * A series whose last day lies before its start is refused before anything is written, so neither
     * an appointment nor the copied parts are left behind.
     */
    @Test
    void aSeriesEndingBeforeItStartsIsRefusedBeforeAnythingIsWritten() {
        sourceOf(STATION_ID);

        var answer = harness.request(client -> client.post(
                PREFIX + "/events",
                body("""
                        {"name": "Dienstabend", "eventType": "RECURRING", "dayOfWeek": 1,
                         "startTime": "2027-03-01T17:00:00Z", "endTime": "2027-03-01T19:00:00Z",
                         "repeatUntil": "2026-12-31", "copiedFromEventId": %d}""".formatted(SOURCE_ID)),
                harness.as(TestSessions.member(STATION_ID, StationPermission.EVENT_EDIT))));

        assertEquals(EventRefusal.EVENT_SERIES_ENDS_BEFORE_IT_STARTS, refusalOf(answer));
        verifyNothingWasMade();
    }

    /** An appointment sent without a sign-up deadline, as an older screen sends it, is made without one. */
    @Test
    void anAppointmentWithoutADeadlineInTheBodyIsMadeWithoutOne() {
        var answer = harness.request(client -> client.post(
                PREFIX + "/events",
                body("""
                        {"name": "Dienstabend", "eventType": "ONE_TIME", "requiresRegistration": true,
                         "startTime": "2027-03-01T17:00:00Z", "endTime": "2027-03-01T19:00:00Z"}"""),
                harness.as(TestSessions.member(STATION_ID, StationPermission.EVENT_EDIT))));

        assertEquals(201, answer.code(), answer.body().string());
        verify(crudService)
                .createWithoutEvent(
                        eq(STATION_ID),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        anyBoolean(),
                        eq(RegistrationDeadline.none()),
                        anyBoolean(),
                        any(),
                        any(),
                        any(),
                        any());
    }

    @Test
    void anAppointmentMadeWithoutAnOriginalCopiesNothing() {
        var answer = harness.request(client -> client.post(
                PREFIX + "/events",
                body("""
                        {"name": "Dienstabend", "eventType": "ONE_TIME",
                         "startTime": "2027-03-01T17:00:00Z", "endTime": "2027-03-01T19:00:00Z"}"""),
                harness.as(TestSessions.member(STATION_ID, StationPermission.EVENT_EDIT))));

        assertEquals(201, answer.code());
        verify(copyService, never()).copyInto(any(), anyInt());
    }
}
