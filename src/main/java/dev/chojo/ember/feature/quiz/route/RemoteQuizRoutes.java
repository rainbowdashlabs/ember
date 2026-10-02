/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.quiz.route;

import dev.chojo.ember.api.Routes;
import dev.chojo.ember.feature.federation.contract.FederationContractBinder;
import dev.chojo.ember.feature.federation.contract.FederationEndpoint;
import dev.chojo.ember.feature.federation.contract.FederationSurface;
import dev.chojo.ember.feature.federation.transport.FederationEndpoints;
import dev.chojo.ember.feature.quiz.entity.QuizCatalog;
import dev.chojo.ember.feature.quiz.entity.QuizCategory;
import dev.chojo.ember.feature.quiz.entity.QuizQuestion;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;

/**
 * Server-to-server quiz endpoints. Serves this station's shared catalogs to a federation partner
 * whose RSA signature {@code AccessManager} already verified, through the serving functions of
 * {@code QuizFederationService}.
 */
@Singleton
public class RemoteQuizRoutes implements Routes {

    public static final FederationEndpoint BROWSE_CATALOGS = FederationEndpoint.getList(
            FederationSurface.QUIZ_SHARE, "/remote/quiz/catalogs", RemoteCatalogSummary.class);
    public static final FederationEndpoint GET_CATALOG = FederationEndpoint.get(
            FederationSurface.QUIZ_SHARE, "/remote/quiz/catalogs/{id}", RemoteCatalogDetail.class);

    public static final List<FederationEndpoint> CONTRACT = List.of(BROWSE_CATALOGS, GET_CATALOG);

    private final FederationEndpoints endpoints;

    @Inject
    public RemoteQuizRoutes(FederationEndpoints endpoints) {
        this.endpoints = endpoints;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        FederationContractBinder.register(routes, prefix, CONTRACT, endpoints, binder -> binder.serve(BROWSE_CATALOGS)
                .serve(GET_CATALOG));
    }

    public record RemoteCatalogSummary(int id, String name, String description, String updatedAt) {}

    public record RemoteCatalogDetail(
            QuizCatalog catalog, List<QuizCategory> categories, List<QuizQuestion> questions) {}
}
