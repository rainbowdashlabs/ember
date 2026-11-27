/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.account.route;

import dev.chojo.ember.api.RouteHarness;
import dev.chojo.ember.api.TestSessions;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.account.entity.SidebarMode;
import dev.chojo.ember.feature.account.repository.AccountPreferenceRepository;
import dev.chojo.ember.feature.account.service.AccountPreferenceService;
import org.junit.jupiter.api.Test;

import static dev.chojo.ember.api.RouteHarness.PREFIX;
import static dev.chojo.ember.api.RouteHarness.body;
import static dev.chojo.ember.api.RouteHarness.json;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Everybody signed in chooses how their own sidebar lists its sections and whether their account can be
 * found by name, and only for their own account; a choice that names nothing is refused rather than
 * taken for one, and nobody signed out reaches either.
 */
class AccountPreferenceRoutesTest {
    private static final String SIDEBAR = PREFIX + "/session/preferences/sidebar";
    private static final String FINDABLE = PREFIX + "/session/preferences/findable";

    private final AccountPreferenceRepository preferences = mock(AccountPreferenceRepository.class);
    private final RouteHarness harness =
            RouteHarness.serving(new AccountPreferenceRoutes(new AccountPreferenceService(preferences)));

    @Test
    void theChoiceIsKeptForTheReadersOwnAccount() {
        var reader = harness.as(TestSessions.member(3, StationPermission.LOGIN));

        var response = harness.request(client -> client.put(SIDEBAR, body("{\"sidebar\": \"TREE\"}"), reader));

        assertEquals(204, response.code());
        verify(preferences).setSidebar(TestSessions.ACCOUNT_ID, SidebarMode.TREE);
    }

    @Test
    void aChoiceWithoutAModeIsRefused() {
        var reader = harness.as(TestSessions.member(3, StationPermission.LOGIN));

        var response = harness.request(client -> client.put(SIDEBAR, body("{}"), reader));

        assertEquals(400, response.code());
        verify(preferences, never()).setSidebar(anyInt(), any());
    }

    @Test
    void whetherTheAccountIsFindableIsReadAndChosenForTheReadersOwnAccount() {
        var reader = harness.as(TestSessions.member(3, StationPermission.LOGIN));
        when(preferences.findableByName(TestSessions.ACCOUNT_ID)).thenReturn(true);

        var read = harness.request(client -> client.get(FINDABLE, reader));
        var chosen = harness.request(client -> client.put(FINDABLE, body("{\"findable\": false}"), reader));

        assertEquals(200, read.code());
        assertTrue(json(read).get("findable").asBoolean());
        assertEquals(204, chosen.code());
        verify(preferences).setFindableByName(TestSessions.ACCOUNT_ID, false);
    }

    @Test
    void aFindableChoiceWithoutAnAnswerIsRefused() {
        var reader = harness.as(TestSessions.member(3, StationPermission.LOGIN));

        var response = harness.request(client -> client.put(FINDABLE, body("{}"), reader));

        assertEquals(400, response.code());
        verify(preferences, never()).setFindableByName(anyInt(), anyBoolean());
    }

    @Test
    void nobodySignedOutReadsOrChoosesWhetherTheyAreFindable() {
        var read = harness.request(client -> client.get(FINDABLE));
        var chosen = harness.request(client -> client.put(FINDABLE, body("{\"findable\": true}")));

        assertEquals(401, read.code());
        assertEquals(401, chosen.code());
        verify(preferences, never()).setFindableByName(anyInt(), anyBoolean());
    }
}
