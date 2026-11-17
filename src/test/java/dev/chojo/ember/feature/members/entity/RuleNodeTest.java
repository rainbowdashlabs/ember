/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.entity;

import dev.chojo.ember.api.auth.StationUserType;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** The tree of a member rule and what can be read off it. */
class RuleNodeTest {

    private static final RuleNode TREE = RuleNode.all(
            RuleNode.group(RuleOperator.IN, 3),
            RuleNode.any(
                    RuleNode.tag(RuleOperator.NOT_IN, 7),
                    RuleNode.userType(RuleOperator.ONE_OF, List.of(StationUserType.MEMBER)),
                    RuleNode.none(RuleNode.group(RuleOperator.NOT_IN, 4))),
            RuleNode.memberSince(RuleOperator.AT_LEAST, 1, RuleUnit.YEARS, LocalDate.of(2027, 1, 1)),
            RuleNode.age(RuleOperator.AT_LEAST, 11, RuleValue.ofYears(10), null),
            RuleNode.field(12, RuleOperator.EMPTY, RuleValue.empty()),
            RuleNode.clusterField(13, RuleOperator.IS_YES, RuleValue.empty()));

    @Test
    void theChecksAreReadDepthFirst() {
        assertEquals(8, TREE.checks().size());
        assertEquals(RuleSubject.GROUP, TREE.checks().getFirst().subject());
        assertEquals(RuleSubject.CLUSTER_FIELD, TREE.checks().getLast().subject());
        assertEquals(11, TREE.nodes().count());
    }

    @Test
    void theGroupsAndTagsAskedAboutAreNamed() {
        assertEquals(List.of(3, 4), TREE.referencedGroups());
        assertEquals(List.of(7), TREE.referencedTags());
    }

    @Test
    void theDepthCountsEveryLevel() {
        assertEquals(4, TREE.depth());
        assertEquals(1, RuleNode.all().depth());
    }

    @Test
    void aCheckWithoutAValueReadsAsEmpty() {
        var check = RuleNode.group(RuleOperator.IN, 1);
        assertEquals(RuleValue.empty(), check.valueOrEmpty());
        assertEquals(RuleValue.ofYears(10), TREE.checks().get(5).valueOrEmpty());
    }

    @Test
    void anAgeCanCountFromAnAssociationField() {
        var check = RuleNode.clusterAge(RuleOperator.LESS_THAN, 9, RuleValue.ofYears(18), null);
        assertEquals(RuleSubject.AGE, check.subject());
        assertEquals(9, check.clusterFieldId());
        assertEquals(null, check.fieldId());
    }

    @Test
    void aCheckIsNotABlock() {
        assertThrows(IllegalArgumentException.class, () -> RuleNode.block(RuleNodeKind.CHECK, List.of()));
    }

    @Test
    void aFieldCheckCanCountFromAFixedDay() {
        var day = LocalDate.of(2027, 1, 1);
        var check = RuleNode.field(5, RuleOperator.WITHIN_NEXT, RuleValue.ofDuration(1, RuleUnit.MONTHS), day);
        assertEquals(day, check.referenceDate());
        assertEquals(RuleSubject.FIELD, check.subject());
    }
}
