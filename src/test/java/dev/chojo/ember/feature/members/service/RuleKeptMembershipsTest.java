/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.service;

import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.api.auth.StepUpGuard;
import dev.chojo.ember.api.refusal.MemberRefusal;
import dev.chojo.ember.api.refusal.RefusalDetail;
import dev.chojo.ember.api.refusal.RefusalResponse;
import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.members.entity.FieldOrigin;
import dev.chojo.ember.feature.members.entity.FieldValueEntry;
import dev.chojo.ember.feature.members.entity.MemberGroup;
import dev.chojo.ember.feature.members.entity.ProfileFieldConfig;
import dev.chojo.ember.feature.members.entity.ProfileFieldScope;
import dev.chojo.ember.feature.members.entity.ProfileWriter;
import dev.chojo.ember.feature.members.entity.RuleNode;
import dev.chojo.ember.feature.members.entity.RuleOperator;
import dev.chojo.ember.feature.members.entity.RuleOwner;
import dev.chojo.ember.feature.members.entity.RuleValue;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.entity.UserTag;
import dev.chojo.ember.feature.members.repository.MemberGroupSetRepository;
import dev.chojo.ember.feature.question.FieldType;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * A group or tag that takes its members from a rule is kept by the rule alone: hand edits, hand-outs on joining
 * and group sets are refused, the automatic flows leave it be, and nothing a rule asks about can be deleted.
 */
class RuleKeptMembershipsTest extends RepositoryTestBase {
    private final List<Account> accounts = new ArrayList<>();
    private final MemberGroupSetRepository sets = new MemberGroupSetRepository();
    private GroupMembershipService memberships;
    private UserTagService tags;
    private MemberGroupService groups;
    private Station station;
    private StationMember manager;
    private StationMember child;

    @BeforeEach
    void freshStation() {
        memberships = new GroupMembershipService(
                memberGroupRepo,
                stationMemberRepo,
                memberRuleRepo,
                mock(StepUpGuard.class),
                () -> mock(MemberNameResolver.class),
                new DomainEventBus(Set.of()));
        tags = new UserTagService(
                userTagRepo, memberGroupRepo, memberRuleRepo, memberships, new DomainEventBus(Set.of()));
        groups = new MemberGroupService(
                memberGroupRepo,
                stationMemberRepo,
                userTagRepo,
                memberRuleRepo,
                memberships,
                newSelfAnsweredRules(),
                new DomainEventBus(Set.of()));
        station = stationRepo.create("Rule Kept Station " + System.nanoTime());
        manager = member(StationUserType.MANAGER);
        child = member(StationUserType.MEMBER);
    }

    @AfterEach
    void tearDown() {
        stationRepo.delete(station.id());
        accounts.forEach(account -> accountRepo.delete(account.id()));
    }

    private StationMember member(StationUserType type) {
        var account = accountRepo.create("rule-kept-" + System.nanoTime() + "@test.com", "Rule", type.name());
        accounts.add(account);
        var created = stationMemberRepo.create(station.id(), account.id());
        stationMemberRepo.setUserType(created.id(), type);
        return stationMemberRepo.findById(created.id()).orElseThrow();
    }

    private MemberGroup ruledGroup(String name) {
        var group = memberGroupRepo.create(station.id(), name);
        memberRuleRepo.replace(station.id(), RuleOwner.group(group.id()), RuleNode.all());
        return group;
    }

    private UserTag ruledTag(String name) {
        var tag = userTagRepo.create(station.id(), name);
        memberRuleRepo.replace(station.id(), RuleOwner.tag(tag.id()), RuleNode.all());
        return tag;
    }

    private List<Integer> groupIdsOf(StationMember member) {
        return memberGroupRepo.findGroupsForMember(member.id()).stream()
                .map(MemberGroup::id)
                .sorted()
                .toList();
    }

    private static void assertNames(String expected, RefusalResponse refused) {
        assertEquals(RefusalDetail.text(expected), refused.detail());
    }

