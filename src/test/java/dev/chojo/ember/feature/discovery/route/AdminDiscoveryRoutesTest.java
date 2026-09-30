/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.discovery.route;

import dev.chojo.ember.api.RouteHarness;
import dev.chojo.ember.api.TestSessions;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.discovery.entity.BlocklistKind;
import dev.chojo.ember.feature.discovery.entity.PeerSource;
import dev.chojo.ember.feature.discovery.protocol.DiscoveryInfoResponse;
import dev.chojo.ember.feature.discovery.service.DiscoveredStationService;
import dev.chojo.ember.feature.discovery.service.DiscoveryAdminService;
import dev.chojo.ember.feature.discovery.service.DiscoveryAdminService.AddPeerRequest;
import dev.chojo.ember.feature.discovery.service.DiscoveryAdminService.BlocklistRequest;
import dev.chojo.ember.feature.discovery.service.DiscoveryAdminService.DiscoverNowResponse;
import dev.chojo.ember.feature.discovery.service.DiscoveryAdminService.IdentityResponse;
import dev.chojo.ember.feature.discovery.service.DiscoveryAdminService.PeerResponse;
import dev.chojo.ember.feature.discovery.service.DiscoverySettingsService;
import io.javalin.testtools.HttpClient;
import io.javalin.testtools.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static dev.chojo.ember.api.RouteHarness.PREFIX;
import static dev.chojo.ember.api.RouteHarness.body;
import static dev.chojo.ember.api.RouteHarness.json;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminDiscoveryRoutesTest {
    private static final String BASE = PREFIX + "/admin/discovery";
    private static final PeerResponse PEER = new PeerResponse(
            "k1", "https://p.test", "i1", Instant.EPOCH, null, null, null, true, PeerSource.MANUAL, null, 1, false);

    private DiscoveryAdminService discovery;
    private DiscoveredStationService stations;
    private DiscoverySettingsService settings;
    private RouteHarness harness;

    @BeforeEach
    void setup() {
        discovery = mock(DiscoveryAdminService.class);
        stations = mock(DiscoveredStationService.class);
        settings = mock(DiscoverySettingsService.class);
        harness = RouteHarness.serving(new AdminDiscoveryRoutes(discovery, stations, settings));
    }

    private Response get(HttpClient client, String path) {
        return client.get(BASE + path, harness.as(TestSessions.administrator()));
    }

    private Response post(HttpClient client, String path, Object json) {
        return client.post(BASE + path, json, harness.as(TestSessions.administrator()));
    }

    private Response delete(HttpClient client, String path) {
        return client.delete(BASE + path, null, harness.as(TestSessions.administrator()));
    }

    @Test
    void theIdentityAndTheSettingsAreReadAndOnlyWhatIsSentIsChanged() {
        when(discovery.identity()).thenReturn(new IdentityResponse("i0", "k0", "https://self.test"));
        when(settings.maxDepth()).thenReturn(2);

        harness.run((server, client) -> {
            assertEquals("i0", json(get(client, "/identity")).path("instanceId").asString());
            assertEquals(2, json(get(client, "/settings")).path("maxDepth").asInt());
            var saved = client.put(
                    BASE + "/settings",
                    body("{\"enabled\": true, \"maxDepth\": null, \"pingIntervalMinutes\": 30}"),
                    harness.as(TestSessions.administrator()));
            assertEquals(200, saved.code());
        });

        verify(settings).setEnabled(true);
        verify(settings).setPingIntervalMinutes(30);
        verify(settings, never()).setMaxDepth(anyInt());
    }

    @Test
    void peersAreProbedAddedVotedOnBlockedPingedAndDeleted() {
        when(discovery.probe("https://p.test"))
                .thenReturn(new DiscoveryInfoResponse("https://p.test", "i1", "k1", "1", true));
        when(discovery.addPeer(any())).thenReturn(PEER);
        when(discovery.upvote("k1")).thenReturn(PEER);
        when(discovery.downvote("k1")).thenReturn(PEER);
        when(discovery.block("k1")).thenReturn(PEER);
        when(discovery.unblock("k1")).thenReturn(PEER);
        when(discovery.deletePeer("k1")).thenReturn(true);
        when(discovery.discoverNow()).thenReturn(new DiscoverNowResponse(2, 5));
        when(discovery.seedFromFederation()).thenReturn(3);

        harness.run((server, client) -> {
            assertEquals(200, get(client, "/peers").code());
            assertEquals(
                    "k1",
                    json(post(client, "/peers/probe", body("{\"baseUrl\": \"https://p.test\"}")))
                            .path("publicKey")
                            .asString());
            post(client, "/peers", body("{\"baseUrl\": \"https://p.test\", \"expectedPublicKey\": \"k1\"}"));
            for (var action : new String[] {"upvote", "downvote", "block", "unblock"}) {
                assertEquals(
                        "k1",
                        json(post(client, "/peers/k1/" + action, null))
                                .path("publicKey")
                                .asString());
            }
            assertEquals(
                    "Ping dispatched",
                    json(post(client, "/peers/k1/ping", null)).path("message").asString());
            assertTrue(json(delete(client, "/peers/k1")).path("changed").asBoolean());
            assertEquals(
                    5,
                    json(post(client, "/discover-now", null))
                            .path("stationsFetched")
                            .asInt());
            assertEquals(3, json(post(client, "/seed", null)).path("changed").asInt());
        });

        verify(discovery).addPeer(new AddPeerRequest("https://p.test", "k1"));
        verify(discovery).pingNow("k1");
    }

    @Test
    void theBlocklistIsListedAddedToAndShortened() {
        when(discovery.removeFromBlocklist("x")).thenReturn(true);

        harness.run((server, client) -> {
            assertEquals(200, get(client, "/blocklist").code());
            assertEquals(
                    "Added to blocklist",
                    json(post(client, "/blocklist", body("{\"value\": \"x\", \"kind\": \"BASE_URL\", \"note\": null}")))
                            .path("message")
                            .asString());
            assertTrue(json(delete(client, "/blocklist/x")).path("changed").asBoolean());
        });

        verify(discovery).addToBlocklist(new BlocklistRequest("x", BlocklistKind.BASE_URL, null));
    }

    @Test
    void membersBrowseTheCachedStationsAndEditorsPickPartners() {
        harness.run((server, client) -> {
            assertEquals(
                    200,
                    client.get(PREFIX + "/discovery/stations", harness.as(TestSessions.member(3)))
                            .code());
            var editor = harness.as(TestSessions.member(3, StationPermission.PAGE_EDIT));
            assertEquals(
                    200,
                    client.get(PREFIX + "/federation/stations/search?q=nord&limit=5", editor)
                            .code());
        });

        verify(stations).cachedStations();
        verify(stations).picker(3, "nord", 5);
    }
}
