/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.service;

import dev.chojo.ember.api.StationSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.api.refusal.MemberRefusal;
import dev.chojo.ember.api.refusal.Refusal;
import dev.chojo.ember.api.refusal.RefusalResponse;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.members.entity.FieldOrigin;
import dev.chojo.ember.feature.members.entity.ProfileFieldConfig;
import dev.chojo.ember.feature.members.entity.RuleField;
import dev.chojo.ember.feature.members.entity.RuleMember;
import dev.chojo.ember.feature.members.entity.RuleNode;
import dev.chojo.ember.feature.members.entity.RuleNodeKind;
import dev.chojo.ember.feature.members.entity.RuleOperator;
import dev.chojo.ember.feature.members.entity.RuleOwner;
import dev.chojo.ember.feature.members.entity.RuleSubject;
import dev.chojo.ember.feature.members.entity.RuleUnit;
import dev.chojo.ember.feature.members.entity.RuleValue;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.entity.TagVisibility;
import dev.chojo.ember.feature.members.repository.MemberGroupSetRepository;
import dev.chojo.ember.feature.question.FieldType;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import tools.jackson.databind.node.JsonNodeFactory;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Checking, saving, trying out and removing the rule of a group or tag. */
class MemberRuleEditServiceTest extends RepositoryTestBase {
    private final List<Account> accounts = new ArrayList<>();
    private Station station;
    private Station other;
    private MemberRuleService rules;
    private MemberRuleEditService edits;
    private StationSession manager;
    private StationMember anna;
    private StationMember ben;
    private RuleOwner group;
    private RuleOwner tag;
    private int juleica;
    private int birth;
    private int foreignField;
    private int clusterId;
    private int clusterField;

    @BeforeEach
    void setUp() {
        station = stationRepo.create("Rule Edit Station");
        other = stationRepo.create("Rule Edit Other");
        rules = mock(MemberRuleService.class);
        var names = mock(MemberNameResolver.class);
        when(names.identified(anyInt())).thenAnswer(call -> "#" + call.getArgument(0));
        edits = new MemberRuleEditService(
                memberRuleRepo,
                memberGroupRepo,
                userTagRepo,
                profileFieldRepo,
                () -> clusterProfileFieldService,
                stationRepo,
                newGroupMemberships(),
                rules,
                () -> names);
        anna = member("Anna");
        ben = member("Ben");
        manager = stationSession(
                anna,
                StationPermission.MEMBER_MANAGE_GROUP,
                StationPermission.MEMBER_MANAGE_TAGS,
                StationPermission.MEMBER_READ);
        group = RuleOwner.group(
                memberGroupRepo.create(station.id(), "Mit JuLeiCa").id());
        tag = RuleOwner.tag(userTagRepo.create(station.id(), "Leitung").id());
        juleica = field(station.id(), "JuLeiCa", FieldType.BOOLEAN);
        birth = field(station.id(), "Geburtsdatum", FieldType.BIRTH_DATE);
        foreignField = field(other.id(), "Fremd", FieldType.BOOLEAN);
        profileFieldRepo.setValue(anna.id(), juleica, JsonNodeFactory.instance.booleanNode(true));
        clusterId = clusterRepo.create("Rule Edit Cluster", null, station.id()).id();
        stationRepo.setCluster(station.id(), clusterId);
        clusterField = clusterProfileFieldRepo
                .create(
                        clusterId,
                        "Verband",
                        FieldType.DATE,
                        ProfileFieldConfig.empty(),
                        false,
                        false,
                        null,
                        false,
                        false,
                        null)
                .id();
    }

    @AfterEach
    void tearDown() {
        memberRuleRepo.delete(group);
        memberRuleRepo.delete(tag);
        stationRepo.setCluster(station.id(), null);
        clusterRepo.delete(clusterId);
        stationRepo.delete(station.id());
        stationRepo.delete(other.id());
        accounts.forEach(account -> accountRepo.delete(account.id()));
    }

