/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.knowledgebase.service;

import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.account.entity.Account;
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
        knowledgeBaseRepo.deleteFile(file.id());
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
        knowledgeBaseRepo.deleteFile(file.id());
        knowledgeBaseRepo.deleteFolder(folder.id());
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
        knowledgeBaseRepo.deleteFolder(child.id());
        knowledgeBaseRepo.deleteFolder(parent.id());
        knowledgeBaseRepo.deleteFolder(grandparent.id());
    }

    @Test
    void memberRestrictionsNameExactlyOneMember() {
        var file = createFile("For One Member", null);
        service.setRestrictions(
                null, file.id(), new RestrictionSelection(List.of(), List.of(), List.of(), List.of(member.id()), null));

        assertTrue(service.canAccess(member.id(), null, file.id(), null, List.of(), List.of()));
        assertFalse(service.canAccess(member.id() + 9999, null, file.id(), null, List.of(), List.of()));

        service.setRestrictions(null, file.id(), RestrictionSelection.empty());
        knowledgeBaseRepo.deleteFile(file.id());
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
        knowledgeBaseRepo.deleteFile(groupFile.id());
        knowledgeBaseRepo.deleteFile(tagFile.id());
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
        knowledgeBaseRepo.deleteFile(file.id());
        userTagRepo.removeMember(tag.id(), member.id());
        memberGroupRepo.removeMember(group.id(), member.id());
        memberGroupRepo.delete(group.id());
        userTagRepo.delete(tag.id());
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

        knowledgeBaseRepo.deleteFolder(folder.id());
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
        knowledgeBaseRepo.deleteFile(fileInHidden.id());
        knowledgeBaseRepo.deleteFolder(child.id());
        knowledgeBaseRepo.deleteFolder(parent.id());
    }
}
