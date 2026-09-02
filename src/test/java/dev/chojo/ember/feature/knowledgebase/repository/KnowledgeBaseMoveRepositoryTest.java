/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.knowledgebase.repository;

import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.knowledgebase.entity.KbFileType;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KnowledgeBaseMoveRepositoryTest extends RepositoryTestBase {
    private static Station station;
    private static Account account;
    private static StationMember member;

    @BeforeAll
    static void setup() {
        station = stationRepo.create("KbMoveRepoStation");
        account = accountRepo.create("kb-move-repo@test.com", "Kb", "Mover");
        member = stationMemberRepo.create(station.id(), account.id());
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
        accountRepo.delete(account.id());
    }

    private int folder(Integer parentId, String name) {
        return knowledgeBaseRepo
                .createFolder(station.id(), parentId, name, "", member.id())
                .id();
    }

    private int file(Integer folderId, String name) {
        return knowledgeBaseRepo
                .createFile(station.id(), folderId, name, "", KbFileType.TEXT, "text/plain", 0, null, member.id())
                .id();
    }

    @Test
    void descendantFolderIdsWalksTheWholeBranchWithoutTheFolderItself() {
        int top = folder(null, "descend-top");
        int middle = folder(top, "descend-middle");
        int bottom = folder(middle, "descend-bottom");

        var descendants = knowledgeBaseRepo.descendantFolderIds(top);

        assertEquals(2, descendants.size());
        assertTrue(descendants.contains(middle));
        assertTrue(descendants.contains(bottom));
        assertFalse(descendants.contains(top));
        assertTrue(knowledgeBaseRepo.descendantFolderIds(bottom).isEmpty());
        knowledgeBaseRepo.purgeFolder(top);
    }

    @Test
    void moveFolderRehangsTheBranch() {
        int source = folder(null, "move-source");
        int child = folder(source, "move-child");
        int target = folder(null, "move-target");

        assertTrue(knowledgeBaseRepo.moveFolder(source, target));

        assertEquals(
                target, knowledgeBaseRepo.findFolderById(source).orElseThrow().parentId());
        assertTrue(knowledgeBaseRepo.descendantFolderIds(target).contains(child));

        assertTrue(knowledgeBaseRepo.moveFolder(source, null));
        assertNull(knowledgeBaseRepo.findFolderById(source).orElseThrow().parentId());
        assertFalse(knowledgeBaseRepo.moveFolder(999999, null));
        knowledgeBaseRepo.purgeFolder(source);
        knowledgeBaseRepo.purgeFolder(target);
    }

    @Test
    void moveFilePutsItIntoAnotherFolder() {
        int target = folder(null, "file-move-target");
        int fileId = file(null, "movable");

        assertTrue(knowledgeBaseRepo.moveFile(fileId, target));
        assertEquals(
                target, knowledgeBaseRepo.findFileById(fileId).orElseThrow().folderId());
        assertEquals(List.of(fileId), knowledgeBaseRepo.findFileIdsInFolders(List.of(target)));

        assertTrue(knowledgeBaseRepo.moveFile(fileId, null));
        assertNull(knowledgeBaseRepo.findFileById(fileId).orElseThrow().folderId());
        assertFalse(knowledgeBaseRepo.moveFile(999999, null));
        assertTrue(knowledgeBaseRepo.findFileIdsInFolders(List.of()).isEmpty());

        knowledgeBaseRepo.purgeFile(fileId);
        knowledgeBaseRepo.purgeFolder(target);
    }

    @Test
    void folderNameTakenSeesRootAndNestedNeighbours() {
        int parent = folder(null, "taken-parent");
        int occupant = folder(parent, "Einsatz");
        int rootOccupant = folder(null, "taken-root-name");

        assertTrue(knowledgeBaseRepo.folderNameTaken(station.id(), parent, "Einsatz", 0));
        assertFalse(knowledgeBaseRepo.folderNameTaken(station.id(), parent, "Einsatz", occupant));
        assertFalse(knowledgeBaseRepo.folderNameTaken(station.id(), parent, "Ausbildung", 0));
        assertTrue(knowledgeBaseRepo.folderNameTaken(station.id(), null, "taken-root-name", 0));
        assertFalse(knowledgeBaseRepo.folderNameTaken(station.id(), null, "taken-root-name", rootOccupant));

        knowledgeBaseRepo.purgeFolder(parent);
        knowledgeBaseRepo.purgeFolder(rootOccupant);
    }

    @Test
    void findRecentFilesAnswersNewestChangeFirst() {
        int older = file(null, "recent-older");
        int newer = file(null, "recent-newer");
        knowledgeBaseRepo.updateFile(newer, "recent-newer", "touched", null, 0);

        var recent = knowledgeBaseRepo.findRecentFiles(station.id(), 5);

        assertFalse(recent.isEmpty());
        assertEquals(newer, recent.getFirst().id());
        knowledgeBaseRepo.purgeFile(older);
        knowledgeBaseRepo.purgeFile(newer);
    }

    @Test
    void findBacklinksReadsTheSameRowsTheOtherWayRound() {
        int source = file(null, "backlink-source");
        int target = file(null, "backlink-target");
        knowledgeBaseRepo.setRelatedFiles(source, List.of(target));

        var backlinks = knowledgeBaseRepo.findBacklinks(target);

        assertEquals(1, backlinks.size());
        assertEquals(source, backlinks.getFirst().id());
        assertTrue(knowledgeBaseRepo.findBacklinks(source).isEmpty());
        knowledgeBaseRepo.purgeFile(source);
        knowledgeBaseRepo.purgeFile(target);
    }

    @Test
    void tagsGoOnAndComeOffOneAtATime() {
        int fileId = file(null, "tagged-file");
        int folderId = folder(null, "tagged-folder");
        var kept = knowledgeBaseRepo.findOrCreateTag(station.id(), "kept");
        var dropped = knowledgeBaseRepo.findOrCreateTag(station.id(), "dropped");

        knowledgeBaseRepo.addFileTag(fileId, kept.id());
        knowledgeBaseRepo.addFileTag(fileId, dropped.id());
        knowledgeBaseRepo.removeFileTag(fileId, dropped.id());
        assertEquals(
                List.of("kept"),
                knowledgeBaseRepo.findFileTags(fileId).stream()
                        .map(t -> t.name())
                        .toList());

        knowledgeBaseRepo.addFolderTag(folderId, kept.id());
        knowledgeBaseRepo.addFolderTag(folderId, dropped.id());
        knowledgeBaseRepo.removeFolderTag(folderId, dropped.id());
        assertEquals(
                List.of("kept"),
                knowledgeBaseRepo.findFolderTags(folderId).stream()
                        .map(t -> t.name())
                        .toList());

        assertTrue(knowledgeBaseRepo.findTagByName(station.id(), "KEPT").isPresent());
        assertTrue(knowledgeBaseRepo.findTagByName(station.id(), "never-used").isEmpty());

        knowledgeBaseRepo.purgeFile(fileId);
        knowledgeBaseRepo.purgeFolder(folderId);
    }
}