    private StationMember member(String name) {
        var account = accountRepo.create(name.toLowerCase() + "@rule-edit.test", name, "Rule");
        accounts.add(account);
        return stationMemberRepo.create(station.id(), account.id());
    }

    private int field(int stationId, String name, FieldType type) {
        return profileFieldRepo
                .create(stationId, name, type, ProfileFieldConfig.empty(), false, false, null)
                .id();
    }

    private RuleNode withJuleica() {
        return RuleNode.all(RuleNode.field(juleica, RuleOperator.IS_YES, RuleValue.empty()));
    }

    private static void refused(Refusal refusal, Executable call) {
        assertEquals(refusal, assertThrows(RefusalResponse.class, call).refusal());
    }

    private void refusedOnSave(Refusal refusal, RuleNode rule) {
        refused(refusal, () -> edits.save(manager, group, rule));
    }

    @Test
    void aValidRuleIsSavedAndTheStationRunsAfterwards() {
        var rule = RuleNode.all(
                RuleNode.any(RuleNode.tag(RuleOperator.IN, tag.id()), RuleNode.none()),
                RuleNode.userType(RuleOperator.ONE_OF, List.of(StationUserType.MEMBER)),
                RuleNode.memberSince(RuleOperator.AT_LEAST, 1, RuleUnit.YEARS, LocalDate.of(2027, 1, 1)),
                RuleNode.age(RuleOperator.AT_LEAST, birth, RuleValue.ofYears(10), LocalDate.of(2027, 1, 1)),
                RuleNode.clusterAge(RuleOperator.LESS_THAN, clusterField, RuleValue.ofYears(30), null),
                RuleNode.clusterField(clusterField, RuleOperator.EMPTY, RuleValue.empty()),
                RuleNode.field(juleica, RuleOperator.IS_YES, RuleValue.empty()));

        assertEquals(rule, edits.save(manager, group, rule));

        assertEquals(Optional.of(rule), edits.find(group));
        verify(rules).stationChanged(station.id());
    }

    @Test
    void theTopOfARuleIsABlock() {
        refusedOnSave(
                MemberRefusal.RULE_NEEDS_A_BLOCK, RuleNode.field(juleica, RuleOperator.IS_YES, RuleValue.empty()));
        verify(rules, never()).stationChanged(station.id());
    }

    @Test
    void aRuleCannotGrowWithoutEnd() {
        var deep = RuleNode.all();
        for (int i = 0; i < MemberRuleEditService.MAX_DEPTH; i++) deep = RuleNode.all(deep);
        refusedOnSave(MemberRefusal.RULE_TOO_LARGE, deep);

        var wide = new ArrayList<RuleNode>();
        for (int i = 0; i <= MemberRuleEditService.MAX_CHECKS; i++) {
            wide.add(RuleNode.field(juleica, RuleOperator.IS_YES, RuleValue.empty()));
        }
        refusedOnSave(MemberRefusal.RULE_TOO_LARGE, RuleNode.block(RuleNodeKind.ALL, wide));
    }

