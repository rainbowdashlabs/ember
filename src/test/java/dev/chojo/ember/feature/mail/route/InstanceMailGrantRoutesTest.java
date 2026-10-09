/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.mail.route;

import dev.chojo.ember.api.RegisteredRoutes;
import dev.chojo.ember.api.RouteHarness;
import dev.chojo.ember.api.TestSessions;
import dev.chojo.ember.api.auth.InstancePermission;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.auth.StepUpCategory;
import dev.chojo.ember.api.refusal.SystemRefusal;
import dev.chojo.ember.feature.mail.entity.InstanceMailStation;
import dev.chojo.ember.feature.mail.service.InstanceMailGrantService;
import io.javalin.http.HandlerType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static dev.chojo.ember.api.RouteHarness.PREFIX;
import static dev.chojo.ember.api.RouteHarness.body;
import static dev.chojo.ember.api.RouteHarness.json;
import static dev.chojo.ember.api.RouteHarness.refusalOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Granting stations the instance's mail providers, over HTTP: only an instance administrator may,
 * and every change asks for a fresh second factor.
 */
class InstanceMailGrantRoutesTest {
    private static final UUID NORD = UUID.fromString("00000000-0000-0000-0000-000000000004");
    private static final UUID SUED = UUID.fromString("00000000-0000-0000-0000-000000000005");
    private static final InstanceMailStation GRANTED =
            new InstanceMailStation(NORD, "Nord", true, Instant.EPOCH, 30, 2);

    private InstanceMailGrantService grants;
    private RouteHarness harness;

    @BeforeEach
    void setup() {
        grants = mock(InstanceMailGrantService.class);
        when(grants.stations()).thenReturn(List.of(GRANTED));
        when(grants.station(NORD)).thenReturn(GRANTED);
        when(grants.grant(NORD, 30)).thenReturn(GRANTED);
        when(grants.withdraw(NORD)).thenReturn(new InstanceMailStation(NORD, "Nord", false, null, null, 2));
        when(grants.grantAll(List.of(NORD, SUED), null)).thenReturn(List.of(GRANTED));
        when(grants.withdrawAll(List.of(NORD))).thenReturn(List.of());
        harness = RouteHarness.serving(new InstanceMailGrantRoutes(grants));
    }

    @Test
    void anAdministratorGrantsChangesAndWithdrawsOneStation() {
        var admin = harness.as(TestSessions.administrator());
        String path = PREFIX + "/admin/config/mailing/stations/" + NORD;

        harness.run((server, client) -> {
            assertEquals(
                    "Nord",
                    json(client.get(PREFIX + "/admin/config/mailing/stations", admin))
                            .get(0)
                            .path("name")
                            .asString());
            assertEquals(30, json(client.get(path, admin)).path("dailyLimit").asInt());
            assertEquals(
                    30,
                    json(client.put(path, body("{\"dailyLimit\": 30}"), admin))
                            .path("dailyLimit")
                            .asInt());
            assertEquals(
                    false,
                    json(client.delete(path, null, admin)).path("granted").asBoolean());
        });

        verify(grants).grant(NORD, 30);
        verify(grants).withdraw(NORD);
    }

    @Test
    void anAdministratorGrantsAndWithdrawsSeveralAtOnce() {
        var admin = harness.as(TestSessions.administrator());

        harness.run((server, client) -> {
            assertEquals(
                    200,
                    client.post(
                                    PREFIX + "/admin/config/mailing/stations/grant",
                                    body("{\"stationUids\": [\"%s\", \"%s\"], \"dailyLimit\": null}"
                                            .formatted(NORD, SUED)),
                                    admin)
                            .code());
            assertEquals(
                    200,
                    client.post(
                                    PREFIX + "/admin/config/mailing/stations/withdraw",
                                    body("{\"stationUids\": [\"%s\"]}".formatted(NORD)),
                                    admin)
                            .code());
        });

        verify(grants).grantAll(List.of(NORD, SUED), null);
        verify(grants).withdrawAll(List.of(NORD));
    }

    @Test
    void aStationManagerMayNotGrantItself() {
        var manager = harness.as(TestSessions.member(4, StationPermission.STATION_MAIL));

        var answer = harness.request(client ->
                client.put(PREFIX + "/admin/config/mailing/stations/" + NORD, body("{\"dailyLimit\": null}"), manager));

        assertEquals(403, answer.code());
    }

    @Test
    void aLimitBelowOneIsRefused() {
        doThrow(SystemRefusal.INSTANCE_MAIL_LIMIT_NOT_POSITIVE.raise())
                .when(grants)
                .grant(NORD, 0);

        var answer = harness.request(client -> client.put(
                PREFIX + "/admin/config/mailing/stations/" + NORD,
                body("{\"dailyLimit\": 0}"),
                harness.as(TestSessions.administrator())));

        assertEquals(SystemRefusal.INSTANCE_MAIL_LIMIT_NOT_POSITIVE, refusalOf(answer));
    }

    /**
     * Reading is an administrator's everyday business; every change is a change to how the instance
     * sends, and asks for the second factor the instance's configuration asks for.
     */
    @Test
    void everyChangeAsksForAFreshSecondFactorAndReadingDoesNot() {
        var routes = RegisteredRoutes.application().routes().stream()
                .filter(route -> route.owner() == InstanceMailGrantRoutes.class)
                .toList();

        assertEquals(6, routes.size());
        for (var route : routes) {
            assertTrue(route.roles().contains(InstancePermission.ADMINISTRATOR), route::toString);
            boolean changes = route.method() != HandlerType.GET;
            assertEquals(changes, route.roles().contains(StepUpCategory.INSTANCE_CONFIG), route::toString);
        }
    }
}
