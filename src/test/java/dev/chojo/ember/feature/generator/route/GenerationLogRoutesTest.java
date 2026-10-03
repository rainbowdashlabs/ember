/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.route;

import dev.chojo.ember.api.RouteHarness;
import dev.chojo.ember.api.TestSessions;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.generator.service.GenerationLogService;
import dev.chojo.ember.feature.generator.service.GenerationLogService.GeneratedDocumentEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static dev.chojo.ember.api.RouteHarness.PREFIX;
import static dev.chojo.ember.api.RouteHarness.json;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The list of generated documents over HTTP: it says who has which document, so it needs the right to
 * read the documents of members, and it lists the reader's own station.
 */
class GenerationLogRoutesTest {
    private static final GeneratedDocumentEntry ENTRY = new GeneratedDocumentEntry(
            7, Instant.EPOCH, 8, "Bescheinigung", 2, true, 11, "Erika Muster", "Nora Fülling", false, 21);

    private GenerationLogService log;
    private RouteHarness harness;

    @BeforeEach
    void setup() {
        log = mock(GenerationLogService.class);
        when(log.list(3)).thenReturn(List.of(ENTRY));
        harness = RouteHarness.serving(new GenerationLogRoutes(log));
    }

    @Test
    void aReaderOfMemberDocumentsSeesTheStationsLog() {
        harness.run((server, client) -> {
            var reader = harness.as(TestSessions.member(3, StationPermission.DOCUMENT_READ_MEMBER));
            var first = json(client.get(PREFIX + "/document-generation/log", reader))
                    .path(0);
            assertEquals("Bescheinigung", first.path("templateName").asString());
            assertEquals("Erika Muster", first.path("memberName").asString());
            assertEquals(true, first.path("ofAssociation").asBoolean());
        });
    }

    @Test
    void theLogNeedsTheRightToReadMemberDocuments() {
        harness.run((server, client) -> {
            var store = harness.as(TestSessions.member(3, StationPermission.DOCUMENT_READ));
            assertEquals(
                    403, client.get(PREFIX + "/document-generation/log", store).code());
        });

        verify(log, never()).list(anyInt());
    }
}