    /** Each way a check can miss what it compares is refused before anything is written. */
    @Test
    void anIncompleteCheckIsRefused() {
        var incomplete = List.of(
                RuleNode.check(RuleSubject.FIELD, RuleOperator.IN, juleica, null, null, null, null, null),
                RuleNode.check(RuleSubject.FIELD, RuleOperator.ONE_OF, juleica, null, null, null, null, null),
                RuleNode.check(RuleSubject.FIELD, RuleOperator.IS_YES, juleica, clusterField, null, null, null, null),
                RuleNode.check(RuleSubject.FIELD, RuleOperator.IS_YES, null, null, null, null, null, null),
                RuleNode.check(RuleSubject.CLUSTER_FIELD, RuleOperator.EMPTY, juleica, null, null, null, null, null),
                RuleNode.check(
                        RuleSubject.AGE, RuleOperator.AT_LEAST, null, null, null, null, RuleValue.ofYears(1), null),
                RuleNode.age(RuleOperator.AT_LEAST, juleica, RuleValue.ofYears(1), null),
                RuleNode.check(RuleSubject.GROUP, RuleOperator.IN, juleica, null, group.id(), null, null, null),
                RuleNode.check(RuleSubject.GROUP, RuleOperator.IN, null, null, null, null, null, null),
                RuleNode.check(RuleSubject.TAG, RuleOperator.IN, null, null, null, null, null, null),
                RuleNode.check(
                        RuleSubject.USER_TYPE,
                        RuleOperator.ONE_OF,
                        juleica,
                        null,
                        null,
                        null,
                        RuleValue.ofUserTypes(List.of(StationUserType.MEMBER)),
                        null),
                RuleNode.check(RuleSubject.USER_TYPE, null, null, null, null, null, null, null),
                RuleNode.field(juleica, RuleOperator.IS_YES, RuleValue.empty(), LocalDate.of(2027, 1, 1)),
                new RuleNode(RuleNodeKind.ALL, List.of(), RuleSubject.GROUP, null, null, null, null, null, null, null),
                new RuleNode(
                        RuleNodeKind.CHECK,
                        List.of(RuleNode.all()),
                        RuleSubject.USER_TYPE,
                        RuleOperator.ONE_OF,
                        null,
                        null,
                        null,
                        null,
                        RuleValue.ofUserTypes(List.of(StationUserType.TEAM)),
                        null));
        for (var check : incomplete) refusedOnSave(MemberRefusal.RULE_CHECK_INCOMPLETE, RuleNode.all(check));
        assertEquals(Optional.empty(), edits.find(group));
    }

    @Test
    void aTargetOfAnotherStationIsNotHere() {
        var otherGroup = memberGroupRepo.create(other.id(), "Fremd").id();
        var otherTag = userTagRepo.create(other.id(), "Fremd").id();
        var notHere = List.of(
                RuleNode.field(foreignField, RuleOperator.IS_YES, RuleValue.empty()),
                RuleNode.field(999_999, RuleOperator.IS_YES, RuleValue.empty()),
                RuleNode.group(RuleOperator.IN, otherGroup),
                RuleNode.tag(RuleOperator.IN, otherTag),
                RuleNode.clusterField(999_999, RuleOperator.EMPTY, RuleValue.empty()));
        for (var check : notHere) refusedOnSave(MemberRefusal.RULE_TARGET_NOT_HERE, RuleNode.all(check));
    }

    /** An association field of a station without an association is not here either. */
    @Test
    void anAssociationFieldNeedsAnAssociation() {
        stationRepo.setCluster(station.id(), null);
        refusedOnSave(
                MemberRefusal.RULE_TARGET_NOT_HERE,
                RuleNode.all(RuleNode.clusterField(clusterField, RuleOperator.EMPTY, RuleValue.empty())));
    }

    @Test
    void rulesMayNotAskAboutEachOtherInACircle() {
        edits.save(manager, group, RuleNode.all(RuleNode.tag(RuleOperator.IN, tag.id())));

        var refusal = assertThrows(
                RefusalResponse.class,
                () -> edits.save(manager, tag, RuleNode.all(RuleNode.group(RuleOperator.IN, group.id()))));

        assertEquals(MemberRefusal.RULE_IN_A_CIRCLE, refusal.refusal());
        assertTrue(String.valueOf(refusal.detail()).contains("Mit JuLeiCa"));
        assertEquals(Optional.empty(), edits.find(tag));
    }

    @Test
    void aGroupInASetTakesNoRule() {
        var set = memberGroupSetRepo().create(station.id(), "Stufen");
        memberGroupRepo.assignSet(group.id(), set.id());

        refusedOnSave(MemberRefusal.RULE_GROUP_IN_A_SET, withJuleica());
    }

