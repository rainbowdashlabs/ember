/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.quiz.route;

import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.quiz.service.QuizFederationService;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import static dev.chojo.ember.api.RouteSupport.pathInt;
import static dev.chojo.ember.api.RouteSupport.pathUuid;

/**
 * User-facing federated quiz endpoints. Aggregates the catalogs federation partners
 * share with the caller's station and fetches or copies a single partner catalog,
 * transparently for local and remote partners alike.
 */
@Singleton
public class FederatedQuizRoutes implements Routes {

    private final QuizFederationService federationService;
    private final QuizRouteGuards guards;

    @Inject
    public FederatedQuizRoutes(QuizFederationService federationService, QuizRouteGuards guards) {
        this.federationService = federationService;
        this.guards = guards;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/federated/quiz/catalogs", this::browseCatalogs, StationPermission.USER);
        routes.get(
                prefix + "/federated/{stationuid}/quiz/catalogs/{id}",
                this::getCatalog,
                StationPermission.TEST_CATALOG_VIEW);
        routes.post(
                prefix + "/federated/quiz/catalogs/{id}/copy", this::copyCatalog, StationPermission.TEST_CATALOG_EDIT);
        routes.post(
                prefix + "/federated/{stationuid}/quiz/catalogs/{id}/copy",
                this::copyCatalog,
                StationPermission.TEST_CATALOG_EDIT);
    }

    private void browseCatalogs(Context ctx) {
        var session = UserSession.from(ctx);
        ctx.json(federationService.browseSharedCatalogs(session.stationId()));
    }

    private void getCatalog(Context ctx) {
        var session = UserSession.from(ctx);
        var stationUid = pathUuid(ctx, "stationuid");
        int catalogId = pathInt(ctx, "id");
        ctx.json(federationService.getFederatedQuizCatalog(session.stationId(), stationUid, catalogId));
    }

    private void copyCatalog(Context ctx) {
        var session = UserSession.from(ctx);
        int catalogId = pathInt(ctx, "id");
        guards.requireOwnedCatalog(ctx, catalogId);
        var copied = federationService.copyQuizCatalog(catalogId, session.stationId());
        ctx.status(HttpStatus.CREATED).json(copied);
    }
}
