/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.route;

import dev.chojo.ember.api.ErrorResponseWrapper;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.StationSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.members.entity.FindableBecause;
import dev.chojo.ember.feature.members.entity.InviteCandidate;
import dev.chojo.ember.feature.members.service.InviteCandidateService;
import io.javalin.http.Context;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiParam;
import io.javalin.openapi.OpenApiResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;

/**
 * Finding somebody who already has an account on this instance by their name, to invite them into the
 * station. Open to whoever may invite members, because the answer is used for nothing else.
 */
@Singleton
public class InviteCandidateRoutes implements Routes {
    private final InviteCandidateService candidates;

    @Inject
    public InviteCandidateRoutes(InviteCandidateService candidates) {
        this.candidates = candidates;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/members/invite-candidates", this::search, StationPermission.MEMBER_EDIT);
    }

    @OpenApi(
            path = "/api/v1/members/invite-candidates",
            methods = HttpMethod.GET,
            summary = "Find people of this instance by name to invite into the station",
            description =
                    "Lists at most ten accounts whose name holds every word of the search: people who share a station or an association with the caller, and people who chose to be findable from every station. Never shows an address. The search needs a word of at least three letters; case and accents do not matter.",
            tags = {"Members"},
            queryParams = @OpenApiParam(name = "q", description = "Words of the person's name", required = true),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = CandidateResponse[].class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class)),
                @OpenApiResponse(status = "429", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void search(Context ctx) {
        String search = Objects.requireNonNullElse(ctx.queryParam("q"), "");
        ctx.json(candidates.search(StationSession.from(ctx), search).stream()
                .map(CandidateResponse::of)
                .toList());
    }

    /**
     * Somebody the caller may invite by name.
     *
     * @param accountUid   the account, as an invitation names it
     * @param firstName    their first name
     * @param lastName     their last name
     * @param because      why the caller may find them
     * @param through      the shared station or association, or null for somebody who chose to be findable
     * @param alreadyHere  whether they are a member of this station already
     * @param alreadyAsked whether this station waits for them to answer a link request
     */
    public record CandidateResponse(
            UUID accountUid,
            String firstName,
            String lastName,
            FindableBecause because,
            @Nullable String through,
            boolean alreadyHere,
            boolean alreadyAsked) {

        static CandidateResponse of(InviteCandidate candidate) {
            return new CandidateResponse(
                    candidate.accountUid(),
                    candidate.firstName(),
                    candidate.lastName(),
                    candidate.because(),
                    candidate.through(),
                    candidate.alreadyHere(),
                    candidate.alreadyAsked());
        }
    }
}
