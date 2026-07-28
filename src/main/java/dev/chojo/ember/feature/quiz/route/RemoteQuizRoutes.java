/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.quiz.route;

import dev.chojo.ember.api.FederationSession;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.feature.federation.entity.FederationPartner;
import dev.chojo.ember.feature.federation.repository.FederationRepository;
import dev.chojo.ember.feature.quiz.entity.QuizCatalog;
import dev.chojo.ember.feature.quiz.entity.QuizCategory;
import dev.chojo.ember.feature.quiz.entity.QuizQuestion;
import dev.chojo.ember.feature.quiz.service.QuizService;
import io.javalin.http.Context;
import io.javalin.http.ForbiddenResponse;
import io.javalin.http.NotFoundResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;

import static dev.chojo.ember.api.RouteSupport.pathInt;

/**
 * Server-to-server quiz endpoints. Serves this station's shared catalogs to a
 * federation partner whose RSA signature {@code AccessManager} already verified.
 */
@Singleton
public class RemoteQuizRoutes implements Routes {

    private final QuizService quizService;
    private final FederationRepository federationRepository;

    @Inject
    public RemoteQuizRoutes(QuizService quizService, FederationRepository federationRepository) {
        this.quizService = quizService;
        this.federationRepository = federationRepository;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/remote/quiz/catalogs", this::browseCatalogs);
        routes.get(prefix + "/remote/quiz/catalogs/{id}", this::getCatalog);
    }

    private void browseCatalogs(Context ctx) {
        var partner = requireFederationPartner(ctx);
        var shares = federationRepository.findQuizShares(partner.stationId());
        var result = shares.stream()
                .filter(s -> s.catalogId() != null)
                .flatMap(s -> quizService.findCatalog(s.catalogId()).stream())
                .filter(catalog -> catalog.stationId() == partner.stationId())
                .map(catalog -> new RemoteCatalogSummary(
                        catalog.id(),
                        catalog.name(),
                        catalog.description(),
                        catalog.updatedAt().toString()))
                .toList();
        ctx.json(result);
    }

    private void getCatalog(Context ctx) {
        var partner = requireFederationPartner(ctx);
        int catalogId = pathInt(ctx, "id");
        var catalog = quizService.findCatalog(catalogId).orElseThrow(NotFoundResponse::new);
        if (catalog.stationId() != partner.stationId()) {
            throw new ForbiddenResponse("Catalog not shared with this partner");
        }
        var categories = quizService.findCategories(catalog.stationId());
        var questions = quizService.findQuestions(catalog.id());
        ctx.json(new RemoteCatalogDetail(catalog, categories, questions));
    }

    private FederationPartner requireFederationPartner(Context ctx) {
        var session = FederationSession.from(ctx);
        if (session == null) {
            throw new ForbiddenResponse("Missing or invalid federation signature");
        }
        return session.partner();
    }

    private record RemoteCatalogSummary(int id, String name, String description, String updatedAt) {}

    private record RemoteCatalogDetail(
            QuizCatalog catalog, List<QuizCategory> categories, List<QuizQuestion> questions) {}
}