    @Test
    void theMembersOfARuledGroupCannotBeSetByHand() {
        var ruled = ruledGroup("Jugend");
        var kept = memberGroupRepo.create(station.id(), "Atemschutz");

        var refused = assertRefused(
                MemberRefusal.MEMBERS_KEPT_BY_RULE,
                () -> memberships.setMembers(ruled, List.of(child.id()), false, stationSession(manager)));

        assertNames("Jugend", refused);
        assertTrue(memberGroupRepo.findMembers(ruled.id()).isEmpty());
        assertEquals(
                1,
                memberships
                        .setMembers(kept, List.of(child.id()), false, stationSession(manager))
                        .size());
    }

    @Test
    void aMemberCannotJoinOrLeaveARuledGroupFromTheirPage() {
        var ruled = ruledGroup("Jugend");
        var kept = memberGroupRepo.create(station.id(), "Atemschutz");

        assertRefused(
                MemberRefusal.MEMBERS_KEPT_BY_RULE,
                () -> memberships.replaceGroupsOfMember(child, List.of(ruled.id()), stationSession(manager)));
        memberGroupRepo.addMember(ruled.id(), child.id());
        assertRefused(
                MemberRefusal.MEMBERS_KEPT_BY_RULE,
                () -> memberships.replaceGroupsOfMember(child, List.of(), stationSession(manager)));

        memberships.replaceGroupsOfMember(child, List.of(ruled.id(), kept.id()), stationSession(manager));

        assertEquals(List.of(ruled.id(), kept.id()).stream().sorted().toList(), groupIdsOf(child));
    }

    @Test
    void theAutomaticFlowsLeaveARuledGroupAlone() {
        var ruled = ruledGroup("Jugend");

        assertFalse(memberships.joinAutomatically(ruled.id(), child.id()));
        assertEquals(List.of(), groupIdsOf(child));

        memberGroupRepo.addMember(ruled.id(), child.id());
        memberships.leaveAutomatically(ruled.id(), child.id());
        assertEquals(List.of(ruled.id()), groupIdsOf(child));
    }

    @Test
    void aRuledGroupIsNotHandedOutOnInvitesOrToAFlow() {
        var ruled = ruledGroup("Jugend");
        var kept = memberGroupRepo.create(station.id(), "Atemschutz");

        var refused = assertRefused(
                MemberRefusal.RULE_GROUP_NOT_HANDED_OUT,
                () -> memberships.requireInvitableInto(station.id(), ruled.id(), signedIn(manager)));
        assertNames("Jugend", refused);
        assertRefused(
                MemberRefusal.RULE_GROUP_NOT_HANDED_OUT,
                () -> memberships.requireAdmits(
                        station.id(),
                        ruled.id(),
                        StationUserType.MEMBER,
                        MemberRefusal.INVITE_GROUP_NOT_HERE,
                        MemberRefusal.GROUP_WRONG_USER_TYPE_FOR_MEMBER));

        memberships.requireInvitableInto(station.id(), kept.id(), signedIn(manager));
    }

    @Test
    void aRegistrationCodeCannotHandOutARuledGroup() {
        var codes = new RegistrationCodeService(registrationCodeRepo, memberships);
        var code = codes.create(station.id(), "RULED" + System.nanoTime(), 5);
        var ruled = ruledGroup("Jugend");
        var kept = memberGroupRepo.create(station.id(), "Atemschutz");

        assertRefused(
                MemberRefusal.RULE_GROUP_NOT_HANDED_OUT,
                () -> codes.setGroups(station.id(), code.id(), List.of(kept.id(), ruled.id())));

        assertEquals(List.of(), codes.findGroupIds(station.id(), code.id()));
        assertEquals(List.of(kept.id()), codes.setGroups(station.id(), code.id(), List.of(kept.id())));
    }

    @Test
    void aRuledGroupCannotJoinAGroupSet() {
        var rules = new GroupRulesService(memberGroupRepo, sets, memberships, new DomainEventBus(Set.of()));
        int levels = sets.create(station.id(), "Stufen").id();
        var ruled = ruledGroup("Jugend");
        var kept = memberGroupRepo.create(station.id(), "Atemschutz");

        var refused = assertRefused(
                MemberRefusal.RULE_GROUP_NOT_FOR_A_SET,
                () -> rules.update(
                        ruled,
                        "Jugend",
                        null,
                        null,
                        new GroupRulesService.GroupRules(levels, Set.of()),
                        false,
                        signedIn(manager)));
        assertNames("Jugend", refused);

        var moved = rules.update(
                kept,
                "Atemschutz",
                null,
                null,
                new GroupRulesService.GroupRules(levels, Set.of()),
                false,
                signedIn(manager));
        assertEquals(Integer.valueOf(levels), moved.groupSetId());
        rules.update(ruled, "Jugend 2", null, null, null, false, signedIn(manager));
    }

