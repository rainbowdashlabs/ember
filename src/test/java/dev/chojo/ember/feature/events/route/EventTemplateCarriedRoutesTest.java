/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.route;

import dev.chojo.ember.api.RouteHarness;
import dev.chojo.ember.api.TestSessions;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.refusal.EventRefusal;
import dev.chojo.ember.api.refusal.GeneralRefusal;
import dev.chojo.ember.feature.events.entity.AppointmentOwner;
import dev.chojo.ember.feature.events.entity.EventAttachment;
import dev.chojo.ember.feature.events.entity.EventFederationShare;
import dev.chojo.ember.feature.events.entity.EventTemplate;
import dev.chojo.ember.feature.events.service.EventAttachmentService;
import dev.chojo.ember.feature.events.service.EventFieldDefaultService;
import dev.chojo.ember.feature.events.service.EventTemplateService;
import dev.chojo.ember.feature.events.service.EventTemplateShareService;
import dev.chojo.ember.feature.federation.entity.ShareScope;
import dev.chojo.ember.feature.restriction.RestrictionMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static dev.chojo.ember.api.RouteHarness.PREFIX;
import static dev.chojo.ember.api.RouteHarness.body;
import static dev.chojo.ember.api.RouteHarness.json;
import static dev.chojo.ember.api.RouteHarness.refusalOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * What a template carries for its appointments, over HTTP: a template is answered for only at its
 * own station, an attachment only under the address of its own template, and a share is written
 * only with the right to share appointments.
 */
class EventTemplateCarriedRoutesTest {
    private static final int STATION = 3;
    private static final int TEMPLATE = 7;
    private static final int OTHER_TEMPLATE = 8;
    private static final int FOREIGN_TEMPLATE = 9;
    private static final int ATTACHMENT_OF_OTHER = 40;

    private EventAttachmentService attachments;
    private EventTemplateShareService shares;
    private RouteHarness harness;

    @BeforeEach
    void setup() {
        attachments = mock(EventAttachmentService.class);
        shares = mock(EventTemplateShareService.class);
        var templates = mock(EventTemplateService.class);
        when(templates.findById(TEMPLATE)).thenReturn(Optional.of(template(TEMPLATE, STATION)));
        when(templates.findById(OTHER_TEMPLATE)).thenReturn(Optional.of(template(OTHER_TEMPLATE, STATION)));
        when(templates.findById(FOREIGN_TEMPLATE)).thenReturn(Optional.of(template(FOREIGN_TEMPLATE, 4)));
        when(attachments.find(AppointmentOwner.TEMPLATE, ATTACHMENT_OF_OTHER))
                .thenReturn(Optional.of(attachmentOf(OTHER_TEMPLATE)));
        when(shares.set(eq(STATION), eq(TEMPLATE), eq(ShareScope.SPECIFIC), any()))
                .thenReturn(new EventTemplateShareService.TemplateShare(
                        new EventFederationShare(1, TEMPLATE, ShareScope.SPECIFIC), List.of(21)));
        harness = RouteHarness.serving(
                new EventTemplateAttachmentRoutes(attachments, templates),
                new EventTemplateSharingRoutes(templates, mock(EventFieldDefaultService.class), shares));
    }

    private static EventTemplate template(int id, int stationId) {
        return new EventTemplate(
                id,
                stationId,
                "Vorlage",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                RestrictionMode.AND,
                null,
                null,
                null,
                null,
                null);
    }

    private static EventAttachment attachmentOf(int templateId) {
        return new EventAttachment(
                ATTACHMENT_OF_OTHER,
                templateId,
                5,
                null,
                false,
                0,
                Instant.EPOCH,
                "plan.pdf",
                "application/pdf",
                10,
                "0".repeat(64));
    }

