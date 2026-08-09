/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.restriction.service;

import de.chojo.sadu.queries.api.call.Call;
import de.chojo.sadu.queries.api.query.Query;
import dev.chojo.ember.api.MemberIdentity;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.members.entity.MemberGroup;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.entity.UserTag;
import dev.chojo.ember.feature.restriction.RestrictionMode;
import dev.chojo.ember.feature.restriction.RestrictionSelection;
import dev.chojo.ember.feature.restriction.RestrictionType;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class RestrictionServiceTest extends RepositoryTestBase {

    private static final RestrictionType TYPE = RestrictionType.NEWS;
    private static final int UNKNOWN_MEMBER_ID = -4711;

    private static final List<Account> accounts = new ArrayList<>();

    private static Station station;
    private static MemberGroup group;
    private static UserTag tag;
    private static StationMember groupMember;
    private static StationMember tagMember;
    private static StationMember bothMember;
    private static StationMember typeMember;
    private static StationMember directMember;
    private static StationMember managerMember;
    private static StationMember outsider;
    private static MemberIdentity author;

    @BeforeAll
    static void setup() {
        station = stationRepo.create("RestrictionServiceStation");
        group = memberGroupRepo.create(station.id(), "Restricted Group");
        tag = userTagRepo.create(station.id(), "Restricted Tag");

        groupMember = createMember("restriction-group@test.com");
        tagMember = createMember("restriction-tag@test.com");
        bothMember = createMember("restriction-both@test.com");
        typeMember = createMember("restriction-type@test.com");
        directMember = createMember("restriction-direct@test.com");
        managerMember = createMember("restriction-manager@test.com");
        outsider = createMember("restriction-outsider@test.com");

        memberGroupRepo.addMember(group.id(), groupMember.id());
        memberGroupRepo.addMember(group.id(), bothMember.id());
        userTagRepo.addMember(tag.id(), tagMember.id());
        userTagRepo.addMember(tag.id(), bothMember.id());
        stationMemberRepo.setUserType(typeMember.id(), StationUserType.TEAM);

        var managerPermission = stationMemberRepo
                .findPermissionByName(StationPermission.NEWS_MANAGER)
                .orElseThrow();
        stationMemberRepo.grantPermission(managerMember.id(), managerPermission.id());

        author = stationMemberRepo.resolveIdentity(directMember.id());
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
        accounts.forEach(account -> accountRepo.delete(account.id()));
    }

    @Test
    void findRestrictionSetReturnsPersistedSelectionWithGivenMode() {
        int newsId = createNews("restriction-set");
        restrictionService.setRestrictions(
                TYPE,
                newsId,
                new RestrictionSelection(
                        List.of(StationUserType.TEAM),
                        List.of(group.id()),
                        List.of(tag.id()),
                        List.of(directMember.id()),
                        RestrictionMode.AND));

        var set = restrictionService.findRestrictionSet(TYPE, newsId, RestrictionMode.OR);

        assertEquals(RestrictionMode.OR, set.mode(), "the mode is taken from the argument, not from the rows");
        assertEquals(4, set.restrictions().size());
        assertEquals(List.of(StationUserType.TEAM), set.userTypes());
        assertEquals(List.of(group.id()), set.groupIds());
        assertEquals(List.of(tag.id()), set.tagIds());
        assertEquals(List.of(directMember.id()), set.memberIds());
    }

    @Test
    void setRestrictionsReplacesThePreviousSelection() {
        int newsId = createNews("restriction-replace");
        restrictionService.setRestrictions(TYPE, newsId, groupSelection());
        restrictionService.setRestrictions(TYPE, newsId, tagSelection());

        var set = restrictionService.findRestrictionSet(TYPE, newsId, RestrictionMode.AND);

        assertEquals(List.of(), set.groupIds(), "the previous group restriction is gone");
        assertEquals(List.of(tag.id()), set.tagIds());
    }

    @Test
    void findMembersPassingRestrictionIsEmptyForUnrestrictedEntity() {
        int newsId = createNews("restriction-none");

        assertEquals(Set.of(), restrictionService.findMembersPassingRestriction(TYPE, newsId, station.id()));
    }

    @Test
    void findMembersPassingRestrictionResolvesGroupMembersAndAddsManagers() {
        int newsId = createNews("restriction-members-group");
        restrictionService.setRestrictions(TYPE, newsId, groupSelection());

        assertEquals(
                Set.of(groupMember.id(), bothMember.id(), managerMember.id()),
                restrictionService.findMembersPassingRestriction(TYPE, newsId, station.id()));
    }

    @Test
    void findMembersPassingRestrictionResolvesTagMembersAndAddsManagers() {
        int newsId = createNews("restriction-members-tag");
        restrictionService.setRestrictions(TYPE, newsId, tagSelection());

        assertEquals(
                Set.of(tagMember.id(), bothMember.id(), managerMember.id()),
                restrictionService.findMembersPassingRestriction(TYPE, newsId, station.id()));
    }

    @Test
    void findMembersPassingRestrictionResolvesUserTypeMembersAndAddsManagers() {
        int newsId = createNews("restriction-members-type");
        restrictionService.setRestrictions(
                TYPE,
                newsId,
                new RestrictionSelection(
                        List.of(StationUserType.TEAM), List.of(), List.of(), List.of(), RestrictionMode.AND));

        assertEquals(
                Set.of(typeMember.id(), managerMember.id()),
                restrictionService.findMembersPassingRestriction(TYPE, newsId, station.id()));
    }

    @Test
    void findMembersPassingRestrictionKeepsDirectlyListedMembersAndAddsManagers() {
        int newsId = createNews("restriction-members-direct");
        restrictionService.setRestrictions(
                TYPE,
                newsId,
                new RestrictionSelection(List.of(), List.of(), List.of(), List.of(outsider.id()), RestrictionMode.AND));

        assertEquals(
                Set.of(outsider.id(), managerMember.id()),
                restrictionService.findMembersPassingRestriction(TYPE, newsId, station.id()));
    }

    @Test
    void findMembersPassingRestrictionUnionsEverySelectedSource() {
        int newsId = createNews("restriction-members-all");
        restrictionService.setRestrictions(
                TYPE,
                newsId,
                new RestrictionSelection(
                        List.of(StationUserType.TEAM),
                        List.of(group.id()),
                        List.of(tag.id()),
                        List.of(directMember.id()),
                        RestrictionMode.AND));

        assertEquals(
                Set.of(
                        groupMember.id(),
                        tagMember.id(),
                        bothMember.id(),
                        typeMember.id(),
                        directMember.id(),
                        managerMember.id()),
                restrictionService.findMembersPassingRestriction(TYPE, newsId, station.id()));
    }

    @Test
    void checkRestrictionLetsManagerPermissionHoldersBypassEverything() {
        int newsId = createNews("restriction-check-manager");
        restrictionService.setRestrictions(TYPE, newsId, groupSelection());

        assertTrue(
                restrictionService.checkRestriction(
                        TYPE, newsId, UNKNOWN_MEMBER_ID, Set.of(StationPermission.NEWS_MANAGER)),
                "the manager permission is answered before the member is even looked up");
        assertFalse(
                restrictionService.checkRestriction(TYPE, newsId, outsider.id(), Set.of()),
                "without the manager permission the restriction still applies");
    }

    @Test
    void checkRestrictionDeniesUnknownMembers() {
        int newsId = createNews("restriction-check-unknown");
        restrictionService.setRestrictions(TYPE, newsId, groupSelection());

        assertFalse(restrictionService.checkRestriction(
                TYPE, newsId, UNKNOWN_MEMBER_ID, Set.of(StationPermission.NEWS_EDIT)));
    }

    @Test
    void checkRestrictionResolvesUserTypeGroupsAndTagsOfTheMember() {
        int newsId = createNews("restriction-check-identity");
        restrictionService.setRestrictions(
                TYPE,
                newsId,
                new RestrictionSelection(
                        List.of(StationUserType.TEAM),
                        List.of(group.id()),
                        List.of(tag.id()),
                        List.of(),
                        RestrictionMode.OR));
        setRestrictionMode(newsId, RestrictionMode.OR);

        assertTrue(checkFor(newsId, groupMember), "the member's group is resolved");
        assertTrue(checkFor(newsId, tagMember), "the member's tag is resolved");
        assertTrue(checkFor(newsId, typeMember), "the member's user type is resolved");
        assertFalse(checkFor(newsId, outsider), "a member without group, tag or user type match is denied");
        assertFalse(checkFor(newsId, managerMember), "the bypass comes from the passed permissions, not the database");
    }

    @Test
    void checkRestrictionAppliesTheModeStoredOnTheEntity() {
        int newsId = createNews("restriction-check-mode");
        restrictionService.setRestrictions(
                TYPE,
                newsId,
                new RestrictionSelection(
                        List.of(), List.of(group.id()), List.of(tag.id()), List.of(), RestrictionMode.AND));

        assertFalse(checkFor(newsId, groupMember), "AND requires the tag as well");
        assertTrue(checkFor(newsId, bothMember), "AND is satisfied by group and tag together");

        setRestrictionMode(newsId, RestrictionMode.OR);

        assertTrue(checkFor(newsId, groupMember), "OR accepts the group alone");
    }

    @Test
    void checkRestrictionAcceptsDirectlyListedMembers() {
        int newsId = createNews("restriction-check-direct");
        restrictionService.setRestrictions(
                TYPE,
                newsId,
                new RestrictionSelection(
                        List.of(), List.of(group.id()), List.of(), List.of(outsider.id()), RestrictionMode.AND));

        assertTrue(checkFor(newsId, outsider), "a directly listed member passes without matching the group");
        assertTrue(checkFor(newsId, groupMember));
        assertFalse(checkFor(newsId, tagMember));
    }

    private static boolean checkFor(int newsId, StationMember member) {
        return restrictionService.checkRestriction(TYPE, newsId, member.id(), Set.of());
    }

    private static RestrictionSelection groupSelection() {
        return new RestrictionSelection(List.of(), List.of(group.id()), List.of(), List.of(), RestrictionMode.AND);
    }

    private static RestrictionSelection tagSelection() {
        return new RestrictionSelection(List.of(), List.of(), List.of(tag.id()), List.of(), RestrictionMode.AND);
    }

    private static StationMember createMember(String email) {
        var account = accountRepo.create(email, "Restriction", "Member");
        accounts.add(account);
        return stationMemberRepo.create(station.id(), account.id());
    }

    private static int createNews(String title) {
        return newsRepo.create(station.id(), title, title, "<p>%s</p>".formatted(title), author)
                .id();
    }

    /**
     * Writes the restriction mode onto the owning news article, which has no repository setter.
     */
    private static void setRestrictionMode(int newsId, RestrictionMode mode) {
        Query.query("UPDATE news SET restriction_mode = :mode WHERE id = :id;")
                .single(Call.call().bind("mode", mode.name()).bind("id", newsId))
                .update();
    }
}
