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
import dev.chojo.ember.feature.events.entity.EventFieldDefault;
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
    private static final int OWN_ATTACHMENT = 41;

    private EventAttachmentService attachments;
    private EventTemplateShareService shares;
    private EventFieldDefaultService fieldDefaults;
    private RouteHarness harness;

    @BeforeEach
    void setup() {
        attachments = mock(EventAttachmentService.class);
        shares = mock(EventTemplateShareService.class);
        fieldDefaults = mock(EventFieldDefaultService.class);
        var templates = mock(EventTemplateService.class);
        when(templates.findById(TEMPLATE)).thenReturn(Optional.of(template(TEMPLATE, STATION)));
        when(templates.findById(OTHER_TEMPLATE)).thenReturn(Optional.of(template(OTHER_TEMPLATE, STATION)));
        when(templates.findById(FOREIGN_TEMPLATE)).thenReturn(Optional.of(template(FOREIGN_TEMPLATE, 4)));
        when(attachments.find(AppointmentOwner.TEMPLATE, ATTACHMENT_OF_OTHER))
                .thenReturn(Optional.of(attachmentOf(ATTACHMENT_OF_OTHER, OTHER_TEMPLATE)));
        when(attachments.find(AppointmentOwner.TEMPLATE, OWN_ATTACHMENT))
                .thenReturn(Optional.of(attachmentOf(OWN_ATTACHMENT, TEMPLATE)));
        when(attachments.listAll(AppointmentOwner.TEMPLATE, TEMPLATE))
                .thenReturn(List.of(attachmentOf(OWN_ATTACHMENT, TEMPLATE)));
        when(attachments.attach(eq(AppointmentOwner.TEMPLATE), eq(TEMPLATE), eq(STATION), eq(5), any(), anyBoolean()))
                .thenReturn(attachmentOf(OWN_ATTACHMENT, TEMPLATE));
        when(attachments.update(eq(AppointmentOwner.TEMPLATE), eq(OWN_ATTACHMENT), any(), anyBoolean()))
                .thenReturn(true);
        when(attachments.detach(AppointmentOwner.TEMPLATE, OWN_ATTACHMENT)).thenReturn(true);
        when(fieldDefaults.find(AppointmentOwner.TEMPLATE, TEMPLATE))
                .thenReturn(List.of(new EventFieldDefault(TEMPLATE, 12, "VALUE", "Greta")));
        when(shares.find(TEMPLATE))
                .thenReturn(Optional.of(new EventTemplateShareService.TemplateShare(
                        new EventFederationShare(1, TEMPLATE, ShareScope.SPECIFIC), List.of(21))));
        when(shares.set(eq(STATION), eq(TEMPLATE), eq(ShareScope.SPECIFIC), any()))
                .thenReturn(new EventTemplateShareService.TemplateShare(
                        new EventFederationShare(1, TEMPLATE, ShareScope.SPECIFIC), List.of(21)));
        harness = RouteHarness.serving(
                new EventTemplateAttachmentRoutes(attachments, templates),
                new EventTemplateSharingRoutes(templates, fieldDefaults, shares));
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

    private static EventAttachment attachmentOf(int id, int templateId) {
        return new EventAttachment(
                id, templateId, 5, null, false, 0, Instant.EPOCH, "plan.pdf", "application/pdf", 10, "0".repeat(64));
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

    /** A manager lists, attaches, renames, orders and removes the files of their own template. */
    @Test
    void aManagerWritesTheFilesOfTheirOwnTemplate() {
        harness.run((server, client) -> {
            var manager = harness.as(TestSessions.member(STATION, StationPermission.EVENT_MANAGE_TEMPLATE));
            String base = PREFIX + "/event-templates/" + TEMPLATE + "/attachments";
            assertEquals(
                    OWN_ATTACHMENT,
                    json(client.get(base, manager)).path(0).path("id").asInt());
            assertEquals(
                    201,
                    client.post(base, body("{\"fileId\": 5, \"label\": \"Plan\", \"internal\": true}"), manager)
                            .code());
            assertEquals(
                    204,
                    client.put(base + "/" + OWN_ATTACHMENT, body("{\"label\": \"Neu\"}"), manager)
                            .code());
            assertEquals(
                    204,
                    client.put(base + "/order", body("{\"attachmentIds\": [41]}"), manager)
                            .code());
            assertEquals(
                    204,
                    client.delete(base + "/" + OWN_ATTACHMENT, null, manager).code());
            assertEquals(400, client.post(base, body("{}"), manager).code());
        });

        verify(attachments).attach(AppointmentOwner.TEMPLATE, TEMPLATE, STATION, 5, "Plan", true);
        verify(attachments).update(AppointmentOwner.TEMPLATE, OWN_ATTACHMENT, "Neu", false);
        verify(attachments).reorder(AppointmentOwner.TEMPLATE, TEMPLATE, List.of(OWN_ATTACHMENT));
        verify(attachments).detach(AppointmentOwner.TEMPLATE, OWN_ATTACHMENT);
    }

    /** Whoever makes appointments from a template reads what it carries; a manager writes it. */
    @Test
    void fieldDefaultsAndTheShareAreReadAndWritten() {
        harness.run((server, client) -> {
            var editor = harness.as(TestSessions.member(STATION, StationPermission.EVENT_EDIT));
            String base = PREFIX + "/event-templates/" + TEMPLATE;
            assertEquals(
                    "Greta",
                    json(client.get(base + "/field-defaults", editor))
                            .path(0)
                            .path("value")
                            .asString());
            var share = json(client.get(base + "/federation", editor));
            assertEquals(true, share.path("shared").asBoolean());
            assertEquals(21, share.path("partnerIds").path(0).asInt());
            assertEquals(
                    false,
                    json(client.get(PREFIX + "/event-templates/" + OTHER_TEMPLATE + "/federation", editor))
                            .path("shared")
                            .asBoolean());

            var manager = harness.as(TestSessions.member(
                    STATION, StationPermission.EVENT_MANAGE_TEMPLATE, StationPermission.EVENTS_FEDERATE));
            assertEquals(
                    200,
                    client.put(
                                    base + "/field-defaults",
                                    body("[{\"fieldId\": 12, \"source\": \"VALUE\", \"value\": \"Greta\"}]"),
                                    manager)
                            .code());
            assertEquals(204, client.delete(base + "/federation", null, manager).code());
        });

        verify(fieldDefaults)
                .set(
                        AppointmentOwner.TEMPLATE,
                        TEMPLATE,
                        List.of(new EventFieldDefault(TEMPLATE, 12, "VALUE", "Greta")));
        verify(shares).remove(TEMPLATE);
    }
}
