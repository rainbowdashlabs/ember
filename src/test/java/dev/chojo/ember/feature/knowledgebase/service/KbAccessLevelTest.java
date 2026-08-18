/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.knowledgebase.service;

import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.knowledgebase.entity.KbAccessLevel;
import dev.chojo.ember.feature.knowledgebase.entity.KbFileType;
import dev.chojo.ember.feature.knowledgebase.repository.KnowledgeBaseRepository;
import dev.chojo.ember.feature.knowledgebase.service.KbAccessService.GrantEntry;
import dev.chojo.ember.feature.members.entity.MemberGroup;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The rules the knowledge base resolves permissions by. The first case is the one the upgrade
 * rests on: with no grants anywhere, everyone keeps exactly the level their station rights give.
 */
class KbAccessLevelTest extends RepositoryTestBase {
    private static KbAccessService accessService;
    private static KnowledgeBaseRepository kbRepo;
    private static Station station;
    private static Account account;
    private static StationMember member;
    private static MemberGroup trainers;
    private static int rootFolder;
    private static int childFolder;
    private static int fileInChild;

    @BeforeAll
    static void setup() {
        kbRepo = new KnowledgeBaseRepository();
        accessService = new KbAccessService(kbRepo, memberGroupRepo, userTagRepo);

        station = stationRepo.create("KbGrantStation");
        account = accountRepo.create("kb-grant@test.com", "Grant", "Tester");
        member = stationMemberRepo.create(station.id(), account.id());
        trainers = memberGroupRepo.create(station.id(), "Trainer");

        var root = kbRepo.createFolder(station.id(), null, "Einsatz", "", member.id());
        rootFolder = root.id();
        var child = kbRepo.createFolder(station.id(), rootFolder, "Handbuch", "", member.id());
        childFolder = child.id();
        fileInChild = kbRepo.createFile(
                        station.id(), childFolder, "Leitfaden", "", KbFileType.MARKDOWN, null, 0, null, member.id())
                .id();
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
        accountRepo.delete(account.id());
    }

    @BeforeEach
    void clearGrants() {
        kbRepo.clearRestrictions(rootFolder, null);
        kbRepo.clearRestrictions(childFolder, null);
        kbRepo.clearRestrictions(null, fileInChild);
        memberGroupRepo.removeMember(trainers.id(), member.id());
    }

    private KbAccessService.MemberAccess access(boolean canEdit, boolean canManage) {
        return accessService.memberAccess(member.id(), StationUserType.MEMBER, canEdit, canManage);
    }

    @Test
    void withoutGrantsEveryoneKeepsTheirStationLevel() {
        assertEquals(KbAccessLevel.READ, accessService.effectiveLevel(access(false, false), null, fileInChild));
        assertEquals(
                KbAccessLevel.MANAGE,
                accessService.effectiveLevel(access(true, false), null, fileInChild),
                "the station edit right already deletes and publishes today, so it maps to MANAGE");
        assertEquals(KbAccessLevel.MANAGE, accessService.effectiveLevel(access(false, true), null, fileInChild));
    }

    @Test
    void anAudienceRowWithoutALevelStillLeavesTheStationLevelInCharge() {
        memberGroupRepo.addMember(trainers.id(), member.id());
        accessService.setGrants(rootFolder, null, List.of(new GrantEntry(null, trainers.id(), null, null, null)));

        assertEquals(KbAccessLevel.READ, accessService.effectiveLevel(access(false, false), null, fileInChild));
        assertEquals(
                KbAccessLevel.MANAGE,
                accessService.effectiveLevel(access(true, false), null, fileInChild),
                "a migrated restriction must not take editing or deleting away from an editor");
    }

    @Test
    void aMemberOutsideTheAudienceIsGatedOut() {
        accessService.setGrants(rootFolder, null, List.of(new GrantEntry(null, trainers.id(), null, null, null)));

        assertEquals(KbAccessLevel.NONE, accessService.effectiveLevel(access(false, false), null, fileInChild));
        assertEquals(
                KbAccessLevel.NONE,
                accessService.effectiveLevel(access(true, false), rootFolder, null),
                "the station edit right does not get you past an audience you are not in");
    }

    @Test
    void aReadGrantStopsAnEditorEditing() {
        memberGroupRepo.addMember(trainers.id(), member.id());
        accessService.setGrants(
                rootFolder, null, List.of(new GrantEntry(null, trainers.id(), null, null, KbAccessLevel.READ)));

        var level = accessService.effectiveLevel(access(true, false), null, fileInChild);
        assertEquals(KbAccessLevel.READ, level);
        assertTrue(level.covers(KbAccessLevel.READ));
        assertTrue(!level.covers(KbAccessLevel.WRITE));
    }

    @Test
    void aWriteGrantLetsSomeoneEditWithoutTheStationRight() {
        memberGroupRepo.addMember(trainers.id(), member.id());
        accessService.setGrants(
                rootFolder, null, List.of(new GrantEntry(null, trainers.id(), null, null, KbAccessLevel.WRITE)));

        assertEquals(KbAccessLevel.WRITE, accessService.effectiveLevel(access(false, false), null, fileInChild));
    }

