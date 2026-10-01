/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.route;

import dev.chojo.ember.api.RouteHarness;
import dev.chojo.ember.api.TestSessions;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.refusal.MemberRefusal;
import dev.chojo.ember.feature.members.entity.FilterTableType;
import dev.chojo.ember.feature.members.entity.SavedFilter;
import dev.chojo.ember.feature.members.service.SavedFilterService;
import dev.chojo.ember.feature.members.service.SavedFilterService.CreateFilterRequest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static dev.chojo.ember.api.RouteHarness.PREFIX;
import static dev.chojo.ember.api.RouteHarness.body;
import static dev.chojo.ember.api.RouteHarness.refusalOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A person's saved filters over HTTP, always for the account that is signed in.
 */
class SavedFilterRoutesTest {
    private final SavedFilterService filters = mock(SavedFilterService.class);
    private final RouteHarness harness = RouteHarness.serving(new SavedFilterRoutes(filters));
    private final int account = TestSessions.ACCOUNT_ID;

    @Test
    void filtersAreListedCreatedAndDeletedForTheSignedInAccount() {
        var session = TestSessions.member(3, StationPermission.LOGIN);
        var request = new CreateFilterRequest(FilterTableType.MEMBERS, "Jugend", "{}");
        when(filters.list(account, FilterTableType.MEMBERS)).thenReturn(List.of());
        when(filters.create(account, request))
                .thenReturn(new SavedFilter(1, account, FilterTableType.MEMBERS, "Jugend", "{}", 0));
        doThrow(MemberRefusal.SAVED_FILTER_NOT_HERE_ON_DELETE.raise())
                .when(filters)
                .delete(account, 9);

        harness.run((server, client) -> {
            assertEquals(
                    200,
                    client.get(PREFIX + "/saved-filters?tableType=members", harness.as(session))
                            .code());
            var created = client.post(
                    PREFIX + "/saved-filters",
                    body("{\"tableType\": \"MEMBERS\", \"name\": \"Jugend\", \"filterData\": \"{}\"}"),
                    harness.as(session));
            assertEquals(201, created.code());
            assertEquals(
                    204,
                    client.delete(PREFIX + "/saved-filters/1", null, harness.as(session))
                            .code());
            assertEquals(
                    MemberRefusal.SAVED_FILTER_NOT_HERE_ON_DELETE,
                    refusalOf(client.delete(PREFIX + "/saved-filters/9", null, harness.as(session))));
        });

        verify(filters).delete(account, 1);
    }
}
