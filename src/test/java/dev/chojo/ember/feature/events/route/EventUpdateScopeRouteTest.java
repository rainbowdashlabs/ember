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
import dev.chojo.ember.feature.events.entity.ChangeNotice;
import dev.chojo.ember.feature.events.entity.ChangePreview;
import dev.chojo.ember.feature.events.entity.EditScope;
import dev.chojo.ember.feature.events.entity.EditScopeKind;
import dev.chojo.ember.feature.events.entity.EventChangePart;
import dev.chojo.ember.feature.events.entity.EventDraft;
import dev.chojo.ember.feature.events.entity.EventLink;
import dev.chojo.ember.feature.events.entity.EventLinks;
import dev.chojo.ember.feature.events.entity.StationEvent;
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

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static dev.chojo.ember.api.RouteHarness.PREFIX;
import static dev.chojo.ember.api.RouteHarness.body;
import static dev.chojo.ember.api.RouteHarness.json;
import static dev.chojo.ember.api.RouteHarness.refusalOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Saving an appointment names which of its dates the change applies to, and whatever the editor sent
 * besides lands on the appointment the change was saved into.
 */
class EventUpdateScopeRouteTest {
    private static final int STATION_ID = 3;
    private static final int SERIES_ID = 40;
    private static final int NEW_ID = 41;

    private EventEditScopeService scopeService;
    private EventRestrictionService restrictions;
    private EventFieldRegistrationService fieldRegistrations;
    private EventChangeNotifier changeNotifier;
    private EventChangePreviewService previewService;
    private StationEvent series;
    private RouteHarness harness;

    private EventCrudService crudService;

    private EventCrudService crudServiceOf() {
        return crudService;
    }

    @BeforeEach
    void serve() {
        var crudService = mock(EventCrudService.class);
        this.crudService = crudService;
        var series = event(SERIES_ID);
        var created = event(NEW_ID);
        when(crudService.findById(SERIES_ID)).thenReturn(Optional.of(series));
        when(crudService.findById(NEW_ID)).thenReturn(Optional.of(created));
        scopeService = mock(EventEditScopeService.class);
        when(scopeService.save(eq(series), any(), any())).thenReturn(created);
        restrictions = mock(EventRestrictionService.class);
        fieldRegistrations = mock(EventFieldRegistrationService.class);
        changeNotifier = mock(EventChangeNotifier.class);
        previewService = mock(EventChangePreviewService.class);
        this.series = series;
        var routes = new EventRoutes(
                crudService,
                mock(EventOccurrenceService.class),
                restrictions,
                mock(EventReminderService.class),
                mock(BatchEventService.class),
                mock(GuardianPolicy.class),
                mock(EventExportService.class),
                mock(EventTemplateService.class),
                fieldRegistrations,
                mock(OccurrenceCalendar.class),
                new EventVisibility(crudService, restrictions, mock(GuardianPolicy.class)),
                scopeService,
                changeNotifier,
                previewService);
        harness = RouteHarness.serving(routes);
    }

    private static StationEvent event(int id) {
        var event = mock(StationEvent.class);
        when(event.id()).thenReturn(id);
        when(event.stationId()).thenReturn(STATION_ID);
        return event;
    }

    private Response update(String scope) {
        return harness.request(client -> client.put(
                PREFIX + "/events/" + SERIES_ID,
                body("""
                        {"name": "Dienst", "eventType": "RECURRING", "dayOfWeek": 2,
                         "startTime": "2027-03-02T17:00:00Z", "endTime": "2027-03-02T19:00:00Z",
                         "repeatCount": 8 %s}""".formatted(scope)),
                harness.as(TestSessions.member(STATION_ID, StationPermission.EVENT_EDIT))));
    }

    @Test
    void aScopedChangeIsSavedIntoTheNewAppointmentAndAnswersWithIt() {
        var answer = update(", \"scope\": {\"kind\": \"THIS_AND_LATER\", \"date\": \"2027-04-06\"}");

        assertEquals(200, answer.code());
        var draft = ArgumentCaptor.forClass(EventDraft.class);
        verify(scopeService)
                .save(
                        any(),
                        draft.capture(),
                        eq(new EditScope(EditScopeKind.THIS_AND_LATER, LocalDate.of(2027, 4, 6))));
        assertEquals("Dienst", draft.getValue().name());
        assertEquals(8, draft.getValue().repeatCount());
        verify(restrictions).setRestrictions(eq(NEW_ID), any());
        verify(fieldRegistrations).reconcile(NEW_ID);
        assertEquals(NEW_ID, json(answer).get("id").asInt());
    }

    @Test
    void aChangeWithoutAScopeAppliesToEveryDate() {
        var answer = update("");

        assertEquals(200, answer.code());
        verify(scopeService).save(any(), any(), eq(EditScope.ALL));
    }

