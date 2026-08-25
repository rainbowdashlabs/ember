/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.quiz.service;

import java.util.HashMap;
import java.util.Map;

/**
 * Turns the category names an imported file carries into category ids of one station.
 *
 * <p>Categories belong to the station, not to the catalog, so an import must not create a second
 * "Brandlehre" beside the one the station already has. Names are matched case insensitively
 * against what the station holds, repeated names inside one file collapse onto a single category,
 * and importing the same file twice leaves the station with the categories it had after the first
 * time.
 */
public final class QuizCategoryResolver {
    private final QuizCatalogService catalogService;
    private final int stationId;
    private final int initialCount;
    private final Map<String, Integer> byName = new HashMap<>();

    public QuizCategoryResolver(QuizCatalogService catalogService, int stationId) {
        this.catalogService = catalogService;
        this.stationId = stationId;
        var existing = catalogService.findCategories(stationId);
        this.initialCount = existing.size();
        for (var category : existing) {
            byName.putIfAbsent(category.name().toLowerCase(), category.id());
        }
    }

    /** Resolves a bare name, appending a new category at the end of the station's list. */
    public Integer resolve(String name) {
        return resolve(name, "", initialCount);
    }

    /**
     * Resolves a name the file describes further. The description and position are used only when
     * the category has to be created: a category the station already has keeps what the station
     * chose for it, because an imported file does not get to rewrite it.
     *
     * @return the category id, or {@code null} for a name nobody gave
     */
    public Integer resolve(String name, String description, int position) {
        if (name == null) return null;
        String trimmed = name.trim();
        if (trimmed.isEmpty()) return null;
        return byName.computeIfAbsent(trimmed.toLowerCase(), _ -> catalogService
                .createCategory(stationId, trimmed, description != null ? description : "", position)
                .id());
    }
}
