/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.event.handlers;

import dev.chojo.ember.event.DomainEventHandler;
import dev.chojo.ember.event.events.EventCreated;
import dev.chojo.ember.feature.cluster.service.ClusterAutoShareService;
import dev.chojo.ember.feature.events.service.EventFederationService;
import dev.chojo.ember.feature.federation.entity.ShareScope;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.util.List;

/**
 * Puts a cluster's appointment in the calendar of every station under it, as it is made.
 *
 * <p>A cluster keeps its calendar on the station it owns, and fills it with the ordinary event screens. An
 * appointment nobody under the cluster can see is an appointment the cluster did not make, so the share
 * happens here rather than as a step somebody has to take afterwards. An appointment made at any other
 * station is left alone.
 *
 * <p>The service that shares an appointment is built on the service that makes one, and that one publishes
 * the event this handler listens for. Asking for it while the handler is being built would therefore ask for
 * something that is itself still being built. It is asked for when an appointment actually arrives instead,
 * by which time everything exists.
 */
@Singleton
public class ClusterEventShareHandler implements DomainEventHandler<EventCreated> {
    private final ClusterAutoShareService autoShareService;
    private final Provider<EventFederationService> federationService;

    @Inject
    public ClusterEventShareHandler(
            ClusterAutoShareService autoShareService, Provider<EventFederationService> federationService) {
        this.autoShareService = autoShareService;
        this.federationService = federationService;
    }

    @Override
    public Class<EventCreated> eventType() {
        return EventCreated.class;
    }

    @Override
    public void handle(EventCreated event) {
        if (autoShareService.owningCluster(event.stationId()).isEmpty()) return;
        federationService.get().setShare(event.event().id(), ShareScope.ALL_PARTNERS, List.of());
    }
}