    @Test
    void theMembersOfARuledTagCannotBeSetByHand() {
        var ruled = ruledTag("Leitung");
        var kept = userTagRepo.create(station.id(), "Presse");

        var refused = assertRefused(
                MemberRefusal.MEMBERS_KEPT_BY_RULE,
                () -> tags.setMembers(ruled.id(), List.of(child.id()), signedIn(manager)));

        assertNames("Leitung", refused);
        assertTrue(userTagRepo.findMembers(ruled.id()).isEmpty());
        tags.setMembers(kept.id(), List.of(child.id()), signedIn(manager));
        assertEquals(1, userTagRepo.findMembers(kept.id()).size());
    }

    private MemberGroup grantingGroupAskingAbout(RuleNode check) {
        var granting = ruledGroup("Verwaltung");
        memberGroupRepo.addGroupPermission(
                granting.id(),
                stationMemberRepo
                        .findPermissionByName(StationPermission.MEMBER_MANAGE_GROUP)
                        .orElseThrow()
                        .id());
        memberRuleRepo.replace(station.id(), RuleOwner.group(granting.id()), RuleNode.all(check));
        return granting;
    }

    @Test
    void changingATagARuleOfAGrantingGroupAsksAboutNeedsItsPermissions() {
        var tag = userTagRepo.create(station.id(), "Presse");
        grantingGroupAskingAbout(RuleNode.tag(RuleOperator.IN, tag.id()));

        var refused = assertRefused(
                MemberRefusal.GROUP_GRANTS_MORE_THAN_YOURS_THROUGH_RULE,
                () -> tags.setMembers(tag.id(), List.of(child.id()), signedIn(manager)));

        assertNames("Verwaltung", refused);
        assertTrue(userTagRepo.findMembers(tag.id()).isEmpty());
        tags.setMembers(tag.id(), List.of(child.id()), signedIn(manager, StationPermission.MEMBER_MANAGE_GROUP));
        assertEquals(1, userTagRepo.findMembers(tag.id()).size());
    }

    @Test
    void changingAGroupARuleOfAGrantingGroupAsksAboutNeedsItsPermissions() {
        var asked = memberGroupRepo.create(station.id(), "Atemschutz");
        grantingGroupAskingAbout(RuleNode.group(RuleOperator.IN, asked.id()));

        assertRefused(
                MemberRefusal.GROUP_GRANTS_MORE_THAN_YOURS_THROUGH_RULE,
                () -> memberships.setMembers(asked, List.of(child.id()), false, stationSession(manager)));
        assertRefused(
                MemberRefusal.GROUP_GRANTS_MORE_THAN_YOURS_THROUGH_RULE,
                () -> memberships.replaceGroupsOfMember(child, List.of(asked.id()), stationSession(manager)));
        assertEquals(List.of(), groupIdsOf(child));

        memberships.replaceGroupsOfMember(
                child, List.of(asked.id()), stationSession(manager, StationPermission.MEMBER_MANAGE_GROUP));
        assertEquals(List.of(asked.id()), groupIdsOf(child));
    }

