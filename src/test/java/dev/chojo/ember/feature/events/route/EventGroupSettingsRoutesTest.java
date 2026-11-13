/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.route;

import dev.chojo.ember.api.RouteHarness;
import dev.chojo.ember.api.TestSessions;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.events.entity.EventFieldDefault;
import dev.chojo.ember.feature.events.entity.EventGroupPartSettings;
import dev.chojo.ember.feature.events.entity.EventGroupSettings;
import dev.chojo.ember.feature.events.service.EventGroupSettingsService;
import io.javalin.testtools.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static dev.chojo.ember.api.RouteHarness.PREFIX;
import static dev.chojo.ember.api.RouteHarness.body;
import static dev.chojo.ember.api.RouteHarness.json;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EventGroupSettingsRoutesTest {
    private static final int STATION_ID = 3;
    private static final int EVENT_GROUP_ID = 70;
    private static final String SETTINGS = PREFIX + "/events/groups/" + EVENT_GROUP_ID + "/settings";

    private EventGroupSettingsService settings;
    private RouteHarness harness;

    @BeforeEach
    void serve() {
        settings = mock(EventGroupSettingsService.class);
        var stored = new EventGroupSettings(
                5, 6, List.of(2), List.of(new EventFieldDefault(EVENT_GROUP_ID, 9, "VALUE", "x")));
        when(settings.find(STATION_ID, EVENT_GROUP_ID)).thenReturn(stored);
        when(settings.save(anyInt(), anyInt(), any())).thenReturn(stored);
        when(settings.ofPart(STATION_ID, 11))
                .thenReturn(Optional.of(new EventGroupPartSettings(EVENT_GROUP_ID, null, 8, stored)));
        when(settings.ofPart(STATION_ID, 12)).thenReturn(Optional.empty());
        harness = RouteHarness.serving(new EventGroupSettingsRoutes(settings));
    }

    private Response call(String method, String path, String json, StationPermission... permissions) {
        var session = harness.as(TestSessions.member(STATION_ID, permissions));
        return harness.request(client -> switch (method) {
            case "GET" -> client.get(path, session);
            default -> client.put(path, body(json), session);
        });
    }

    @Test
    void onlyWhoeverEditsAppointmentsSetsWhatAGroupHandsDown() {
        String wanted = "{\"templateId\": 5, \"categoryId\": null, \"reminderDays\": [2],"
                + " \"fieldDefaults\": [{\"fieldId\": 9, \"source\": \"VALUE\", \"value\": \"x\"}]}";
        assertEquals(403, call("PUT", SETTINGS, wanted, StationPermission.USER).code());
        assertEquals(403, call("GET", SETTINGS, null, StationPermission.USER).code());
        verify(settings, never()).save(anyInt(), anyInt(), any());

        assertEquals(
                200, call("PUT", SETTINGS, wanted, StationPermission.EVENT_EDIT).code());
        verify(settings)
                .save(
                        STATION_ID,
                        EVENT_GROUP_ID,
                        new EventGroupSettings(
                                5, null, List.of(2), List.of(new EventFieldDefault(EVENT_GROUP_ID, 9, "VALUE", "x"))));

        var read = json(call("GET", SETTINGS, null, StationPermission.EVENT_EDIT));
        assertEquals(5, read.get("templateId").asInt());
        assertEquals(2, read.get("reminderDays").get(0).asInt());
    }

    @Test
    void aPartSaysWhatItSetsItself() {
        var part = json(call("GET", PREFIX + "/events/11/group-settings", null, StationPermission.EVENT_EDIT));
        assertEquals(8, part.get("ownCategoryId").asInt());
        assertEquals(5, part.get("eventGroup").get("templateId").asInt());

        assertEquals(
                204,
                call("GET", PREFIX + "/events/12/group-settings", null, StationPermission.EVENT_EDIT)
                        .code());
    }
}