    @Test
    void aTemplateOfAnotherStationIsNotHere() {
        harness.run((server, client) -> {
            var manager = harness.as(TestSessions.member(
                    STATION, StationPermission.EVENT_MANAGE_TEMPLATE, StationPermission.EVENTS_FEDERATE));
            assertEquals(
                    404,
                    client.get(PREFIX + "/event-templates/" + FOREIGN_TEMPLATE + "/attachments", manager)
                            .code());
            assertEquals(
                    404,
                    client.put(
                                    PREFIX + "/event-templates/" + FOREIGN_TEMPLATE + "/federation",
                                    body("{\"scope\": \"ALL_PARTNERS\"}"),
                                    manager)
                            .code());
            assertEquals(
                    404,
                    client.get(PREFIX + "/event-templates/" + FOREIGN_TEMPLATE + "/field-defaults", manager)
                            .code());
        });

        verify(attachments, never()).listAll(any(), anyInt());
        verify(shares, never()).set(anyInt(), anyInt(), any(), any());
    }

    /** An attachment of one template cannot be written through the address of another. */
    @Test
    void anAttachmentIsWrittenOnlyUnderItsOwnTemplate() {
        harness.run((server, client) -> {
            var manager = harness.as(TestSessions.member(STATION, StationPermission.EVENT_MANAGE_TEMPLATE));
            String foreignAddress = PREFIX + "/event-templates/" + TEMPLATE + "/attachments/" + ATTACHMENT_OF_OTHER;

            var renamed = client.put(foreignAddress, body("{\"label\": \"Neu\"}"), manager);
            assertEquals(404, renamed.code());
            assertEquals(
                    EventRefusal.EVENT_FILE_NOT_HERE_ON_WRITE.code(),
                    refusalOf(renamed).code());

            var detached = client.delete(foreignAddress, null, manager);
            assertEquals(404, detached.code());
            assertEquals(
                    EventRefusal.EVENT_FILE_NOT_HERE_ON_WRITE.code(),
                    refusalOf(detached).code());
        });

        verify(attachments, never()).update(any(), anyInt(), any(), anyBoolean());
        verify(attachments, never()).detach(any(), anyInt());
    }

    @Test
    void writingAShareNeedsTheRightToShareAppointments() {
        harness.run((server, client) -> {
            var manager = harness.as(TestSessions.member(STATION, StationPermission.EVENT_MANAGE_TEMPLATE));
            String address = PREFIX + "/event-templates/" + TEMPLATE + "/federation";

            var shared = client.put(address, body("{\"scope\": \"ALL_PARTNERS\"}"), manager);
            assertEquals(403, shared.code());
            assertEquals(
                    GeneralRefusal.ROUTE_PERMISSION_MISSING.code(),
                    refusalOf(shared).code());

            var removed = client.delete(address, null, manager);
            assertEquals(403, removed.code());
            assertEquals(
                    GeneralRefusal.ROUTE_PERMISSION_MISSING.code(),
                    refusalOf(removed).code());
        });

        verify(shares, never()).set(anyInt(), anyInt(), any(), any());
        verify(shares, never()).remove(anyInt());
    }

    /** The answer names the partners the share kept, which is what the editor shows afterwards. */
    @Test
    void aShareIsAnsweredWithThePartnersItKept() {
        harness.run((server, client) -> {
            var manager = harness.as(TestSessions.member(
                    STATION, StationPermission.EVENT_MANAGE_TEMPLATE, StationPermission.EVENTS_FEDERATE));
            var answer = json(client.put(
                    PREFIX + "/event-templates/" + TEMPLATE + "/federation",
                    body("{\"scope\": \"SPECIFIC\", \"partnerIds\": [21, 99]}"),
                    manager));
            assertEquals("SPECIFIC", answer.path("scope").asString());
            assertEquals(1, answer.path("partnerIds").size());
            assertEquals(21, answer.path("partnerIds").path(0).asInt());
        });

        verify(shares).set(STATION, TEMPLATE, ShareScope.SPECIFIC, List.of(21, 99));
    }
}