    @Test
    void aGroupATagOrAFieldAskedAboutByARuleCannotBeDeleted() {
        var askedGroup = memberGroupRepo.create(station.id(), "Atemschutz");
        var askedTag = userTagRepo.create(station.id(), "Presse");
        int askedField = profileFieldRepo
                .create(station.id(), "Stufe", FieldType.TEXT, ProfileFieldConfig.empty(), false, false, null)
                .id();
        var asking = ruledGroup("Jugend");
        memberRuleRepo.replace(
                station.id(),
                RuleOwner.group(asking.id()),
                RuleNode.all(
                        RuleNode.group(RuleOperator.IN, askedGroup.id()),
                        RuleNode.tag(RuleOperator.IN, askedTag.id()),
                        RuleNode.field(askedField, RuleOperator.EMPTY, RuleValue.empty())));
        var askingTag = ruledTag("Leitung");
        memberRuleRepo.replace(
                station.id(),
                RuleOwner.tag(askingTag.id()),
                RuleNode.all(RuleNode.group(RuleOperator.IN, askedGroup.id())));

        assertNames(
                "Jugend, Leitung",
                assertRefused(MemberRefusal.USED_BY_A_RULE, () -> groups.delete(askedGroup, signedIn(manager))));
        assertNames("Jugend", assertRefused(MemberRefusal.USED_BY_A_RULE, () -> tags.delete(askedTag.id())));
        assertNames(
                "Jugend", assertRefused(MemberRefusal.USED_BY_A_RULE, () -> profileFieldService.delete(askedField)));

        assertTrue(groups.delete(asking, signedIn(manager)));
        assertTrue(tags.delete(askingTag.id()));
        assertFalse(memberRuleRepo.hasRule(RuleOwner.group(asking.id())));
        assertTrue(groups.delete(askedGroup, signedIn(manager)));
        assertTrue(tags.delete(askedTag.id()));
        assertTrue(profileFieldService.delete(askedField));
    }

    /** A field a rule reads keeps its type, since the rule's operator was chosen for it; a rename is fine. */
    @Test
    void aFieldAskedAboutByARuleKeepsItsType() {
        int asked = profileFieldRepo
                .create(station.id(), "Lehrgang", FieldType.BOOLEAN, ProfileFieldConfig.empty(), false, false, null)
                .id();
        var asking = ruledGroup("Jugend");
        memberRuleRepo.replace(
                station.id(),
                RuleOwner.group(asking.id()),
                RuleNode.all(RuleNode.field(asked, RuleOperator.IS_YES, RuleValue.empty())));

        assertNames(
                "Jugend",
                assertRefused(
                        MemberRefusal.USED_BY_A_RULE,
                        () -> profileFieldService.update(
                                asked,
                                "Lehrgang",
                                FieldType.TEXT,
                                ProfileFieldConfig.empty(),
                                false,
                                false,
                                null,
                                false)));
        assertTrue(profileFieldService
                .update(asked, "Lehrgang 2", FieldType.BOOLEAN, ProfileFieldConfig.empty(), false, false, null, false)
                .isPresent());
    }

    /**
     * A question a permission-granting group's rule reads stays read only to members: neither the question itself
     * nor a new audience may open it for writing, and a refused change leaves it as it was.
     */
    @Test
    void aFieldAPermissionGroupReadsStaysReadOnly() {
        int managed = profileFieldRepo
                .create(station.id(), "Lehrgang", FieldType.BOOLEAN, ProfileFieldConfig.empty(), false, true, null)
                .id();
        profileFieldRepo.assignToRole(managed, ProfileFieldScope.MEMBER, 0, null, null, null);
        var admins = ruledGroup("Verwaltung");
        memberRuleRepo.replace(
                station.id(),
                RuleOwner.group(admins.id()),
                RuleNode.all(RuleNode.field(managed, RuleOperator.IS_YES, RuleValue.empty())));
        var edit = stationMemberRepo
                .findPermissionByName(StationPermission.MEMBER_EDIT)
                .orElseThrow();
        memberGroupRepo.addGroupPermission(admins.id(), edit.id());

        assertRefused(
                MemberRefusal.RULE_PERMISSIONS_FROM_OWN_ANSWER,
                () -> profileFieldService.update(
                        managed, "Lehrgang", FieldType.BOOLEAN, ProfileFieldConfig.empty(), false, false, null, false));
        assertTrue(profileFieldRepo.findById(managed).orElseThrow().readonly());

        assertRefused(
                MemberRefusal.RULE_PERMISSIONS_FROM_OWN_ANSWER,
                () -> profileFieldService.assignToRole(managed, ProfileFieldScope.GUARDIAN, 0, null, false, null));
        assertTrue(profileFieldRepo.findAssignments(managed).stream()
                .noneMatch(assignment -> assignment.role() == ProfileFieldScope.GUARDIAN));
    }

