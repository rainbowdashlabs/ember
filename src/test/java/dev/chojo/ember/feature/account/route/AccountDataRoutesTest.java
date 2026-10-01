/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.account.route;

import dev.chojo.ember.api.RouteHarness;
import dev.chojo.ember.api.TestSessions;
import dev.chojo.ember.api.auth.SessionCookies;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.refusal.MemberRefusal;
import dev.chojo.ember.feature.legal.service.GdprDeletionService;
import dev.chojo.ember.feature.legal.service.GdprExportService;
import org.junit.jupiter.api.Test;

import static dev.chojo.ember.api.RouteHarness.PREFIX;
import static dev.chojo.ember.api.RouteHarness.refusalOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Deleting one's own account leaves the decision to the deletion service and signs the reader out
 * only once it went through.
 */
class AccountDataRoutesTest {
    private final GdprDeletionService deletion = mock(GdprDeletionService.class);
    private final SessionCookies cookies = mock(SessionCookies.class);
    private final RouteHarness harness =
            RouteHarness.serving(new AccountDataRoutes(mock(GdprExportService.class), deletion, cookies));

    @Test
    void theReaderDeletesTheirOwnAccount() {
        var reader = harness.as(TestSessions.member(3, StationPermission.LOGIN));

        var response = harness.request(client -> client.delete(PREFIX + "/session/account", null, reader));

        assertEquals(204, response.code());
        verify(deletion).deleteOwnAccount(TestSessions.ACCOUNT_ID);
        verify(cookies).clear(any());
    }

    @Test
    void aStationAdministratorIsRefusedAndStaysSignedIn() {
        doThrow(MemberRefusal.ACCOUNT_STILL_ADMINISTERS_STATION.raise())
                .when(deletion)
                .deleteOwnAccount(TestSessions.ACCOUNT_ID);
        var reader = harness.as(TestSessions.member(3, StationPermission.LOGIN));

        var response = harness.request(client -> client.delete(PREFIX + "/session/account", null, reader));

        assertEquals(MemberRefusal.ACCOUNT_STILL_ADMINISTERS_STATION, refusalOf(response));
        verify(cookies, never()).clear(any());
    }
}
