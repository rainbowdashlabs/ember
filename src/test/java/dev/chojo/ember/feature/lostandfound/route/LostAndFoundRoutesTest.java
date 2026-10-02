/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.lostandfound.route;

import dev.chojo.ember.api.RouteHarness;
import dev.chojo.ember.api.TestSessions;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.conf.file.elements.Api;
import dev.chojo.ember.feature.lostandfound.entity.LostAndFoundItem;
import dev.chojo.ember.feature.lostandfound.service.LostAndFoundImageService;
import dev.chojo.ember.feature.lostandfound.service.LostAndFoundService;
import dev.chojo.ember.feature.members.service.GuardianPolicy;
import dev.chojo.ember.feature.members.service.MemberNameResolver;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static dev.chojo.ember.api.RouteHarness.PREFIX;
import static dev.chojo.ember.api.RouteHarness.json;
import static dev.chojo.ember.api.TestSessions.MEMBER_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A member reads what was found, and their own claims and those of the people in their care, with
 * the claimer named; somebody nobody can name any more reads as a question mark.
 */
class LostAndFoundRoutesTest {
    private static final int STATION = 3;
    private static final int WARD = 12;

    @Test
    void aMemberReadsTheirHouseholdsClaimsByName() {
        var service = mock(LostAndFoundService.class);
        var guardians = mock(GuardianPolicy.class);
        var names = mock(MemberNameResolver.class);
        when(guardians.household(any())).thenReturn(List.of(MEMBER_ID, WARD));
        when(names.called(WARD)).thenReturn("Tom");
        when(service.findUnclaimedOrClaimedBy(STATION, List.of(MEMBER_ID, WARD)))
                .thenReturn(List.of(
                        new LostAndFoundItem(
                                1, STATION, "Helm", LocalDate.EPOCH, WARD, Instant.EPOCH, 2, Instant.EPOCH),
                        new LostAndFoundItem(2, STATION, "Jacke", LocalDate.EPOCH, 77, Instant.EPOCH, 2, Instant.EPOCH),
                        new LostAndFoundItem(3, STATION, "Handschuh", LocalDate.EPOCH, null, null, 2, Instant.EPOCH)));
        var harness = RouteHarness.serving(
                new LostAndFoundRoutes(service, guardians, names, mock(LostAndFoundImageService.class), new Api()));

        var found = json(harness.request(client -> client.get(
                PREFIX + "/lost-and-found", harness.as(TestSessions.member(STATION, StationPermission.LOGIN)))));

        assertEquals("Tom", found.path(0).path("claimedByName").asString());
        assertEquals("?", found.path(1).path("claimedByName").asString());
        assertEquals(true, found.path(2).path("claimedByName").isNull());
    }
}
