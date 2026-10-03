/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.contract;

import dev.chojo.ember.feature.board.route.RemoteBoardRoutes;
import dev.chojo.ember.feature.board.route.RemoteBoardTicketDetailRoutes;
import dev.chojo.ember.feature.board.route.RemoteBoardTicketLinkRoutes;
import dev.chojo.ember.feature.board.route.RemoteBoardTicketRoutes;
import dev.chojo.ember.feature.board.route.RemoteBoardWebhookRoutes;
import dev.chojo.ember.feature.events.route.RemoteEventRoutes;
import dev.chojo.ember.feature.federation.route.RemoteFederationRoutes;
import dev.chojo.ember.feature.federation.route.RemoteLendingRoutes;
import dev.chojo.ember.feature.inventory.route.RemoteInventoryTagRoutes;
import dev.chojo.ember.feature.knowledgebase.route.RemoteKnowledgeBaseRoutes;
import dev.chojo.ember.feature.news.route.RemoteNewsRoutes;
import dev.chojo.ember.feature.protocol.route.RemoteTestProtocolRoutes;
import dev.chojo.ember.feature.quiz.route.RemoteQuizRoutes;
import io.javalin.http.HandlerType;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * The complete declared federation contract: every {@code /remote} endpoint of every route
 * class, aggregated from the per-class {@code CONTRACT} lists. The per-surface version
 * hashes are computed from this catalog, and outgoing requests resolve the surface of the
 * path they are about to call through it.
 */
public final class FederationContractCatalog {

    public static final List<FederationEndpoint> ENDPOINTS = Stream.of(
                    RemoteFederationRoutes.CONTRACT,
                    RemoteKnowledgeBaseRoutes.CONTRACT,
                    RemoteQuizRoutes.CONTRACT,
                    RemoteTestProtocolRoutes.CONTRACT,
                    RemoteLendingRoutes.CONTRACT,
                    RemoteInventoryTagRoutes.CONTRACT,
                    RemoteEventRoutes.CONTRACT,
                    RemoteNewsRoutes.CONTRACT,
                    RemoteBoardWebhookRoutes.CONTRACT,
                    RemoteBoardRoutes.CONTRACT,
                    RemoteBoardTicketRoutes.CONTRACT,
                    RemoteBoardTicketDetailRoutes.CONTRACT,
                    RemoteBoardTicketLinkRoutes.CONTRACT)
            .flatMap(List::stream)
            .toList();

    private FederationContractCatalog() {}

    /**
     * Resolves the contract surface of a concrete contract path, e.g.
     * {@code /remote/boards/a3f1/tickets/7}.
     */
    public static Optional<FederationSurface> surfaceOf(HandlerType method, String path) {
        return ENDPOINTS.stream()
                .filter(endpoint -> endpoint.matches(method, path))
                .findFirst()
                .map(FederationEndpoint::surface);
    }

    /**
     * {@link #surfaceOf} for a full request path that still carries the API prefix, such as
     * a partner-registered webhook URL.
     */
    public static Optional<FederationSurface> surfaceOfRequestPath(HandlerType method, String requestPath) {
        int remote = requestPath.indexOf("/remote/");
        if (remote < 0) return Optional.empty();
        return surfaceOf(method, requestPath.substring(remote));
    }
}
