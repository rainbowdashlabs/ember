/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.route;

import dev.chojo.ember.api.ErrorResponseWrapper;
import dev.chojo.ember.api.MemberIdentity;
import dev.chojo.ember.api.RouteSupport;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.inventory.entity.AckKind;
import dev.chojo.ember.feature.inventory.entity.Inventory;
import dev.chojo.ember.feature.inventory.entity.ItemCustody;
import dev.chojo.ember.feature.inventory.entity.ItemMovement;
import dev.chojo.ember.feature.inventory.entity.MovementPurpose;
import dev.chojo.ember.feature.inventory.entity.MovementState;
import dev.chojo.ember.feature.inventory.entity.StepActor;
import dev.chojo.ember.feature.inventory.entity.StepSubject;
import dev.chojo.ember.feature.inventory.repository.InventoryRepository;
import dev.chojo.ember.feature.inventory.service.ItemMovementService;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.members.service.MemberIdentityFactory;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.ForbiddenResponse;
import io.javalin.http.HttpStatus;
import io.javalin.http.NotFoundResponse;
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
import java.util.HashSet;
import java.util.List;

import static dev.chojo.ember.api.RouteSupport.pathInt;

/**
 * Routes for movements of gear between parties.
 *
 * <p>Acting on a movement needs no more than {@code USER}, because which permission applies depends
 * on the step the movement is standing on: a member's own announcement is theirs to make, and the
 * station's steps are the station's. The real check is
 * {@link ItemMovementService#acknowledge(int, int, ItemMovementService.Actor, String, Integer)}'s to
 * make, and it makes it against the step rather than against the route.
 */
@Singleton
public class MovementRoutes implements Routes {
    private final ItemMovementService movementService;
    private final InventoryRepository inventoryRepository;
    private final AccountRepository accountRepository;
    private final StationMemberRepository stationMemberRepository;
    private final MemberIdentityFactory memberIdentityFactory;

    @Inject
    public MovementRoutes(
            ItemMovementService movementService,
            InventoryRepository inventoryRepository,
            AccountRepository accountRepository,
            StationMemberRepository stationMemberRepository,
            MemberIdentityFactory memberIdentityFactory) {
        this.movementService = movementService;
        this.inventoryRepository = inventoryRepository;
        this.accountRepository = accountRepository;
        this.stationMemberRepository = stationMemberRepository;
        this.memberIdentityFactory = memberIdentityFactory;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/movements", this::list, StationPermission.USER);
        routes.post(prefix + "/movements", this::create, StationPermission.USER);
        routes.get(prefix + "/movements/{id}", this::get, StationPermission.USER);
        routes.post(prefix + "/movements/{id}/acknowledge", this::acknowledge, StationPermission.USER);
        routes.post(prefix + "/movements/{id}/force", this::force, StationPermission.INVENTORY_MANAGER);
        routes.post(prefix + "/movements/{id}/decline", this::decline, StationPermission.USER);
        routes.post(prefix + "/movements/{id}/cancel", this::cancel, StationPermission.USER);
        routes.delete(prefix + "/movements/{id}", this::delete, StationPermission.INVENTORY_EXCHANGE);
    }

