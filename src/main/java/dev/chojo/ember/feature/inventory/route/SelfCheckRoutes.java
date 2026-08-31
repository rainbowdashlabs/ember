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
import dev.chojo.ember.feature.inventory.entity.InventoryItem;
import dev.chojo.ember.feature.inventory.entity.RequiredInventoryItem;
import dev.chojo.ember.feature.inventory.entity.SelfCheck;
import dev.chojo.ember.feature.inventory.entity.SelfCheckAnswer;
import dev.chojo.ember.feature.inventory.entity.SelfCheckAnswerInput;
import dev.chojo.ember.feature.inventory.entity.SelfCheckRaised;
import dev.chojo.ember.feature.inventory.entity.SelfCheckRow;
import dev.chojo.ember.feature.inventory.entity.SelfCheckState;
import dev.chojo.ember.feature.inventory.service.SelfCheckService;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
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
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;

import static dev.chojo.ember.api.RouteSupport.pathInt;

/**
 * A member answering for their own gear, and a checker handing that question out.
 *
 * <p>Only one of these endpoints settles anything, and it is the one that hands the task out: it
 * asks for the check permission and creates nothing but the question. Everything a member may reach
 * here writes what they said and nothing else. Assigning a piece, creating one, ordering one,
 * putting a record right and signing a submission off all live behind the check permission, on the
 * walk's own endpoints, and none of them has a counterpart here.
 */
@Singleton
public class SelfCheckRoutes implements Routes {
    private final SelfCheckService selfCheckService;
    private final StationMemberRepository stationMemberRepository;
    private final AccountRepository accountRepository;

    @Inject
    public SelfCheckRoutes(
            SelfCheckService selfCheckService,
            StationMemberRepository stationMemberRepository,
            AccountRepository accountRepository) {
        this.selfCheckService = selfCheckService;
        this.stationMemberRepository = stationMemberRepository;
        this.accountRepository = accountRepository;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.post(prefix + "/self-checks", this::handOut, StationPermission.INVENTORY_CHECK);
        routes.get(prefix + "/self-checks/mine", this::mine, StationPermission.USER);
        routes.get(prefix + "/self-checks/{id}", this::read, StationPermission.USER);
        routes.put(prefix + "/self-checks/{id}/answers", this::answer, StationPermission.USER);
        routes.post(prefix + "/self-checks/{id}/submit", this::submit, StationPermission.USER);
    }

