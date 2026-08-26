/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.event.handlers;

import dev.chojo.ember.event.DomainEventHandler;
import dev.chojo.ember.event.events.NewsCreated;
import dev.chojo.ember.feature.cluster.service.ClusterAutoShareService;
import dev.chojo.ember.feature.federation.entity.ShareScope;
import dev.chojo.ember.feature.news.entity.NewsVisibilityRole;
import dev.chojo.ember.feature.news.service.NewsFederationService;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.util.List;

/**
 * Passes a cluster's news on to its stations as it is written.
 *
 * <p>A cluster writes its news at the station it owns, with the ordinary news screens. What makes the entry
 * the cluster's rather than that one station's is that every station under it reads it, so the share is made
 * here rather than left to somebody to remember. An entry written at any other station is left alone.
 *
 * <p>The service that shares an entry is built on the service that writes one, and that one publishes the
 * event this handler listens for. Asking for it while the handler is being built would therefore ask for
 * something that is itself still being built. It is asked for when an entry actually arrives instead, by
 * which time everything exists.
 */
@Singleton
public class ClusterNewsShareHandler implements DomainEventHandler<NewsCreated> {
    private final ClusterAutoShareService autoShareService;
    private final Provider<NewsFederationService> federationService;

    @Inject
    public ClusterNewsShareHandler(
            ClusterAutoShareService autoShareService, Provider<NewsFederationService> federationService) {
        this.autoShareService = autoShareService;
        this.federationService = federationService;
    }

    @Override
    public Class<NewsCreated> eventType() {
        return NewsCreated.class;
    }

    @Override
    public void handle(NewsCreated event) {
        if (autoShareService.owningCluster(event.stationId()).isEmpty()) return;
        federationService.get().setShare(event.newsId(), ShareScope.ALL_PARTNERS, NewsVisibilityRole.MEMBER, List.of());
    }
}