    /**
     * The management writes answers members cannot, but changing one that an admin group's rule reads moves people
     * into that group, which takes the group's permissions; an answer left as it was takes nothing.
     */
    @Test
    void changingAnAnswerAPermissionGroupReadsTakesItsPermissions() {
        int managed = profileFieldRepo
                .create(station.id(), "Lehrgang", FieldType.BOOLEAN, ProfileFieldConfig.empty(), false, true, null)
                .id();
        profileFieldRepo.assignToRole(managed, ProfileFieldScope.MEMBER, 0, null, null, null);
        var admins = ruledGroup("Verwaltung");
        memberRuleRepo.replace(
                station.id(),
                RuleOwner.group(admins.id()),
                RuleNode.all(RuleNode.field(managed, RuleOperator.IS_YES, RuleValue.empty())));
        var admin = stationMemberRepo
                .findPermissionByName(StationPermission.STATION_ADMINISTRATOR)
                .orElseThrow();
        memberGroupRepo.addGroupPermission(admins.id(), admin.id());
        var yes = List.of(new FieldValueEntry(managed, "\"true\"", FieldOrigin.STATION));
        var editor = ProfileWriter.station(true, Set.of(StationPermission.MEMBER_EDIT));

        assertNames(
                "Verwaltung",
                assertRefused(
                        MemberRefusal.GROUP_GRANTS_MORE_THAN_YOURS_THROUGH_RULE,
                        () -> profileFieldService.setValues(child.id(), yes, manager.id(), editor)));
        assertTrue(profileFieldRepo.findValue(child.id(), managed).isEmpty());

        var administrator = ProfileWriter.station(
                true, Set.of(StationPermission.MEMBER_EDIT, StationPermission.STATION_ADMINISTRATOR));
        profileFieldService.setValues(child.id(), yes, manager.id(), administrator);
        profileFieldService.setValues(child.id(), yes, manager.id(), editor);
        assertTrue(profileFieldRepo.findValue(child.id(), managed).isPresent());
    }

    /**
     * The answer may also reach the group through a tag kept by a rule, and the association, which holds none of
     * the station's permissions, may not change it either.
     */
    @Test
    void anAnswerReachingAPermissionGroupThroughATagTakesItsPermissionsToo() {
        int managed = profileFieldRepo
                .create(station.id(), "Lehrgang", FieldType.BOOLEAN, ProfileFieldConfig.empty(), false, true, null)
                .id();
        profileFieldRepo.assignToRole(managed, ProfileFieldScope.MEMBER, 0, null, null, null);
        var trained = ruledTag("Ausgebildet");
        memberRuleRepo.replace(
                station.id(),
                RuleOwner.tag(trained.id()),
                RuleNode.all(RuleNode.field(managed, RuleOperator.IS_YES, RuleValue.empty())));
        var admins = ruledGroup("Verwaltung");
        memberRuleRepo.replace(
                station.id(), RuleOwner.group(admins.id()), RuleNode.all(RuleNode.tag(RuleOperator.IN, trained.id())));
        var admin = stationMemberRepo
                .findPermissionByName(StationPermission.STATION_ADMINISTRATOR)
                .orElseThrow();
        memberGroupRepo.addGroupPermission(admins.id(), admin.id());
        var yes = List.of(new FieldValueEntry(managed, "\"true\"", FieldOrigin.STATION));

        assertRefused(
                MemberRefusal.GROUP_GRANTS_MORE_THAN_YOURS_THROUGH_RULE,
                () -> profileFieldService.setValues(
                        child.id(),
                        yes,
                        manager.id(),
                        ProfileWriter.station(true, Set.of(StationPermission.MEMBER_EDIT))));
        assertRefused(
                MemberRefusal.GROUP_GRANTS_MORE_THAN_YOURS_THROUGH_RULE,
                () -> profileFieldService.setValues(child.id(), yes, manager.id(), ProfileWriter.association()));
        assertTrue(profileFieldRepo.findValue(child.id(), managed).isEmpty());
    }

    /** Putting the question to a group without the read-only lock is refused the same way as to a kind of member. */
    @Test
    void aFieldAPermissionGroupReadsIsNotPutToAGroupWithoutTheLock() {
        int managed = profileFieldRepo
                .create(station.id(), "Lehrgang", FieldType.BOOLEAN, ProfileFieldConfig.empty(), false, true, null)
                .id();
        var admins = ruledGroup("Verwaltung");
        memberRuleRepo.replace(
                station.id(),
                RuleOwner.group(admins.id()),
                RuleNode.all(RuleNode.field(managed, RuleOperator.IS_YES, RuleValue.empty())));
        var edit = stationMemberRepo
                .findPermissionByName(StationPermission.MEMBER_EDIT)
                .orElseThrow();
        memberGroupRepo.addGroupPermission(admins.id(), edit.id());
        var youth = memberGroupRepo.create(station.id(), "Jugend");

        assertRefused(
                MemberRefusal.RULE_PERMISSIONS_FROM_OWN_ANSWER,
                () -> profileFieldService.assignToGroup(managed, youth.id(), 0, null, false, null));
        assertTrue(profileFieldRepo.findAssignments(managed).isEmpty());
        profileFieldService.assignToGroup(managed, youth.id(), 0, null, null, null);
    }

