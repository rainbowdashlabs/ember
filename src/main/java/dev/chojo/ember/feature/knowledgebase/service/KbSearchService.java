/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.knowledgebase.service;

import dev.chojo.ember.feature.knowledgebase.entity.KbFile;
import dev.chojo.ember.feature.knowledgebase.entity.KbSearchResult;
import dev.chojo.ember.feature.knowledgebase.repository.KnowledgeBaseRepository;
import dev.chojo.ember.feature.station.repository.StationRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Full-text search over a station's knowledge base and the index that backs it. Every write that
 * changes what a file says routes through {@link #reindex(int, String)} so the stored index stays
 * in step with the file's title, description and body.
 *
 * <p>The text search configuration follows the station's locale, so stemming and stop words match
 * the language the content is written in; unknown or unset locales fall back to {@code simple},
 * which indexes words verbatim.
 */
@Singleton
public class KbSearchService {
    private static final Map<String, String> LOCALE_TO_TS_CONFIG = Map.of(
            "de", "german",
            "en", "english",
            "fr", "french",
            "es", "spanish",
            "it", "italian",
            "nl", "dutch",
            "pt", "portuguese",
            "ru", "russian");
    private static final String DEFAULT_TS_CONFIG = "simple";

    private final KnowledgeBaseRepository repository;
    private final StationRepository stationRepository;

    @Inject
    public KbSearchService(KnowledgeBaseRepository repository, StationRepository stationRepository) {
        this.repository = repository;
        this.stationRepository = stationRepository;
    }

    /**
     * Strips HTML tags and markdown punctuation so the index holds plain words only.
     */
    private static String toPlainText(String text) {
        return text.replaceAll("<[^>]+>", " ")
                .replaceAll("[#*_\\[\\]()>`~]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    /**
     * Finds the files of a station matching a query. A blank query matches nothing.
     *
     * @param stationId the station to search in
     * @param query     the search terms
     * @return the matching files
     */
    public List<KbFile> search(int stationId, String query) {
        if (query == null || query.isBlank()) return List.of();
        return repository.search(stationId, query, textSearchConfig(stationId));
    }

    /**
     * Finds the files of a station matching a query, each with a highlighted excerpt. A blank
     * query matches nothing.
     *
     * @param stationId the station to search in
     * @param query     the search terms
     * @return the matching files with their excerpt
     */
    public List<KbSearchResult> searchWithSnippets(int stationId, String query) {
        if (query == null || query.isBlank()) return List.of();
        return repository.searchWithSnippets(stationId, query, textSearchConfig(stationId));
    }

    /**
     * Rebuilds the search index of a file from its title, description and body. Unknown files and
     * files whose combined text is empty leave the stored index untouched.
     *
     * @param fileId the file to reindex
     * @param text   the file's body, or {@code null} when it has none
     */
    public void reindex(int fileId, String text) {
        var file = repository.findFileById(fileId).orElse(null);
        if (file == null) return;

        var combined = new StringBuilder();
        combined.append(file.name()).append(' ');
        if (file.description() != null && !file.description().isBlank()) {
            combined.append(file.description()).append(' ');
        }
        if (text != null && !text.isBlank()) {
            combined.append(toPlainText(text));
        }

        String indexed = combined.toString().trim();
        if (indexed.isBlank()) return;
        repository.updateSearchIndex(fileId, indexed, textSearchConfig(file.stationId()));
    }

    /**
     * Resolves the PostgreSQL text search configuration a station's content is indexed and queried
     * with, derived from the language part of its locale.
     *
     * @param stationId the station whose locale decides the configuration
     * @return the text search configuration name
     */
    public String textSearchConfig(int stationId) {
        return stationRepository
                .findById(stationId)
                .map(station -> {
                    String locale = station.locale();
                    if (locale == null || locale.isBlank()) return DEFAULT_TS_CONFIG;
                    String language = locale.contains("-") ? locale.substring(0, locale.indexOf('-')) : locale;
                    return LOCALE_TO_TS_CONFIG.getOrDefault(language.toLowerCase(Locale.ROOT), DEFAULT_TS_CONFIG);
                })
                .orElse(DEFAULT_TS_CONFIG);
    }
}
