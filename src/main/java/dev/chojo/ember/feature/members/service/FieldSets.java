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
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
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
 * its first question stands.
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
}
