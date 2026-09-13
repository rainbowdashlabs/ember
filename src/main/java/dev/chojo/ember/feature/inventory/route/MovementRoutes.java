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
import dev.chojo.ember.feature.cluster.entity.Cluster;
import dev.chojo.ember.feature.cluster.repository.ClusterRepository;
import dev.chojo.ember.feature.inventory.entity.AckKind;
import dev.chojo.ember.feature.inventory.entity.Glyph;
import dev.chojo.ember.feature.inventory.entity.Inventory;
import dev.chojo.ember.feature.inventory.entity.InventoryItem;
import dev.chojo.ember.feature.inventory.entity.InventoryItemMetadata;
import dev.chojo.ember.feature.inventory.entity.InventorySize;
import dev.chojo.ember.feature.inventory.entity.InventoryType;
import dev.chojo.ember.feature.inventory.entity.ItemCustody;
import dev.chojo.ember.feature.inventory.entity.ItemMovement;
import dev.chojo.ember.feature.inventory.entity.ItemOwner;
import dev.chojo.ember.feature.inventory.entity.MovementFlowStep;
import dev.chojo.ember.feature.inventory.entity.MovementParty;
import dev.chojo.ember.feature.inventory.entity.MovementPurpose;
import dev.chojo.ember.feature.inventory.entity.MovementState;
import dev.chojo.ember.feature.inventory.entity.StepActor;
import dev.chojo.ember.feature.inventory.entity.StepSubject;
import dev.chojo.ember.feature.inventory.repository.InventoryRepository;
import dev.chojo.ember.feature.inventory.service.GlyphResolver;
import dev.chojo.ember.feature.inventory.service.InventoryService;
import dev.chojo.ember.feature.inventory.service.ItemMovementService;
import dev.chojo.ember.feature.inventory.service.LossReportService;
import dev.chojo.ember.feature.inventory.service.MovementExportService;
import dev.chojo.ember.feature.inventory.service.MovementTargeting;
import dev.chojo.ember.feature.inventory.service.SelfCheckService;
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
import java.util.Optional;

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
    private final MovementTargeting targeting;
    private final GlyphResolver glyphResolver;
    private final MovementExportService exportService;
    private final SelfCheckService selfCheckService;
    private final ClusterRepository clusterRepository;

    @Inject
    public MovementRoutes(
            ItemMovementService movementService,
            InventoryRepository inventoryRepository,
            AccountRepository accountRepository,
            StationMemberRepository stationMemberRepository,
            MemberIdentityFactory memberIdentityFactory,
            LossReportService lossReportService,
            InventoryService inventoryService,
            MovementTargeting targeting,
            GlyphResolver glyphResolver,
            MovementExportService exportService,
            SelfCheckService selfCheckService,
            ClusterRepository clusterRepository) {
        this.clusterRepository = clusterRepository;
        this.selfCheckService = selfCheckService;
        this.targeting = targeting;
        this.glyphResolver = glyphResolver;
        this.exportService = exportService;
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
        // Both literals before the one that takes an id, or neither would ever be reached.
        routes.get(prefix + "/movements/at-member", this::listAtMember, StationPermission.USER);
        routes.post(prefix + "/movements", this::create, StationPermission.USER);
        routes.post(
                prefix + "/movements/return-everything", this::returnEverything, StationPermission.INVENTORY_MANAGER);
        routes.post(prefix + "/movements/export", this::exportPdf, StationPermission.INVENTORY_MANAGER);
        routes.get(
                prefix + "/movements/{id}",
                this::get,
                StationPermission.USER,
                ClusterPermission.CLUSTER_INVENTORY_MOVEMENTS);
        routes.post(
                prefix + "/movements/{id}/acknowledge",
                this::acknowledge,
                StationPermission.USER,
                ClusterPermission.CLUSTER_INVENTORY_MOVEMENTS);
        routes.get(
                prefix + "/movements/{id}/document",
                this::document,
                StationPermission.USER,
                ClusterPermission.CLUSTER_INVENTORY_MOVEMENTS);
        routes.post(prefix + "/movements/{id}/force", this::force, StationPermission.INVENTORY_MANAGER);
        routes.post(prefix + "/movements/{id}/correct", this::correct, StationPermission.INVENTORY_MANAGER);
        routes.get(prefix + "/movements/{id}/rechain/plan", this::rechainPlan, StationPermission.INVENTORY_MANAGER);
        routes.post(prefix + "/movements/{id}/rechain", this::rechain, StationPermission.INVENTORY_MANAGER);
        routes.post(
                prefix + "/movements/{id}/decline",
                this::decline,
                StationPermission.USER,
                ClusterPermission.CLUSTER_INVENTORY_MOVEMENTS);
        routes.post(prefix + "/movements/{id}/cancel", this::cancel, StationPermission.USER);
        routes.delete(prefix + "/movements/{id}", this::delete, StationPermission.INVENTORY_MOVEMENTS);
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
        if (session.hasPermission(StationPermission.INVENTORY_MOVEMENTS)) {
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
        ctx.json(movements.stream()
                .map(movement -> toResponse(movement, session))
                .toList());
    }

    /**
     * The movements standing at the member they are for, which is what a check with somebody in the room
     * reads: the piece is on them now, or the next step hands them one.
     */
    @OpenApi(
            path = "/api/v1/movements/at-member",
            methods = HttpMethod.GET,
            summary = "List the movements waiting on the member they are for",
            tags = {"Inventory"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = MovementResponse[].class)))
    private void listAtMember(Context ctx) {
        UserSession session = UserSession.from(ctx);
        var rows = movementService.findAtMemberByStation(session.stationId());
        if (!session.hasPermission(StationPermission.INVENTORY_MOVEMENTS)) {
            var visible = new HashSet<Integer>();
            visible.add(session.member().id());
            if (session.hasPermission(StationPermission.MEMBER_GUARDIAN)) {
                stationMemberRepository.findManaged(session.member().id()).forEach(m -> visible.add(m.id()));
            }
            rows = rows.stream()
                    .filter(movement -> movement.memberId() != null && visible.contains(movement.memberId()))
                    .toList();
        }
        ctx.json(rows.stream().map(movement -> toResponse(movement, session)).toList());
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
        if (request.selfCheckId() != null) {
            selfCheckService.recordExchange(
                    request.selfCheckId(),
                    session.stationId(),
                    session.member().id(),
                    session.hasPermission(StationPermission.MEMBER_GUARDIAN),
                    request.outgoingItemId(),
                    movement.id());
        }
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
     * <p>It lands in the inventory the movement is about and is owned by whoever that inventory
     * says it holds gear of; only a mixed inventory leaves the answer to the piece that left.
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
        ctx.json(started.stream().map(movement -> toResponse(movement, session)).toList());
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

    /**
     * The sheet somebody takes to the shelf: one row per member, one column per inventory, the sizes in
     * the cells, and whichever profile fields the reader asked for beside the names.
     */
    @OpenApi(
            path = "/api/v1/movements/export",
            methods = HttpMethod.POST,
            summary = "Export the selected movements as a PDF",
            tags = {"Inventory"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = ExportMovementsRequest.class)),
            responses = {
                @OpenApiResponse(status = "200"),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void exportPdf(Context ctx) {
        UserSession session = UserSession.from(ctx);
        var request = ctx.bodyAsClass(ExportMovementsRequest.class);
        var generatedBy = stationMemberRepository
                .findById(session.member().id())
                .flatMap(m -> accountRepository.findById(m.accountId()))
                .map(a -> a.firstName() + " " + a.lastName())
                .orElse("?");
        var pdf = exportService.exportPdf(
                session.stationId(),
                request.movementIds() != null ? request.movementIds() : List.of(),
                request.extraFieldIds() != null ? request.extraFieldIds() : List.of(),
                generatedBy);
        if (pdf.isEmpty()) {
            throw new NotFoundResponse("No data to export");
        }
        ctx.contentType("application/pdf");
        ctx.header("Content-Disposition", "attachment; filename=\"movements.pdf\"");
        ctx.result(pdf.get());
    }

    /**
     * Puts a movement where somebody says it should have been, by saying where its pieces are.
     *
     * <p>Not a status to write, because a movement has none: where it stands is read off its pieces. A
     * correction therefore says what is true of them, and the chain follows to whichever step that world
     * has not reached. The log keeps the reason and says it was corrected rather than walked.
     */
    @OpenApi(
            path = "/api/v1/movements/{id}/correct",
            methods = HttpMethod.POST,
            summary = "Correct where a movement's pieces are, and let the chain follow",
            tags = {"Inventory"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = CorrectMovementRequest.class)),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = MovementDetail.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void correct(Context ctx) {
        UserSession session = UserSession.from(ctx);
        ItemMovement movement = requireVisible(pathInt(ctx, "id"), session);
        var request = ctx.bodyAsClass(CorrectMovementRequest.class);
        if (request.outgoing() != null && !ItemMovementService.legalStepCustody(request.outgoing())
                || request.incoming() != null && !ItemMovementService.legalStepCustody(request.incoming())) {
            throw new BadRequestResponse(
                    "A movement can put a piece with its owner, at a station, with a member or in the post");
        }
        var correction = new ItemMovementService.Correction(
                request.outgoing(), request.incoming(), request.detachArrival(), request.closeAs());
        var corrected =
                movementService.correct(movement.id(), correction, actorOf(session, movement), request.reason());
        ctx.json(toDetail(corrected, session));
    }

    /**
     * What moving this movement onto the chain it belongs on would come to, said before it is done.
     *
     * <p>Asked rather than assumed, because the step it stands on and the steps of the other chain do
     * not line up on their own and a guess would put somebody's gear a stage further on than it is.
     */
    @OpenApi(
            path = "/api/v1/movements/{id}/rechain/plan",
            methods = HttpMethod.GET,
            summary = "The chain a movement belongs on, and where it would stand",
            tags = {"Inventory"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = {
                @OpenApiResponse(
                        status = "200",
                        content = @OpenApiContent(from = ItemMovementService.RechainPlan.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void rechainPlan(Context ctx) {
        UserSession session = UserSession.from(ctx);
        ItemMovement movement = requireVisible(pathInt(ctx, "id"), session);
        ctx.json(movementService.planRechain(movement.id()));
    }

    /**
     * Moves a movement onto the chain it belongs on.
     *
     * <p>For the movement started on the wrong one, which was decided when it set out and is not
     * decided again. Without this the only ways on are forcing it through steps written for somebody
     * else's gear or calling it off, and both write a history that did not happen.
     */
    @OpenApi(
            path = "/api/v1/movements/{id}/rechain",
            methods = HttpMethod.POST,
            summary = "Move a movement onto the chain it belongs on",
            tags = {"Inventory"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = RechainRequest.class)),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = MovementDetail.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void rechain(Context ctx) {
        UserSession session = UserSession.from(ctx);
        ItemMovement movement = requireVisible(pathInt(ctx, "id"), session);
        var request = ctx.body().isBlank() ? null : ctx.bodyAsClass(RechainRequest.class);
        movementService.rechain(
                movement.id(),
                request == null ? null : request.stepIndex(),
                session.member() != null ? session.member().id() : null);
        ctx.json(toDetail(movementService.findById(movement.id()).orElseThrow(), session));
    }

    /**
     * Where a movement is to stand on the chain it is moved onto.
     *
     * @param stepIndex the step counted from the front, or {@code null} to take the only one that
     *                  means what the step it stands on means
     */
    public record RechainRequest(Integer stepIndex) {}

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
                session.hasPermission(StationPermission.INVENTORY_MOVEMENTS),
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
        if (!session.hasClusterPermission(ClusterPermission.CLUSTER_INVENTORY_MOVEMENTS)) return false;
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
        if (session.hasPermission(StationPermission.INVENTORY_MOVEMENTS)) return true;
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
        if (session.hasPermission(StationPermission.INVENTORY_MOVEMENTS)) return movement;
        if (movement.memberId() != null
                && (movement.memberId() == session.member().id() || mayActForMember(session, movement.memberId()))) {
            return movement;
        }
        throw new NotFoundResponse();
    }

    private MovementResponse toResponse(ItemMovement movement, UserSession session) {
        var steps = movementService.stepsOf(movement);
        var current = steps.stream()
                .filter(s -> movement.currentStepId() != null && s.id() == movement.currentStepId())
                .findFirst();
        // The piece a row is about: what left, or what was promised where nothing left. An issue and a
        // request have no outgoing side at all, and a row naming nothing says nothing.
        Integer subject = movement.outgoingItemId() != null ? movement.outgoingItemId() : movement.incomingItemId();
        var target = belongsOn(movement);
        Glyph glyph = subject != null
                ? glyphResolver.forItemId(subject)
                : glyphResolver.forInventoryId(movement.inventoryId());
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
                inventoryType(movement.inventoryId()),
                current.map(s -> s.label()).orElse(null),
                reachedLabel(steps, current.orElse(null)),
                current.map(s -> s.actor()).orElse(null),
                movement.reason(),
                movement.oldSizeId(),
                movement.newSizeId(),
                firstWritten(sizeName(movement.oldSizeId()), itemSize(movement.outgoingItemId())),
                firstWritten(sizeName(movement.newSizeId()), itemSize(movement.incomingItemId())),
                movement.createdAt(),
                movement.updatedAt(),
                movement.closedAt(),
                movement.closeReason(),
                movementService.ownerAnswersHere(movement),
                owningCluster(target).map(Cluster::name).orElse(null),
                owningCluster(target).map(cluster -> cluster.uid().toString()).orElse(null),
                itemName(movement.outgoingItemId()),
                movement.outgoingItemId(),
                movementService.stillHeldBy(movement),
                target.party(),
                target.ownerKind(),
                current.map(MovementFlowStep::subject).orElse(null),
                current.map(MovementFlowStep::custodyAfter).orElse(null),
                current.map(step -> movementService.mayAct(movement, step, actorOf(session, movement)))
                        .orElse(false),
                movement.incomingItemId(),
                itemName(movement.incomingItemId()),
                itemInternalId(subject),
                firstWritten(
                        sizeName(movement.newSizeId() != null ? movement.newSizeId() : movement.oldSizeId()),
                        itemSize(subject)),
                glyph.icon(),
                glyph.color(),
                movement.flowId() != null && movement.flowId() != target.flowId());
    }

    /**
     * The step whose words are true of the world right now, which is the one before the step being
     * waited on.
     *
     * <p>A step is named after the state it brings about, so the one a movement stands on has not
     * happened yet: a row wearing that label says a piece has been taken in while it is still on the
     * member. What has happened is everything before it, and the last of those is where it stands.
     *
     * @param steps   the chain, in order
     * @param current the step being waited on, or {@code null} once the chain is over
     * @return the words for where it stands, or {@code null} at a chain's very beginning
     */
    private String reachedLabel(List<MovementFlowStep> steps, MovementFlowStep current) {
        if (steps.isEmpty()) return null;
        if (current == null) return steps.getLast().label();
        int standing = steps.indexOf(current);
        return standing > 0 ? steps.get(standing - 1).label() : null;
    }

    /**
     * Where this movement belongs: whose gear it is, who it is with, and the chain that combination is
     * bound to today.
     *
     * <p>A station is free to unbind a combination while a movement of that kind is still walking, and
     * a row that cannot be read is worse than one that cannot say where it ought to be. The chain it is
     * actually on stands in for the answer then.
     *
     * @param movement the movement
     * @return where it belongs, falling back to the chain it walks
     */
    private MovementTargeting.Target belongsOn(ItemMovement movement) {
        try {
            return targeting.of(movement);
        } catch (BadRequestResponse unbound) {
            return new MovementTargeting.Target(
                    targeting.ownerOf(movement.outgoingItemId(), movement.incomingItemId(), movement.inventoryId()),
                    targeting.owningClusterOf(
                            movement.outgoingItemId() != null ? movement.outgoingItemId() : movement.incomingItemId(),
                            movement.stationId()),
                    movement.memberId() != null ? MovementParty.MEMBER : MovementParty.STORE,
                    movement.flowId() != null ? movement.flowId() : 0);
        }
    }

    /**
     * The association that owns this movement's gear, where one on this instance does.
     *
     * <p>"The owner" is an abstraction on screen, and somebody holding a pair of gloves cannot tell
     * from it whose gloves they are. A name can, and the identity tells one body's gear from another's
     * where a replacement is being picked.
     *
     * @param target whose gear it is and which body that is
     * @return the association, or empty where the station owns it or the body is not here
     */
    private Optional<Cluster> owningCluster(MovementTargeting.Target target) {
        if (target.ownerKind() != ItemOwner.CLUSTER || target.ownerClusterId() == null) return Optional.empty();
        return clusterRepository.findById(target.ownerClusterId());
    }

    /** What the piece that set out is called, which is how a list of movements says which jacket this is. */
    private String itemName(Integer itemId) {
        if (itemId == null) return null;
        return inventoryService.findItemById(itemId).map(InventoryItem::name).orElse(null);
    }

    /** What is written on the piece, which is what somebody standing in front of a shelf reads. */
    private String itemInternalId(Integer itemId) {
        if (itemId == null) return null;
        return inventoryService
                .findItemById(itemId)
                .map(InventoryItem::internalId)
                .orElse(null);
    }

    /** The first of the two that says anything, which is how a chosen answer beats a fallback. */
    private String firstWritten(String chosen, String fallback) {
        return chosen != null ? chosen : fallback;
    }

    /**
     * The size a piece is written down as, which is what it is regardless of what its movement asked
     * for.
     *
     * <p>A movement names a size only where somebody chose one: a swap for a bigger jacket does, a
     * return does not. The piece has one either way, and a chip that leaves it out makes two pairs of
     * gloves on the same shelf look like the same pair.
     */
    private String itemSize(Integer itemId) {
        if (itemId == null) return null;
        return inventoryService
                .findItemById(itemId)
                .map(InventoryItem::sizeId)
                .map(this::sizeName)
                .orElse(null);
    }

    /** The size a row names, which is the one asked for where there is one and the one replaced otherwise. */
    private String sizeName(Integer sizeId) {
        if (sizeId == null) return null;
        return inventoryRepository.findSizesByIds(List.of(sizeId)).stream()
                .findFirst()
                .map(InventorySize::label)
                .orElse(null);
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
        return new MovementDetail(toResponse(movement, session), steps, lossReportOf(movement));
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

    /** Whose gear the inventory holds, which is the shelf a replacement is allowed to come off. */
    private InventoryType inventoryType(Integer inventoryId) {
        if (inventoryId == null) return null;
        return inventoryRepository
                .findById(inventoryId)
                .map(Inventory::inventoryType)
                .orElse(null);
    }

    /**
     * @param selfCheckId the self-check this was raised during, where it was raised during one. It waits
     *                    for nothing either way: naming the task only records that it happened while the
     *                    member was answering
     */
    public record CreateMovementRequest(
            MovementPurpose purpose,
            Integer memberId,
            Integer outgoingItemId,
            Integer inventoryId,
            Integer oldSizeId,
            Integer newSizeId,
            String reason,
            Integer pickedItemId,
            Integer selfCheckId) {}

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

    /**
     * What a correction is to make true of a movement's pieces.
     *
     * @param outgoing      where the piece that set out is to be, or {@code null} to leave it
     * @param incoming      where the arriving piece is to be, or {@code null} to leave it
     * @param detachArrival whether the arriving piece is to be unhooked, which is what putting an
     *                      exchange back before a replacement was named means
     * @param closeAs       the state to close it in, or {@code null} to leave it open on whichever step
     *                      the corrected world has not reached
     * @param reason        why, which the log keeps and which is not optional
     */
    /**
     * @param movementIds   the movements to put on the sheet, or empty for every one of the station's
     * @param extraFieldIds the profile fields to add as columns beside the names
     */
    public record ExportMovementsRequest(List<Integer> movementIds, List<Integer> extraFieldIds) {}

    public record CorrectMovementRequest(
            ItemCustody outgoing, ItemCustody incoming, boolean detachArrival, MovementState closeAs, String reason) {}

    public record MovementResponse(
            int id,
            MovementPurpose purpose,
            MovementState state,
            Integer memberId,
            String memberName,
            MemberIdentity memberIdentity,
            Integer inventoryId,
            String inventoryName,
            /** Whose gear that inventory holds, which is the shelf a replacement may be taken off. */
            InventoryType inventoryType,
            /** The step being waited on, which is what pressing the row's button says has happened. */
            String currentStepLabel,
            /** Where it stands: the last step whose words are already true, or null at the beginning. */
            String reachedStepLabel,
            StepActor currentStepActor,
            String reason,
            /** The size being replaced, and the size asked for, which a piece written down starts as. */
            Integer oldSizeId,
            Integer newSizeId,
            /**
             * Those two in words, falling back to the size the piece itself is written down as. A
             * movement names a size only where somebody chose one, and both ends have one either way.
             */
            String oldSizeName,
            String newSizeName,
            Instant createdAt,
            /** When it last moved, which is what says whether a row has gone quiet. */
            Instant updatedAt,
            Instant closedAt,
            /** Why it was refused or taken back, which the reason it was started does not say. */
            String closeReason,
            /**
             * Whether the body that owns the gear can answer for itself here. Where it cannot, the
             * station both walks its steps and writes down what arrived, because nobody else will.
             */
            boolean ownerAnswersHere,
            /** The association that owns the gear, by name, or null where the station owns it. */
            String ownerName,
            /** That association's stable identity, which is how a screen tells one body's gear from another's. */
            String ownerClusterId,
            /**
             * What the piece that set out is called. A member reading their movements has one question
             * first, which is which of their things this is about, and their inventory no longer answers
             * it once they have handed the piece in.
             */
            String itemName,
            /** The piece that set out, so a row can be followed to the piece it is about. */
            Integer itemId,
            /**
             * Whether the member still has the piece, which is what lets them call the movement off
             * themselves. Once the station has taken it, calling off is the station's to do.
             */
            boolean itemStillWithMember,
            /** The end that is not the owner: a member, or the station's store. */
            MovementParty party,
            /** Whose gear it is, which is what the owner's column of the chain is named after. */
            ItemOwner ownerKind,
            /** Which of the two pieces the current step is about, and where it lands once acknowledged. */
            StepSubject currentStepSubject,
            ItemCustody currentStepCustody,
            /**
             * Whether this caller may acknowledge the step it stands on, which is what puts the button on
             * a row of the queue rather than only on the page about one movement.
             */
            boolean actionable,
            /**
             * The arriving piece. An issue has one from the start where it was promised, and an exchange
             * gains one halfway; a row that named only what left says nothing at all about either.
             */
            Integer incomingItemId,
            String incomingItemName,
            /** What is written on the piece the row is about, which is what somebody at a shelf reads. */
            String itemInternalId,
            /** The size the row names: the one asked for where there is one, the one replaced otherwise. */
            String itemSizeName,
            /** The picture the row is drawn with, resolved from the piece's kind and its inventory. */
            String icon,
            String color,
            /**
             * Whether the chain it walks is no longer the one its combination is bound to, which is the
             * only case where moving it across to another one is worth offering.
             */
            boolean belongsOnAnotherFlow) {}

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
