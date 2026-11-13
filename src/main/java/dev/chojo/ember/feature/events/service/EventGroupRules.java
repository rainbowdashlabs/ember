/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Whether a choice of parts meets an event group's rules.
 *
 * <p>An event group sorts its parts into sets, and each set asks for a number of its parts: "Practice: at
 * least 2 of 3", "Test: 1 of 1". Cancellations never leave anybody short. A part called off that was
 * chosen still counts as chosen, so a sign-up that met the rules keeps meeting them. A part called
 * off that was not chosen can no longer be asked for, so a set never asks for more parts than the
 * chosen ones and those still taking place together hold. A choice cannot add a part already called
 * off, so a new choice still needs as many parts that take place as the set asks for, while a changed
 * choice keeps the called-off parts it already held.
 */
public final class EventGroupRules {
    private EventGroupRules() {}

    /**
     * One set of an event group as the rules see it.
     *
     * <p>A minimum above the number of parts asks for all of them, one below zero for none: the
     * editor keeps both from being stored, and the rules do not fail on them either.
     *
     * @param setId   the set
     * @param name    its name, which a result repeats to the reader
     * @param minimum how many of its parts a sign-up has to choose
     * @param partIds the parts in the set
     */
    public record SetRule(int setId, String name, int minimum, Set<Integer> partIds) {
        /** A set from parts in any collection, each part counted once. */
        public SetRule(int setId, String name, int minimum, Collection<Integer> partIds) {
            this(setId, name, minimum, Set.copyOf(partIds));
        }

        public SetRule {
            partIds = Set.copyOf(partIds);
        }
    }

    /**
     * How a choice stands against one set.
     *
     * @param setId    the set
     * @param name     its name
     * @param required how many of its parts the choice has to hold, after cancellations
     * @param chosen   how many of its parts the choice holds
     */
    public record SetResult(int setId, String name, int required, int chosen) {
        /**
         * Whether the choice meets the set.
         *
         * @return true where it holds at least as many parts as required
         */
        public boolean met() {
            return chosen >= required;
        }

        /**
         * How many parts are still missing.
         *
         * @return zero for a set that is met
         */
        public int missing() {
            return Math.max(0, required - chosen);
        }
    }

    /**
     * How a choice stands against every set, in the order the sets were given.
     *
     * @param sets      the event group's sets
     * @param chosen    the parts chosen
     * @param cancelled the parts called off
     * @return one result per set
     */
    public static List<SetResult> evaluate(List<SetRule> sets, Set<Integer> chosen, Set<Integer> cancelled) {
        return sets.stream().map(set -> resultOf(set, chosen, cancelled)).toList();
    }

    /**
     * The sets a choice falls short of, in the order the sets were given.
     *
     * @param sets      the event group's sets
     * @param chosen    the parts chosen
     * @param cancelled the parts called off
     * @return every set the choice falls short of, empty where it meets all of them
     */
    public static List<SetResult> shortfalls(List<SetRule> sets, Set<Integer> chosen, Set<Integer> cancelled) {
        return evaluate(sets, chosen, cancelled).stream()
                .filter(result -> !result.met())
                .toList();
    }

    /**
     * Whether a choice meets every set.
     *
     * @param sets      the event group's sets
     * @param chosen    the parts chosen
     * @param cancelled the parts called off
     * @return true where no set is short
     */
    public static boolean met(List<SetRule> sets, Set<Integer> chosen, Set<Integer> cancelled) {
        return shortfalls(sets, chosen, cancelled).isEmpty();
    }

    private static SetResult resultOf(SetRule set, Set<Integer> chosen, Set<Integer> cancelled) {
        int picked = 0;
        int available = 0;
        for (int part : set.partIds()) {
            boolean isChosen = chosen.contains(part);
            if (isChosen) picked++;
            if (isChosen || !cancelled.contains(part)) available++;
        }
        int required = Math.min(Math.max(set.minimum(), 0), available);
        return new SetResult(set.setId(), set.name(), required, picked);
    }
}
