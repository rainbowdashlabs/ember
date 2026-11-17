/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.repository;

import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.members.entity.ExpiryState;
import dev.chojo.ember.feature.members.entity.ProfileFieldConfig;
import dev.chojo.ember.feature.members.entity.RuleChange;
import dev.chojo.ember.feature.members.entity.RuleNode;
import dev.chojo.ember.feature.members.entity.RuleOperator;
import dev.chojo.ember.feature.members.entity.RuleOwner;
import dev.chojo.ember.feature.members.entity.RuleUnit;
import dev.chojo.ember.feature.members.entity.RuleValue;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.question.FieldType;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Storing the rules of groups and tags, and writing who they match into the memberships. */
class MemberRuleStorageTest extends RepositoryTestBase {
    private final List<Account> accounts = new ArrayList<>();
    private Station station;
    private RuleOwner group;
    private RuleOwner tag;
    private int field;
    private int clusterId;
    private int clusterField;

    @BeforeEach
    void setUp() {
        station = stationRepo.create("Rule Storage Station");
        group = RuleOwner.group(memberGroupRepo.create(station.id(), "Jugend").id());
        tag = RuleOwner.tag(userTagRepo.create(station.id(), "Leitung").id());
        field = profileFieldRepo
                .create(station.id(), "Stufe", FieldType.CHOICE, ProfileFieldConfig.empty(), false, false, null)
                .id();
        clusterId =
                clusterRepo.create("Rule Storage Cluster", null, station.id()).id();
        clusterField = clusterProfileFieldRepo
                .create(
                        clusterId,
                        "Verband",
                        FieldType.NUMBER,
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
        clusterRepo.delete(clusterId);
        stationRepo.delete(station.id());
        accounts.forEach(account -> accountRepo.delete(account.id()));
    }

    private StationMember member(String name) {
        var account = accountRepo.create(name.toLowerCase() + "@rule-storage.test", name, "Rule");
        accounts.add(account);
        return stationMemberRepo.create(station.id(), account.id());
    }

    private RuleNode everyShape() {
        return RuleNode.all(
                RuleNode.any(
                        RuleNode.group(RuleOperator.NOT_IN, group.id()),
                        RuleNode.tag(RuleOperator.IN, tag.id()),
                        RuleNode.none()),
                RuleNode.userType(RuleOperator.ONE_OF, List.of(StationUserType.MEMBER, StationUserType.TEAM)),
                RuleNode.memberSince(RuleOperator.AT_LEAST, 1, RuleUnit.YEARS, LocalDate.of(2027, 1, 1)),
                RuleNode.age(RuleOperator.BETWEEN, field, RuleValue.ofYears(10, 14), null),
                RuleNode.field(field, RuleOperator.ONE_OF, RuleValue.ofAnswers(List.of("Bronze", "Silber"))),
                RuleNode.field(field, RuleOperator.STATE_IN, RuleValue.ofStates(List.of(ExpiryState.EXPIRING))),
                RuleNode.clusterField(
                        clusterField, RuleOperator.BETWEEN, RuleValue.ofNumbers(BigDecimal.ONE, BigDecimal.TEN)),
                RuleNode.field(field, RuleOperator.EMPTY, RuleValue.empty()));
    }

    @Test
    void aRuleComesBackAsItWasSaved() {
        var rule = everyShape();

        memberRuleRepo.replace(station.id(), group, rule);

        assertEquals(Optional.of(rule), memberRuleRepo.find(group));
        assertEquals(Optional.empty(), memberRuleRepo.find(tag));
    }

    /** A check that carried no value comes back with none, which reads as empty. */
    @Test
    void aCheckWithoutAValueComesBackWithout() {
        var rule = RuleNode.all(RuleNode.group(RuleOperator.IN, group.id()));

        memberRuleRepo.replace(station.id(), tag, rule);

        var found = memberRuleRepo.find(tag).orElseThrow();
        assertEquals(rule, found);
        assertEquals(RuleValue.empty(), found.checks().getFirst().valueOrEmpty());
    }

    @Test
    void savingAgainReplacesTheWholeTree() {
        memberRuleRepo.replace(station.id(), group, everyShape());
        var simpler = RuleNode.any(RuleNode.tag(RuleOperator.IN, tag.id()));

        memberRuleRepo.replace(station.id(), group, simpler);

        assertEquals(Optional.of(simpler), memberRuleRepo.find(group));
        assertEquals(Map.of(group, simpler), memberRuleRepo.findByStation(station.id()));
    }

    @Test
    void theRulesOfAStationAreKeyedByTheirGroupOrTag() {
        var forGroup = RuleNode.all(RuleNode.tag(RuleOperator.IN, tag.id()));
        var forTag = RuleNode.none(RuleNode.field(field, RuleOperator.NOT_EMPTY, RuleValue.empty()));
        memberRuleRepo.replace(station.id(), group, forGroup);
        memberRuleRepo.replace(station.id(), tag, forTag);

        assertEquals(Map.of(group, forGroup, tag, forTag), memberRuleRepo.findByStation(station.id()));
        assertTrue(memberRuleRepo.stationsWithRules().contains(station.id()));
    }

    @Test
    void deletingARuleLeavesTheGroupHandKept() {
        memberRuleRepo.replace(station.id(), group, everyShape());

        assertTrue(memberRuleRepo.delete(group));
        assertFalse(memberRuleRepo.delete(group));
        assertEquals(Optional.empty(), memberRuleRepo.find(group));
        assertFalse(memberRuleRepo.stationsWithRules().contains(station.id()));
    }

    /** Deleting the group deletes its rule; a group another rule asks about cannot be deleted. */
    @Test
    void aGroupInUseByARuleCannotBeDeleted() {
        var other = memberGroupRepo.create(station.id(), "Andere").id();
        memberRuleRepo.replace(station.id(), tag, RuleNode.all(RuleNode.group(RuleOperator.IN, other)));

        assertThrows(RuntimeException.class, () -> memberGroupRepo.delete(other));

        memberRuleRepo.replace(station.id(), group, RuleNode.all());
        assertTrue(memberGroupRepo.delete(group.id()));
        assertEquals(Optional.empty(), memberRuleRepo.find(group));
    }

    @Test
    void applyingMakesTheMembersExactlyTheGivenOnes() {
        var anna = member("Anna").id();
        var ben = member("Ben").id();
        var clara = member("Clara").id();
        memberGroupRepo.addMember(group.id(), anna);
        memberGroupRepo.addMember(group.id(), clara);

        var change = memberRuleRepo.apply(group, List.of(ben, anna));

        assertEquals(new RuleChange(group, List.of(ben), List.of(clara)), change);
        assertEquals(
                List.of(anna, ben),
                memberGroupRepo.findMembers(group.id()).stream()
                        .map(StationMember::id)
                        .sorted()
                        .toList());
        assertTrue(memberRuleRepo.apply(group, List.of(anna, ben)).isEmpty());
    }

    @Test
    void applyingFillsAndEmptiesATag() {
        var anna = member("Anna").id();
        var ben = member("Ben").id();

        assertEquals(new RuleChange(tag, List.of(anna, ben), List.of()), memberRuleRepo.apply(tag, List.of(ben, anna)));
        assertEquals(new RuleChange(tag, List.of(), List.of(anna, ben)), memberRuleRepo.apply(tag, List.of()));
        assertTrue(userTagRepo.findMembers(tag.id()).isEmpty());
        assertEquals(RuleChange.none(tag), memberRuleRepo.apply(tag, List.of()));
    }
}
