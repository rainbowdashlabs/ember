/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.service;

import dev.chojo.ember.api.refusal.MemberRefusal;
import dev.chojo.ember.feature.members.entity.FieldSet;
import dev.chojo.ember.feature.members.entity.FieldSetPlacement;
import dev.chojo.ember.feature.members.entity.ProfileFieldScope;
import dev.chojo.ember.feature.members.repository.FieldSetTables;
import dev.chojo.ember.util.sql.Transactions;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;

/**
 * The rules of bundles, the same for a station's questions and an association's: a bundle has a name and
 * belongs to one owner, and the questions of a bundle stand together, in the bundle's own order, where
 * its first question stands. A form put in a new order is the authority for its bundles too: the order its
 * questions arrive in becomes their bundle's order.
 */
public final class FieldSets {

    private FieldSets() {}

    /**
     * The name a bundle is written down under.
     *
     * @throws dev.chojo.ember.api.refusal.RefusalResponse {@link MemberRefusal#PROFILE_FIELD_SET_NEEDS_A_NAME}
     *                                                     for a blank name
     */
    public static String requireName(@Nullable String name) {
        if (name == null || name.isBlank()) throw MemberRefusal.PROFILE_FIELD_SET_NEEDS_A_NAME.raise();
        return name.strip();
    }

    /**
     * A bundle of this owner.
     *
     * @throws dev.chojo.ember.api.refusal.RefusalResponse {@link MemberRefusal#PROFILE_FIELD_SET_NOT_HERE}
     *                                                     for a bundle of another owner, or none at all
     */
    public static FieldSet requireOwned(FieldSetTables sets, int ownerId, int setId) {
        return sets.findById(setId)
                .filter(set -> set.ownerId() == ownerId)
                .orElseThrow(MemberRefusal.PROFILE_FIELD_SET_NOT_HERE::raise);
    }

    /**
     * Puts one form of an owner in a new order, in one transaction. A bundle whose questions arrive in a
     * new order takes that order, and every form asking the bundle follows it; questions of a bundle that
     * arrive apart are pulled together where the first of them stands.
     *
     * @param ownerId     the station or association the form belongs to
     * @param fieldIds    the questions in the order they were dragged into
     * @param sets        the owner's bundles
     * @param applyForm   writes the form's order and returns how many questions it moved
     * @param regroupAll  puts every form of the owner back together once a bundle's order changed
     * @return how many questions of the form moved
     */
    public static int reorderForm(
            int ownerId,
            List<Integer> fieldIds,
            FieldSetTables sets,
            ToIntFunction<List<Integer>> applyForm,
            Runnable regroupAll) {
        return Transactions.call(() -> {
            Map<Integer, List<Integer>> bundles = reorderedBundles(fieldIds, sets.placements(ownerId));
            bundles.forEach(sets::applyOrder);
            int written = applyForm.applyAsInt(keepTogether(fieldIds, sets.placements(ownerId)));
            if (!bundles.isEmpty()) regroupAll.run();
            return written;
        });
    }

    /**
     * Puts every form of an owner back into an order that keeps each bundle together. Every form is written,
     * since questions sharing a position stand in an order only the write settles.
     *
     * @param assignments every question as put to a kind of member
     * @param role        who an assignment asks
     * @param position    where it stands on that form
     * @param fieldId     the question it puts
     * @param placements  where each bundled question stands within its bundle
     * @param apply       writes one form's new order
     */
    public static <A> void keepFormsTogether(
            List<A> assignments,
            Function<A, ProfileFieldScope> role,
            ToIntFunction<A> position,
            ToIntFunction<A> fieldId,
            Map<Integer, FieldSetPlacement> placements,
            BiConsumer<ProfileFieldScope, List<Integer>> apply) {
        assignments.stream().collect(Collectors.groupingBy(role)).forEach((form, asked) -> {
            List<Integer> ordered = asked.stream()
                    .sorted(Comparator.comparingInt(position))
                    .map(fieldId::applyAsInt)
                    .toList();
            apply.accept(form, keepTogether(ordered, placements));
        });
    }

    /**
     * Puts a form's questions in order with every bundle kept together.
     *
     * <p>A bundle takes the place of whichever of its questions comes first, and its questions follow in the
     * bundle's order, so dragging any question of a bundle moves the whole bundle.
     *
     * @param ordered    the questions in the order they were asked to stand
     * @param placements where each bundled question stands within its bundle; one missing here is on its own
     * @return the same questions, bundles together
     */
    public static List<Integer> keepTogether(List<Integer> ordered, Map<Integer, FieldSetPlacement> placements) {
        List<Integer> result = new ArrayList<>(ordered.size());
        Set<Integer> placedSets = new HashSet<>();
        for (Integer fieldId : ordered) {
            FieldSetPlacement placement = placements.get(fieldId);
            if (placement == null) {
                result.add(fieldId);
                continue;
            }
            if (!placedSets.add(placement.setId())) continue;
            ordered.stream()
                    .filter(other -> placements.containsKey(other)
                            && placements.get(other).setId() == placement.setId())
                    .sorted(Comparator.comparingInt(
                            other -> placements.get(other).position()))
                    .forEach(result::add);
        }
        return result;
    }

    /**
     * The bundles whose own order a form's new order changes, each with all its questions in the new order.
     *
     * <p>The questions of a bundle on the form take the places those same questions held in the bundle, in
     * the order they arrived, wherever on the form they arrived. Questions of the bundle the form does not
     * ask keep their places, so a form asking only part of a bundle reorders that part and nothing else.
     *
     * @param ordered    the questions in the order they were asked to stand
     * @param placements where each bundled question stands within its bundle; one missing here is on its own
     * @return the full new order of every bundle that changes, by bundle; empty when none does
     */
    public static Map<Integer, List<Integer>> reorderedBundles(
            List<Integer> ordered, Map<Integer, FieldSetPlacement> placements) {
        Map<Integer, List<Integer>> arrived = new LinkedHashMap<>();
        for (Integer fieldId : new LinkedHashSet<>(ordered)) {
            FieldSetPlacement placement = placements.get(fieldId);
            if (placement != null)
                arrived.computeIfAbsent(placement.setId(), id -> new ArrayList<>())
                        .add(fieldId);
        }
        Map<Integer, List<Integer>> reordered = new LinkedHashMap<>();
        arrived.forEach((setId, moved) -> {
            List<Integer> stored = bundleOrder(setId, placements);
            List<Integer> next = new ArrayList<>(stored);
            int arrival = 0;
            for (int slot = 0; slot < stored.size(); slot++) {
                if (moved.contains(stored.get(slot))) next.set(slot, moved.get(arrival++));
            }
            if (!next.equals(stored)) reordered.put(setId, next);
        });
        return reordered;
    }

    /** Every question of a bundle, in the bundle's stored order. */
    private static List<Integer> bundleOrder(int setId, Map<Integer, FieldSetPlacement> placements) {
        return placements.entrySet().stream()
                .filter(entry -> entry.getValue().setId() == setId)
                .sorted(Comparator.<Map.Entry<Integer, FieldSetPlacement>>comparingInt(
                                entry -> entry.getValue().position())
                        .thenComparing(Map.Entry::getKey))
                .map(Map.Entry::getKey)
                .toList();
    }
}
