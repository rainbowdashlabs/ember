/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.mailimport.route;

import dev.chojo.ember.api.RouteHarness;
import dev.chojo.ember.api.TestSessions;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.refusal.GeneralRefusal;
import dev.chojo.ember.conf.file.elements.MailImport;
import dev.chojo.ember.feature.mailimport.service.MailboxService;
import dev.chojo.ember.feature.station.entity.StationModule;
import org.junit.jupiter.api.Test;

import java.util.List;

import static dev.chojo.ember.api.RouteHarness.PREFIX;
import static dev.chojo.ember.api.RouteHarness.json;
import static dev.chojo.ember.api.RouteHarness.refusalOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Mail import files into the document store, so it is gone wherever the store is. */
class MailImportRoutesTest {
    private static final int STATION = 4;

    private final MailboxService mailboxes = mock(MailboxService.class);
    private final RouteHarness harness = RouteHarness.serving(new MailImportRoutes(mailboxes, new MailImport()));

    /** A station that keeps documents lists its mailboxes. */
    @Test
    void aStationKeepingDocumentsListsItsMailboxes() {
        when(mailboxes.findByStation(STATION)).thenReturn(List.of());
        var session = harness.as(TestSessions.member(STATION, StationPermission.STATION_MAIL));

        var listed = json(harness.request(client -> client.get(PREFIX + "/station/mail-import/mailboxes", session)));

        assertEquals(0, listed.size());
    }

    /**
     * A station whose document store is off, by its own switch or its cluster's denial, answers as if mail
     * import were not there.
     */
    @Test
    void aStationWithoutDocumentsHasNoMailImport() {
        var session = harness.as(TestSessions.member(STATION, StationPermission.STATION_MAIL));
        harness.withModulesOff(StationModule.DOCUMENTS);

        var refused = harness.request(client -> client.get(PREFIX + "/station/mail-import/mailboxes", session));

        assertEquals(GeneralRefusal.MODULE_SWITCHED_OFF, refusalOf(refused));
    }
}
