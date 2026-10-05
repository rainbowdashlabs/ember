/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.protocol.route;

import dev.chojo.ember.api.RouteHarness;
import dev.chojo.ember.api.TestSessions;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.refusal.TestProtocolRefusal;
import dev.chojo.ember.feature.protocol.entity.TestProtocolRun;
import dev.chojo.ember.feature.protocol.service.TestProtocolEvaluationService;
import dev.chojo.ember.feature.protocol.service.TestProtocolExaminerService;
import dev.chojo.ember.feature.protocol.service.TestProtocolExaminerService.GradingScope;
import dev.chojo.ember.feature.protocol.service.TestProtocolGuards;
import dev.chojo.ember.feature.protocol.service.TestProtocolPdfService;
import dev.chojo.ember.feature.protocol.service.TestProtocolRunService;
import dev.chojo.ember.feature.protocol.service.TestProtocolService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static dev.chojo.ember.api.RouteHarness.PREFIX;
import static dev.chojo.ember.api.RouteHarness.body;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Grading a run with examiners, over HTTP: who is let in, and which ticks reach the sheet.
 */
class TestProtocolGradingRoutesTest {
    private static final int STATION = 3;
    private static final TestProtocolRun RUN = new TestProtocolRun(
            5, 1, STATION, "Herbst", LocalDate.of(2026, 9, 1), TestProtocolRun.RunStatus.values()[0], 11, null);

    private TestProtocolService protocols;
    private TestProtocolExaminerService examiners;
    private RouteHarness harness;

    @BeforeEach
    void setup() {
        protocols = mock(TestProtocolService.class);
        examiners = mock(TestProtocolExaminerService.class);
        when(protocols.findRun(5)).thenReturn(Optional.of(RUN));
        harness = RouteHarness.serving(new TestProtocolRoutes(
                protocols,
                new TestProtocolGuards(protocols),
                mock(TestProtocolPdfService.class),
                mock(TestProtocolRunService.class),
                mock(TestProtocolEvaluationService.class),
                examiners));
    }

    @Test
    void aTesterWhoIsNoExaminerOfThePlannedRunIsTurnedAway() {
        when(examiners.requireGrader(eq(RUN), anyInt(), anyBoolean()))
                .thenThrow(TestProtocolRefusal.PROTOCOL_RUN_GRADED_BY_ITS_EXAMINERS.raise());

        harness.run((server, client) -> {
            var tester = harness.as(TestSessions.member(STATION, StationPermission.PROTOCOL_TESTER));
            assertEquals(
                    403,
                    client.post(PREFIX + "/protocols/runs/5/members/9/lock", body("{}"), tester)
                            .code());
            assertEquals(
                    403,
                    client.put(
                                    PREFIX + "/protocols/runs/5/members/9/checks",
                                    body("{\"checks\": {\"1\": true}}"),
                                    tester)
                            .code());
        });

        verify(protocols, never()).lockMember(anyInt(), anyInt(), anyInt());
        verify(protocols, never()).saveChecks(anyInt(), anyInt(), anyMap(), anyInt(), anyInt());
    }

    /** An examiner's sheet is saved with only the ticks of their own sections. */
    @Test
    void anExaminerSavesOnlyTheirOwnTicks() {
        var scope = new GradingScope(true, true, Set.of(2));
        when(examiners.requireGrader(eq(RUN), anyInt(), anyBoolean())).thenReturn(scope);
        when(protocols.findAllItemsByProtocol(1)).thenReturn(List.of());
        when(examiners.allowedChecks(eq(scope), anyMap(), any())).thenReturn(Map.of(7, true));

        harness.run((server, client) -> {
            var tester = harness.as(TestSessions.member(STATION, StationPermission.PROTOCOL_TESTER));
            assertEquals(
                    200,
                    client.put(
                                    PREFIX + "/protocols/runs/5/members/9/checks",
                                    body("{\"checks\": {\"7\": true, \"8\": true}}"),
                                    tester)
                            .code());
        });

        verify(protocols).saveChecks(eq(5), eq(9), eq(Map.of(7, true)), anyInt(), eq(1));
    }

    /** Examiners of one run grade a member side by side, so nobody holds the member for the others. */
    @Test
    void aPlannedRunIsNotHeldByOneExaminer() {
        when(examiners.requireGrader(eq(RUN), anyInt(), anyBoolean()))
                .thenReturn(new GradingScope(true, true, Set.of()));
        when(examiners.isPlanned(5)).thenReturn(true);
        when(protocols.findRunMember(5, 9)).thenReturn(Optional.empty());

        harness.run((server, client) -> client.post(
                PREFIX + "/protocols/runs/5/members/9/lock",
                body("{}"),
                harness.as(TestSessions.member(STATION, StationPermission.PROTOCOL_TESTER))));

        verify(protocols, never()).lockMember(anyInt(), anyInt(), anyInt());
    }
}
