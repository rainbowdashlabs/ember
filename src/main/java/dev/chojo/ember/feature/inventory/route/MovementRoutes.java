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
import dev.chojo.ember.api.auth.ClusterPermission;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.inventory.entity.AckKind;
import dev.chojo.ember.feature.inventory.entity.Inventory;
import dev.chojo.ember.feature.inventory.entity.InventoryItem;
import dev.chojo.ember.feature.inventory.entity.InventoryItemMetadata;
import dev.chojo.ember.feature.inventory.entity.ItemCustody;
import dev.chojo.ember.feature.inventory.entity.ItemMovement;
import dev.chojo.ember.feature.inventory.entity.ItemOwner;
import dev.chojo.ember.feature.inventory.entity.MovementPurpose;
import dev.chojo.ember.feature.inventory.entity.MovementState;
import dev.chojo.ember.feature.inventory.entity.StepActor;
import dev.chojo.ember.feature.inventory.entity.StepSubject;
import dev.chojo.ember.feature.inventory.repository.InventoryRepository;
import dev.chojo.ember.feature.inventory.service.InventoryService;
import dev.chojo.ember.feature.inventory.service.ItemMovementService;
import dev.chojo.ember.feature.inventory.service.LossReportService;
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
    private final LossReportService lossReportService;
    private final InventoryService inventoryService;

    @Inject
    public MovementRoutes(
            ItemMovementService movementService,
            InventoryRepository inventoryRepository,
            AccountRepository accountRepository,
            StationMemberRepository stationMemberRepository,
            MemberIdentityFactory memberIdentityFactory,
            LossReportService lossReportService,
            InventoryService inventoryService) {
        this.movementService = movementService;
        this.inventoryRepository = inventoryRepository;
        this.accountRepository = accountRepository;
        this.stationMemberRepository = stationMemberRepository;
        this.memberIdentityFactory = memberIdentityFactory;
        this.lossReportService = lossReportService;
        this.inventoryService = inventoryService;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/movements", this::list, StationPermission.USER);
        routes.post(prefix + "/movements", this::create, StationPermission.USER);
        routes.post(
                prefix + "/movements/return-everything", this::returnEverything, StationPermission.INVENTORY_MANAGER);
        routes.get(
                prefix + "/movements/{id}",
                this::get,
                StationPermission.USER,
                ClusterPermission.CLUSTER_INVENTORY_EXCHANGE);
        routes.post(
                prefix + "/movements/{id}/acknowledge",
                this::acknowledge,
                StationPermission.USER,
                ClusterPermission.CLUSTER_INVENTORY_EXCHANGE);
        routes.get(
                prefix + "/movements/{id}/document",
                this::document,
                StationPermission.USER,
                ClusterPermission.CLUSTER_INVENTORY_EXCHANGE);
        routes.post(prefix + "/movements/{id}/force", this::force, StationPermission.INVENTORY_MANAGER);
        routes.post(
                prefix + "/movements/{id}/decline",
                this::decline,
                StationPermission.USER,
                ClusterPermission.CLUSTER_INVENTORY_EXCHANGE);
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
                memberName(memberId),
                request.outgoingItemId(),
                request.inventoryId(),
                request.oldSizeId(),
                request.newSizeId(),
                request.reason() != null ? request.reason() : "",
                actorOf(session, null),
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
        Integer picked = request.pickedItemId();
        if (request.newItem() != null)
            picked = recordArrival(movement, request.newItem()).id();
        var updated = movementService.acknowledge(
                movement.id(), request.stepId(), actorOf(session, movement), request.note(), picked);
        ctx.json(toDetail(updated, session));
    }

    /**
     * Writes down the piece that just arrived and was never here before.
     *
     * <p>Only where nobody else could have named it: a body that keeps its gear on this instance says
     * what it sends when it sends it, and letting the station invent a second row for the same piece
     * would give one thing two records. Where the owner is outside Ember there is nothing to pick
     * from, and this is the only way the chain gets past the step that asks which piece came.
     *
     * <p>It lands in the inventory the movement is about and keeps the owner of the piece that left,
     * because a replacement belongs to whoever owned what it replaces.
     */
    private InventoryItem recordArrival(ItemMovement movement, NewItemRequest request) {
        if (movement.inventoryId() == null) {
            throw new BadRequestResponse("This movement is about no inventory, so a new piece has no home");
        }
        if (movementService.ownerAnswersHere(movement)) {
            throw new BadRequestResponse("The owner names what it sends, so pick the piece rather than recording one");
        }
        if (request.name() == null || request.name().isBlank()) {
            throw new BadRequestResponse("A piece needs a name");
        }

        ItemOwner owner = movementService.ownerOf(movement);
        return inventoryService.createItem(
                movement.inventoryId(),
                request.internalId(),
                request.name(),
                request.sizeId(),
                InventoryItemMetadata.empty(),
                owner,
                null);
    }

    @OpenApi(
            path = "/api/v1/movements/return-everything",
            methods = HttpMethod.POST,
            summary = "Ask a member for every piece they hold, one chain per piece",
            tags = {"Inventory"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = ReturnEverythingRequest.class)),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = MovementResponse[].class)))
    private void returnEverything(Context ctx) {
        UserSession session = UserSession.from(ctx);
        var request = ctx.bodyAsClass(ReturnEverythingRequest.class);
        if (request.memberId() == null) throw new BadRequestResponse("memberId is required");
        var member = stationMemberRepository
                .findById(request.memberId())
                .filter(row -> row.stationId() == session.stationId())
                .orElseThrow(() -> new BadRequestResponse("That member is not at this station"));

        var started = movementService.requestEverythingBack(
                session.stationId(), member.id(), memberName(member.id()), actorOf(session, null));
        ctx.json(started.stream().map(this::toResponse).toList());
    }

    @OpenApi(
            path = "/api/v1/movements/{id}/document",
            methods = HttpMethod.GET,
            summary = "Download the file attached to a movement",
            tags = {"Inventory"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "200"))
    private void document(Context ctx) {
        UserSession session = UserSession.from(ctx);
        ItemMovement movement = requireVisible(pathInt(ctx, "id"), session);
        var document = lossReportService.documentOf(movement.id()).orElseThrow(NotFoundResponse::new);
        // Kept by the station that raised the report, wherever the reader is answering from
        byte[] data = lossReportService.read(movement.stationId(), document).orElseThrow(NotFoundResponse::new);
        ctx.contentType(document.mimeType());
        ctx.header("Content-Disposition", "attachment; filename=\"" + document.fileName() + "\"");
        ctx.result(data);
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
                movement.id(), request.stepId(), actorOf(session, movement), request.note(), request.pickedItemId());
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
        ctx.json(toDetail(
                movementService.decline(movement.id(), actorOf(session, movement), request.reason()), session));
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
        ctx.json(
                toDetail(movementService.cancel(movement.id(), actorOf(session, movement), request.reason()), session));
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
     * What the caller may act as, which is not one thing but two.
     *
     * <p>Somebody can be at the station, at the cluster that owns the gear, or both at once, and a step
     * belonging to the owner reads differently depending on which of those answered it. A cluster manager
     * pressing it has confirmed something they can see; the station pressing the same button has asserted
     * something on the owner's behalf, and the record keeps those apart.
     *
     * <p>The cluster half is only true when the caller is acting for the cluster that owns this gear.
     * Holding the permission at some other cluster says nothing about this one.
     */
    private ItemMovementService.Actor actorOf(UserSession session, ItemMovement movement) {
        // Somebody acting for a cluster need not be at any station, so there may be no membership to
        // name. Member ids start at one, so zero is nobody rather than somebody.
        return new ItemMovementService.Actor(
                session.member() != null ? session.member().id() : 0,
                session.hasPermission(StationPermission.INVENTORY_EXCHANGE),
                hasOwnerRights(session, movement));
    }

    /**
     * Whether the caller may answer for the body that owns the gear this movement is about.
     *
     * @param session  who is asking
     * @param movement the movement, or {@code null} when one is being started
     * @return {@code true} when they act for the owning cluster and hold its exchange permission
     */
    private boolean hasOwnerRights(UserSession session, ItemMovement movement) {
        if (session.clusterId() == null) return false;
        if (!session.hasClusterPermission(ClusterPermission.CLUSTER_INVENTORY_EXCHANGE)) return false;
        if (movement == null) return true;

        Integer owner = movement.outgoingItemId() == null
                ? null
                : inventoryRepository
                        .findItemById(movement.outgoingItemId())
                        .map(InventoryItem::ownerClusterId)
                        .orElse(null);
        // A movement that has not named its item yet is about the cluster the station answers to
        if (owner == null) return true;
        return owner.equals(session.clusterId());
    }

    private boolean mayActForMember(UserSession session, int memberId) {
        if (session.hasPermission(StationPermission.INVENTORY_EXCHANGE)) return true;
        return stationMemberRepository.findManagers(memberId).stream()
                .anyMatch(m -> m.id() == session.member().id());
    }

    /**
     * The movement, if this caller has any business with it. Answering 404 for both absent and
     * none-of-yours keeps the two indistinguishable from outside.
     *
     * <p>Three kinds of caller do. Somebody who works a station's queue sees that station's movements.
     * The member a movement is about sees their own, and so does whoever answers for them. And somebody
     * acting for the cluster that owns the gear sees it wherever it is: the step the movement is standing
     * on may be theirs to answer, and a step nobody can open is a step nobody can answer.
     */
    private ItemMovement requireVisible(int movementId, UserSession session) {
        ItemMovement movement = movementService.findById(movementId).orElseThrow(NotFoundResponse::new);
        if (hasOwnerRights(session, movement)) return movement;

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
                movement.oldSizeId(),
                movement.newSizeId(),
                movement.createdAt(),
                movement.closedAt(),
                movement.closeReason(),
                movementService.ownerAnswersHere(movement),
                itemName(movement.outgoingItemId()),
                movementService.stillHeldBy(movement));
    }

    /** What the piece that set out is called, which is how a list of movements says which jacket this is. */
    private String itemName(Integer itemId) {
        if (itemId == null) return null;
        return inventoryService.findItemById(itemId).map(InventoryItem::name).orElse(null);
    }

    /**
     * The whole chain: every step in order, what was acknowledged on each, and whether this caller
     * is the one being waited on. The frontend draws a stepper from exactly this.
     */
    private MovementDetail toDetail(ItemMovement movement, UserSession session) {
        var logs = movementService.findLogs(movement.id());
        var actor = actorOf(session, movement);
        var steps = movementService.stepsOf(movement).stream()
                // A movement that has closed is a record of what happened, so it shows the steps it walked
                // and no others. The flow it read is free to grow afterwards, and a finished chain must not
                // grow with it: a step nobody took would read as one somebody skipped.
                .filter(step -> movement.state() == MovementState.OPEN
                        || logs.stream().anyMatch(l -> l.stepId() != null && l.stepId() == step.id()))
                .map(step -> {
                    var entry = logs.stream()
                            .filter(l -> l.stepId() != null && l.stepId() == step.id())
                            .findFirst();
                    boolean isCurrent = movement.currentStepId() != null && movement.currentStepId() == step.id();
                    return new MovementStepResponse(
                            step.id(),
                            step.position(),
                            // The words it was walked under, where it has been walked. Renaming a step
                            // changes what the next movement says and never what a finished one said.
                            entry.map(l -> l.stepLabel()).orElseGet(step::label),
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
                            isCurrent && movementService.mayAct(movement, step, actor));
                })
                .toList();
        return new MovementDetail(toResponse(movement), steps, lossReportOf(movement));
    }

    /**
     * The loss this movement reports, when it reports one.
     *
     * <p>The member's note is read off the item rather than copied onto the movement, because it is a note
     * about the loss and not about the request: the item is still missing, and what was said about it is
     * still the last thing anybody knows.
     */
    private LossReport lossReportOf(ItemMovement movement) {
        if (!movement.lostReport()) return null;
        InventoryItem item = movement.outgoingItemId() == null
                ? null
                : inventoryRepository.findItemById(movement.outgoingItemId()).orElse(null);
        var document = lossReportService.documentOf(movement.id()).orElse(null);
        return new LossReport(
                movement.reason(),
                item != null ? item.lostNote() : null,
                item != null && item.lostNoteBy() != null
                        ? memberIdentityFactory.fromMemberId(item.lostNoteBy())
                        : null,
                document != null ? document.fileName() : null,
                document != null ? document.mimeType() : null);
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

    /**
     * @param pickedItemId the arriving piece, when it is one the station already has on its books
     * @param newItem      the arriving piece, when it has never been recorded here. A piece coming
     *                     from a body outside Ember is the ordinary case for this: there is nothing
     *                     to pick, and without it the chain stops on the step that asks
     */
    public record AcknowledgeStepRequest(int stepId, String note, Integer pickedItemId, NewItemRequest newItem) {}

    /**
     * A piece recorded at the moment it arrives. Owner and inventory are not asked: they are the
     * ones the movement is already about.
     *
     * @param internalId what is written on it
     * @param name       what it is
     * @param sizeId     the size, or null where the inventory keeps none
     */
    public record NewItemRequest(String internalId, String name, Integer sizeId) {}

    /**
     * @param memberId the member who is to hand everything back
     */
    public record ReturnEverythingRequest(Integer memberId) {}

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
            /** The size being replaced, and the size asked for, which a piece written down starts as. */
            Integer oldSizeId,
            Integer newSizeId,
            Instant createdAt,
            Instant closedAt,
            /** Why it was refused or taken back, which the reason it was started does not say. */
            String closeReason,
            /**
             * Whether the body that owns the gear can answer for itself here. Where it cannot, the
             * station both walks its steps and writes down what arrived, because nobody else will.
             */
            boolean ownerAnswersHere,
            /**
             * What the piece that set out is called. A member reading their movements has one question
             * first, which is which of their things this is about, and their inventory no longer answers
             * it once they have handed the piece in.
             */
            String itemName,
            /**
             * Whether the member still has the piece, which is what lets them call the movement off
             * themselves. Once the station has taken it, calling off is the station's to do.
             */
            boolean itemStillWithMember) {}

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

    public record MovementDetail(MovementResponse movement, List<MovementStepResponse> steps, LossReport lossReport) {}

    /**
     * What a report that a piece of gear is gone carries, read at both ends.
     *
     * <p>Two notes with two authors, neither standing in for the other: the member said what happened to
     * them, and the manager said what the station is asking the owner for. The document is evidence for this
     * request and hangs off the movement beside them.
     *
     * @param managerNote     what the reporting manager wrote, which is the reason the movement carries
     * @param memberNote      what the person holding it wrote when they reported it missing, or {@code null}
     * @param memberNoteBy    who wrote that, which is the guardian when one wrote it for somebody
     * @param documentName    the file attached to the report, or {@code null} when none was
     * @param documentType    what that file is
     */
    public record LossReport(
            String managerNote,
            String memberNote,
            MemberIdentity memberNoteBy,
            String documentName,
            String documentType) {}
}
