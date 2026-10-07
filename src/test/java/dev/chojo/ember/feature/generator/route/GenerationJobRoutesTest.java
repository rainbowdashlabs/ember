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
import dev.chojo.ember.feature.generator.service.BulkGenerationService;
import dev.chojo.ember.feature.generator.service.BulkGenerationService.BulkPreviewResponse;
import dev.chojo.ember.feature.generator.service.BulkGenerationService.GenerationJobResponse;
import dev.chojo.ember.feature.generator.service.BulkGenerationService.GenerationJobSummary;
import dev.chojo.ember.feature.generator.service.BulkGenerationService.MemberSelection;
import dev.chojo.ember.feature.generator.service.DocumentIssuerService.IssuerChoice;
import dev.chojo.ember.feature.generator.service.DocumentTemplateService;
import dev.chojo.ember.owner.Owner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static dev.chojo.ember.api.RouteHarness.PREFIX;
import static dev.chojo.ember.api.RouteHarness.body;
import static dev.chojo.ember.api.RouteHarness.json;
import static dev.chojo.ember.api.RouteHarness.refusalOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Runs for many members over HTTP: they need the right to file member documents, and the template named
 * in the address has to be of the reader's station.
 */
class GenerationJobRoutesTest {
    private static final Owner.Station OWNER = new Owner.Station(3);
    private static final GenerationJobSummary SUMMARY =
            new GenerationJobSummary(5, 8, "Bescheinigung", "Nora Fülling", false, Instant.EPOCH, null, 2, 0, 0);
    private static final GenerationJobResponse JOB = new GenerationJobResponse(SUMMARY, List.of());

    private BulkGenerationService bulk;
    private RouteHarness harness;

    @BeforeEach
    void setup() {
        bulk = mock(BulkGenerationService.class);
        var templates = mock(DocumentTemplateService.class);
        when(templates.requireOwned(OWNER, 8)).thenReturn(DocumentTemplateRoutesTest.template(8));
        when(templates.requireOwned(OWNER, 9)).thenThrow(DocumentRefusal.DOCUMENT_TEMPLATE_NOT_HERE.raise());
        when(bulk.preview(any(), eq(8), any(), any())).thenReturn(new BulkPreviewResponse(2, 11, null, List.of()));
        when(bulk.start(any(), eq(8), any(), eq(true), any())).thenReturn(JOB);
        when(bulk.recent(any())).thenReturn(List.of(SUMMARY));
        when(bulk.job(3, 5)).thenReturn(JOB);
        when(bulk.job(3, 6)).thenThrow(DocumentRefusal.DOCUMENT_JOB_NOT_HERE.raise());
        harness = RouteHarness.serving(new GenerationJobRoutes(bulk, templates));
    }

    @Test
    void aManagerLooksStartsAndFollowsARun() {
        harness.run((server, client) -> {
            var filer = harness.as(TestSessions.member(3, StationPermission.DOCUMENT_EDIT_MEMBER));
            var preview = client.post(
                    PREFIX + "/document-generation/templates/8/jobs/preview", body("{\"memberIds\": [11, 12]}"), filer);
            assertEquals(2, json(preview).path("memberCount").asInt());
            var started = client.post(
                    PREFIX + "/document-generation/templates/8/jobs",
                    body("{\"audience\": {\"userTypes\": [], \"groupIds\": [4], \"tagIds\": [], \"memberIds\": [],"
                            + " \"mode\": \"AND\"}, \"acceptMissing\": true,"
                            + " \"issuer\": {\"memberId\": 14, \"function\": \"Kassenwart\"}}"),
                    filer);
            assertEquals(202, started.code());
            assertEquals(5, json(started).path("job").path("id").asInt());
            assertEquals(
                    "Bescheinigung",
                    json(client.get(PREFIX + "/document-generation/jobs", filer))
                            .path(0)
                            .path("templateName")
                            .asString());
            assertEquals(
                    200,
                    client.get(PREFIX + "/document-generation/jobs/5", filer).code());
            assertEquals(
                    DocumentRefusal.DOCUMENT_JOB_NOT_HERE,
                    refusalOf(client.get(PREFIX + "/document-generation/jobs/6", filer)));
            assertEquals(
                    DocumentRefusal.DOCUMENT_TEMPLATE_NOT_HERE,
                    refusalOf(client.post(
                            PREFIX + "/document-generation/templates/9/jobs", body("{\"memberIds\": [11]}"), filer)));
        });

        verify(bulk).preview(any(), eq(8), eq(new MemberSelection(List.of(11, 12), null)), isNull());
        verify(bulk).start(any(), eq(8), any(), eq(true), eq(new IssuerChoice(14, "Kassenwart")));
        verify(bulk, never()).start(any(), eq(9), any(), anyBoolean(), any());
    }

    @Test
    void aRunNeedsTheRightToFileMemberDocuments() {
        harness.run((server, client) -> {
            var reader = harness.as(TestSessions.member(3, StationPermission.DOCUMENT_READ_MEMBER));
            assertEquals(
                    403,
                    client.post(PREFIX + "/document-generation/templates/8/jobs", body("{}"), reader)
                            .code());
            assertEquals(
                    403,
                    client.get(PREFIX + "/document-generation/jobs", reader).code());
        });

        verify(bulk, never()).start(any(), anyInt(), any(), anyBoolean(), any());
    }
}
