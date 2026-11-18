/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import org.jspecify.annotations.Nullable;

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
 *
 * <p>A set may require single parts: every choice has to hold them while they take place, and they count
 * toward the set's minimum. A required part called off is required no more.
 *
 * <p>A set may also allow no more than a number of its parts, so two parts on the same topic become
 * alternatives: "at least 1, at most 1". Only parts still taking place count toward the maximum, so a
 * part called off never pushes a choice over it.
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
     * @param requiredPartIds the parts of the set every choice has to hold while they take place
     * @param maximum how many of its parts taking place a choice may hold, or null for no maximum
     */
    public record SetRule(
            int setId,
            String name,
            int minimum,
            Set<Integer> partIds,
            Set<Integer> requiredPartIds,
            @Nullable Integer maximum) {
        /** A set from parts in any collection, each part counted once, requiring none of them. */
        public SetRule(int setId, String name, int minimum, Collection<Integer> partIds) {
            this(setId, name, minimum, Set.copyOf(partIds), Set.of(), null);
        }

        /** A set without a maximum. */
        public SetRule(int setId, String name, int minimum, Set<Integer> partIds, Set<Integer> requiredPartIds) {
            this(setId, name, minimum, partIds, requiredPartIds, null);
        }

        public SetRule {
            partIds = Set.copyOf(partIds);
            requiredPartIds = Set.copyOf(requiredPartIds);
        }
    }

    /**
     * How a choice stands against one set.
     *
     * @param setId    the set
     * @param name     its name
     * @param required how many of its parts the choice has to hold, after cancellations
     * @param chosen   how many of its parts the choice holds
     * @param missingRequired the parts the set requires that take place and the choice does not hold
     * @param maximum  how many of its parts taking place the choice may hold, or null for no maximum
     * @param tooMany  how many parts taking place the choice holds beyond the maximum
     */
    public record SetResult(
            int setId,
            String name,
            int required,
            int chosen,
            List<Integer> missingRequired,
            @Nullable Integer maximum,
            int tooMany) {
        /** A result missing none of the parts the set requires. */
        public SetResult(int setId, String name, int required, int chosen) {
            this(setId, name, required, chosen, List.of());
        }

        /** A result of a set without a maximum. */
        public SetResult(int setId, String name, int required, int chosen, List<Integer> missingRequired) {
            this(setId, name, required, chosen, missingRequired, null, 0);
        }

        public SetResult {
            missingRequired = List.copyOf(missingRequired);
        }

        /**
         * Whether the choice meets the set.
         *
         * @return true where it holds at least as many parts as required, every required part among them
         */
        public boolean met() {
            return chosen >= required && missingRequired.isEmpty();
        }

        /**
         * How many parts are still missing.
         *
         * @return zero for a set that is met
         */
        public int missing() {
            return Math.max(Math.max(0, required - chosen), missingRequired.size());
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
     * The sets a choice holds more parts of than they allow, in the order the sets were given.
     *
     * @param sets      the event group's sets
     * @param chosen    the parts chosen
     * @param cancelled the parts called off
     * @return every set the choice goes beyond, empty where it stays within all of them
     */
    public static List<SetResult> excesses(List<SetRule> sets, Set<Integer> chosen, Set<Integer> cancelled) {
        return evaluate(sets, chosen, cancelled).stream()
                .filter(result -> result.tooMany() > 0)
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
        int pickedTakingPlace = 0;
        int available = 0;
        for (int part : set.partIds()) {
            boolean isChosen = chosen.contains(part);
            if (isChosen) picked++;
            if (isChosen && !cancelled.contains(part)) pickedTakingPlace++;
            if (isChosen || !cancelled.contains(part)) available++;
        }
        int required = Math.min(Math.max(set.minimum(), 0), available);
        var missingRequired = set.requiredPartIds().stream()
                .filter(set.partIds()::contains)
                .filter(part -> !chosen.contains(part) && !cancelled.contains(part))
                .sorted()
                .toList();
        Integer maximum = set.maximum();
        int tooMany = maximum == null ? 0 : Math.max(0, pickedTakingPlace - maximum);
        return new SetResult(set.setId(), set.name(), required, picked, missingRequired, maximum, tooMany);
    }
}
