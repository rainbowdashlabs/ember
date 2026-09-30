/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.knowledgebase.service;

import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.knowledgebase.entity.KbAccessGrant;
import dev.chojo.ember.feature.knowledgebase.entity.KbAccessLevel;
import dev.chojo.ember.feature.knowledgebase.entity.KbFile;
import dev.chojo.ember.feature.knowledgebase.entity.KbFolder;
import dev.chojo.ember.feature.knowledgebase.entity.PublicKbMode;
import dev.chojo.ember.feature.knowledgebase.service.KbAccessService.ChildLevels;
import dev.chojo.ember.feature.knowledgebase.service.KbAccessService.MemberAccess;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.service.StationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class KbBrowseServiceTest {
    private static final MemberAccess READER =
            new MemberAccess(11, StationUserType.MEMBER, List.of(), List.of(), false, false);

    private KnowledgeBaseService knowledgeBase;
    private KbAccessService access;
    private KnowledgeBaseFederationService federation;
    private StationService stations;
    private KbBrowseService browse;

    private static KbFolder folder(int id) {
        var folder = mock(KbFolder.class);
        when(folder.id()).thenReturn(id);
        return folder;
    }

    private static KbFile file(int id) {
        var file = mock(KbFile.class);
        when(file.id()).thenReturn(id);
        return file;
    }

    @BeforeEach
    void setup() {
        knowledgeBase = mock(KnowledgeBaseService.class);
        access = mock(KbAccessService.class);
        federation = mock(KnowledgeBaseFederationService.class);
        stations = mock(StationService.class);
        browse = new KbBrowseService(knowledgeBase, access, federation, stations);
        when(access.childLevels(any(), any(), any(), any())).thenReturn(new ChildLevels(Map.of(), Map.of()));
        when(access.findRestrictions(any(), any())).thenReturn(List.of());
        when(federation.narrowlyShared(anyInt(), anyBoolean())).thenReturn(Set.of());
        when(federation.broadlyShared(anyInt(), anyBoolean())).thenReturn(Set.of());
    }

    @Test
    void aReaderSeesOnlyWhatTheirAccessReaches() {
        var open = folder(1);
        var closed = folder(2);
        var readable = file(5);
        var hidden = file(6);
        when(knowledgeBase.findFolders(3, null)).thenReturn(List.of(open, closed));
        when(knowledgeBase.findFiles(3, null)).thenReturn(List.of(readable, hidden));
        when(access.canAccess(READER, 1, null)).thenReturn(true);
        when(access.canAccess(READER, null, 5)).thenReturn(true);
        when(access.effectiveLevel(READER, null, null)).thenReturn(KbAccessLevel.READ);

        var level = browse.browse(3, null, READER, false);

        assertEquals(List.of(open), level.folders());
        assertEquals(1, level.files().size());
        assertEquals(KbAccessLevel.READ, level.currentLevel());
        assertNull(level.currentFolder());
    }

    @Test
    void aManagerSeesEverythingAndEachEntryIsMarkedWithItsReach() {
        var inside = folder(9);
        var shared = folder(1);
        var restricted = folder(2);
        var published = file(5);
        var station = mock(Station.class);
        when(station.publicKbMode()).thenReturn(PublicKbMode.ALLOW_ALL);
        when(stations.findById(3)).thenReturn(Optional.of(station));
        when(knowledgeBase.findFolder(9)).thenReturn(Optional.of(inside));
        when(knowledgeBase.findFolders(3, 9)).thenReturn(List.of(shared, restricted));
        when(knowledgeBase.findFiles(3, 9)).thenReturn(List.of(published));
        when(federation.broadlyShared(3, true)).thenReturn(Set.of(1, 2));
        when(access.findRestrictions(2, null)).thenReturn(List.of(mock(KbAccessGrant.class)));
        when(access.isPubliclyVisible(eq(PublicKbMode.ALLOW_ALL), isNull(), eq(5)))
                .thenReturn(true);

        var level = browse.browse(3, 9, READER, true);

        assertEquals(inside, level.currentFolder());
        assertEquals(2, level.folders().size());
        assertEquals(Set.of(1), level.folderReach().federated());
        assertEquals(Set.of(2), level.folderReach().narrowly());
        assertEquals(Set.of(5), level.fileReach().publicly());
    }
}
