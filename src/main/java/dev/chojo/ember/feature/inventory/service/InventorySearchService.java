/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.service;

import dev.chojo.ember.feature.inventory.entity.InventoryMatch;
import dev.chojo.ember.feature.inventory.repository.InventorySearchRepository;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.util.sql.FullTextSearch;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Finds the inventories of a station a search is about: by their own name and description, or by those
 * of any of their kinds or pieces. What the station shares is not decided here; the caller narrows the
 * answer to what it may show.
 */
@Singleton
public class InventorySearchService {
    /** Longer than any search somebody types, short enough that a partner cannot make every row work. */
    static final int MAX_LENGTH = 100;

    /** More words than a search for gear ever needs; the rest are dropped. */
    static final int MAX_WORDS = 8;

    private final InventorySearchRepository repository;
    private final StationRepository stationRepository;

    @Inject
    public InventorySearchService(InventorySearchRepository repository, StationRepository stationRepository) {
        this.repository = repository;
        this.stationRepository = stationRepository;
    }

    /**
     * Every inventory of the station the search matches, by itself or through a kind or a piece, best
     * match first.
     *
     * @param stationId the station searched
     * @param search    what was searched for
     * @return one match per inventory, keyed by its id in rank order; empty for a blank search
     */
    public Map<Integer, InventoryMatch> match(int stationId, String search) {
        List<String> words = wordsOf(search);
        if (words.isEmpty()) return Map.of();
        String config = stationRepository
                .findById(stationId)
                .map(Station::locale)
                .map(FullTextSearch::forLocale)
                .orElse(FullTextSearch.DEFAULT_CONFIG);

        var builders = new LinkedHashMap<Integer, Builder>();
        for (var hit : repository.inventories(stationId, words, config)) {
            builders.computeIfAbsent(hit.id(), Builder::new).itself(hit.rank());
        }
        for (var owned : repository.kinds(stationId, words, config)) {
            builders.computeIfAbsent(owned.inventoryId(), Builder::new).kinds.add(owned.hit());
        }
        for (var owned : repository.pieces(stationId, words, config)) {
            builders.computeIfAbsent(owned.inventoryId(), Builder::new).pieces.add(owned.hit());
        }

        var result = new LinkedHashMap<Integer, InventoryMatch>();
        builders.values().stream()
                .map(Builder::build)
                .sorted(Comparator.comparingDouble(InventoryMatch::rank).reversed())
                .forEach(match -> result.put(match.inventoryId(), match));
        return result;
    }

    /**
     * The words a search is made of, cut to {@link #MAX_LENGTH} characters and {@link #MAX_WORDS} words.
     *
     * @param search what was typed, or {@code null}
     * @return the words, empty for a blank search
     */
    static List<String> wordsOf(String search) {
        if (search == null) return List.of();
        String stripped = search.strip();
        if (stripped.length() > MAX_LENGTH) stripped = stripped.substring(0, MAX_LENGTH);
        if (stripped.isEmpty()) return List.of();
        return Arrays.stream(stripped.split("\\s+")).limit(MAX_WORDS).toList();
    }

    private static final class Builder {
        private final int inventoryId;
        private final List<InventoryMatch.Hit> kinds = new ArrayList<>();
        private final List<InventoryMatch.Hit> pieces = new ArrayList<>();
        private boolean itself;
        private double ownRank;

        private Builder(int inventoryId) {
            this.inventoryId = inventoryId;
        }

        private void itself(double rank) {
            itself = true;
            ownRank = rank;
        }

        private InventoryMatch build() {
            double best = ownRank;
            for (var hit : kinds) best = Math.max(best, hit.rank());
            for (var hit : pieces) best = Math.max(best, hit.rank());
            return new InventoryMatch(
                    inventoryId,
                    best,
                    itself,
                    kinds.stream().sorted(byRank()).toList(),
                    pieces.stream().sorted(byRank()).toList());
        }

        private static Comparator<InventoryMatch.Hit> byRank() {
            return Comparator.comparingDouble(InventoryMatch.Hit::rank).reversed();
        }
    }
}
