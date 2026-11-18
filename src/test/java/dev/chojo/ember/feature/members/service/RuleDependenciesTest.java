/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.service;

import dev.chojo.ember.feature.members.entity.RuleNode;
import dev.chojo.ember.feature.members.entity.RuleOperator;
import dev.chojo.ember.feature.members.entity.RuleOwner;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The order the rules of a station run in, and the circles that have none. */
class RuleDependenciesTest {
    private static final RuleOwner A = RuleOwner.group(1);
    private static final RuleOwner B = RuleOwner.group(2);
    private static final RuleOwner C = RuleOwner.tag(3);
    private static final RuleOwner D = RuleOwner.tag(4);

    private static Map<RuleOwner, RuleNode> rules(Object... ownersAndRules) {
        var rules = new LinkedHashMap<RuleOwner, RuleNode>();
        for (int i = 0; i < ownersAndRules.length; i += 2) {
            rules.put((RuleOwner) ownersAndRules[i], (RuleNode) ownersAndRules[i + 1]);
        }
        return rules;
    }

    /** A rule asking about a hand-kept group depends on nothing that runs. */
    @Test
    void onlyRuleKeptGroupsAndTagsAreDependencies() {
        var rules = rules(
                A,
                RuleNode.all(RuleNode.group(RuleOperator.IN, 9), RuleNode.tag(RuleOperator.IN, 3)),
                C,
                RuleNode.all());
        assertEquals(Set.of(C), RuleDependencies.dependsOn(rules.get(A), rules));
    }

    @Test
    void aRuleRunsAfterTheRulesItAsksAbout() {
        var rules = rules(
                A, RuleNode.all(RuleNode.group(RuleOperator.IN, 2)),
                B, RuleNode.all(RuleNode.tag(RuleOperator.NOT_IN, 3)),
                C, RuleNode.all());
        assertEquals(List.of(C, B, A), RuleDependencies.order(rules));
        assertEquals(Optional.empty(), RuleDependencies.cycle(rules));
    }

    /** A circle and whatever asks about it are left out; the rest still runs. */
    @Test
    void aCircleIsLeftOutWithEverythingThatNeedsIt() {
        var rules = rules(
                A, RuleNode.all(RuleNode.group(RuleOperator.IN, 2)),
                B, RuleNode.all(RuleNode.group(RuleOperator.IN, 1)),
                C, RuleNode.all(RuleNode.group(RuleOperator.IN, 1)),
                D, RuleNode.all());
        assertEquals(List.of(D), RuleDependencies.order(rules));
        assertEquals(Optional.of(List.of(A, B, A)), RuleDependencies.cycle(rules));
    }

    @Test
    void aRuleAskingAboutItselfIsACircle() {
        var rules = rules(C, RuleNode.all(RuleNode.tag(RuleOperator.IN, 3)));
        assertTrue(RuleDependencies.order(rules).isEmpty());
        assertEquals(Optional.of(List.of(C, C)), RuleDependencies.cycle(rules));
    }

    /** A rule needed by two others runs once. */
    @Test
    void aSharedDependencyRunsOnce() {
        var rules = rules(
                A, RuleNode.all(RuleNode.tag(RuleOperator.IN, 3)),
                B, RuleNode.any(RuleNode.tag(RuleOperator.IN, 3), RuleNode.group(RuleOperator.IN, 1)),
                C, RuleNode.all());
        assertEquals(List.of(C, A, B), RuleDependencies.order(rules));
        assertEquals(Optional.empty(), RuleDependencies.cycle(rules));
    }
}
