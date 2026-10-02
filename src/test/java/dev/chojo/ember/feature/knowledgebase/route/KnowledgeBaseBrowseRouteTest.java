/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.knowledgebase.route;

import dev.chojo.ember.api.RouteHarness;
import dev.chojo.ember.api.TestSessions;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.knowledgebase.entity.KbAccessLevel;
import dev.chojo.ember.feature.knowledgebase.service.KbAccessService;
import dev.chojo.ember.feature.knowledgebase.service.KbAuthorNameService;
import dev.chojo.ember.feature.knowledgebase.service.KbBrowseService;
import dev.chojo.ember.feature.knowledgebase.service.KbBrowseService.BrowseResponse;
import dev.chojo.ember.feature.knowledgebase.service.KbBrowseService.Reach;
import dev.chojo.ember.feature.knowledgebase.service.KbBulkService;
import dev.chojo.ember.feature.knowledgebase.service.KbContentService;
import dev.chojo.ember.feature.knowledgebase.service.KbFilePictureService;
import dev.chojo.ember.feature.knowledgebase.service.KbIconService;
import dev.chojo.ember.feature.knowledgebase.service.KbImageService;
import dev.chojo.ember.feature.knowledgebase.service.KbMoveService;
import dev.chojo.ember.feature.knowledgebase.service.KbPdfExportService;
import dev.chojo.ember.feature.knowledgebase.service.KbPresentationService;
import dev.chojo.ember.feature.knowledgebase.service.KbSearchService;
import dev.chojo.ember.feature.knowledgebase.service.KbTrashService;
import dev.chojo.ember.feature.knowledgebase.service.KnowledgeBaseFederationService;
import dev.chojo.ember.feature.knowledgebase.service.KnowledgeBaseService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static dev.chojo.ember.api.RouteHarness.PREFIX;
import static dev.chojo.ember.api.RouteHarness.json;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KnowledgeBaseBrowseRouteTest {
    @Test
    void aLevelIsBrowsedWithTheReadersAccessAndWhetherTheyManage() {
        var browse = mock(KbBrowseService.class);
        var reach = new Reach(Set.of(), Set.of(), Set.of());
        when(browse.browse(anyInt(), any(), any(), anyBoolean()))
                .thenReturn(new BrowseResponse(
                        null, List.of(), List.of(), KbAccessLevel.READ, Map.of(), Map.of(), reach, reach));
        var harness = RouteHarness.serving(new KnowledgeBaseRoutes(
                mock(KnowledgeBaseService.class),
                mock(KbContentService.class),
                mock(KbSearchService.class),
                mock(KbAccessService.class),
                mock(KbPresentationService.class),
                mock(KbAuthorNameService.class),
                mock(KnowledgeBaseFederationService.class),
                mock(KbIconService.class),
                mock(KbImageService.class),
                mock(KbFilePictureService.class),
                mock(KbPdfExportService.class),
                mock(KbMoveService.class),
                mock(KbBulkService.class),
                mock(KbTrashService.class),
                browse));

        var answer = harness.request(client -> client.get(
                PREFIX + "/kb/browse?folderId=9",
                harness.as(TestSessions.member(3, StationPermission.USER, StationPermission.KNOWLEDGE_MANAGER))));

        assertEquals("READ", json(answer).path("currentLevel").asString());
        verify(browse).browse(eq(3), eq(9), any(), eq(true));
    }
}