    @Test
    void aScopeWithoutItsKindIsRefused() {
        when(scopeService.save(any(), any(), eq(new EditScope(null, LocalDate.of(2027, 4, 6)))))
                .thenThrow(EventRefusal.EVENT_SCOPE_NEEDS_A_KIND.raise());

        var answer = update(", \"scope\": {\"date\": \"2027-04-06\"}");

        assertEquals(EventRefusal.EVENT_SCOPE_NEEDS_A_KIND, refusalOf(answer));
    }

    @Test
    void aChangeToEveryDateTellsAfterEverythingIsSaved() {
        when(scopeService.save(eq(series), any(), eq(EditScope.ALL))).thenReturn(series);

        var answer =
                update(", \"changeNotice\": {\"tell\": true, \"changedParts\": [\"DESCRIPTION\"], \"note\": \"Helm\"}");

        assertEquals(200, answer.code());
        var notice = new ChangeNotice(true, List.of(EventChangePart.DESCRIPTION), "Helm");
        verify(changeNotifier).requireValid(notice);
        verify(changeNotifier).tellChanged(series, series, notice);
    }

    @Test
    void aChangeSavedIntoANewAppointmentTellsNothingYet() {
        update(", \"scope\": {\"kind\": \"THIS\", \"date\": \"2027-04-06\"}, \"changeNotice\": {\"tell\": true}");

        verify(changeNotifier, never()).tellChanged(any(), any(), any());
    }

    @Test
    void theChangeNoticeOfANewAppointmentTellsWhatTheEditorChose() {
        var answer = harness.request(client -> client.post(
                PREFIX + "/events/" + NEW_ID + "/change-notice",
                body("""
                        {"tell": true, "changedParts": ["FIELDS"], "note": null}"""),
                harness.as(TestSessions.member(STATION_ID, StationPermission.EVENT_EDIT))));

        assertEquals(204, answer.code());
        verify(changeNotifier).tellContinued(any(), eq(new ChangeNotice(true, List.of(EventChangePart.FIELDS), null)));
    }

    @Test
    void thePreviewCountsWhatTheDraftWouldTell() {
        when(previewService.preview(eq(series), any(), eq(new EditScope(EditScopeKind.THIS, LocalDate.of(2027, 4, 6)))))
                .thenReturn(new ChangePreview(4, true, "19:00", 2, 5));

        var answer = harness.request(client -> client.post(
                PREFIX + "/events/" + SERIES_ID + "/change-notice/preview",
                body("""
                        {"name": "Dienst", "eventType": "RECURRING", "dayOfWeek": 2,
                         "startTime": "2027-03-02T18:00:00Z", "endTime": "2027-03-02T20:00:00Z",
                         "scope": {"kind": "THIS", "date": "2027-04-06"}}"""),
                harness.as(TestSessions.member(STATION_ID, StationPermission.EVENT_EDIT))));

        assertEquals(200, answer.code());
        var counts = json(answer);
        assertEquals(4, counts.get("noticeAudience").asInt());
        assertEquals("19:00", counts.get("newStart").asString());
        verify(scopeService, never()).save(any(), any(), any());
    }

    @Test
    void theLinksOfAnAppointmentAreRead() {
        when(scopeService.linksOf(SERIES_ID))
                .thenReturn(new EventLinks(
                        null, List.of(new EventLink(NEW_ID, "Dienst neu", LocalDate.of(2027, 4, 6), false))));

        var answer = harness.request(client -> client.get(
                PREFIX + "/events/" + SERIES_ID + "/links",
                harness.as(TestSessions.member(STATION_ID, StationPermission.USER, StationPermission.EVENT_EDIT))));

        assertEquals(200, answer.code());
        var links = json(answer);
        assertEquals(NEW_ID, links.get("continuations").get(0).get("eventId").asInt());
        assertEquals(false, links.get("continuations").get(0).get("series").asBoolean());
    }

    @Test
    void aPartOfACourseTakesItsAudienceFromTheCourse() {
        when(crudServiceOf().isCoursePart(SERIES_ID)).thenReturn(true);

        var response = harness.request(client -> client.put(
                PREFIX + "/events/" + SERIES_ID + "/restrictions",
                body("{}"),
                harness.as(TestSessions.member(STATION_ID, StationPermission.EVENT_EDIT))));

        assertEquals(EventRefusal.COURSE_PART_AUDIENCE_FROM_COURSE, refusalOf(response));
    }

    @Test
    void savingAPartOfACourseLeavesTheAudienceTheCourseGaveIt() {
        when(crudServiceOf().isCoursePart(NEW_ID)).thenReturn(true);

        assertEquals(200, update("").code());

        verify(restrictions, never()).setRestrictions(eq(NEW_ID), any());
        verify(restrictions, never()).setViewRestrictions(eq(NEW_ID), any());
    }
}