    @Test
    void theDeepestLevelWinsWhileEveryLevelStillGates() {
        memberGroupRepo.addMember(trainers.id(), member.id());
        accessService.setGrants(
                rootFolder, null, List.of(new GrantEntry(null, trainers.id(), null, null, KbAccessLevel.READ)));
        accessService.setGrants(
                null, fileInChild, List.of(new GrantEntry(null, trainers.id(), null, null, KbAccessLevel.WRITE)));

        assertEquals(KbAccessLevel.WRITE, accessService.effectiveLevel(access(false, false), null, fileInChild));
        assertEquals(
                KbAccessLevel.READ,
                accessService.effectiveLevel(access(false, false), childFolder, null),
                "the file's own level says nothing about the folder holding it");
    }

    @Test
    void aChildCannotOpenUpWhatItsParentClosed() {
        accessService.setGrants(rootFolder, null, List.of(new GrantEntry(null, trainers.id(), null, null, null)));
        accessService.setGrants(
                null, fileInChild, List.of(new GrantEntry(StationUserType.MEMBER, null, null, null, null)));

        assertEquals(
                KbAccessLevel.NONE,
                accessService.effectiveLevel(access(false, false), null, fileInChild),
                "the parent still gates a member who is not in its audience");
    }

    @Test
    void anExplicitDenialWinsWhereverItAppears() {
        memberGroupRepo.addMember(trainers.id(), member.id());
        accessService.setGrants(rootFolder, null, List.of(new GrantEntry(null, trainers.id(), null, null, null)));
        accessService.setGrants(
                null, fileInChild, List.of(new GrantEntry(null, trainers.id(), null, null, KbAccessLevel.NONE)));

        assertEquals(KbAccessLevel.NONE, accessService.effectiveLevel(access(true, false), null, fileInChild));
        assertEquals(
                KbAccessLevel.MANAGE,
                accessService.effectiveLevel(access(true, false), childFolder, null),
                "the denial belongs to the file, not to the folder above it");
    }

    @Test
    void theManageRightBypassesEveryDenial() {
        accessService.setGrants(
                rootFolder, null, List.of(new GrantEntry(null, trainers.id(), null, null, KbAccessLevel.NONE)));

        assertEquals(
                KbAccessLevel.MANAGE,
                accessService.effectiveLevel(access(false, true), null, fileInChild),
                "a station must not be able to lock itself out of its own knowledge base");
    }

    @Test
    void childLevelsMatchWhatResolvingEachChildWouldSay() {
        memberGroupRepo.addMember(trainers.id(), member.id());
        accessService.setGrants(
                rootFolder, null, List.of(new GrantEntry(null, trainers.id(), null, null, KbAccessLevel.READ)));
        accessService.setGrants(
                null, fileInChild, List.of(new GrantEntry(null, trainers.id(), null, null, KbAccessLevel.WRITE)));

        var levels = accessService.childLevels(
                access(false, false),
                childFolder,
                List.of(),
                List.of(new KbAccessService.ChildNode(fileInChild, null)));

        assertEquals(
                accessService.effectiveLevel(access(false, false), null, fileInChild),
                levels.files().get(fileInChild),
                "the listing must report the level the item's own check would answer");
        assertEquals(KbAccessLevel.WRITE, levels.files().get(fileInChild));
    }

    @Test
    void childLevelsDenyEverythingBelowAGateTheMemberFails() {
        accessService.setGrants(rootFolder, null, List.of(new GrantEntry(null, trainers.id(), null, null, null)));

        var levels = accessService.childLevels(
                access(true, false), childFolder, List.of(), List.of(new KbAccessService.ChildNode(fileInChild, null)));

        assertEquals(KbAccessLevel.NONE, levels.files().get(fileInChild));
    }

    @Test
    void theExplanationNamesTheFolderThatHeldTheLevelDown() {
        memberGroupRepo.addMember(trainers.id(), member.id());
        accessService.setGrants(
                rootFolder, null, List.of(new GrantEntry(null, trainers.id(), null, null, KbAccessLevel.READ)));

        var explained = accessService.explainLevel(access(true, false), null, fileInChild);
        assertEquals(KbAccessLevel.READ, explained.level());
        assertEquals("Einsatz", explained.source(), "the reader has to be told which folder decided it");
    }

    @Test
    void theExplanationNamesNoFolderWhenTheStationPermissionDecided() {
        var explained = accessService.explainLevel(access(true, false), null, fileInChild);
        assertEquals(KbAccessLevel.MANAGE, explained.level());
        assertEquals(null, explained.source());
    }

    @Test
    void silenceInheritsFromTheFolderAbove() {
        memberGroupRepo.addMember(trainers.id(), member.id());
        accessService.setGrants(
                rootFolder, null, List.of(new GrantEntry(null, trainers.id(), null, null, KbAccessLevel.WRITE)));

        assertEquals(
                KbAccessLevel.WRITE,
                accessService.effectiveLevel(access(false, false), childFolder, null),
                "a folder with no grants of its own is exactly as accessible as its parent");
    }
}
