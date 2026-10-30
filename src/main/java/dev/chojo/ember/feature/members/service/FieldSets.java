/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.service;

import dev.chojo.ember.feature.members.entity.FieldSetPlacement;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The one rule of bundles on a form, the same for a station's questions and an association's: the
 * questions of a bundle stand together, in the bundle's own order, where its first question stands.
 */
public final class FieldSets {

    private FieldSets() {}

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
