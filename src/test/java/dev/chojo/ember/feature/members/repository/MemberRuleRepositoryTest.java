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
import dev.chojo.ember.feature.members.entity.RuleNode;
import dev.chojo.ember.feature.members.entity.RuleNodeKind;
import dev.chojo.ember.feature.members.entity.RuleOperator;
import dev.chojo.ember.feature.members.entity.RuleSubject;
import dev.chojo.ember.feature.members.entity.RuleUnit;
import dev.chojo.ember.feature.members.entity.RuleValue;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.question.FieldType;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Who a member rule matches, run against a seeded station. Four members: Anna and Ben are in the team, Clara
 * holds a tag, and Dora has left the station, so no rule ever matches her.
 */
class MemberRuleRepositoryTest extends RepositoryTestBase {
    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 17);
    private static final LocalDate NEW_YEAR = LocalDate.of(2027, 1, 1);

    private static final List<Account> accounts = new ArrayList<>();
    private static final Map<Integer, String> names = new HashMap<>();
    private static Station station;
    private static int team;
    private static int tag;
    private static int juleica;
    private static int juleicaEnd;
    private static int badge;
    private static int birth;
    private static int note;
    private static int size;
    private static int buddies;
    private static int broken;
    private static int claraId;
    private static int clusterField;
    private static int clusterId;

    @BeforeAll
    static void seed() {
        station = stationRepo.create("Rule Station");
        team = memberGroupRepo.create(station.id(), "Team").id();
        tag = userTagRepo.create(station.id(), "Schwimmer").id();
        juleica = field("JuLeiCa", FieldType.BOOLEAN);
        juleicaEnd = field("JuLeiCa Ablaufdatum", FieldType.EXPIRY_DATE);
        badge = field("Jugendflamme", FieldType.CHOICE);
        birth = field("Geburtsdatum", FieldType.BIRTH_DATE);
        note = field("Notiz", FieldType.TEXT);
        size = field("Größe", FieldType.NUMBER);
        buddies = field("Freunde", FieldType.TEXT);
        broken = field("Altdaten", FieldType.DATE);
        clusterId = clusterRepo.create("Rule Cluster", null, station.id()).id();
        clusterField = clusterProfileFieldRepo
                .create(
                        clusterId,
                        "Verband",
                        FieldType.BOOLEAN,
                        ProfileFieldConfig.empty(),
                        false,
                        false,
                        null,
                        false,
                        false,
                        null)
                .id();

        var anna = member("Anna", StationUserType.TEAM, LocalDate.of(2025, 1, 1));
        memberGroupRepo.addMember(team, anna.id());
        value(anna, juleica, NODES.booleanNode(false));
        value(anna, juleicaEnd, NODES.stringNode("2027-03-01"));
        value(anna, birth, NODES.stringNode("2016-06-01"));
        value(anna, note, NODES.stringNode("  Atemschutz Lehrgang "));
        value(anna, size, NODES.numberNode(new BigDecimal("1.5")));
        value(anna, buddies, NODES.arrayNode().add(1));
        clusterProfileFieldRepo.setValue(anna.id(), clusterField, NODES.booleanNode(true));

        var ben = member("Ben", StationUserType.MEMBER, LocalDate.of(2026, 6, 1));
        memberGroupRepo.addMember(team, ben.id());
        value(ben, juleica, NODES.stringNode("true"));
        value(ben, juleicaEnd, NODES.stringNode("2028-06-01"));
        value(ben, badge, NODES.stringNode("Bronze"));
        value(ben, birth, NODES.stringNode("2017-03-01"));
        value(ben, size, NODES.stringNode("2,5"));
        value(ben, buddies, NODES.arrayNode());
        value(ben, broken, NODES.stringNode("0000-01-01"));

        var clara = member("Clara", StationUserType.MEMBER, LocalDate.of(2020, 1, 1));
        userTagRepo.addMember(tag, clara.id());
        value(clara, juleicaEnd, NODES.stringNode("2026-10-01"));
        value(clara, badge, NODES.objectNode());
        value(clara, birth, NODES.stringNode("2010-01-01"));
        value(clara, note, NODES.stringNode("null"));
        value(clara, size, NODES.stringNode("groß"));
        value(clara, broken, NODES.stringNode("2026-02-30"));
        claraId = clara.id();

        var dora = member("Dora", StationUserType.TEAM, LocalDate.of(2000, 1, 1));
        memberGroupRepo.addMember(team, dora.id());
        value(dora, juleica, NODES.booleanNode(false));
        stationMemberRepo.setFormer(dora.id(), true);
    }

    @AfterAll
    static void cleanup() {
        clusterRepo.delete(clusterId);
        stationRepo.delete(station.id());
        accounts.forEach(account -> accountRepo.delete(account.id()));
    }

    private static int field(String name, FieldType type) {
        return profileFieldRepo
                .create(station.id(), name, type, ProfileFieldConfig.empty(), false, false, null)
                .id();
    }

    private static StationMember member(String name, StationUserType type, LocalDate joined) {
        var account = accountRepo.create(name.toLowerCase() + "@rule.test", name, "Rule");
        accounts.add(account);
        var member = stationMemberRepo.create(station.id(), account.id());
        stationMemberRepo.setUserType(member.id(), type);
        stationMemberRepo.setJoinDate(member.id(), joined);
        names.put(member.id(), name);
        return member;
    }

    private static void value(StationMember member, int fieldId, JsonNode value) {
        value(member.id(), fieldId, value);
    }

    private static void value(int memberId, int fieldId, JsonNode value) {
        profileFieldRepo.setValue(memberId, fieldId, value);
    }

    private static Set<String> matching(RuleNode root) {
        return matching(root, List.of());
    }

    private static Set<String> matching(RuleNode root, List<StationUserType> bound) {
        var found = new TreeSet<String>();
        for (int id : memberRuleRepo.matching(station.id(), root, TODAY, bound)) found.add(names.get(id));
        return found;
    }

    private static Set<String> are(String... who) {
        return new TreeSet<>(List.of(who));
    }

    @Test
    void everybodyInTheTeamWithoutJuleica() {
        var rule = RuleNode.all(
                RuleNode.group(RuleOperator.IN, team), RuleNode.field(juleica, RuleOperator.IS_NO, RuleValue.empty()));
        assertEquals(are("Anna"), matching(rule));
    }

    @Test
    void tenByNewYearAMemberForAYearAndNoJugendflammeYet() {
        var rule = RuleNode.all(
                RuleNode.age(RuleOperator.AT_LEAST, birth, RuleValue.ofYears(10), NEW_YEAR),
                RuleNode.memberSince(RuleOperator.AT_LEAST, 1, RuleUnit.YEARS, NEW_YEAR),
                RuleNode.field(badge, RuleOperator.EMPTY, RuleValue.empty()));
        assertEquals(are("Anna", "Clara"), matching(rule));
    }

    @Test
    void juleicaRunningOutWithinTenMonths() {
        var rule = RuleNode.all(
                RuleNode.field(juleicaEnd, RuleOperator.EXPIRES_WITHIN, RuleValue.ofDuration(10, RuleUnit.MONTHS)));
        assertEquals(are("Anna"), matching(rule));
    }

    @Test
    void blocksJoinTheirChecks() {
        var inTeam = RuleNode.group(RuleOperator.IN, team);
        var tagged = RuleNode.tag(RuleOperator.IN, tag);
        assertEquals(are("Anna", "Ben", "Clara"), matching(RuleNode.any(inTeam, tagged)));
        assertEquals(are(), matching(RuleNode.all(inTeam, tagged)));
        assertEquals(are("Clara"), matching(RuleNode.none(inTeam)));
        assertEquals(are("Anna", "Ben", "Clara"), matching(RuleNode.all()));
        assertEquals(are(), matching(RuleNode.any()));
        assertEquals(are("Anna", "Ben", "Clara"), matching(RuleNode.none()));
    }

    @Test
    void membershipAndItsAbsence() {
        assertEquals(are("Clara"), matching(RuleNode.all(RuleNode.group(RuleOperator.NOT_IN, team))));
        assertEquals(are("Clara"), matching(RuleNode.all(RuleNode.tag(RuleOperator.IN, tag))));
        assertEquals(are("Anna", "Ben"), matching(RuleNode.all(RuleNode.tag(RuleOperator.NOT_IN, tag))));
    }

    @Test
    void userTypes() {
        assertEquals(
                are("Anna"),
                matching(RuleNode.all(RuleNode.userType(RuleOperator.ONE_OF, List.of(StationUserType.TEAM)))));
        assertEquals(
                are("Ben", "Clara"),
                matching(RuleNode.all(RuleNode.userType(RuleOperator.NONE_OF, List.of(StationUserType.TEAM)))));
    }

    /** A group bound to user types never takes a member of another type, whatever its rule says. */
    @Test
    void aBoundGroupKeepsToItsTypes() {
        assertEquals(are("Ben", "Clara"), matching(RuleNode.all(), List.of(StationUserType.MEMBER)));
    }

    @Test
    void membershipLengthCountsFromTheReferenceDay() {
        assertEquals(
                are("Anna", "Clara"),
                matching(RuleNode.all(RuleNode.memberSince(RuleOperator.AT_LEAST, 1, RuleUnit.YEARS, null))));
        assertEquals(
                are("Ben"),
                matching(RuleNode.all(RuleNode.memberSince(RuleOperator.LESS_THAN, 6, RuleUnit.MONTHS, null))));
        assertEquals(
                are("Anna", "Ben", "Clara"),
                matching(RuleNode.all(RuleNode.memberSince(RuleOperator.AT_LEAST, 30, RuleUnit.DAYS, null))));
    }

    /** Anna turns ten in June 2026, Ben in March 2027, Clara is sixteen. */
    @Test
    void agesCountWholeYears() {
        assertEquals(
                are("Anna", "Clara"),
                matching(RuleNode.all(RuleNode.age(RuleOperator.AT_LEAST, birth, RuleValue.ofYears(10), null))));
        assertEquals(
                are("Ben"),
                matching(RuleNode.all(RuleNode.age(RuleOperator.LESS_THAN, birth, RuleValue.ofYears(10), null))));
        assertEquals(
                are("Anna", "Ben"),
                matching(RuleNode.all(RuleNode.age(RuleOperator.BETWEEN, birth, RuleValue.ofYears(9, 10), null))));
        assertEquals(
                are("Anna", "Ben"),
                matching(RuleNode.all(RuleNode.age(
                        RuleOperator.BETWEEN, birth, RuleValue.ofYears(10, 10), LocalDate.of(2027, 3, 1)))));
    }

    @Test
    void yesOrNo() {
        assertEquals(
                are("Ben"), matching(RuleNode.all(RuleNode.field(juleica, RuleOperator.IS_YES, RuleValue.empty()))));
        assertEquals(
                are("Anna", "Clara"),
                matching(RuleNode.all(RuleNode.field(juleica, RuleOperator.IS_NOT_YES, RuleValue.empty()))));
    }

    /** No answer, a JSON null text, an empty object and an empty list all count as empty. */
    @Test
    void emptinessInEveryStoredShape() {
        assertEquals(
                are("Anna", "Clara"),
                matching(RuleNode.all(RuleNode.field(badge, RuleOperator.EMPTY, RuleValue.empty()))));
        assertEquals(
                are("Ben", "Clara"),
                matching(RuleNode.all(RuleNode.field(note, RuleOperator.EMPTY, RuleValue.empty()))));
        assertEquals(
                are("Anna"),
                matching(RuleNode.all(RuleNode.field(buddies, RuleOperator.NOT_EMPTY, RuleValue.empty()))));
    }

    @Test
    void choices() {
        assertEquals(
                are("Ben"),
                matching(RuleNode.all(
                        RuleNode.field(badge, RuleOperator.ONE_OF, RuleValue.ofAnswers(List.of("Bronze", "Gold"))))));
        assertEquals(
                are("Anna", "Clara"),
                matching(RuleNode.all(
                        RuleNode.field(badge, RuleOperator.NONE_OF, RuleValue.ofAnswers(List.of("Bronze"))))));
    }

    @Test
    void texts() {
        assertEquals(
                are("Anna"),
                matching(RuleNode.all(
                        RuleNode.field(note, RuleOperator.EQUALS, RuleValue.ofText("atemschutz lehrgang")))));
        assertEquals(
                are("Anna"),
                matching(RuleNode.all(RuleNode.field(note, RuleOperator.CONTAINS, RuleValue.ofText("SCHUTZ")))));
    }

    /** A decimal comma counts as a number; a word in a number field never does. */
    @Test
    void numbers() {
        var oneAndAHalf = new BigDecimal("1.5");
        assertEquals(
                are("Anna"),
                matching(RuleNode.all(RuleNode.field(size, RuleOperator.EQ, RuleValue.ofNumber(oneAndAHalf)))));
        assertEquals(
                are(), matching(RuleNode.all(RuleNode.field(size, RuleOperator.LT, RuleValue.ofNumber(oneAndAHalf)))));
        assertEquals(
                are("Anna"),
                matching(RuleNode.all(RuleNode.field(size, RuleOperator.LE, RuleValue.ofNumber(oneAndAHalf)))));
        assertEquals(
                are("Ben"),
                matching(RuleNode.all(RuleNode.field(size, RuleOperator.GT, RuleValue.ofNumber(oneAndAHalf)))));
        assertEquals(
                are("Anna", "Ben"),
                matching(RuleNode.all(RuleNode.field(size, RuleOperator.GE, RuleValue.ofNumber(oneAndAHalf)))));
        assertEquals(
                are("Ben"),
                matching(RuleNode.all(RuleNode.field(
                        size, RuleOperator.BETWEEN, RuleValue.ofNumbers(BigDecimal.TWO, BigDecimal.TEN)))));
    }

    @Test
    void dates() {
        assertEquals(
                are("Clara"),
                matching(RuleNode.all(
                        RuleNode.field(birth, RuleOperator.BEFORE, RuleValue.ofDate(LocalDate.of(2016, 1, 1))))));
        assertEquals(
                are("Anna", "Ben"),
                matching(RuleNode.all(
                        RuleNode.field(birth, RuleOperator.ON_OR_AFTER, RuleValue.ofDate(LocalDate.of(2016, 6, 1))))));
        assertEquals(
                are("Anna"),
                matching(RuleNode.all(RuleNode.field(
                        juleicaEnd, RuleOperator.WITHIN_NEXT, RuleValue.ofDuration(4, RuleUnit.MONTHS)))));
        assertEquals(
                are("Clara"),
                matching(RuleNode.all(RuleNode.field(
                        juleicaEnd, RuleOperator.WITHIN_LAST, RuleValue.ofDuration(2, RuleUnit.MONTHS)))));
        assertEquals(
                are("Ben"),
                matching(RuleNode.all(RuleNode.field(
                        juleicaEnd,
                        RuleOperator.WITHIN_NEXT,
                        RuleValue.ofDuration(1, RuleUnit.YEARS),
                        LocalDate.of(2027, 7, 1)))));
    }

    /** Anna's runs out in March, within the default month of warning only in February; Clara's ran out in October. */
    @Test
    void expiry() {
        assertEquals(
                are("Clara"),
                matching(RuleNode.all(RuleNode.field(juleicaEnd, RuleOperator.EXPIRED, RuleValue.empty()))));
        assertEquals(
                are("Anna", "Ben"),
                matching(RuleNode.all(RuleNode.field(
                        juleicaEnd, RuleOperator.STATE_IN, RuleValue.ofStates(List.of(ExpiryState.VALID))))));
        assertEquals(
                are("Anna"),
                matching(RuleNode.all(RuleNode.field(
                        juleicaEnd,
                        RuleOperator.STATE_IN,
                        RuleValue.ofStates(List.of(ExpiryState.EXPIRING)),
                        LocalDate.of(2027, 2, 15)))));
        assertEquals(
                are("Clara"),
                matching(RuleNode.all(RuleNode.field(
                        juleicaEnd, RuleOperator.STATE_IN, RuleValue.ofStates(List.of(ExpiryState.EXPIRED))))));
        assertEquals(
                are(),
                matching(RuleNode.all(RuleNode.field(
                        juleicaEnd, RuleOperator.STATE_IN, RuleValue.ofStates(List.of(ExpiryState.EMPTY))))));
        assertEquals(
                are("Anna", "Ben", "Clara"),
                matching(RuleNode.all(RuleNode.field(
                        juleica, RuleOperator.STATE_IN, RuleValue.ofStates(List.of(ExpiryState.EMPTY))))));
        assertEquals(
                are(),
                matching(RuleNode.all(
                        RuleNode.field(juleicaEnd, RuleOperator.STATE_IN, RuleValue.ofStates(List.of())))));
    }

    @Test
    void associationFields() {
        assertEquals(
                are("Anna"),
                matching(RuleNode.all(RuleNode.clusterField(clusterField, RuleOperator.IS_YES, RuleValue.empty()))));
        assertEquals(
                are("Ben", "Clara"),
                matching(RuleNode.all(RuleNode.clusterField(clusterField, RuleOperator.EMPTY, RuleValue.empty()))));
    }

    /** A check whose association field is gone matches nobody. */
    @Test
    void aCheckOnAFieldThatIsGoneMatchesNobody() {
        var gone = RuleNode.check(RuleSubject.CLUSTER_FIELD, RuleOperator.EMPTY, null, null, null, null, null, null);
        assertEquals(are(), matching(RuleNode.all(gone)));
        var goneAge = RuleNode.check(
                RuleSubject.AGE, RuleOperator.LESS_THAN, null, null, null, null, RuleValue.ofYears(5), null);
        assertEquals(are(), matching(RuleNode.all(goneAge)));
        var noGroup = RuleNode.check(RuleSubject.GROUP, RuleOperator.NOT_IN, null, null, null, null, null, null);
        assertEquals(are(), matching(RuleNode.all(noGroup)));
    }

    /** A negation around a check that lost its field still matches nobody, since the whole rule does. */
    @Test
    void aNegatedCheckOnAFieldThatIsGoneMatchesNobody() {
        var gone = RuleNode.check(RuleSubject.CLUSTER_FIELD, RuleOperator.IS_YES, null, null, null, null, null, null);
        assertEquals(are(), matching(RuleNode.none(gone)));
        assertEquals(are(), matching(RuleNode.any(RuleNode.none(gone), RuleNode.group(RuleOperator.IN, team))));
        var lostTag = RuleNode.check(RuleSubject.TAG, RuleOperator.IN, null, null, null, null, null, null);
        assertEquals(are(), matching(RuleNode.none(lostTag)));
    }

    /**
     * A text that only looks like a date (a year 0000, the 30th of February) sorts by its digits instead of failing
     * the statement, so every other check of the station keeps working.
     */
    @Test
    void datesThatAreNoDatesDoNotBreakTheRule() {
        assertEquals(
                are("Ben"),
                matching(RuleNode.all(
                        RuleNode.field(broken, RuleOperator.BEFORE, RuleValue.ofDate(LocalDate.of(2000, 1, 1))))));
        assertEquals(
                are("Clara"),
                matching(RuleNode.all(
                        RuleNode.field(broken, RuleOperator.WITHIN_LAST, RuleValue.ofDuration(1, RuleUnit.YEARS)))));
        assertEquals(
                are("Ben", "Clara"),
                matching(RuleNode.all(RuleNode.field(broken, RuleOperator.NOT_EMPTY, RuleValue.empty()))));
        assertEquals(
                are("Ben"),
                matching(RuleNode.all(RuleNode.age(RuleOperator.AT_LEAST, broken, RuleValue.ofYears(100), null))));
        assertEquals(
                are("Anna", "Ben", "Clara"),
                matching(RuleNode.any(
                        RuleNode.age(RuleOperator.AT_LEAST, birth, RuleValue.ofYears(10), null),
                        RuleNode.field(broken, RuleOperator.EXPIRED, RuleValue.empty()))));
    }

    /** A sign, a leading or a trailing point still make a number. */
    @Test
    void numbersInLooserShapes() {
        value(claraId, size, NODES.stringNode("+.5"));
        try {
            assertEquals(
                    are("Clara"),
                    matching(RuleNode.all(RuleNode.field(size, RuleOperator.LT, RuleValue.ofNumber(BigDecimal.ONE)))));
            value(claraId, size, NODES.stringNode("3."));
            assertEquals(
                    are("Ben", "Clara"),
                    matching(RuleNode.all(RuleNode.field(size, RuleOperator.GT, RuleValue.ofNumber(BigDecimal.TWO)))));
        } finally {
            value(claraId, size, NODES.stringNode("groß"));
        }
    }

    /** An age can count from an association date field too. */
    @Test
    void anAgeFromAnAssociationField() {
        var rule = RuleNode.all(RuleNode.clusterAge(RuleOperator.AT_LEAST, clusterField, RuleValue.ofYears(1), null));
        assertEquals(are(), matching(rule));
    }

    /** An operator that does not belong to a field check matches nobody rather than everybody. */
    @Test
    void anOperatorAFieldDoesNotOfferMatchesNobody() {
        assertEquals(are(), matching(RuleNode.all(RuleNode.field(note, RuleOperator.IN, RuleValue.empty()))));
    }

    /** Enough checks to run the parameter names past one letter. */
    @Test
    void manyChecksKeepTheirOwnValues() {
        var checks = new ArrayList<RuleNode>();
        for (int i = 0; i < 40; i++)
            checks.add(RuleNode.field(note, RuleOperator.CONTAINS, RuleValue.ofText("schutz")));
        assertEquals(are("Anna"), matching(RuleNode.block(RuleNodeKind.ALL, checks)));
    }
}