    /** A rule for a group that grants permissions needs a caller who holds them all. */
    @Test
    void aRuleForAGroupThatGrantsMoreThanYoursIsRefused() {
        var admin = stationMemberRepo
                .findPermissionByName(StationPermission.MEMBER_EDIT)
                .orElseThrow();
        memberGroupRepo.addGroupPermission(group.id(), admin.id());

        refusedOnSave(MemberRefusal.GROUP_GRANTS_MORE_THAN_YOURS_ON_RULE, withJuleica());

        var holder = stationSession(
                ben,
                StationPermission.MEMBER_MANAGE_GROUP,
                StationPermission.MEMBER_EDIT,
                StationPermission.MEMBER_READ);
        assertEquals(withJuleica(), edits.save(holder, group, withJuleica()));
    }

    /** A rule that reads member data needs the right to read members; one on groups and types does not. */
    @Test
    void aRuleReadingMemberDataTakesTheRightToReadMembers() {
        var tagsOnly = stationSession(ben, StationPermission.MEMBER_MANAGE_GROUP);
        for (var reading : List.of(
                withJuleica(),
                RuleNode.all(RuleNode.age(RuleOperator.AT_LEAST, birth, RuleValue.ofYears(1), null)),
                RuleNode.all(RuleNode.memberSince(RuleOperator.AT_LEAST, 1, RuleUnit.DAYS, null)),
                RuleNode.all(RuleNode.clusterField(clusterField, RuleOperator.EMPTY, RuleValue.empty())))) {
            refused(MemberRefusal.RULE_READS_MEMBER_DATA, () -> edits.save(tagsOnly, group, reading));
            refused(MemberRefusal.RULE_READS_MEMBER_DATA, () -> edits.preview(tagsOnly, group, reading));
        }
        var plain = RuleNode.all(
                RuleNode.tag(RuleOperator.IN, tag.id()),
                RuleNode.userType(RuleOperator.ONE_OF, List.of(StationUserType.MEMBER)));
        assertEquals(plain, edits.save(tagsOnly, group, plain));
    }

    /** A private tag labels people and never chooses them, not even through a rule. */
    @Test
    void aPrivateTagChoosesNobody() {
        var hidden = userTagRepo
                .create(station.id(), "Vertraulich", null, TagVisibility.PRIVATE)
                .id();
        refusedOnSave(MemberRefusal.TAG_PRIVATE_CHOOSES_NOBODY, RuleNode.all(RuleNode.tag(RuleOperator.IN, hidden)));
    }

    /** Saving the rule of a tag that a permission-granting group's rule asks about needs that group's rights. */
    @Test
    void aRuleReachingAGroupThatGrantsMoreThanYoursIsRefused() {
        var edit = stationMemberRepo
                .findPermissionByName(StationPermission.MEMBER_EDIT)
                .orElseThrow();
        memberGroupRepo.addGroupPermission(group.id(), edit.id());
        memberRuleRepo.replace(station.id(), group, RuleNode.all(RuleNode.tag(RuleOperator.IN, tag.id())));

        refused(
                MemberRefusal.GROUP_GRANTS_MORE_THAN_YOURS_THROUGH_RULE,
                () -> edits.save(manager, tag, RuleNode.all()));

        var holder = stationSession(
                ben,
                StationPermission.MEMBER_MANAGE_TAGS,
                StationPermission.MEMBER_EDIT,
                StationPermission.MEMBER_READ);
        assertEquals(RuleNode.all(), edits.save(holder, tag, RuleNode.all()));
    }

    /** A group a registration code hands out takes no rule, since the code would place nobody in it any more. */
    @Test
    void aGroupHandedOutOnJoiningTakesNoRule() {
        var code = registrationCodeRepo.create(station.id(), "RULECODE1", 5);
        registrationCodeRepo.addGroup(code.id(), group.id());

        refusedOnSave(MemberRefusal.RULE_GROUP_HANDED_OUT, withJuleica());
    }

