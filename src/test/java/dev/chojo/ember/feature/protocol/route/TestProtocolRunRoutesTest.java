/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.protocol.route;

import dev.chojo.ember.api.RouteHarness;
import dev.chojo.ember.api.TestSessions;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.protocol.entity.TestProtocol;
import dev.chojo.ember.feature.protocol.entity.TestProtocolRun;
import dev.chojo.ember.feature.protocol.service.TestProtocolEvaluationService;
import dev.chojo.ember.feature.protocol.service.TestProtocolEvaluationService.EvaluationResponse;
import dev.chojo.ember.feature.protocol.service.TestProtocolExaminerService;
import dev.chojo.ember.feature.protocol.service.TestProtocolGuards;
import dev.chojo.ember.feature.protocol.service.TestProtocolPdfService;
import dev.chojo.ember.feature.protocol.service.TestProtocolRunService;
import dev.chojo.ember.feature.protocol.service.TestProtocolRunService.ProtocolRunRequest;
import dev.chojo.ember.feature.protocol.service.TestProtocolService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static dev.chojo.ember.api.RouteHarness.PREFIX;
import static dev.chojo.ember.api.RouteHarness.body;
import static dev.chojo.ember.api.RouteHarness.header;
import static dev.chojo.ember.api.RouteHarness.json;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Starting, evaluating and exporting a test run, over HTTP.
 */
class TestProtocolRunRoutesTest {
    private static final int STATION = 3;
    private static final LocalDate DAY = LocalDate.of(2026, 9, 1);
    private static final TestProtocol PROTOCOL = new TestProtocol(1, STATION, "Knoten", "", 70, null, null);
    private static final TestProtocolRun RUN =
            new TestProtocolRun(5, 1, STATION, "Herbst", DAY, TestProtocolRun.RunStatus.values()[0], 11, null);

    private TestProtocolService protocols;
    private TestProtocolRunService runs;
    private TestProtocolEvaluationService evaluations;
    private TestProtocolPdfService pdfs;
    private RouteHarness harness;

    @BeforeEach
    void setup() {
        protocols = mock(TestProtocolService.class);
        runs = mock(TestProtocolRunService.class);
        evaluations = mock(TestProtocolEvaluationService.class);
        pdfs = mock(TestProtocolPdfService.class);
        when(protocols.findProtocol(1)).thenReturn(Optional.of(PROTOCOL));
        when(protocols.findRun(5)).thenReturn(Optional.of(RUN));
        harness = RouteHarness.serving(new TestProtocolRoutes(
                protocols,
                new TestProtocolGuards(protocols),
                pdfs,
                runs,
                evaluations,
                mock(TestProtocolExaminerService.class)));
    }

    @Test
    void aRunIsStartedForTheStationAndTheTesterStartingIt() {
        when(runs.start(anyInt(), anyInt(), anyInt(), any())).thenReturn(RUN);

        var answer = harness.request(client -> client.post(
                PREFIX + "/protocols/1/runs",
                body("{\"name\": \"Herbst\", \"testDate\": \"2026-09-01\", \"memberIds\": [4]}"),
                harness.as(TestSessions.member(STATION, StationPermission.PROTOCOL_CREATE))));

        assertEquals(201, answer.code());
        verify(runs)
                .start(
                        1,
                        STATION,
                        TestSessions.MEMBER_ID,
                        new ProtocolRunRequest("Herbst", DAY, List.of(4), null, null, null));
    }

    @Test
    void theEvaluationAndTheExportsAreForARunOfTheStation() {
        when(evaluations.evaluate(RUN))
                .thenReturn(new EvaluationResponse("Knoten", DAY, List.of(), Map.of(), List.of(), 70));
        when(runs.archive(RUN, "Knoten")).thenReturn(new byte[] {1, 2});
        when(runs.memberFileName(4)).thenReturn("Mara");
        when(pdfs.exportRunMember(5, 4, "Knoten", DAY)).thenReturn(new byte[] {3});

        harness.run((server, client) -> {
            var tester = harness.as(TestSessions.member(STATION, StationPermission.PROTOCOL_TESTER));
            assertEquals(
                    "Knoten",
                    json(client.get(PREFIX + "/protocols/runs/5/evaluation", tester))
                            .path("protocolName")
                            .asString());
            var archive = client.get(PREFIX + "/protocols/runs/5/export-all", tester);
            assertTrue(header(archive, "Content-Type").startsWith("application/zip"));
            var sheet = client.get(PREFIX + "/protocols/runs/5/members/4/export", tester);
            assertTrue(header(sheet, "Content-Disposition").contains("Mara"));
            var foreign = harness.as(TestSessions.member(9, StationPermission.PROTOCOL_TESTER));
            assertEquals(
                    404,
                    client.get(PREFIX + "/protocols/runs/5/evaluation", foreign).code());
        });
    }
}
