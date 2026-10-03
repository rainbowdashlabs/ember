/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.station.route;

import dev.chojo.ember.api.RouteHarness;
import dev.chojo.ember.api.TestSessions;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.station.service.StationDiscoveryService;
import dev.chojo.ember.feature.station.service.StationDiscoveryService.DiscoveryEntry;
import dev.chojo.ember.feature.station.service.StationDiscoveryService.Viewer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static dev.chojo.ember.api.RouteHarness.json;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The public discovery list over HTTP: open to everybody, the same shape for local and remote stations.
 */
class DiscoveryRoutesTest {
    private static final String LIST = RouteHarness.PREFIX + "/public/discovery";
    private static final int STATION_ID = 7;

    private StationDiscoveryService listing;
    private RouteHarness harness;

    @BeforeEach
    void setup() {
        listing = mock(StationDiscoveryService.class);
        when(listing.list(any())).thenReturn(List.of(local(), remote()));
        harness = RouteHarness.serving(new DiscoveryRoutes(listing));
    }

    private static DiscoveryEntry local() {
        return new DiscoveryEntry(
                new UUID(0, 1),
                "Hier",
                null,
                true,
                "/api/v1/public/stations/" + new UUID(0, 1) + "/logo?size=128",
                true,
                false,
                true,
                false,
                true,
                false,
                false,
                true,
                true,
                "hier",
                "/public/station/hier",
                "Hauptstraße 1",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
    }

    private static DiscoveryEntry remote() {
        return new DiscoveryEntry(
                new UUID(0, 2),
                "Dort",
                "Von drüben",
                false,
                null,
                true,
                false,
                true,
                true,
                true,
                false,
                false,
                false,
                true,
                "dort",
                "https://feuer.example/public/station/dort",
                "Nordweg 2",
                "Nordstadt",
                "DE",
                52.5,
                13.4,
                null,
                null,
                "feuer.example",
                "https://feuer.example:8443");
    }

    @Test
    void localAndRemoteEntriesCarryTheSameFields() {
        var body = json(harness.request(client -> client.get(LIST)));

        assertEquals(fieldNames(body.get(0)), fieldNames(body.get(1)));
        assertTrue(body.get(0).get("hasPublicWiki").asBoolean());
        assertTrue(body.get(1).get("hasPublicWiki").asBoolean());
        assertTrue(body.get(1).get("waitingListOpen").asBoolean());
        assertEquals("Nordweg 2", body.get(1).get("addressLine").asString());
        assertEquals(
                "https://feuer.example:8443", body.get(1).get("instanceUrl").asString());
        assertFalse(body.get(0).has("memberCount"));
        assertFalse(body.get(1).has("memberCount"));
    }

    private static Set<String> fieldNames(JsonNode node) {
        return new HashSet<>(node.propertyNames());
    }

    private static UserSession signedIn() {
        return new UserSession(
                new Account(1, null, "wer@test.com", null, "Wer", "Da", true, null, "Wer Da", null, null),
                1,
                STATION_ID,
                null,
                null,
                Set.of(StationPermission.USER),
                Set.of(),
                null);
    }

    @Test
    void aVisitorWithoutSessionIsListedLocalAndRemoteStations() {
        var response = harness.request(client -> client.get(LIST));

        assertEquals(200, response.code());
        var body = json(response);
        assertEquals(2, body.size());
        assertTrue(body.get(0).get("instanceHost").isNull());
        assertEquals("feuer.example", body.get(1).get("instanceHost").asString());
        assertEquals(
                "https://feuer.example/public/station/dort",
                body.get(1).get("publicPageUrl").asString());
        verify(listing).list(Viewer.anonymous());
    }

    @Test
    void aSignedInReaderIsListedForTheStationTheyActFor() {
        var response = harness.request(client -> client.get(LIST, harness.as(signedIn())));

        assertEquals(200, response.code());
        verify(listing).list(new Viewer(true, STATION_ID, false));
    }

    @Test
    void aReaderWithTheFederationPermissionIsListedAsOneWhoMayAsk() {
        var federator = TestSessions.member(STATION_ID, StationPermission.STATION_FEDERATION);

        var response = harness.request(client -> client.get(LIST, harness.as(federator)));

        assertEquals(200, response.code());
        verify(listing).list(new Viewer(true, STATION_ID, true));
        assertTrue(json(response).get(0).get("canRequest").asBoolean());
    }
}
