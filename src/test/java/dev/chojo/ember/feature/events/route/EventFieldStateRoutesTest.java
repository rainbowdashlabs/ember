/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.route;

import dev.chojo.ember.api.RouteHarness;
import dev.chojo.ember.api.TestSessions;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.events.entity.AppointmentField;
import dev.chojo.ember.feature.events.entity.EventQuestionSettings;
import dev.chojo.ember.feature.events.entity.SelfRegistration;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.service.EventBreakService;
import dev.chojo.ember.feature.events.service.EventCategoryService;
import dev.chojo.ember.feature.events.service.EventCrudService;
import dev.chojo.ember.feature.events.service.EventFieldDefaultService;
import dev.chojo.ember.feature.events.service.EventFieldService;
import dev.chojo.ember.feature.events.service.EventRestrictionService;
import dev.chojo.ember.feature.events.service.OccurrenceCalendar;
import dev.chojo.ember.feature.members.service.GuardianPolicy;
import dev.chojo.ember.feature.question.FieldType;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static dev.chojo.ember.api.RouteHarness.PREFIX;
import static dev.chojo.ember.api.RouteHarness.json;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Every answer that reaches the self-registration button carries what the reader asking may do, and
 * whoever manages appointments is asked about as the one running it.
 */
class EventFieldStateRoutesTest {
    private static final int STATION_ID = 3;
    private static final int EVENT_ID = 9;
    private static final int FIELD_ID = 4;

    private EventFieldService fields;
    private RouteHarness harness;

    private static AppointmentField question(@Nullable SelfRegistration state) {
        return new AppointmentField(
                FIELD_ID,
                EVENT_ID,
                "Fahrer",
                FieldType.MEMBER_LIST,
                EventQuestionSettings.empty(),
                "",
                0,
                false,
                null,
                false,
                state);
    }

    @BeforeEach
    void setup() {
        var crudService = mock(EventCrudService.class);
        var event = mock(StationEvent.class);
        when(event.stationId()).thenReturn(STATION_ID);
        when(crudService.findById(EVENT_ID)).thenReturn(Optional.of(event));
        fields = mock(EventFieldService.class);
        var restrictions = mock(EventRestrictionService.class);
        when(restrictions.canViewAny(anyInt(), any(), any())).thenReturn(true);
        var visibility = new EventVisibility(crudService, restrictions, mock(GuardianPolicy.class));
        harness = RouteHarness.serving(new EventStructureRoutes(
                crudService,
                mock(EventCategoryService.class),
                mock(EventBreakService.class),
                mock(EventFieldDefaultService.class),
                fields,
                mock(OccurrenceCalendar.class),
                visibility));
    }

    @Test
    void theListIsMeasuredAgainstTheMemberAsking() {
        when(fields.findByEventFor(eq(EVENT_ID), isNull(), eq(TestSessions.MEMBER_ID), eq(false)))
                .thenReturn(List.of(question(SelfRegistration.NOT_ELIGIBLE)));

        harness.run((server, client) -> {
            var member = harness.as(TestSessions.member(STATION_ID, StationPermission.USER));
            var answer = json(client.get(PREFIX + "/events/" + EVENT_ID + "/fields", member));
            assertEquals(
                    "NOT_ELIGIBLE", answer.get(0).get("selfRegistrationState").asText());
        });
    }

    @Test
    void aManagerIsAskedAboutAsTheOneRunningIt() {
        when(fields.findByEventFor(anyInt(), any(), anyInt(), eq(true)))
                .thenReturn(List.of(question(SelfRegistration.OPEN)));

        harness.run((server, client) -> {
            var manager = harness.as(
                    TestSessions.member(STATION_ID, StationPermission.USER, StationPermission.EVENT_MANAGER));
            client.get(PREFIX + "/events/" + EVENT_ID + "/fields", manager);
        });

        verify(fields).findByEventFor(EVENT_ID, null, TestSessions.MEMBER_ID, true);
    }

    @Test
    void steppingInAnswersWithTheStateAfterwards() {
        var stored = question(null);
        when(fields.toggleSelfRegistration(EVENT_ID, FIELD_ID, TestSessions.MEMBER_ID, null, false))
                .thenReturn(stored);
        when(fields.asSeenBy(stored, TestSessions.MEMBER_ID, false)).thenReturn(question(SelfRegistration.OPEN));

        harness.run((server, client) -> {
            var member = harness.as(TestSessions.member(STATION_ID, StationPermission.USER));
            var answer = json(client.post(
                    PREFIX + "/events/" + EVENT_ID + "/fields/" + FIELD_ID + "/self-register",
                    RouteHarness.body("{}"),
                    member));
            assertEquals("OPEN", answer.get("selfRegistrationState").asText());
        });
    }
}
