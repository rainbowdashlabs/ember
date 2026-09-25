/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.page.service;

import dev.chojo.ember.feature.content.service.CellDescriptions;
import dev.chojo.ember.feature.page.entity.StationPage;
import dev.chojo.ember.feature.page.repository.PageRepository;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.repository.StationRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.HashSet;
import java.util.Optional;
import java.util.UUID;

/**
 * Where a station page is reached, for the cards elsewhere that link to one.
 *
 * <p>Worked out when the card is drawn rather than written into it, because both halves of the
 * answer move: a page is renamed, a page is filed under another, a page stops being listed and is
 * reached by a link instead. A card holding a copy of either would go quietly wrong on the day
 * somebody changed one, and a card in a news entry would go wrong without anybody editing the entry.
 */
@Singleton
public class StationPageAddressing implements CellDescriptions.PageAddressing {
    private final PageRepository pageRepository;
    private final StationRepository stationRepository;

    @Inject
    public StationPageAddressing(PageRepository pageRepository, StationRepository stationRepository) {
        this.pageRepository = pageRepository;
        this.stationRepository = stationRepository;
    }

    @Override
    public Optional<CellDescriptions.PageAddress> addressOf(Integer stationId, String pageUid) {
        UUID uid;
        try {
            uid = UUID.fromString(pageUid);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
        return pageRepository
                .findByPublicUid(uid)
                .filter(page -> stationId == null || page.stationId() == stationId)
                .filter(page -> page.visibility().reachable())
                .flatMap(this::address);
    }

    private Optional<CellDescriptions.PageAddress> address(StationPage page) {
        return href(page).map(href -> new CellDescriptions.PageAddress(page.title(), href));
    }

    /**
     * A listed page is reached under its station, by the path its slugs spell. A page that is only
     * reachable by its link is reached by that link, which names no station because it does not sit
     * in one's site.
     */
    private Optional<String> href(StationPage page) {
        if (!page.visibility().listed()) {
            return pageRepository.findShareToken(page.id()).map(token -> "/s/" + token);
        }
        return stationRepository
                .findById(page.stationId())
                .map(StationPageAddressing::publicName)
                .map(station -> "/public/station/" + station + "/page/" + pathOf(page));
    }

    /**
     * The slugs of the line above a page, spelling the address it answers at.
     *
     * <p>The walk stops on a page it has already been through. Nothing is supposed to be able to
     * make a page its own ancestor, but this runs while a public page is being rendered, and a line
     * that closed on itself would not report a broken tree: it would build a string until the
     * request died of it.
     */
    private String pathOf(StationPage page) {
        var segments = new StringBuilder(page.slug());
        var parentId = page.parentId();
        var seen = new HashSet<Integer>();
        while (parentId != null && seen.add(parentId)) {
            var parent = pageRepository.findById(parentId).orElse(null);
            if (parent == null) break;
            segments.insert(0, parent.slug() + "/");
            parentId = parent.parentId();
        }
        return segments.toString();
    }

    private static String publicName(Station station) {
        return station.publicSlug() != null
                ? station.publicSlug()
                : station.uid().toString();
    }
}