    @Test
    void theRuleKeptOwnersHidePrivateTagsFromReadersWhoMayNotSeeThem() {
        var hidden = RuleOwner.tag(userTagRepo
                .create(station.id(), "Vertraulich", null, TagVisibility.PRIVATE)
                .id());
        memberRuleRepo.replace(station.id(), hidden, RuleNode.all());
        memberRuleRepo.replace(station.id(), group, RuleNode.all());

        assertEquals(List.of(hidden.id()), edits.owners(manager).tagIds());
        var reader = stationSession(ben);
        assertEquals(List.of(group.id()), edits.owners(reader).groupIds());
        assertTrue(edits.owners(reader).tagIds().isEmpty());
        memberRuleRepo.delete(hidden);
    }

    @Test
    void aPreviewSaysWhoWouldJoinAndLeaveWithoutSaving() {
        memberGroupRepo.addMember(group.id(), ben.id());

        var preview = edits.preview(manager, group, withJuleica());

        assertEquals(List.of(new RuleMember(anna.id(), "#" + anna.id())), preview.matching());
        assertEquals(List.of(new RuleMember(anna.id(), "#" + anna.id())), preview.joining());
        assertEquals(List.of(new RuleMember(ben.id(), "#" + ben.id())), preview.leaving());
        assertEquals(Optional.empty(), edits.find(group));
    }

    @Test
    void aPreviewOfATagReadsTheTag() {
        userTagRepo.addMember(tag.id(), anna.id());

        var preview = edits.preview(manager, tag, withJuleica());

        assertEquals(1, preview.matching().size());
        assertTrue(preview.joining().isEmpty());
        assertTrue(preview.leaving().isEmpty());
        refused(MemberRefusal.RULE_NEEDS_A_BLOCK, () -> edits.preview(manager, tag, RuleNode.tag(RuleOperator.IN, 1)));
    }

    @Test
    void removingARuleKeepsTheMembers() {
        edits.save(manager, group, withJuleica());
        memberGroupRepo.addMember(group.id(), anna.id());

        assertTrue(edits.remove(group));
        assertFalse(edits.remove(group));
        assertEquals(1, memberGroupRepo.findMembers(group.id()).size());
    }

    /** The fields a rule can read are the station's and its association's, without those no check reads. */
    @Test
    void theReadableFieldsComeFromStationAndAssociation() {
        field(station.id(), "Abschnitt", FieldType.SECTION);

        var fields = edits.fields(station.id());

        assertEquals(
                Set.of(
                        new RuleField(FieldOrigin.STATION, juleica, "JuLeiCa", FieldType.BOOLEAN, List.of()),
                        new RuleField(FieldOrigin.STATION, birth, "Geburtsdatum", FieldType.BIRTH_DATE, List.of())),
                Set.copyOf(fields.subList(0, 2)));
        assertEquals(
                new RuleField(FieldOrigin.CLUSTER, clusterField, "Verband", FieldType.DATE, List.of()), fields.get(2));
        assertEquals(3, fields.size());
        stationRepo.setCluster(station.id(), null);
        assertEquals(2, edits.fields(station.id()).size());
    }

    @Test
    void theOperatorTableNamesEveryOfferedOperator() {
        var table = MemberRuleEditService.operators();
        assertTrue(table.stream()
                .anyMatch(option -> option.subject() == RuleSubject.FIELD
                        && option.fieldType() == FieldType.EXPIRY_DATE
                        && option.operator() == RuleOperator.EXPIRES_WITHIN));
        assertTrue(
                table.stream().anyMatch(option -> option.subject() == RuleSubject.GROUP && option.fieldType() == null));
        assertTrue(table.stream()
                .noneMatch(option -> option.subject() == RuleSubject.AGE && option.fieldType() == FieldType.TEXT));
    }

    private static MemberGroupSetRepository memberGroupSetRepo() {
        return new MemberGroupSetRepository();
    }
}
