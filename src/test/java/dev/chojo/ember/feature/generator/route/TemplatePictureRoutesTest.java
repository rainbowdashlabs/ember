/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.route;

import dev.chojo.ember.api.RouteHarness;
import dev.chojo.ember.api.TestSessions;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.refusal.DocumentRefusal;
import dev.chojo.ember.api.refusal.EventRefusal;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.route.EventVisibility;
import dev.chojo.ember.feature.generator.entity.DocumentTemplateKind;
import dev.chojo.ember.feature.generator.entity.RequiredTemplate;
import dev.chojo.ember.feature.generator.service.EventRequirementService;
import dev.chojo.ember.feature.generator.service.TemplatePictureService;
import dev.chojo.ember.feature.media.entity.MediaContent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static dev.chojo.ember.api.RouteHarness.PREFIX;
import static dev.chojo.ember.api.RouteHarness.refusalOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Whoever sees an appointment sees the pictures of the documents it asks for, and of no other
 * template.
 */
class TemplatePictureRoutesTest {
    private RouteHarness harness;

    @BeforeEach
    void setup() {
        var pictures = mock(TemplatePictureService.class);
        var visibility = mock(EventVisibility.class);
        var requirements = mock(EventRequirementService.class);
        var event = mock(StationEvent.class);
        when(event.id()).thenReturn(5);
        when(visibility.requireVisibleEvent(any(), eq(5))).thenReturn(event);
        when(visibility.requireVisibleEvent(any(), eq(6))).thenThrow(EventRefusal.EVENT_NOT_YOURS_TO_SEE.raise());
        when(requirements.forEvent(5))
                .thenReturn(List.of(
                        new RequiredTemplate(8, "Einverständnis", DocumentTemplateKind.PDF, 1, false, null),
                        new RequiredTemplate(9, "Alt", DocumentTemplateKind.PDF, 1, true, null)));
        when(pictures.forStation(anyInt(), eq(8), anyInt()))
                .thenReturn(Optional.of(new MediaContent(new byte[] {1}, "image/png")));
        harness = RouteHarness.serving(new TemplatePictureRoutes(pictures, visibility, requirements));
    }

    @Test
    void aMemberSeesThePictureOfADocumentTheAppointmentAsksFor() {
        harness.run((server, client) -> {
            var member = harness.as(TestSessions.member(3, StationPermission.USER));
            String base = PREFIX + "/events/5/documents-to-bring/";

            assertEquals(200, client.get(base + "8/picture?size=128", member).code());
            assertEquals(
                    DocumentRefusal.DOCUMENT_REQUIREMENT_NOT_REQUIRED,
                    refusalOf(client.get(base + "7/picture", member)));
            assertEquals(
                    DocumentRefusal.DOCUMENT_REQUIREMENT_NOT_REQUIRED,
                    refusalOf(client.get(base + "9/picture", member)),
                    "an archived document is no longer asked for");
            assertEquals(
                    EventRefusal.EVENT_NOT_YOURS_TO_SEE,
                    refusalOf(client.get(PREFIX + "/events/6/documents-to-bring/8/picture", member)));
        });
    }
}
