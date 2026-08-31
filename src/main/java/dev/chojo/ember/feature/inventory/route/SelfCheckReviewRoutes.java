/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.route;

import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.inventory.entity.InventoryItemMetadata;
import dev.chojo.ember.feature.inventory.entity.ItemCorrection;
import dev.chojo.ember.feature.inventory.entity.ItemOwner;
import dev.chojo.ember.feature.inventory.entity.SelfCheck;
import dev.chojo.ember.feature.inventory.entity.SelfCheckState;
import dev.chojo.ember.feature.inventory.service.SelfCheckReviewService;
import dev.chojo.ember.feature.inventory.service.SelfCheckReviewService.SelfCheckReview;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import io.javalin.http.Context;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiParam;
import io.javalin.openapi.OpenApiRequestBody;
import io.javalin.openapi.OpenApiResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

import static dev.chojo.ember.api.RouteSupport.pathInt;

/**
 * Reading a member's submission and settling it, which is everything the member's own endpoints
 * refuse.
 *
 * <p>Every address here asks for the check permission, and it is the same permission that hands the
 * task out in the first place. Whether this particular reviewer may settle this particular
 * submission is a narrower question than the permission answers, and the service asks it: the two
 * names on a check are the point of it, so they may not be the same person.
 */
@Singleton
public class SelfCheckReviewRoutes implements Routes {
    private final SelfCheckReviewService reviewService;
    private final StationMemberRepository stationMemberRepository;
    private final AccountRepository accountRepository;

    @Inject
    public SelfCheckReviewRoutes(
            SelfCheckReviewService reviewService,
            StationMemberRepository stationMemberRepository,
            AccountRepository accountRepository) {
        this.reviewService = reviewService;
        this.stationMemberRepository = stationMemberRepository;
        this.accountRepository = accountRepository;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/self-check-reviews", this::forStation, StationPermission.INVENTORY_CHECK);
        routes.get(prefix + "/self-check-reviews/{id}", this::read, StationPermission.INVENTORY_CHECK);
        routes.post(
                prefix + "/self-check-reviews/{id}/rows/{rowId}/take",
                this::take,
                StationPermission.INVENTORY_CHECK);
        routes.post(
                prefix + "/self-check-reviews/{id}/rows/{rowId}/correct",
                this::correct,
                StationPermission.INVENTORY_CHECK);
        routes.post(
                prefix + "/self-check-reviews/{id}/rows/{rowId}/refuse",
                this::refuse,
                StationPermission.INVENTORY_CHECK);
    }

    @OpenApi(
            path = "/api/v1/self-check-reviews",
            methods = HttpMethod.GET,
            summary = "The self-checks this station has out, and where each of them stands",
            tags = {"Inventory Checks"},
            queryParams = @OpenApiParam(name = "includeEnded", type = Boolean.class),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = SelfCheckTask[].class)))
    private void forStation(Context ctx) {
        UserSession session = UserSession.from(ctx);
        boolean includeEnded = "true".equalsIgnoreCase(ctx.queryParam("includeEnded"));
        var tasks = reviewService.forStation(session.stationId(), includeEnded);
        ctx.json(tasks.stream().map(this::toTask).toList());
    }

    @OpenApi(
            path = "/api/v1/self-check-reviews/{id}",
            methods = HttpMethod.GET,
            summary = "One submission, with what taking each answer would do",
            tags = {"Inventory Checks"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = SelfCheckReview.class)))
    private void read(Context ctx) {
        UserSession session = UserSession.from(ctx);
        ctx.json(reviewService.read(
                pathInt(ctx, "id"), session.stationId(), session.member().id()));
    }

    @OpenApi(
            path = "/api/v1/self-check-reviews/{id}/rows/{rowId}/take",
            methods = HttpMethod.POST,
            summary = "Take one answer and write what follows from it",
            tags = {"Inventory Checks"},
            pathParams = {
                @OpenApiParam(name = "id", type = Integer.class, required = true),
                @OpenApiParam(name = "rowId", type = Integer.class, required = true)
            },
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = SelfCheckReview.class)))
    private void take(Context ctx) {
        UserSession session = UserSession.from(ctx);
        ctx.json(reviewService.take(
                pathInt(ctx, "id"),
                pathInt(ctx, "rowId"),
                session.stationId(),
                session.member().id()));
    }

    @OpenApi(
            path = "/api/v1/self-check-reviews/{id}/rows/{rowId}/correct",
            methods = HttpMethod.POST,
            summary = "Put the record right and take the answer in one act",
            tags = {"Inventory Checks"},
            pathParams = {
                @OpenApiParam(name = "id", type = Integer.class, required = true),
                @OpenApiParam(name = "rowId", type = Integer.class, required = true)
            },
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = CorrectRowRequest.class)),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = SelfCheckReview.class)))
    private void correct(Context ctx) {
        UserSession session = UserSession.from(ctx);
        var request = ctx.bodyAsClass(CorrectRowRequest.class);
        ctx.json(reviewService.correctAndTake(
                pathInt(ctx, "id"),
                pathInt(ctx, "rowId"),
                session.stationId(),
                session.member().id(),
                request.toCorrection()));
    }

    @OpenApi(
            path = "/api/v1/self-check-reviews/{id}/rows/{rowId}/refuse",
            methods = HttpMethod.POST,
            summary = "Send one answer back to the member with a reason",
            tags = {"Inventory Checks"},
            pathParams = {
                @OpenApiParam(name = "id", type = Integer.class, required = true),
                @OpenApiParam(name = "rowId", type = Integer.class, required = true)
            },
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = RefuseRowRequest.class)),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = SelfCheckReview.class)))
    private void refuse(Context ctx) {
        UserSession session = UserSession.from(ctx);
        var request = ctx.bodyAsClass(RefuseRowRequest.class);
        ctx.json(reviewService.refuse(
                pathInt(ctx, "id"),
                pathInt(ctx, "rowId"),
                session.stationId(),
                session.member().id(),
                request.reason()));
    }

    /**
     * One task on the chasing list, named for whom it is about.
     */
    private SelfCheckTask toTask(SelfCheck task) {
        return new SelfCheckTask(
                task.id(),
                task.memberId(),
                nameOf(task.memberId()),
                task.dueOn(),
                task.state(),
                task.handedOutAt(),
                task.submittedAt(),
                nameOf(task.handedOutBy()),
                task.checkId());
    }

    private String nameOf(Integer memberId) {
        if (memberId == null) return "";
        return stationMemberRepository
                .findById(memberId)
                .flatMap(member ->
                        member.accountId() == null ? Optional.empty() : accountRepository.findById(member.accountId()))
                .map(account -> account.fullName().strip())
                .orElse("");
    }

    /**
     * What the member is actually holding, as the reviewer names it. The piece coming off the record
     * is never named here: it is the one the answer is about, and the service reads it from the row.
     */
    public record CorrectRowRequest(
            int inventoryId,
            Integer pickedItemId,
            Integer sizeId,
            ItemOwner ownerKind,
            String internalId,
            InventoryItemMetadata metadata) {
        ItemCorrection toCorrection() {
            return new ItemCorrection(inventoryId, null, pickedItemId, sizeId, ownerKind, internalId, metadata);
        }
    }

    public record RefuseRowRequest(String reason) {}

    public record SelfCheckTask(
            int id,
            int memberId,
            String memberName,
            LocalDate dueOn,
            SelfCheckState state,
            Instant handedOutAt,
            Instant submittedAt,
            String handedOutByName,
            Integer checkId) {}
}
