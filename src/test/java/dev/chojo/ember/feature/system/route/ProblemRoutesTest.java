/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.route;

import dev.chojo.ember.api.RouteHarness;
import dev.chojo.ember.api.TestSessions;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.refusal.SystemRefusal;
import dev.chojo.ember.feature.system.entity.ProblemLevel;
import dev.chojo.ember.feature.system.entity.ProblemState;
import dev.chojo.ember.feature.system.entity.StoredProblem;
import dev.chojo.ember.feature.system.service.ProblemLogService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static dev.chojo.ember.api.RouteHarness.PREFIX;
import static dev.chojo.ember.api.RouteHarness.json;
import static dev.chojo.ember.api.RouteHarness.refusalOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The problem log over HTTP: listed by state and kind, resolved, muted, unmuted and deleted by an
 * administrator, and closed to everybody else.
 */
class ProblemRoutesTest {
    private static final int ADMIN = TestSessions.administrator().account().id();

    private static StoredProblem problem(long id, ProblemLevel level) {
        Instant now = Instant.parse("2026-11-01T10:00:00Z");
        return new StoredProblem(
                id,
                "fp",
                level,
                "dev.chojo.ember.X",
                "broken",
                null,
                null,
                null,
                List.of("broken"),
                3,
                now,
                now,
                ProblemState.OPEN,
                null,
                1);
    }

    @Test
    void theListAsksForTheStateAndKindItWasGiven() {
        var service = mock(ProblemLogService.class);
        when(service.list(Set.of(ProblemState.OPEN), false)).thenReturn(List.of(problem(1, ProblemLevel.ERROR)));
        when(service.list(Set.of(ProblemState.MUTED), true)).thenReturn(List.of(problem(2, ProblemLevel.REFUSAL)));
        when(service.list(Set.of(), false)).thenReturn(List.of());
        var harness = RouteHarness.serving(new ProblemRoutes(service));

        harness.run((server, client) -> {
            var admin = harness.as(TestSessions.administrator());
            var open = json(client.get(PREFIX + "/admin/problems", admin));
            assertEquals(1, open.get(0).path("id").asLong());
            assertEquals(3, open.get(0).path("count").asLong());
            assertEquals(1, open.get(0).path("reopenedCount").asInt());
            var muted = json(client.get(PREFIX + "/admin/problems?state=muted&refusals=true", admin));
            assertEquals("REFUSAL", muted.get(0).path("level").asString());
            assertEquals(
                    0,
                    json(client.get(PREFIX + "/admin/problems?state=all", admin))
                            .size());
            assertEquals(
                    400,
                    client.get(PREFIX + "/admin/problems?state=gone", admin).code());
        });
    }

    @Test
    void anAdministratorWorksThroughAProblem() {
        var service = mock(ProblemLogService.class);
        when(service.resolveAll(true, ADMIN)).thenReturn(4);
        var harness = RouteHarness.serving(new ProblemRoutes(service));

        harness.run((server, client) -> {
            var admin = harness.as(TestSessions.administrator());
            assertEquals(
                    204,
                    client.post(PREFIX + "/admin/problems/5/resolve", null, admin)
                            .code());
            assertEquals(
                    204,
                    client.post(PREFIX + "/admin/problems/5/mute", null, admin).code());
            assertEquals(
                    204,
                    client.post(PREFIX + "/admin/problems/5/unmute", null, admin)
                            .code());
            assertEquals(
                    204,
                    client.delete(PREFIX + "/admin/problems/5", null, admin).code());
            var resolved = json(client.post(PREFIX + "/admin/problems/resolve-all?refusals=true", null, admin));
            assertEquals(4, resolved.path("resolved").asInt());
        });

        verify(service).resolve(5, ADMIN);
        verify(service).mute(5, ADMIN);
        verify(service).unmute(5, ADMIN);
        verify(service).delete(5);
    }

    @Test
    void aProblemThatIsGoneIsSaidToBe() {
        var service = mock(ProblemLogService.class);
        doThrow(SystemRefusal.PROBLEM_NOT_HERE.raise()).when(service).resolve(eq(9L), anyInt());
        var harness = RouteHarness.serving(new ProblemRoutes(service));

        var answer = harness.request(client ->
                client.post(PREFIX + "/admin/problems/9/resolve", null, harness.as(TestSessions.administrator())));

        assertEquals(SystemRefusal.PROBLEM_NOT_HERE, refusalOf(answer));
    }

    @Test
    void aMemberIsKeptOut() {
        var service = mock(ProblemLogService.class);
        var harness = RouteHarness.serving(new ProblemRoutes(service));

        var answer = harness.request(client -> client.post(
                PREFIX + "/admin/problems/5/mute", null, harness.as(TestSessions.member(3, StationPermission.LOGIN))));

        assertEquals(403, answer.code());
        verifyNoInteractions(service);
    }
}
