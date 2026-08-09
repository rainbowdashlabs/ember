/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.quiz.route;

import dev.chojo.ember.api.FederationSession;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.feature.federation.contract.FederationContractBinder;
import dev.chojo.ember.feature.federation.contract.FederationEndpoint;
import dev.chojo.ember.feature.federation.contract.FederationSurface;
import dev.chojo.ember.feature.federation.repository.FederationRepository;
import dev.chojo.ember.feature.quiz.entity.QuizCatalog;
import dev.chojo.ember.feature.quiz.entity.QuizCategory;
import dev.chojo.ember.feature.quiz.entity.QuizQuestion;
import dev.chojo.ember.feature.quiz.service.QuizCatalogService;
import dev.chojo.ember.feature.quiz.service.QuizQuestionService;
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

    public static final FederationEndpoint BROWSE_CATALOGS = FederationEndpoint.getList(
            FederationSurface.QUIZ_SHARE, "/remote/quiz/catalogs", RemoteCatalogSummary.class);
    public static final FederationEndpoint GET_CATALOG = FederationEndpoint.get(
            FederationSurface.QUIZ_SHARE, "/remote/quiz/catalogs/{id}", RemoteCatalogDetail.class);

    public static final List<FederationEndpoint> CONTRACT = List.of(BROWSE_CATALOGS, GET_CATALOG);

    private final QuizCatalogService catalogService;
    private final QuizQuestionService questionService;
    private final FederationRepository federationRepository;

    @Inject
    public RemoteQuizRoutes(
            QuizCatalogService catalogService,
            QuizQuestionService questionService,
            FederationRepository federationRepository) {
        this.catalogService = catalogService;
        this.questionService = questionService;
        this.federationRepository = federationRepository;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        FederationContractBinder.register(
                routes, prefix, CONTRACT, binder -> binder.handle(BROWSE_CATALOGS, this::browseCatalogs)
                        .handle(GET_CATALOG, this::getCatalog));
    }

    private void browseCatalogs(Context ctx) {
        var partner = FederationSession.requirePartner(ctx);
        var shares = federationRepository.findQuizShares(partner.stationId());
        var result = shares.stream()
                .filter(s -> s.catalogId() != null)
                .flatMap(s -> catalogService.findCatalog(s.catalogId()).stream())
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
        var partner = FederationSession.requirePartner(ctx);
        int catalogId = pathInt(ctx, "id");
        var catalog = catalogService.findCatalog(catalogId).orElseThrow(NotFoundResponse::new);
        if (catalog.stationId() != partner.stationId()) {
            throw new ForbiddenResponse("Catalog not shared with this partner");
        }
        var categories = catalogService.findCategories(catalog.stationId());
        var questions = questionService.findQuestions(catalog.id());
        ctx.json(new RemoteCatalogDetail(catalog, categories, questions));
    }

    public record RemoteCatalogSummary(int id, String name, String description, String updatedAt) {}

    public record RemoteCatalogDetail(
            QuizCatalog catalog, List<QuizCategory> categories, List<QuizQuestion> questions) {}
}
