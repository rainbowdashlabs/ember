/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.service;

import dev.chojo.ember.feature.members.entity.RuleNode;
import dev.chojo.ember.feature.members.entity.RuleOwner;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The order the rules of a station run in. A rule that asks about a group or tag kept by another rule runs after
 * that one, so a single run settles every rule. Rules that ask about each other in a circle have no such order;
 * saving one is refused, and a run leaves the rules of the circle alone.
 */
public final class RuleDependencies {
    private RuleDependencies() {}

    /**
     * The groups and tags kept by rules that a rule asks about.
     *
     * @param root  the rule
     * @param rules every rule of the station
     */
    public static Set<RuleOwner> dependsOn(RuleNode root, Map<RuleOwner, RuleNode> rules) {
        var owners = new LinkedHashSet<RuleOwner>();
        for (int id : root.referencedGroups()) owners.add(RuleOwner.group(id));
        for (int id : root.referencedTags()) owners.add(RuleOwner.tag(id));
        owners.retainAll(rules.keySet());
        return owners;
    }

    /**
     * The groups and tags whose rules ask about the given one, directly or through other rules, so their members
     * follow when its members change.
     *
     * @param changed the group or tag whose members change
     * @param rules   every rule of the station
     */
    public static Set<RuleOwner> dependents(RuleOwner changed, Map<RuleOwner, RuleNode> rules) {
        var reached = new LinkedHashSet<RuleOwner>();
        var waiting = new ArrayList<RuleOwner>(List.of(changed));
        while (!waiting.isEmpty()) {
            var current = waiting.removeLast();
            for (var entry : rules.entrySet()) {
                var owner = entry.getKey();
                if (owner.equals(changed) || reached.contains(owner)) continue;
                if (!asksAbout(entry.getValue(), current)) continue;
                reached.add(owner);
                waiting.add(owner);
            }
        }
        return reached;
    }

    /** Whether a rule asks about a group or tag, whether that one is kept by a rule or by hand. */
    private static boolean asksAbout(RuleNode rule, RuleOwner owner) {
        var ids = owner.kind() == RuleOwner.Kind.GROUP ? rule.referencedGroups() : rule.referencedTags();
        return ids.contains(owner.id());
    }

    /**
     * Every rule in an order in which a rule comes after the rules it asks about, leaving out the rules that stand
     * in a circle and every rule that asks about one of them.
     *
     * @param rules every rule of the station
     */
    public static List<RuleOwner> order(Map<RuleOwner, RuleNode> rules) {
        var ordered = new ArrayList<RuleOwner>();
        var blocked = new HashSet<RuleOwner>();
        var done = new HashSet<RuleOwner>();
        for (var owner : rules.keySet()) visit(owner, rules, new LinkedHashSet<>(), done, blocked, ordered);
        return ordered;
    }

    /**
     * The first circle of rules asking about each other, starting and ending with the same group or tag, or empty
     * when there is none.
     *
     * @param rules every rule of the station
     */
    public static Optional<List<RuleOwner>> cycle(Map<RuleOwner, RuleNode> rules) {
        var done = new HashSet<RuleOwner>();
        for (var owner : rules.keySet()) {
            var found = circle(owner, rules, new ArrayList<>(), done);
            if (found.isPresent()) return found;
        }
        return Optional.empty();
    }

    private static boolean visit(
            RuleOwner owner,
            Map<RuleOwner, RuleNode> rules,
            LinkedHashSet<RuleOwner> path,
            Set<RuleOwner> done,
            Set<RuleOwner> blocked,
            List<RuleOwner> ordered) {
        if (blocked.contains(owner)) return false;
        if (done.contains(owner)) return true;
        if (path.contains(owner)) {
            boolean inCircle = false;
            for (var step : path) {
                if (step.equals(owner)) inCircle = true;
                if (inCircle) blocked.add(step);
            }
            return false;
        }
        path.add(owner);
        boolean runs = true;
        for (var needed : dependsOn(rules.get(owner), rules)) {
            if (!visit(needed, rules, path, done, blocked, ordered)) runs = false;
        }
        path.remove(owner);
        if (!runs || blocked.contains(owner)) {
            blocked.add(owner);
            return false;
        }
        done.add(owner);
        ordered.add(owner);
        return true;
    }

    private static Optional<List<RuleOwner>> circle(
            RuleOwner owner, Map<RuleOwner, RuleNode> rules, List<RuleOwner> path, Set<RuleOwner> done) {
        int at = path.indexOf(owner);
        if (at >= 0) {
            var circle = new ArrayList<>(path.subList(at, path.size()));
            circle.add(owner);
            return Optional.of(List.copyOf(circle));
        }
        if (done.contains(owner)) return Optional.empty();
        path.add(owner);
        for (var needed : dependsOn(rules.get(owner), rules)) {
            var found = circle(needed, rules, path, done);
            if (found.isPresent()) return found;
        }
        path.removeLast();
        done.add(owner);
        return Optional.empty();
    }
}
