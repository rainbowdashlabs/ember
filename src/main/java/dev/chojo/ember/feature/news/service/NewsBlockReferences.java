/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.news.service;

import dev.chojo.ember.api.refusal.NewsRefusal;
import dev.chojo.ember.feature.content.entity.BlockAudience;
import dev.chojo.ember.feature.content.entity.CellConfig;
import dev.chojo.ember.feature.content.service.BlockReferences;
import dev.chojo.ember.feature.news.repository.NewsRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * Refuses a news block naming an entry not every reader of its content may read: on a page anything
 * not on the station's public blog, in a news or wiki article anything unpublished or kept to part
 * of the station. An entry of another station is never the station's to name.
 */
@Singleton
public class NewsBlockReferences implements BlockReferences {
    private final NewsRepository repository;

    @Inject
    public NewsBlockReferences(NewsRepository repository) {
        this.repository = repository;
    }

    @Override
    public void requireReachable(@Nullable Integer stationId, BlockAudience audience, CellConfig config) {
        if (!(config instanceof CellConfig.NewsTeaserConfig teaser)) return;
        String newsUid = teaser.newsUid();
        if (newsUid == null) return;
        boolean reachable = stationId != null
                && repository
                        .findOpenByUid(stationId, audience, UUID.fromString(newsUid))
                        .isPresent();
        if (reachable) return;
        throw (audience == BlockAudience.PUBLIC
                        ? NewsRefusal.NEWS_BLOCK_ENTRY_NOT_PUBLIC
                        : NewsRefusal.NEWS_BLOCK_ENTRY_NOT_FOR_EVERY_MEMBER)
                .raise();
    }
}
