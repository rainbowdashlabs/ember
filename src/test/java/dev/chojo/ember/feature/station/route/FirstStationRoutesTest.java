/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.station.route;

import dev.chojo.ember.api.RouteHarness;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.InstancePermission;
import dev.chojo.ember.api.refusal.StationRefusal;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.service.FirstStationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static dev.chojo.ember.api.RouteHarness.body;
import static dev.chojo.ember.api.RouteHarness.json;
import static dev.chojo.ember.api.RouteHarness.refusalOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The first station of a fresh instance over HTTP: asked about and founded by an administrator only.
 */
class FirstStationRoutesTest {
    private static final String PATH = RouteHarness.PREFIX + "/admin/first-station";
    private static final int ACCOUNT = 5;
    private static final UUID STATION_UID = UUID.fromString("7f0c1f5e-3f4c-4b6f-9a51-0d1e2f3a4b5c");

    private FirstStationService service;
    private RouteHarness harness;

    @BeforeEach
    void setup() {
        service = mock(FirstStationService.class);
        harness = RouteHarness.serving(new FirstStationRoutes(service));
    }

    private static UserSession signedIn(Set<InstancePermission> instancePermissions) {
        return new UserSession(
                new Account(ACCOUNT, null, "admin@test.com", null, "Ad", "Min", true, null, "Ad Min", null, null),
                1,
                null,
                null,
                null,
                Set.of(),
                instancePermissions,
                null);
    }

    private static UserSession administrator() {
        return signedIn(Set.of(InstancePermission.ADMINISTRATOR));
    }

    @Test
    void anAdministratorLearnsWhetherTheFirstStationIsStillMissing() {
        when(service.isNeeded()).thenReturn(true);

        var response = harness.request(client -> client.get(PATH, harness.as(administrator())));

        assertEquals(200, response.code());
        assertTrue(json(response).get("needed").asBoolean());
    }

    @Test
    void anAdministratorFoundsTheFirstStationAsThemselves() {
        var station = mock(Station.class);
        when(station.uid()).thenReturn(STATION_UID);
        when(station.name()).thenReturn("Jugendfeuerwehr Musterstadt");
        when(service.found("Jugendfeuerwehr Musterstadt", ACCOUNT)).thenReturn(station);

        var response = harness.request(client ->
                client.post(PATH, body("{\"name\": \"Jugendfeuerwehr Musterstadt\"}"), harness.as(administrator())));

        assertEquals(201, response.code());
        assertEquals(STATION_UID.toString(), json(response).get("stationUid").asString());
        verify(service).found("Jugendfeuerwehr Musterstadt", ACCOUNT);
    }

    @Test
    void aSecondFirstStationIsRefusedByName() {
        when(service.found(anyString(), anyInt())).thenThrow(StationRefusal.FIRST_STATION_ALREADY_FOUNDED.raise());

        var response = harness.request(
                client -> client.post(PATH, body("{\"name\": \"Noch eine\"}"), harness.as(administrator())));

        assertEquals(StationRefusal.FIRST_STATION_ALREADY_FOUNDED, refusalOf(response));
    }

    @Test
    void somebodyWhoDoesNotAdministerTheInstanceMayNotFoundIt() {
        var response = harness.request(
                client -> client.post(PATH, body("{\"name\": \"Meine\"}"), harness.as(signedIn(Set.of()))));

        assertTrue(response.code() >= 400);
        verify(service, never()).found(anyString(), anyInt());
    }
}
