/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.route;

import dev.chojo.ember.api.RouteHarness;
import dev.chojo.ember.api.TestSessions;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.inventory.entity.SelfCheck;
import dev.chojo.ember.feature.inventory.entity.SelfCheckState;
import dev.chojo.ember.feature.inventory.service.SelfCheckReviewService;
import dev.chojo.ember.feature.inventory.service.SelfCheckService;
import dev.chojo.ember.feature.members.service.MemberNameResolver;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static dev.chojo.ember.api.RouteHarness.PREFIX;
import static dev.chojo.ember.api.RouteHarness.json;
import static dev.chojo.ember.api.TestSessions.MEMBER_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The names on a self-check, as its member and its reviewer read them, come from the name
 * resolver; somebody it cannot name reads as nobody rather than as an error.
 */
class SelfCheckRoutesTest {
    private static final int STATION = 3;
    private static final SelfCheck TASK = new SelfCheck(
            7, STATION, MEMBER_ID, 99, Instant.EPOCH, LocalDate.EPOCH, SelfCheckState.OPEN, null, null, null, null);

    @Test
    void theMemberReadsTheirTasksByName() {
        var selfChecks = mock(SelfCheckService.class);
        var names = mock(MemberNameResolver.class);
        when(selfChecks.outstandingFor(eq(MEMBER_ID), anyBoolean())).thenReturn(List.of(TASK));
        when(names.called(MEMBER_ID)).thenReturn("Mara");
        var harness = RouteHarness.serving(new SelfCheckRoutes(selfChecks, names));

        var mine = harness.request(client -> client.get(
                PREFIX + "/self-checks/mine", harness.as(TestSessions.member(STATION, StationPermission.USER))));

        assertEquals("Mara", json(mine).path(0).path("memberName").asString());
    }

    @Test
    void theReviewerReadsWhoTheTaskIsForAndWhoHandedItOut() {
        var reviews = mock(SelfCheckReviewService.class);
        var names = mock(MemberNameResolver.class);
        when(reviews.forStation(STATION, true)).thenReturn(List.of(TASK));
        when(names.called(MEMBER_ID)).thenReturn("Mara");
        var harness = RouteHarness.serving(new SelfCheckReviewRoutes(reviews, names));

        var tasks = harness.request(client -> client.get(
                PREFIX + "/self-check-reviews?includeEnded=true",
                harness.as(TestSessions.member(STATION, StationPermission.INVENTORY_CHECK))));

        assertEquals("Mara", json(tasks).path(0).path("memberName").asString());
        assertEquals("", json(tasks).path(0).path("handedOutByName").asString());
    }
}