    /**
     * Moving a question into a group of questions puts it to everybody the group is put to, which may open it for
     * writing; while a permission group's rule reads it, that is refused and the question stays outside.
     */
    @Test
    void aFieldAPermissionGroupReadsDoesNotJoinASetMembersWrite() {
        int asked = profileFieldRepo
                .create(station.id(), "Lehrgang", FieldType.BOOLEAN, ProfileFieldConfig.empty(), false, false, null)
                .id();
        var admins = ruledGroup("Verwaltung");
        memberRuleRepo.replace(
                station.id(),
                RuleOwner.group(admins.id()),
                RuleNode.all(RuleNode.field(asked, RuleOperator.IS_YES, RuleValue.empty())));
        var edit = stationMemberRepo
                .findPermissionByName(StationPermission.MEMBER_EDIT)
                .orElseThrow();
        memberGroupRepo.addGroupPermission(admins.id(), edit.id());
        int answered = profileFieldRepo
                .create(station.id(), "Größe", FieldType.TEXT, ProfileFieldConfig.empty(), false, false, null)
                .id();
        profileFieldRepo.assignToRole(answered, ProfileFieldScope.MEMBER, 0, null, null, null);
        var set = profileFieldService.createSet(station.id(), "Ausbildung", false);
        profileFieldService.joinSet(answered, set.id());

        assertRefused(
                MemberRefusal.RULE_PERMISSIONS_FROM_OWN_ANSWER, () -> profileFieldService.joinSet(asked, set.id()));
        assertTrue(profileFieldRepo.findAssignments(asked).isEmpty());
        assertNull(profileFieldRepo.findById(asked).orElseThrow().fieldSetId());
    }

    @Test
    void aGroupOrTagAskedAboutByARuleCannotBeConverted() {
        var askedGroup = memberGroupRepo.create(station.id(), "Atemschutz");
        var askedTag = userTagRepo.create(station.id(), "Presse");
        var asking = ruledGroup("Jugend");
        memberRuleRepo.replace(
                station.id(),
                RuleOwner.group(asking.id()),
                RuleNode.all(
                        RuleNode.group(RuleOperator.IN, askedGroup.id()),
                        RuleNode.tag(RuleOperator.IN, askedTag.id())));

        assertRefused(MemberRefusal.USED_BY_A_RULE, () -> groups.convertToTag(askedGroup, signedIn(manager)));
        assertRefused(MemberRefusal.USED_BY_A_RULE, () -> tags.convertToGroup(askedTag.id()));

        assertTrue(memberGroupRepo.findById(askedGroup.id()).isPresent());
        assertTrue(userTagRepo.findById(askedTag.id()).isPresent());
    }

    @Test
    void aRuledGroupBecomesARuledTagAndBack() {
        var ruled = ruledGroup("Jugend");
        memberGroupRepo.addMember(ruled.id(), child.id());

        groups.convertToTag(ruled, signedIn(manager));

        var tag = userTagRepo.findByStation(station.id()).stream()
                .filter(candidate -> candidate.name().equals("Jugend"))
                .findFirst()
                .orElseThrow();
        assertTrue(memberRuleRepo.hasRule(RuleOwner.tag(tag.id())));
        assertEquals(1, userTagRepo.findMembers(tag.id()).size());

        tags.convertToGroup(tag.id());

        var group = memberGroupRepo.findByStation(station.id()).stream()
                .filter(candidate -> candidate.name().equals("Jugend"))
                .findFirst()
                .orElseThrow();
        assertTrue(memberRuleRepo.hasRule(RuleOwner.group(group.id())));
        assertEquals(List.of(group.id()), groupIdsOf(child));
    }
}
