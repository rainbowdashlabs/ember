/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.page.service;

import dev.chojo.ember.feature.account.service.AvatarService;
import dev.chojo.ember.feature.content.service.CellDescriptions;
import dev.chojo.ember.feature.content.service.ContentBlockService;
import dev.chojo.ember.feature.form.entity.Form;
import dev.chojo.ember.feature.form.entity.FormVisibility;
import dev.chojo.ember.feature.media.service.MediaLibraryService;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.page.entity.PageUsingForm;
import dev.chojo.ember.feature.page.entity.PageVisibility;
import dev.chojo.ember.feature.page.repository.PageRepository;
import dev.chojo.ember.feature.station.repository.StationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * What the page editor asks the page service beside the pages themselves: which pages a form's
 * change strands, and how a member list cell resolves while it is being edited.
 */
class PageEditorLookupsTest {
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private PageRepository pages;
    private PageService service;

    @BeforeEach
    void setup() {
        pages = mock(PageRepository.class);
        service = new PageService(
                pages,
                mock(ContentBlockService.class),
                mock(MediaLibraryService.class),
                mock(CellDescriptions.class),
                mock(StationMemberRepository.class),
                mock(AvatarService.class),
                mock(StationRepository.class));
    }

    private static Form form(FormVisibility visibility, UUID publicUid) {
        var form = mock(Form.class);
        when(form.stationId()).thenReturn(3);
        when(form.visibility()).thenReturn(visibility);
        when(form.publicUid()).thenReturn(publicUid);
        return form;
    }

    @Test
    void closingAFormToItsLinkNamesThePagesItStands() {
        var uid = UUID.randomUUID();
        var holding = List.of(new PageUsingForm(1, "Start", PageVisibility.values()[0]));
        when(pages.findPagesEmbedding(3, uid.toString())).thenReturn(holding);

        assertEquals(holding, service.pagesStrandedBy(form(FormVisibility.PUBLIC, uid), FormVisibility.UNLISTED));
    }

    @Test
    void aFormStillListedOrNeverPublishedStrandsNothing() {
        assertTrue(service.pagesStrandedBy(form(FormVisibility.PUBLIC, UUID.randomUUID()), FormVisibility.PUBLIC)
                .isEmpty());
        assertTrue(service.pagesStrandedBy(form(FormVisibility.PUBLIC, null), FormVisibility.UNLISTED)
                .isEmpty());
        verify(pages, never()).findPagesEmbedding(anyInt(), anyString());
    }

    @Test
    void aHalfEditedMemberListStillResolves() {
        var cell = JSON.readTree("""
                {"sortBy": "SIDEWAYS", "memberDescriptions": {"a": "Leitung", "b": 3},
                 "memberOrder": ["a", 4, "b"]}""");

        assertTrue(service.resolveMemberList(3, cell).isEmpty());
        assertTrue(service.resolveMemberList(3, JSON.readTree("{\"sortBy\": \"ORDER\", \"memberOrder\": {}}"))
                .isEmpty());
    }
}
