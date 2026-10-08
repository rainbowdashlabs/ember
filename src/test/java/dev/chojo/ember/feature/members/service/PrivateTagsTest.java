/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.service;

import dev.chojo.ember.api.MemberIdentity;
import dev.chojo.ember.api.refusal.MemberRefusal;
import dev.chojo.ember.api.refusal.Refusal;
import dev.chojo.ember.api.refusal.RefusalResponse;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.entity.TagVisibility;
import dev.chojo.ember.feature.members.entity.UserTag;
import dev.chojo.ember.feature.restriction.RestrictionSelection;
import dev.chojo.ember.feature.restriction.RestrictionType;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrivateTagsTest extends RepositoryTestBase {
    private static UserTagService tags;
    private static Station station;
    private static Account account;
    private static StationMember member;
    private static int newsId;

    @BeforeAll
    static void setup() {
        tags = new UserTagService(userTagRepo, memberGroupRepo);
        station = stationRepo.create("PrivateTagStation");
        account = accountRepo.create("private-tags@test.com", "Private", "Tagged");
        member = stationMemberRepo.create(station.id(), account.id());
        newsId = newsRepo.create(
                        station.id(), "News", "text", "<p>text</p>", new MemberIdentity(station.uid(), member.uid()))
                .id();
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
        accountRepo.delete(account.id());
    }

    private static UserTag privateTagOn(String name, StationMember carrier) {
        var tag = tags.create(station.id(), name, null, TagVisibility.PRIVATE);
        userTagRepo.addMember(tag.id(), carrier.id());
        return tag;
    }

    private static RestrictionSelection byTag(int tagId) {
        return new RestrictionSelection(List.of(), List.of(), List.of(tagId), List.of(), null);
    }

    private static void assertRefused(Refusal expected, Executable call) {
        assertEquals(expected, assertThrows(RefusalResponse.class, call).refusal());
    }

    @Test
    void aTagIsCreatedWithTheVisibilityAskedFor() {
        var tag = tags.create(station.id(), "Created private", "#123456", TagVisibility.PRIVATE);

        var stored = userTagRepo.findById(tag.id()).orElseThrow();
        assertEquals(TagVisibility.PRIVATE, stored.visibility());
        assertEquals("#123456", stored.color());
    }

    @Test
    void aPrivateTagCannotChooseWhoSeesSomething() {
        var tag = privateTagOn("Restricts nothing", member);

        assertRefused(
                MemberRefusal.TAG_PRIVATE_CHOOSES_NOBODY,
                () -> restrictionService.setRestrictions(RestrictionType.NEWS, newsId, byTag(tag.id())));
    }

    @Test
    void aTagStillChoosingPeopleCannotBecomePrivate() {
        var tag = tags.create(station.id(), "Chooses readers");
        restrictionService.setRestrictions(RestrictionType.NEWS, newsId, byTag(tag.id()));

        assertRefused(
                MemberRefusal.TAG_PRIVATE_STILL_CHOOSES,
                () -> tags.update(tag.id(), tag.name(), null, TagVisibility.PRIVATE, 0));
        assertEquals(
                TagVisibility.PLAIN,
                userTagRepo.findById(tag.id()).orElseThrow().visibility());

        restrictionService.setRestrictions(RestrictionType.NEWS, newsId, RestrictionSelection.empty());
    }

    @Test
    void aTagChoosingNobodyBecomesPrivate() {
        var tag = tags.create(station.id(), "Becomes private");

        assertTrue(tags.update(tag.id(), tag.name(), null, TagVisibility.PRIVATE, 0));
        assertEquals(
                TagVisibility.PRIVATE,
                userTagRepo.findById(tag.id()).orElseThrow().visibility());
    }

    @Test
    void aPrivateTagCannotBecomeAGroup() {
        var tag = privateTagOn("Not a group", member);

        assertRefused(MemberRefusal.TAG_PRIVATE_NOT_A_GROUP, () -> tags.convertToGroup(tag.id()));
        assertTrue(userTagRepo.findById(tag.id()).isPresent());
    }

    @Test
    void restrictionsDoNotSeeAPrivateTag() {
        var tag = privateTagOn("Unseen by restrictions", member);

        assertFalse(
                restrictionService.memberOf(member.id()).orElseThrow().tagIds().contains(tag.id()));
        assertFalse(restrictionService
                .membersOf(List.of(member.id()))
                .get(member.id())
                .tagIds()
                .contains(tag.id()));
        assertFalse(userTagRepo
                .findOpenTagIdsOfMembers(List.of(member.id()))
                .getOrDefault(member.id(), Set.of())
                .contains(tag.id()));
    }

    @Test
    void aMemberQuestionNarrowedToAPrivateTagLetsNobodyThrough() {
        var tag = privateTagOn("Unanswerable", member);
        var eligibility = new StationMemberEligibility(memberGroupRepo, stationMemberRepo, userTagRepo);

        assertFalse(eligibility.hasTag(member.id(), tag.id()));
    }

    @Test
    void aPageMemberListOfAPrivateTagListsNobody() {
        var tag = privateTagOn("Not on pages", member);

        assertTrue(stationMemberRepo.findOfficersByTag(station.id(), tag.id()).isEmpty());
    }
}
