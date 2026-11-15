/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.route;

import dev.chojo.ember.api.RouteHarness;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.refusal.EventRefusal;
import dev.chojo.ember.feature.events.entity.AppointmentOwner;
import dev.chojo.ember.feature.events.entity.EventAttachment;
import dev.chojo.ember.feature.events.entity.EventGroup;
import dev.chojo.ember.feature.events.service.EventAttachmentService;
import dev.chojo.ember.feature.events.service.EventGroupGuards;
import dev.chojo.ember.feature.events.service.EventGroupService;
import dev.chojo.ember.feature.media.service.MediaLibraryService;
import dev.chojo.ember.repository.EventGroupFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static dev.chojo.ember.api.RouteHarness.PREFIX;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EventGroupAttachmentRoutesTest {
    private static final int STATION_ID = 3;
    private static final int EVENT_GROUP_ID = 70;
    private static final String FILES = PREFIX + "/events/groups/" + EVENT_GROUP_ID + "/attachments";
    private static final EventGroup GROUP = EventGroupFixtures.eventGroup(EVENT_GROUP_ID, STATION_ID, "Jugendflamme");

    private EventAttachmentService attachments;
    private EventGroupGuards readGuards;
    private RouteHarness harness;

    @BeforeEach
    void serve() {
        attachments = mock(EventAttachmentService.class);
        readGuards = mock(EventGroupGuards.class);
        var eventGroups = mock(EventGroupService.class);
        when(eventGroups.requireEventGroup(STATION_ID, EVENT_GROUP_ID)).thenReturn(GROUP);
        when(readGuards.requireOpen(any(), anyInt())).thenReturn(GROUP);
        var file = new EventAttachment(
                9,
                EVENT_GROUP_ID,
                5,
                null,
                false,
                0,
                Instant.EPOCH,
                "plan.pdf",
                "application/pdf",
                10,
                "0".repeat(64),
                true);
        when(attachments.listForEventGroup(anyInt(), any())).thenReturn(List.of(file));
        when(attachments.findOfEventGroup(EVENT_GROUP_ID, 9)).thenReturn(Optional.of(file));
        when(attachments.detach(AppointmentOwner.EVENT_GROUP, 9)).thenReturn(true);
        when(attachments.attach(eq(AppointmentOwner.EVENT_GROUP), anyInt(), anyInt(), anyInt(), any(), anyBoolean()))
                .thenReturn(file);
        harness = RouteHarness.serving(
                new EventGroupAttachmentRoutes(attachments, eventGroups, readGuards, mock(MediaLibraryService.class)));
    }

    @Test
    void membersReadTheFilesOfAGroupTheyMayOpen() {
        assertEquals(
                200,
                harness.call(STATION_ID, "GET", FILES, null, StationPermission.USER)
                        .code());
        verify(readGuards).requireOpen(any(), anyInt());

        when(readGuards.requireOpen(any(), anyInt())).thenThrow(EventRefusal.EVENT_GROUP_NOT_HERE.raise());
        assertEquals(
                404,
                harness.call(STATION_ID, "GET", FILES, null, StationPermission.USER)
                        .code());
    }

    @Test
    void onlyWhoeverEditsAppointmentsAttachesAndTakesOff() {
        String attach = "{\"fileId\": 5, \"label\": null, \"internal\": false}";
        assertEquals(
                403,
                harness.call(STATION_ID, "POST", FILES, attach, StationPermission.USER)
                        .code());
        assertEquals(
                403,
                harness.call(STATION_ID, "DELETE", FILES + "/9", null, StationPermission.USER)
                        .code());
        verify(attachments, never()).detach(any(), anyInt());

        assertEquals(
                201,
                harness.call(STATION_ID, "POST", FILES, attach, StationPermission.EVENT_EDIT)
                        .code());
        verify(attachments).attach(AppointmentOwner.EVENT_GROUP, EVENT_GROUP_ID, STATION_ID, 5, null, false);
        assertEquals(
                204,
                harness.call(STATION_ID, "DELETE", FILES + "/9", null, StationPermission.EVENT_EDIT)
                        .code());
        assertEquals(
                404,
                harness.call(STATION_ID, "DELETE", FILES + "/10", null, StationPermission.EVENT_EDIT)
                        .code());
    }
}
