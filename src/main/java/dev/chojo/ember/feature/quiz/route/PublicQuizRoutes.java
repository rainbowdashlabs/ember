/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.quiz.route;

import dev.chojo.ember.api.Routes;
import dev.chojo.ember.feature.quiz.entity.QuizQuestion;
import dev.chojo.ember.feature.quiz.repository.QuizCatalogRepository;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.repository.StationRepository;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.NotFoundResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Public anonymous-internet endpoints backing the QUIZ_TEASER cell. The cell picks one
 * random question from the requested catalogs and reveals the answer on click - same shape
 * as the internal training view.
 */
@Singleton
public class PublicQuizRoutes implements Routes {

    private final QuizCatalogRepository catalogRepository;
    private final StationRepository stationRepository;

    @Inject
    public PublicQuizRoutes(QuizCatalogRepository catalogRepository, StationRepository stationRepository) {
        this.catalogRepository = catalogRepository;
        this.stationRepository = stationRepository;
    }

    private static List<Integer> parseCatalogIds(String raw) {
        var out = new ArrayList<Integer>();
        for (String token : raw.split(",")) {
            String trimmed = token.trim();
            if (trimmed.isEmpty()) continue;
            try {
                out.add(Integer.parseInt(trimmed));
            } catch (NumberFormatException ignored) {
                throw new BadRequestResponse("catalogs contains a non-numeric id: " + trimmed);
            }
        }
        return out;
    }

    private static PublicQuestion toPublicQuestion(QuizQuestion q) {
        return new PublicQuestion(
                q.id(), q.quizQuestionType().name(), q.title(), q.description(), q.imageUrl(), q.configNode());
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/public/quiz/{stationUid}/catalogs", this::listPublicCatalogs);
        routes.get(prefix + "/public/quiz/{stationUid}/random", this::randomQuestion);
    }

    private Station resolveStation(Context ctx) {
        String uidParam = ctx.pathParam("stationUid");
        UUID uid;
        try {
            uid = UUID.fromString(uidParam);
        } catch (IllegalArgumentException e) {
            throw new BadRequestResponse("Invalid station ID");
        }
        return stationRepository.findByUid(uid).orElseThrow(NotFoundResponse::new);
    }

    private void listPublicCatalogs(Context ctx) {
        var station = resolveStation(ctx);
        var catalogs = catalogRepository.findPublicByStation(station.id()).stream()
                .map(c -> new PublicCatalog(c.id(), c.name(), c.description()))
                .toList();
        ctx.json(catalogs);
    }

    private void randomQuestion(Context ctx) {
        var station = resolveStation(ctx);
        String catalogsParam = ctx.queryParam("catalogs");
        if (catalogsParam == null || catalogsParam.isBlank()) {
            throw new BadRequestResponse("catalogs query parameter is required");
        }
        var ids = parseCatalogIds(catalogsParam);
        if (ids.isEmpty()) throw new BadRequestResponse("catalogs query parameter is empty");
        var picked =
                catalogRepository.findRandomPublicQuestion(station.id(), ids).orElseThrow(NotFoundResponse::new);
        ctx.header("Cache-Control", "no-store, no-cache, must-revalidate");
        ctx.json(toPublicQuestion(picked));
    }

    /**
     * Public catalog shape - only the bits the editor's catalog picker (auth-gated, but using
     * this same endpoint via the page-render layer) needs. No question counts, no audit fields.
     */
    public record PublicCatalog(int id, String name, String description) {}

    /**
     * Single random question payload. The {@code config} node intentionally carries every field
     * the question type uses, including the {@code correct} marker on options - the cell reveals
     * the answer on click, so the client legitimately needs it.
     */
    public record PublicQuestion(
            int id, String questionType, String title, String description, String imageUrl, JsonNode config) {}
}
