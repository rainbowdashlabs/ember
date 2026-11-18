/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.route;

import dev.chojo.ember.api.RouteHarness;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.events.entity.EventFieldDefault;
import dev.chojo.ember.feature.events.entity.EventGroupPartSettings;
import dev.chojo.ember.feature.events.entity.EventGroupSettings;
import dev.chojo.ember.feature.events.service.EventGroupRegistrationService;
import dev.chojo.ember.feature.events.service.EventGroupSettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static dev.chojo.ember.api.RouteHarness.PREFIX;
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
    private EventGroupRegistrationService signUps;

    @BeforeEach
    void serve() {
        settings = mock(EventGroupSettingsService.class);
        var stored = new EventGroupSettings(
                5, 6, List.of(2), List.of(new EventFieldDefault(EVENT_GROUP_ID, 9, "VALUE", "x")));
        when(settings.find(STATION_ID, EVENT_GROUP_ID)).thenReturn(stored);
        when(settings.save(anyInt(), anyInt(), any())).thenReturn(stored);
        when(settings.ofPart(STATION_ID, 11))
                .thenReturn(Optional.of(new EventGroupPartSettings(EVENT_GROUP_ID, null, 8, stored, null, null)));
        when(settings.ofPart(STATION_ID, 12)).thenReturn(Optional.empty());
        signUps = mock(EventGroupRegistrationService.class);
        harness = RouteHarness.serving(new EventGroupSettingsRoutes(settings, signUps));
    }

    @Test
    void onlyWhoeverEditsAppointmentsSetsWhatAGroupHandsDown() {
        String wanted = "{\"templateId\": 5, \"categoryId\": null, \"reminderDays\": [2],"
                + " \"fieldDefaults\": [{\"fieldId\": 9, \"source\": \"VALUE\", \"value\": \"x\"}]}";
        assertEquals(
                403,
                harness.call(STATION_ID, "PUT", SETTINGS, wanted, StationPermission.USER)
                        .code());
        assertEquals(
                403,
                harness.call(STATION_ID, "GET", SETTINGS, null, StationPermission.USER)
                        .code());
        verify(settings, never()).save(anyInt(), anyInt(), any());

        assertEquals(
                200,
                harness.call(STATION_ID, "PUT", SETTINGS, wanted, StationPermission.EVENT_EDIT)
                        .code());
        verify(settings)
                .save(
                        STATION_ID,
                        EVENT_GROUP_ID,
                        new EventGroupSettings(
                                5, null, List.of(2), List.of(new EventFieldDefault(EVENT_GROUP_ID, 9, "VALUE", "x"))));

        var read = json(harness.call(STATION_ID, "GET", SETTINGS, null, StationPermission.EVENT_EDIT));
        assertEquals(5, read.get("templateId").asInt());
        assertEquals(2, read.get("reminderDays").get(0).asInt());
    }

    @Test
    void aPartSaysWhatItSetsItself() {
        var part = json(harness.call(
                STATION_ID, "GET", PREFIX + "/events/11/group-settings", null, StationPermission.EVENT_EDIT));
        assertEquals(8, part.get("ownCategoryId").asInt());
        assertEquals(5, part.get("eventGroup").get("templateId").asInt());

        assertEquals(
                204,
                harness.call(
                                STATION_ID,
                                "GET",
                                PREFIX + "/events/12/group-settings",
                                null,
                                StationPermission.EVENT_EDIT)
                        .code());
    }

    @Test
    void onlyWhoeverEditsAppointmentsGivesAPartPlacesOfItsOwn() {
        var stored = new EventGroupSettings(5, 6, List.of(2), List.of());
        when(settings.setPartLimit(STATION_ID, 11, 4))
                .thenReturn(new EventGroupPartSettings(EVENT_GROUP_ID, null, 8, stored, 4, 10));
        String path = PREFIX + "/events/11/group-settings/limit";

        assertEquals(
                403,
                harness.call(STATION_ID, "PUT", path, "{\"limit\": 4}", StationPermission.USER)
                        .code());
        verify(settings, never()).setPartLimit(anyInt(), anyInt(), any());

        var part = json(harness.call(STATION_ID, "PUT", path, "{\"limit\": 4}", StationPermission.EVENT_EDIT));
        assertEquals(4, part.get("ownRegistrationLimit").asInt());
        assertEquals(10, part.get("partRegistrationLimit").asInt());
        verify(signUps).moveUp(STATION_ID, EVENT_GROUP_ID);
    }
}
