/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * What changes between the set a thing holds and the set it should hold: what joins and what leaves.
 *
 * <p>Every "replace all" write that keeps unchanged rows (who is in a group, what a group grants, which
 * groups somebody is in) starts with this, and writing the two filters out by hand at each of them is
 * where one of them once compared a list against itself. Both sides are read as sets, so a value named
 * twice counts once, and each list keeps the order its side was given in.
 *
 * @param added   what the wanted side holds and the current side does not
 * @param removed what the current side holds and the wanted side does not
 * @param <T>     what the sets hold
 */
public record SetDiff<T>(List<T> added, List<T> removed) {
    public SetDiff {
        added = List.copyOf(added);
        removed = List.copyOf(removed);
    }

    /**
     * The difference between two sides.
     *
     * @param current what is held now
     * @param wanted  what should be held afterwards
     * @param <T>     what the sets hold
     * @return what joins and what leaves
     */
    public static <T> SetDiff<T> of(Collection<? extends T> current, Collection<? extends T> wanted) {
        Set<T> now = new LinkedHashSet<>(current);
        Set<T> after = new LinkedHashSet<>(wanted);
        List<T> added = new ArrayList<>();
        for (T value : after) {
            if (!now.contains(value)) added.add(value);
        }
        List<T> removed = new ArrayList<>();
        for (T value : now) {
            if (!after.contains(value)) removed.add(value);
        }
        return new SetDiff<>(added, removed);
    }

    /**
     * @return whether nothing joins and nothing leaves
     */
    public boolean isEmpty() {
        return added.isEmpty() && removed.isEmpty();
    }

    /**
     * @return everything that joins or leaves, the joining first
     */
    public List<T> touched() {
        List<T> all = new ArrayList<>(added);
        all.addAll(removed);
        return all;
    }
}
