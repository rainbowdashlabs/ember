/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.route;

import dev.chojo.ember.api.RouteHarness;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.events.entity.EventRegistrationFieldValue;
import dev.chojo.ember.feature.events.service.PartnerQuestionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static dev.chojo.ember.api.RouteHarness.PREFIX;
import static dev.chojo.ember.api.RouteHarness.json;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Whoever decides about sign-ups reads the answers partners' members gave, for their own station only. */
class PartnerAnswerRoutesTest {
    private static final int STATION_ID = 3;
    private static final String ANSWERS = PREFIX + "/events/40/partner-answers";

    private PartnerQuestionService questions;
    private RouteHarness harness;

    @BeforeEach
    void serve() {
        questions = mock(PartnerQuestionService.class);
        when(questions.answers(STATION_ID, 40, false))
                .thenReturn(Map.of(9, List.of(new EventRegistrationFieldValue(5, "L"))));
        when(questions.answers(STATION_ID, 40, true))
                .thenReturn(Map.of(
                        9,
                        List.of(
                                new EventRegistrationFieldValue(5, "L"),
                                new EventRegistrationFieldValue(6, "Allergie"))));
        harness = RouteHarness.serving(new PartnerAnswerRoutes(questions));
    }

    @Test
    void theAnswersAreListedPerPartnerRegistration() {
        var listed = json(harness.call(STATION_ID, "GET", ANSWERS, null, StationPermission.EVENT_REGISTRATION));

        assertEquals(9, listed.get(0).get("registrationId").asInt());
        assertEquals(5, listed.get(0).get("answers").get(0).get("fieldId").asInt());
        assertEquals("L", listed.get(0).get("answers").get(0).get("value").asString());
    }

    /** An answer kept for organisers reaches only whoever edits the appointment. */
    @Test
    void anAnswerKeptForOrganisersReachesOnlyWhoeverEditsTheAppointment() {
        var registrar = json(harness.call(STATION_ID, "GET", ANSWERS, null, StationPermission.EVENT_REGISTRATION));
        var editor = json(harness.call(
                STATION_ID, "GET", ANSWERS, null, StationPermission.EVENT_REGISTRATION, StationPermission.EVENT_EDIT));

        assertEquals(1, registrar.get(0).get("answers").size());
        assertEquals(2, editor.get(0).get("answers").size());
        assertEquals(
                "Allergie", editor.get(0).get("answers").get(1).get("value").asString());
    }

    @Test
    void aMemberWhoDoesNotHandleRegistrationsReadsNothing() {
        assertEquals(
                403,
                harness.call(STATION_ID, "GET", ANSWERS, null, StationPermission.USER)
                        .code());
        verify(questions, never()).answers(anyInt(), anyInt(), anyBoolean());
    }
}
