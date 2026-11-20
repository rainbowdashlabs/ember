/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.route;

import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.StationSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.events.entity.EventRegistrationFieldValue;
import dev.chojo.ember.feature.events.service.PartnerQuestionService;
import io.javalin.http.Context;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiParam;
import io.javalin.openapi.OpenApiResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;

import static dev.chojo.ember.api.RouteSupport.pathInt;

/**
 * The answers partner stations' members gave to the registration questions of an appointment, read
 * beside their registrations by whoever decides about them.
 */
@Singleton
public class PartnerAnswerRoutes implements Routes {
    private final PartnerQuestionService questions;

    @Inject
    public PartnerAnswerRoutes(PartnerQuestionService questions) {
        this.questions = questions;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/events/{id}/partner-answers", this::listAnswers, StationPermission.EVENT_REGISTRATION);
    }

    @OpenApi(
            path = "/api/v1/events/{id}/partner-answers",
            methods = HttpMethod.GET,
            summary = "The answers partner stations' members gave to an event's registration questions",
            tags = {"Events"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = PartnerAnswers[].class)))
    private void listAnswers(Context ctx) {
        StationSession session = StationSession.from(ctx);
        var answers = questions.answers(
                session.stationId(), pathInt(ctx, "id"), session.hasPermission(StationPermission.EVENT_EDIT));
        ctx.json(answers.entrySet().stream()
                .map(entry -> new PartnerAnswers(entry.getKey(), entry.getValue()))
                .toList());
    }

    /**
     * The answers of one partner registration.
     *
     * @param registrationId the partner's registration, as the list of partner registrations names it
     * @param answers        its answers, one per question answered
     */
    public record PartnerAnswers(int registrationId, List<EventRegistrationFieldValue> answers) {}
}
