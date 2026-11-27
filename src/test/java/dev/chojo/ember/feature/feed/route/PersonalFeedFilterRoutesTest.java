/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.feed.route;

import dev.chojo.ember.api.RouteHarness;
import dev.chojo.ember.api.TestSessions;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.refusal.FeedRefusal;
import dev.chojo.ember.feature.feed.service.FeedTokenService;
import dev.chojo.ember.feature.feed.service.PersonalFeedFilterService;
import dev.chojo.ember.feature.station.entity.StationModule;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The routes a member sets the categories of their own calendar feed through.
 */
class PersonalFeedFilterRoutesTest {
    private static final int STATION = 3;
    private static final String CATEGORIES = RouteHarness.PREFIX + "/feed/categories";

    private final PersonalFeedFilterService filters = mock(PersonalFeedFilterService.class);

    private RouteHarness harness() {
        return RouteHarness.serving(new FeedTokenRoutes(mock(FeedTokenService.class), filters));
    }

    @Test
    void anyMemberSetsTheirOwnChoice() {
        when(filters.setHiddenCategories(anyInt(), eq(STATION), eq(List.of(4, 2))))
                .thenReturn(List.of(2, 4));
        var response =
                harness().call(STATION, "PUT", CATEGORIES, "{\"hiddenCategories\":[4,2]}", StationPermission.USER);
        assertEquals(200, response.code());
        assertEquals(
                2, RouteHarness.json(response).get("hiddenCategories").get(0).asInt());
    }

    @Test
    void aMemberReadsTheirOwnChoice() {
        when(filters.hiddenCategories(TestSessions.MEMBER_ID)).thenReturn(List.of(7));
        var response = harness().call(STATION, "GET", CATEGORIES, null, StationPermission.USER);
        assertEquals(
                7, RouteHarness.json(response).get("hiddenCategories").get(0).asInt());
    }

    @Test
    void aCategoryOfAnotherStationIsRefused() {
        when(filters.setHiddenCategories(anyInt(), eq(STATION), eq(List.of(99))))
                .thenThrow(FeedRefusal.FEED_CATEGORY_NOT_HERE.raise());
        var response =
                harness().call(STATION, "PUT", CATEGORIES, "{\"hiddenCategories\":[99]}", StationPermission.USER);
        assertEquals(FeedRefusal.FEED_CATEGORY_NOT_HERE, RouteHarness.refusalOf(response));
    }

    @Test
    void theChoiceIsNotHereWhileAppointmentsAreSwitchedOff() {
        var off = harness().withModulesOff(StationModule.EVENTS);
        assertEquals(
                404,
                off.call(STATION, "GET", CATEGORIES, null, StationPermission.USER)
                        .code());
    }
}
