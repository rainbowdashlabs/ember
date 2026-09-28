/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.service;

import dev.chojo.ember.feature.board.repository.BoardTicketRepository;
import dev.chojo.ember.feature.documents.service.DocumentService;
import dev.chojo.ember.feature.knowledgebase.service.KbSearchService;
import dev.chojo.ember.feature.system.repository.ApplicationSettingRepository;
import dev.chojo.ember.feature.system.repository.DatabaseServerRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * Builds every full-text search index again when the database has moved to another major version.
 *
 * <p>The indexes hold words as the database stemmed them, and a new major version may stem the same
 * word differently: PostgreSQL 18 reads "ae", "oe" and "ue" in German words as umlauts, where 17 did
 * not. An index built by the old version then no longer matches what the new one makes of a search,
 * and words go missing from the results without anything failing.
 *
 * <p>The major version the indexes were last built on is kept, and a start that finds another one
 * builds them again. An instance that has never recorded one builds them once, since it cannot tell
 * whether it was upgraded before this check existed. The version is only recorded once everything
 * went through, so a rebuild that fails halfway is tried again on the next start.
 */
@Singleton
public class SearchIndexRebuildService {
    private static final Logger log = LoggerFactory.getLogger(SearchIndexRebuildService.class);

    /** The setting holding the major version the search indexes were last built on. */
    static final String BUILT_ON_KEY = "search_index_postgres_major";

    private final ApplicationSettingRepository settings;
    private final DatabaseServerRepository server;
    private final KbSearchService knowledgeBase;
    private final DocumentService documents;
    private final BoardTicketRepository boardTickets;

    @Inject
    public SearchIndexRebuildService(
            ApplicationSettingRepository settings,
            DatabaseServerRepository server,
            KbSearchService knowledgeBase,
            DocumentService documents,
            BoardTicketRepository boardTickets) {
        this.settings = settings;
        this.server = server;
        this.knowledgeBase = knowledgeBase;
        this.documents = documents;
        this.boardTickets = boardTickets;
    }

    /**
     * Builds the indexes again if they were built on another major version, or on none recorded.
     *
     * @return whether a rebuild ran and finished
     */
    public boolean rebuildIfNeeded() {
        int current = server.majorVersion();
        Optional<String> builtOn = settings.get(BUILT_ON_KEY);
        if (builtOn.filter(String.valueOf(current)::equals).isPresent()) return false;

        log.info(
                "Search indexes were built on PostgreSQL {}, the database is {}; building them again",
                builtOn.orElse("an unknown version"),
                current);
        int wikiFiles = knowledgeBase.rebuildSearchIndex();
        int memberDocuments = documents.rebuildSearchIndex();
        int tickets = boardTickets.rebuildSearchVectors();
        settings.set(BUILT_ON_KEY, String.valueOf(current));
        log.info(
                "Search indexes built again: {} wiki file(s), {} document(s), {} board ticket(s)",
                wikiFiles,
                memberDocuments,
                tickets);
        return true;
    }

    /**
     * The same, for a background thread: a failure is written down rather than thrown, and the next
     * start tries again because nothing was recorded.
     */
    public void rebuildInBackground() {
        try {
            rebuildIfNeeded();
        } catch (Exception e) {
            log.warn("The search indexes could not be built again; the next start tries again", e);
        }
    }
}
