/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.knowledgebase.service;

import dev.chojo.ember.api.ApiServer;
import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.api.RefusalResponse;
import dev.chojo.ember.api.TestSessions;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.knowledgebase.entity.KbAccessLevel;
import dev.chojo.ember.feature.knowledgebase.entity.KbFile;
import dev.chojo.ember.feature.knowledgebase.entity.KbFolder;
import dev.chojo.ember.feature.knowledgebase.service.KbAccessService.MemberAccess;
import io.javalin.http.Context;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class KbGuardsTest {
    private Context ctx;
    private KnowledgeBaseService knowledgeBase;
    private KbAccessService access;

    private void signedIn(UserSession session) {
        when(ctx.<UserSession>attribute(ApiServer.ATTR_SESSION)).thenReturn(session);
    }

    private static KbFile file(int stationId) {
        var file = mock(KbFile.class);
        when(file.stationId()).thenReturn(stationId);
        return file;
    }

    private static KbFolder folder(int stationId) {
        var folder = mock(KbFolder.class);
        when(folder.stationId()).thenReturn(stationId);
        return folder;
    }

    @BeforeEach
    void setup() {
        ctx = mock(Context.class);
        knowledgeBase = mock(KnowledgeBaseService.class);
        access = mock(KbAccessService.class);
        signedIn(TestSessions.member(3, StationPermission.KNOWLEDGE_EDIT));
    }

    @Test
    void anEntryOfTheStationIsHandedOutAndOneOfAnotherIsNotHere() {
        var own = file(3);
        var foreign = file(4);
        var ownFolder = folder(3);
        var trashed = file(3);
        var trashedFolder = folder(3);
        when(knowledgeBase.findFile(1)).thenReturn(Optional.of(own));
        when(knowledgeBase.findFile(2)).thenReturn(Optional.of(foreign));
        when(knowledgeBase.findFolder(1)).thenReturn(Optional.of(ownFolder));
        when(knowledgeBase.findDeletedFile(1)).thenReturn(Optional.of(trashed));
        when(knowledgeBase.findDeletedFolder(1)).thenReturn(Optional.of(trashedFolder));

        assertEquals(own, KbGuards.requireOwnedFile(ctx, knowledgeBase, 1));
        assertEquals(ownFolder, KbGuards.requireOwnedFolder(ctx, knowledgeBase, 1));
        assertEquals(trashed, KbGuards.requireOwnedTrashedFile(ctx, knowledgeBase, 1));
        assertEquals(trashedFolder, KbGuards.requireOwnedTrashedFolder(ctx, knowledgeBase, 1));
        assertEquals(
                Refusal.NOT_HERE_OR_NOT_YOURS,
                assertThrows(RefusalResponse.class, () -> KbGuards.requireOwnedFile(ctx, knowledgeBase, 2))
                        .refusal());
    }

    @Test
    void theReadersAccessCarriesTheirStationRights() {
        var resolved =
                new MemberAccess(TestSessions.MEMBER_ID, StationUserType.MEMBER, List.of(), List.of(), true, false);
        when(access.memberAccess(TestSessions.MEMBER_ID, StationUserType.MEMBER, true, false))
                .thenReturn(resolved);

        assertEquals(resolved, KbGuards.accessOf(ctx, access));
        assertEquals(StationUserType.MEMBER, KbGuards.readerUserType(TestSessions.member(3)));
    }

    @Test
    void aSessionWithoutAMemberRowMatchesNobodyButKeepsItsRights() {
        var session = new UserSession(
                TestSessions.account(), 1, 3, null, null, Set.of(StationPermission.KNOWLEDGE_MANAGER), Set.of(), null);
        signedIn(session);

        var reader = KbGuards.accessOf(ctx, access);

        assertEquals(0, reader.memberId());
        assertEquals(true, reader.canManage());
        assertNull(KbGuards.readerUserType(session));
    }

    @Test
    void tooLittleAccessIsAnEntryThatIsNotThere() {
        when(access.effectiveLevel(any(), eq(5), eq(null))).thenReturn(KbAccessLevel.READ);

        assertDoesNotThrow(() -> KbGuards.requireLevel(ctx, access, 5, null, KbAccessLevel.READ));
        assertEquals(
                Refusal.KB_ENTRY_NOT_YOURS_TO_OPEN,
                assertThrows(
                                RefusalResponse.class,
                                () -> KbGuards.requireLevel(ctx, access, 5, null, KbAccessLevel.WRITE))
                        .refusal());
    }
}