    /**
     * The movements the caller may see: all of the station's for somebody who works the queue, and
     * their own plus their charges' for everybody else.
     */
    @OpenApi(
            path = "/api/v1/movements",
            methods = HttpMethod.GET,
            summary = "List movements the caller may see",
            tags = {"Inventory"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = MovementResponse[].class)))
    private void list(Context ctx) {
        UserSession session = UserSession.from(ctx);
        List<ItemMovement> movements;
        if (session.hasPermission(StationPermission.INVENTORY_EXCHANGE)) {
            movements = movementService.findByStation(session.stationId());
        } else {
            var visible = new HashSet<Integer>();
            visible.add(session.member().id());
            if (session.hasPermission(StationPermission.MEMBER_GUARDIAN)) {
                stationMemberRepository.findManaged(session.member().id()).forEach(m -> visible.add(m.id()));
            }
            movements = movementService.findByStation(session.stationId()).stream()
                    .filter(m -> m.memberId() != null && visible.contains(m.memberId()))
                    .toList();
        }
        ctx.json(movements.stream().map(this::toResponse).toList());
    }

    @OpenApi(
            path = "/api/v1/movements/{id}",
            methods = HttpMethod.GET,
            summary = "Get a movement with its steps and what was acknowledged on it",
            tags = {"Inventory"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = MovementDetail.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void get(Context ctx) {
        UserSession session = UserSession.from(ctx);
        ItemMovement movement = requireVisible(pathInt(ctx, "id"), session);
        ctx.json(toDetail(movement, session));
    }

    @OpenApi(
            path = "/api/v1/movements",
            methods = HttpMethod.POST,
            summary = "Start a movement",
            tags = {"Inventory"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = CreateMovementRequest.class)),
            responses = @OpenApiResponse(status = "201", content = @OpenApiContent(from = MovementDetail.class)))
    private void create(Context ctx) {
        UserSession session = UserSession.from(ctx);
        var request = ctx.bodyAsClass(CreateMovementRequest.class);
        if (request.purpose() == null) throw new BadRequestResponse("purpose is required");

        Integer memberId = request.memberId();
        if (memberId != null && memberId != session.member().id() && !mayActForMember(session, memberId)) {
            throw new ForbiddenResponse("You do not manage this member");
        }
        ItemMovement movement = movementService.create(
                session.stationId(),
                request.purpose(),
                memberId,
                request.outgoingItemId(),
                request.inventoryId(),
                request.oldSizeId(),
                request.newSizeId(),
                request.reason() != null ? request.reason() : "",
                actorOf(session),
                request.pickedItemId());
        ctx.status(HttpStatus.CREATED).json(toDetail(movement, session));
    }

    @OpenApi(
            path = "/api/v1/movements/{id}/acknowledge",
            methods = HttpMethod.POST,
            summary = "Acknowledge the step a movement is standing on",
            tags = {"Inventory"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = AcknowledgeStepRequest.class)),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = MovementDetail.class)))
    private void acknowledge(Context ctx) {
        UserSession session = UserSession.from(ctx);
        ItemMovement movement = requireVisible(pathInt(ctx, "id"), session);
        var request = ctx.bodyAsClass(AcknowledgeStepRequest.class);
        var updated = movementService.acknowledge(
                movement.id(), request.stepId(), actorOf(session), request.note(), request.pickedItemId());
        ctx.json(toDetail(updated, session));
    }

    @OpenApi(
            path = "/api/v1/movements/{id}/force",
            methods = HttpMethod.POST,
            summary = "Acknowledge a step on behalf of a party that has not answered",
            tags = {"Inventory"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = AcknowledgeStepRequest.class)),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = MovementDetail.class)))
    private void force(Context ctx) {
        UserSession session = UserSession.from(ctx);
        ItemMovement movement = requireVisible(pathInt(ctx, "id"), session);
        var request = ctx.bodyAsClass(AcknowledgeStepRequest.class);
        var updated = movementService.force(
                movement.id(), request.stepId(), actorOf(session), request.note(), request.pickedItemId());
        ctx.json(toDetail(updated, session));
    }

    @OpenApi(
            path = "/api/v1/movements/{id}/decline",
            methods = HttpMethod.POST,
            summary = "Refuse the step whose turn it is, closing the movement",
            tags = {"Inventory"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = CloseMovementRequest.class)),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = MovementDetail.class)))
    private void decline(Context ctx) {
        UserSession session = UserSession.from(ctx);
        ItemMovement movement = requireVisible(pathInt(ctx, "id"), session);
        var request = ctx.bodyAsClass(CloseMovementRequest.class);
        ctx.json(toDetail(movementService.decline(movement.id(), actorOf(session), request.reason()), session));
    }

    @OpenApi(
            path = "/api/v1/movements/{id}/cancel",
            methods = HttpMethod.POST,
            summary = "Call off a movement that is still on the caller's side",
            tags = {"Inventory"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = CloseMovementRequest.class)),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = MovementDetail.class)))
    private void cancel(Context ctx) {
        UserSession session = UserSession.from(ctx);
        ItemMovement movement = requireVisible(pathInt(ctx, "id"), session);
        var request = ctx.bodyAsClass(CloseMovementRequest.class);
        ctx.json(toDetail(movementService.cancel(movement.id(), actorOf(session), request.reason()), session));
    }

    @OpenApi(
            path = "/api/v1/movements/{id}",
            methods = HttpMethod.DELETE,
            summary = "Delete a movement outright",
            tags = {"Inventory"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "204"))
    private void delete(Context ctx) {
        UserSession session = UserSession.from(ctx);
        ItemMovement movement = requireVisible(pathInt(ctx, "id"), session);
        if (!movementService.delete(movement.id())) throw new NotFoundResponse();
        ctx.status(HttpStatus.NO_CONTENT);
    }

    /**
     * Nobody signs in on the owner's side yet, so an actor never carries owner rights. When a body
     * above the station can answer for itself, this is where that arrives.
     */
    private ItemMovementService.Actor actorOf(UserSession session) {
        return new ItemMovementService.Actor(
                session.member().id(), session.hasPermission(StationPermission.INVENTORY_EXCHANGE));
    }

    private boolean mayActForMember(UserSession session, int memberId) {
        if (session.hasPermission(StationPermission.INVENTORY_EXCHANGE)) return true;
        return stationMemberRepository.findManagers(memberId).stream()
                .anyMatch(m -> m.id() == session.member().id());
    }

    /**
     * Loads a movement and refuses it to somebody who has no business seeing it. Answering 404 for
     * both absent and none-of-yours keeps the two indistinguishable from outside.
     */
    private ItemMovement requireVisible(int movementId, UserSession session) {
        ItemMovement movement = movementService.findById(movementId).orElseThrow(NotFoundResponse::new);
        RouteSupport.requireSameStation(session, movement.stationId());
        if (session.hasPermission(StationPermission.INVENTORY_EXCHANGE)) return movement;
        if (movement.memberId() != null
                && (movement.memberId() == session.member().id() || mayActForMember(session, movement.memberId()))) {
            return movement;
        }
        throw new NotFoundResponse();
    }

    private MovementResponse toResponse(ItemMovement movement) {
        var current = movementService.stepsOf(movement).stream()
                .filter(s -> movement.currentStepId() != null && s.id() == movement.currentStepId())
                .findFirst();
        return new MovementResponse(
                movement.id(),
                movement.purpose(),
                movement.state(),
                movement.memberId(),
                memberName(movement.memberId()),
                movement.memberId() != null
                        ? memberIdentityFactory.local(movement.stationId(), movement.memberId())
                        : null,
                movement.inventoryId(),
                inventoryName(movement.inventoryId()),
                current.map(s -> s.label()).orElse(null),
                current.map(s -> s.actor()).orElse(null),
                movement.reason(),
                movement.createdAt(),
                movement.closedAt());
    }

    /**
     * The whole chain: every step in order, what was acknowledged on each, and whether this caller
     * is the one being waited on. The frontend draws a stepper from exactly this.
     */
    private MovementDetail toDetail(ItemMovement movement, UserSession session) {
        var logs = movementService.findLogs(movement.id());
        var actor = actorOf(session);
        var steps = movementService.stepsOf(movement).stream()
                .map(step -> {
                    var entry = logs.stream()
                            .filter(l -> l.stepId() != null && l.stepId() == step.id())
                            .findFirst();
                    boolean isCurrent = movement.currentStepId() != null && movement.currentStepId() == step.id();
                    return new MovementStepResponse(
                            step.id(),
                            step.position(),
                            step.label(),
                            step.actor(),
                            step.subject(),
                            step.custodyAfter(),
                            step.picksItem(),
                            step.archived(),
                            isCurrent,
                            entry.map(l -> l.ackKind()).orElse(null),
                            entry.map(l -> memberName(l.changedBy())).orElse(null),
                            entry.map(l -> l.changedAt()).orElse(null),
                            entry.map(l -> l.note()).orElse(null),
                            isCurrent && mayAct(step.actor(), movement, actor));
                })
                .toList();
        return new MovementDetail(toResponse(movement), steps);
    }

    /**
     * Whether this caller could press the step, which is the same question the service asks and is
     * repeated here only so the frontend knows whether to draw a button.
     */
    private boolean mayAct(StepActor stepActor, ItemMovement movement, ItemMovementService.Actor actor) {
        return switch (stepActor) {
            case MEMBER ->
                movement.memberId() != null && (movement.memberId() == actor.memberId() || actor.stationRights());
            case STATION, OWNER -> actor.stationRights() || actor.ownerRights();
        };
    }

    private String memberName(Integer memberId) {
        if (memberId == null) return null;
        return stationMemberRepository
                .findById(memberId)
                .flatMap(m -> accountRepository.findById(m.accountId()))
                .map(a -> (a.firstName() + " " + a.lastName()).trim())
                .orElse(null);
    }

    private String inventoryName(Integer inventoryId) {
        if (inventoryId == null) return null;
        return inventoryRepository.findById(inventoryId).map(Inventory::name).orElse(null);
    }

    public record CreateMovementRequest(
            MovementPurpose purpose,
            Integer memberId,
            Integer outgoingItemId,
            Integer inventoryId,
            Integer oldSizeId,
            Integer newSizeId,
            String reason,
            Integer pickedItemId) {}

    public record AcknowledgeStepRequest(int stepId, String note, Integer pickedItemId) {}

    public record CloseMovementRequest(String reason) {}

    public record MovementResponse(
            int id,
            MovementPurpose purpose,
            MovementState state,
            Integer memberId,
            String memberName,
            MemberIdentity memberIdentity,
            Integer inventoryId,
            String inventoryName,
            String currentStepLabel,
            StepActor currentStepActor,
            String reason,
            Instant createdAt,
            Instant closedAt) {}

    public record MovementStepResponse(
            int id,
            int position,
            String label,
            StepActor actor,
            StepSubject subject,
            ItemCustody custodyAfter,
            boolean picksItem,
            boolean archived,
            boolean current,
            AckKind ackKind,
            String acknowledgedByName,
            Instant acknowledgedAt,
            String note,
            boolean actionable) {}

    public record MovementDetail(MovementResponse movement, List<MovementStepResponse> steps) {}
}
