/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.knowledgebase.service;

import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.knowledgebase.entity.KbAccessLevel;
import dev.chojo.ember.feature.knowledgebase.entity.KbFile;
import dev.chojo.ember.feature.knowledgebase.entity.KbFileType;
import dev.chojo.ember.feature.knowledgebase.entity.KbFolder;
import dev.chojo.ember.feature.knowledgebase.entity.PublicKbMode;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.restriction.RestrictionSelection;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class KbAccessServiceTest extends RepositoryTestBase {
    private static KbAccessService service;
    private static Station station;
    private static Account account;
    private static StationMember member;

    @BeforeAll
    static void setup() {
        service = new KbAccessService(knowledgeBaseRepo, memberGroupRepo, userTagRepo);
        station = stationRepo.create("KbAccessStation");
        account = accountRepo.create("kb-access@test.com", "Kb", "AccessTester");
        member = stationMemberRepo.create(station.id(), account.id());
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
        accountRepo.delete(account.id());
    }

    private static KbFolder createFolder(String name, Integer parentId) {
        return knowledgeBaseRepo.createFolder(station.id(), parentId, name, "", member.id());
    }

    private static KbFile createFile(String name, Integer folderId) {
        return knowledgeBaseRepo.createFile(
                station.id(), folderId, name, "", KbFileType.MARKDOWN, "text/markdown", 0, null, member.id());
    }

    private static RestrictionSelection forUserType(StationUserType userType) {
        return new RestrictionSelection(List.of(userType), List.of(), List.of(), List.of(), null);
    }

    @Test
    void unrestrictedItemsAreOpenToEveryone() {
        var file = createFile("Open", null);
        assertTrue(service.canAccess(member.id(), null, file.id(), null, List.of(), List.of()));
        assertTrue(service.canAccess(member.id(), null, null, null, List.of(), List.of()));
        assertTrue(service.canAccess(member.id(), 999999, null, null, List.of(), List.of()));
        assertTrue(service.findRestrictions(null, file.id()).isEmpty());
        knowledgeBaseRepo.purgeFile(file.id());
    }

    /**
     * A restriction on a folder decides who reaches the folder and everything inside it, so a file
     * inherits a parent it never mentions itself.
     */
    @Test
    void folderRestrictionsAreInheritedByTheirFiles() {
        var folder = createFolder("Restricted", null);
        var file = createFile("Inside", folder.id());
        service.setRestrictions(folder.id(), null, forUserType(StationUserType.MEMBER));
        assertFalse(service.findRestrictions(folder.id(), null).isEmpty());

        assertFalse(service.canAccess(member.id(), folder.id(), null, null, List.of(), List.of()));
        assertTrue(service.canAccess(member.id(), folder.id(), null, StationUserType.MEMBER, List.of(), List.of()));
        assertFalse(service.canAccess(member.id(), null, file.id(), null, List.of(), List.of()));
        assertTrue(service.canAccess(member.id(), null, file.id(), StationUserType.MEMBER, List.of(), List.of()));

        service.setRestrictions(folder.id(), null, RestrictionSelection.empty());
        knowledgeBaseRepo.purgeFile(file.id());
        knowledgeBaseRepo.purgeFolder(folder.id());
    }

    /**
     * A restriction two folders up still applies, so nesting cannot be used to escape it.
     */
    @Test
    void restrictionsApplyThroughEveryFolderAbove() {
        var grandparent = createFolder("Grandparent", null);
        var parent = createFolder("Parent", grandparent.id());
        var child = createFolder("Child", parent.id());
        service.setRestrictions(grandparent.id(), null, forUserType(StationUserType.MEMBER));

        assertFalse(service.canAccess(member.id(), child.id(), null, null, List.of(), List.of()));
        assertTrue(service.canAccess(member.id(), child.id(), null, StationUserType.MEMBER, List.of(), List.of()));

        service.setRestrictions(grandparent.id(), null, RestrictionSelection.empty());
        knowledgeBaseRepo.purgeFolder(child.id());
        knowledgeBaseRepo.purgeFolder(parent.id());
        knowledgeBaseRepo.purgeFolder(grandparent.id());
    }

    @Test
    void memberRestrictionsNameExactlyOneMember() {
        var file = createFile("For One Member", null);
        service.setRestrictions(
                null, file.id(), new RestrictionSelection(List.of(), List.of(), List.of(), List.of(member.id()), null));

        assertTrue(service.canAccess(member.id(), null, file.id(), null, List.of(), List.of()));
        assertFalse(service.canAccess(member.id() + 9999, null, file.id(), null, List.of(), List.of()));

        service.setRestrictions(null, file.id(), RestrictionSelection.empty());
        knowledgeBaseRepo.purgeFile(file.id());
    }

    @Test
    void groupAndTagRestrictionsFollowTheMembersMemberships() {
        var group = memberGroupRepo.create(station.id(), "KbAccessGroupRestriction");
        var tag = userTagRepo.create(station.id(), "KbAccessTagRestriction");
        var groupFile = createFile("Group Only", null);
        var tagFile = createFile("Tag Only", null);
        service.setRestrictions(
                null,
                groupFile.id(),
                new RestrictionSelection(List.of(), List.of(group.id()), List.of(), List.of(), null));
        service.setRestrictions(
                null, tagFile.id(), new RestrictionSelection(List.of(), List.of(), List.of(tag.id()), List.of(), null));

        assertFalse(service.canAccess(member.id(), null, groupFile.id(), null, List.of(), List.of()));
        assertTrue(service.canAccess(member.id(), null, groupFile.id(), null, List.of(group.id()), List.of()));
        assertFalse(service.canAccess(member.id(), null, tagFile.id(), null, List.of(), List.of()));
        assertTrue(service.canAccess(member.id(), null, tagFile.id(), null, List.of(), List.of(tag.id())));

        service.setRestrictions(null, groupFile.id(), RestrictionSelection.empty());
        service.setRestrictions(null, tagFile.id(), RestrictionSelection.empty());
        knowledgeBaseRepo.purgeFile(groupFile.id());
        knowledgeBaseRepo.purgeFile(tagFile.id());
        memberGroupRepo.delete(group.id());
        userTagRepo.delete(tag.id());
    }

    /**
     * The memberships behind an access check are read once and then reused, so filtering a whole
     * listing does not re-read them per row.
     */
    @Test
    void aReadAccessContextAnswersTheSameAsAFullCheck() {
        var group = memberGroupRepo.create(station.id(), "KbAccessContextGroup");
        var tag = userTagRepo.create(station.id(), "KbAccessContextTag");
        memberGroupRepo.addMember(group.id(), member.id());
        userTagRepo.addMember(tag.id(), member.id());
        var file = createFile("Context", null);
        service.setRestrictions(null, file.id(), forUserType(StationUserType.MEMBER));

        var access = service.memberAccess(member.id(), StationUserType.MEMBER);
        assertEquals(member.id(), access.memberId());
        assertEquals(StationUserType.MEMBER, access.userType());
        assertTrue(access.groupIds().contains(group.id()));
        assertTrue(access.tagIds().contains(tag.id()));
        assertTrue(service.canAccess(access, null, file.id()));
        assertFalse(service.canAccess(service.memberAccess(member.id(), null), null, file.id()));

        service.setRestrictions(null, file.id(), RestrictionSelection.empty());
        knowledgeBaseRepo.purgeFile(file.id());
        userTagRepo.removeMember(tag.id(), member.id());
        memberGroupRepo.removeMember(group.id(), member.id());
        memberGroupRepo.delete(group.id());
        userTagRepo.delete(tag.id());
    }

    /**
     * A search hands back files from anywhere in the tree at once, so the batch has to answer
     * exactly what a single check would, folder by folder and file by file.
     */
    @Test
    void aBatchOfFilesResolvesTheSameLevelsAsOneCheckEach() {
        var openFolder = createFolder("Batch Open", null);
        var closedFolder = createFolder("Batch Closed", null);
        var deepFolder = createFolder("Batch Deep", closedFolder.id());
        var openFile = createFile("Batch Readable", openFolder.id());
        var closedFile = createFile("Batch Hidden", closedFolder.id());
        var deepFile = createFile("Batch Deeply Hidden", deepFolder.id());
        var rootFile = createFile("Batch Root", null);
        var deniedFile = createFile("Batch Denied", openFolder.id());
        service.setRestrictions(closedFolder.id(), null, forUserType(StationUserType.MEMBER));
        service.setRestrictions(null, deniedFile.id(), forUserType(StationUserType.MEMBER));

        var files = List.of(openFile, closedFile, deepFile, rootFile, deniedFile);
        var nodes = files.stream().map(KbAccessService.FileNode::of).toList();
        var access = service.memberAccess(member.id(), null);
        var levels = service.fileLevels(access, nodes);

        for (var file : files) {
            assertEquals(
                    service.effectiveLevel(access, null, file.id()),
                    levels.get(file.id()),
                    "batch level differs for " + file.name());
        }
        assertEquals(Set.of(openFile.id(), rootFile.id()), service.readableFiles(access, nodes));

        service.setRestrictions(closedFolder.id(), null, RestrictionSelection.empty());
        service.setRestrictions(null, deniedFile.id(), RestrictionSelection.empty());
        for (var file : files) knowledgeBaseRepo.purgeFile(file.id());
        knowledgeBaseRepo.purgeFolder(deepFolder.id());
        knowledgeBaseRepo.purgeFolder(closedFolder.id());
        knowledgeBaseRepo.purgeFolder(openFolder.id());
    }

    /**
     * A station manager reads everything, and an empty batch asks nothing of the database.
     */
    @Test
    void aBatchAnswersManagersAndEmptyListsWithoutWalkingTheTree() {
        var folder = createFolder("Batch Managed", null);
        var file = createFile("Batch Managed File", folder.id());
        service.setRestrictions(folder.id(), null, forUserType(StationUserType.MEMBER));

        var manager = new KbAccessService.MemberAccess(member.id(), null, List.of(), List.of(), false, true);
        var node = KbAccessService.FileNode.of(file);
        assertEquals(
                KbAccessLevel.MANAGE, service.fileLevels(manager, List.of(node)).get(file.id()));
        assertEquals(Set.of(file.id()), service.readableFiles(manager, List.of(node)));
        assertTrue(service.fileLevels(manager, List.of()).isEmpty());
        assertTrue(service.readableFiles(service.memberAccess(member.id(), null), List.of())
                .isEmpty());

        service.setRestrictions(folder.id(), null, RestrictionSelection.empty());
        knowledgeBaseRepo.purgeFile(file.id());
        knowledgeBaseRepo.purgeFolder(folder.id());
    }

    /**
     * The station mode is the default and an explicit override wins over it in both directions.
     */
    @Test
    void publicVisibilityFollowsTheStationModeUnlessOverridden() {
        var folder = createFolder("Public Folder", null);

        assertFalse(service.isPubliclyVisible(PublicKbMode.OFF, folder.id(), null));
        assertTrue(service.isPubliclyVisible(PublicKbMode.ALLOW_ALL, folder.id(), null));
        assertFalse(service.isPubliclyVisible(PublicKbMode.DENY_ALL, folder.id(), null));
        assertTrue(service.findPublicVisibility(folder.id(), null).isEmpty());

        service.setPublicVisibility(folder.id(), null, true);
        assertTrue(service.findPublicVisibility(folder.id(), null).orElseThrow());
        assertTrue(service.isPubliclyVisible(PublicKbMode.DENY_ALL, folder.id(), null));

        service.setPublicVisibility(folder.id(), null, false);
        assertFalse(service.isPubliclyVisible(PublicKbMode.ALLOW_ALL, folder.id(), null));

        service.removePublicVisibility(folder.id(), null);
        assertTrue(service.findPublicVisibility(folder.id(), null).isEmpty());

        knowledgeBaseRepo.purgeFolder(folder.id());
    }

    /**
     * A folder kept off the public knowledge base takes everything below it along, and an item
     * carrying an access restriction is never public whatever the mode says.
     */
    @Test
    void hiddenFoldersAndRestrictedItemsAreNeverPublic() {
        var parent = createFolder("Hidden Parent", null);
        var child = createFolder("Child Of Hidden", parent.id());
        var fileInHidden = createFile("File In Hidden", parent.id());
        service.setPublicVisibility(parent.id(), null, false);

        assertFalse(service.isPubliclyVisible(PublicKbMode.ALLOW_ALL, child.id(), null));
        assertFalse(service.isPubliclyVisible(PublicKbMode.ALLOW_ALL, null, fileInHidden.id()));

        service.removePublicVisibility(parent.id(), null);
        assertTrue(service.isPubliclyVisible(PublicKbMode.ALLOW_ALL, null, fileInHidden.id()));

        service.setRestrictions(parent.id(), null, forUserType(StationUserType.MEMBER));
        assertFalse(service.isPubliclyVisible(PublicKbMode.ALLOW_ALL, parent.id(), null));
        assertFalse(service.isPubliclyVisible(PublicKbMode.ALLOW_ALL, null, fileInHidden.id()));

        service.setRestrictions(parent.id(), null, RestrictionSelection.empty());
        knowledgeBaseRepo.purgeFile(fileInHidden.id());
        knowledgeBaseRepo.purgeFolder(child.id());
        knowledgeBaseRepo.purgeFolder(parent.id());
    }

    /**
     * Asked about a folder an item does not sit in yet, which is what the move dialog needs in order
     * to warn before a station that publishes by default publishes something nobody submitted.
     */
    @Test
    void publicVisibilityCanBeAskedAboutAFolderAnItemIsNotInYet() {
        var open = createFolder("Would Publish", null);
        var closed = createFolder("Would Not Publish", null);
        service.setPublicVisibility(closed.id(), null, false);
        var article = createFile("Not Yet Moved", closed.id());

        assertFalse(service.isPubliclyVisible(PublicKbMode.ALLOW_ALL, null, article.id()));
        assertTrue(service.isPubliclyVisibleUnder(PublicKbMode.ALLOW_ALL, open.id(), null, article.id()));
        assertTrue(service.isPubliclyVisibleUnder(PublicKbMode.ALLOW_ALL, null, null, article.id()));
        assertFalse(service.isPubliclyVisibleUnder(PublicKbMode.ALLOW_ALL, closed.id(), null, article.id()));
        assertFalse(service.isPubliclyVisibleUnder(PublicKbMode.OFF, open.id(), null, article.id()));

        service.setRestrictions(null, article.id(), forUserType(StationUserType.MEMBER));
        assertFalse(service.isPubliclyVisibleUnder(PublicKbMode.ALLOW_ALL, open.id(), null, article.id()));

        service.setRestrictions(null, article.id(), RestrictionSelection.empty());
        service.removePublicVisibility(closed.id(), null);
        knowledgeBaseRepo.purgeFile(article.id());
        knowledgeBaseRepo.purgeFolder(open.id());
        knowledgeBaseRepo.purgeFolder(closed.id());
    }

    /**
     * The whole tree at once, which is what a picker offering somewhere to put an entry needs. It
     * has to agree with the answer a single lookup gives, gate included: a folder the reader is out
     * of takes everything under it out too.
     */
    @Test
    void theWholeTreeResolvesTheSameWayOneFolderDoes() {
        var open = createFolder("Tree Open", null);
        var inner = createFolder("Tree Inner", open.id());
        var gated = createFolder("Tree Gated", null);
        var belowGate = createFolder("Tree Below Gate", gated.id());
        service.setRestrictions(gated.id(), null, forUserType(StationUserType.MEMBER));

        var nodes = List.of(
                new KbAccessService.TreeNode(open.id(), null, null),
                new KbAccessService.TreeNode(inner.id(), open.id(), null),
                new KbAccessService.TreeNode(gated.id(), null, null),
                new KbAccessService.TreeNode(belowGate.id(), gated.id(), null));
        var access = new KbAccessService.MemberAccess(
                member.id(), StationUserType.GUARDIAN, List.of(), List.of(), true, false);

        var levels = service.treeLevels(access, nodes);

        assertEquals(KbAccessLevel.MANAGE, levels.get(open.id()));
        assertEquals(KbAccessLevel.MANAGE, levels.get(inner.id()));
        assertEquals(KbAccessLevel.NONE, levels.get(gated.id()));
        assertEquals(KbAccessLevel.NONE, levels.get(belowGate.id()));
        assertEquals(service.effectiveLevel(access, belowGate.id(), null), levels.get(belowGate.id()));

        var manager = new KbAccessService.MemberAccess(
                member.id(), StationUserType.GUARDIAN, List.of(), List.of(), true, true);
        assertEquals(KbAccessLevel.MANAGE, service.treeLevels(manager, nodes).get(gated.id()));

        service.setRestrictions(gated.id(), null, RestrictionSelection.empty());
        knowledgeBaseRepo.purgeFolder(open.id());
        knowledgeBaseRepo.purgeFolder(gated.id());
    }
}
