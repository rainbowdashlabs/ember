/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.quiz.route;

import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.refusal.QuizRefusal;
import dev.chojo.ember.feature.quiz.service.PublicQuizService;
import dev.chojo.ember.feature.quiz.service.PublicQuizService.PublicQuizCatalog;
import dev.chojo.ember.feature.quiz.service.PublicQuizService.PublicQuizQuestion;
import io.javalin.http.Context;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Public anonymous-internet endpoints backing the QUIZ_TEASER cell. The cell picks one
 * random question from the requested catalogs and reveals the answer on click, the same shape
 * as the internal training view.
 */
@Singleton
public class PublicQuizRoutes implements Routes {

    private final PublicQuizService quiz;

    @Inject
    public PublicQuizRoutes(PublicQuizService quiz) {
        this.quiz = quiz;
    }

    private static List<Integer> parseCatalogIds(String raw) {
        var out = new ArrayList<Integer>();
        for (String token : raw.split(",")) {
            String trimmed = token.trim();
            if (trimmed.isEmpty()) continue;
            try {
                out.add(Integer.parseInt(trimmed));
            } catch (NumberFormatException ignored) {
                throw QuizRefusal.PUBLIC_QUIZ_CATALOG_NOT_A_NUMBER.raise();
            }
        }
        return out;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/public/quiz/{stationUid}/catalogs", this::listPublicCatalogs);
        routes.get(prefix + "/public/quiz/{stationUid}/random", this::randomQuestion);
    }

    private static UUID stationUid(Context ctx) {
        String uidParam = ctx.pathParam("stationUid");
        try {
            return UUID.fromString(uidParam);
        } catch (IllegalArgumentException e) {
            throw QuizRefusal.PUBLIC_QUIZ_STATION_LINK_NOT_GOOD.raise();
        }
    }

    @OpenApi(
            path = "/api/v1/public/quiz/{stationUid}/catalogs",
            methods = HttpMethod.GET,
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = PublicQuizCatalog[].class)))
    private void listPublicCatalogs(Context ctx) {
        ctx.json(quiz.catalogs(stationUid(ctx)));
    }

    @OpenApi(
            path = "/api/v1/public/quiz/{stationUid}/random",
            methods = HttpMethod.GET,
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = PublicQuizQuestion.class)))
    private void randomQuestion(Context ctx) {
        var station = stationUid(ctx);
        String catalogsParam = ctx.queryParam("catalogs");
        if (catalogsParam == null || catalogsParam.isBlank()) {
            throw QuizRefusal.PUBLIC_QUIZ_CATALOGS_NOT_NAMED.raise();
        }
        var question = quiz.randomQuestion(station, parseCatalogIds(catalogsParam));
        ctx.header("Cache-Control", "no-store, no-cache, must-revalidate");
        ctx.json(question);
    }
}