    @OpenApi(
            path = "/api/v1/self-checks",
            methods = HttpMethod.POST,
            summary = "Ask members to check their own gear",
            tags = {"Inventory Checks"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = HandOutSelfChecksRequest.class)),
            responses = @OpenApiResponse(status = "201", content = @OpenApiContent(from = SelfCheckSummary[].class)))
    private void handOut(Context ctx) {
        UserSession session = UserSession.from(ctx);
        var request = ctx.bodyAsClass(HandOutSelfChecksRequest.class);
        var handed = selfCheckService.handOut(
                session.stationId(),
                request.memberIds(),
                parseDueOn(request.dueOn()),
                session.member().id());
        ctx.status(HttpStatus.CREATED).json(handed.stream().map(this::toSummary).toList());
    }

    @OpenApi(
            path = "/api/v1/self-checks/mine",
            methods = HttpMethod.GET,
            summary = "The self-checks I am answerable for, mine and those of members in my care",
            tags = {"Inventory Checks"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = SelfCheckSummary[].class)))
    private void mine(Context ctx) {
        UserSession session = UserSession.from(ctx);
        var tasks = selfCheckService.outstandingFor(session.member().id(), guardian(session));
        ctx.json(tasks.stream().map(this::toSummary).toList());
    }

    @OpenApi(
            path = "/api/v1/self-checks/{id}",
            methods = HttpMethod.GET,
            summary = "Read one self-check with the gear it asks about",
            tags = {"Inventory Checks"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = SelfCheckResponse.class)))
    private void read(Context ctx) {
        UserSession session = UserSession.from(ctx);
        var view = selfCheckService.read(
                pathInt(ctx, "id"), session.stationId(), session.member().id(), guardian(session));
        ctx.json(new SelfCheckResponse(
                toSummary(view.task()), view.required(), view.assigned(), view.rows(), view.raised()));
    }

    @OpenApi(
            path = "/api/v1/self-checks/{id}/answers",
            methods = HttpMethod.PUT,
            summary = "Save what the member says, as often as they like while the check is open",
            tags = {"Inventory Checks"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = SelfCheckAnswerRequest.class)),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = SelfCheckRow[].class)))
    private void answer(Context ctx) {
        UserSession session = UserSession.from(ctx);
        var request = ctx.bodyAsClass(SelfCheckAnswerRequest.class);
        if (request.answers() == null) {
            throw new BadRequestResponse("answers are required");
        }
        var rows = selfCheckService.answer(
                pathInt(ctx, "id"),
                session.stationId(),
                session.member().id(),
                guardian(session),
                request.answers().stream().map(AnswerBody::toInput).toList());
        ctx.json(rows);
    }

    @OpenApi(
            path = "/api/v1/self-checks/{id}/submit",
            methods = HttpMethod.POST,
            summary = "Hand the self-check in",
            tags = {"Inventory Checks"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = SelfCheckSummary.class)))
    private void submit(Context ctx) {
        UserSession session = UserSession.from(ctx);
        var task = selfCheckService.submit(
                pathInt(ctx, "id"), session.stationId(), session.member().id(), guardian(session));
        ctx.json(toSummary(task));
    }

    private static boolean guardian(UserSession session) {
        return session.hasPermission(StationPermission.MEMBER_GUARDIAN);
    }

    private static LocalDate parseDueOn(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return LocalDate.parse(raw.strip());
        } catch (DateTimeParseException e) {
            throw new BadRequestResponse("dueOn is not a date");
        }
    }

    /**
     * One task with the name of whoever it is about, because a guardian's list holds several and
     * names alone tell them apart.
     */
    private SelfCheckSummary toSummary(SelfCheck task) {
        String memberName = stationMemberRepository
                .findById(task.memberId())
                .flatMap(member ->
                        member.accountId() == null ? Optional.empty() : accountRepository.findById(member.accountId()))
                .map(account -> account.fullName().strip())
                .orElse("");
        return new SelfCheckSummary(
                task.id(),
                task.memberId(),
                memberName,
                task.dueOn(),
                task.state(),
                task.handedOutAt(),
                task.submittedAt());
    }

    public record HandOutSelfChecksRequest(List<Integer> memberIds, String dueOn) {}

    public record SelfCheckAnswerRequest(List<AnswerBody> answers) {}

    /**
     * One answer as it arrives over the wire.
     *
     * @param itemId          the piece it is about, or {@code null} on an empty place
     * @param inventoryId     the inventory an empty place belongs to
     * @param slot            which empty place in that inventory, counted from zero
     * @param answer          what the member said
     * @param note            what they wrote beside it
     * @param typedInternalId the number they read off a piece nobody wrote down
     */
    public record AnswerBody(
            Integer itemId,
            Integer inventoryId,
            Integer slot,
            SelfCheckAnswer answer,
            String note,
            String typedInternalId) {
        SelfCheckAnswerInput toInput() {
            return new SelfCheckAnswerInput(itemId, inventoryId, slot, answer, note, typedInternalId);
        }
    }

    public record SelfCheckSummary(
            int id,
            int memberId,
            String memberName,
            LocalDate dueOn,
            SelfCheckState state,
            Instant handedOutAt,
            Instant submittedAt) {}

    /**
     * A task as the person answering it reads it.
     *
     * <p>What is not here is the point of it: the free stock a checker's walk carries is nowhere in
     * this answer, and neither is anything the typed numbers matched.
     */
    public record SelfCheckResponse(
            SelfCheckSummary task,
            List<RequiredInventoryItem> required,
            List<InventoryItem> assigned,
            List<SelfCheckRow> rows,
            List<SelfCheckRaised> raised) {}
}
