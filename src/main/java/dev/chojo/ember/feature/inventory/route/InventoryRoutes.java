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
import dev.chojo.ember.api.StationSession;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.ClusterPermission;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.api.refusal.GeneralRefusal;
import dev.chojo.ember.api.refusal.InventoryRefusal;
import dev.chojo.ember.feature.cluster.entity.LossReportRequirement;
import dev.chojo.ember.feature.inventory.entity.ContainerPath;
import dev.chojo.ember.feature.inventory.entity.Glyph;
import dev.chojo.ember.feature.inventory.entity.Inventory;
import dev.chojo.ember.feature.inventory.entity.InventoryIntakeRow;
import dev.chojo.ember.feature.inventory.entity.InventoryItem;
import dev.chojo.ember.feature.inventory.entity.InventoryItemMetadata;
import dev.chojo.ember.feature.inventory.entity.InventoryRequirement;
import dev.chojo.ember.feature.inventory.entity.InventorySize;
import dev.chojo.ember.feature.inventory.entity.InventorySummary;
import dev.chojo.ember.feature.inventory.entity.InventoryType;
import dev.chojo.ember.feature.inventory.entity.ItemOwner;
import dev.chojo.ember.feature.inventory.entity.MyInventoryItem;
import dev.chojo.ember.feature.inventory.entity.RequiredInventoryItem;
import dev.chojo.ember.feature.inventory.entity.SwitchBlocker;
import dev.chojo.ember.feature.inventory.service.BorrowedGearService;
import dev.chojo.ember.feature.inventory.service.InventoryCheckService;
import dev.chojo.ember.feature.inventory.service.InventoryContainerService;
import dev.chojo.ember.feature.inventory.service.InventoryExportService;
import dev.chojo.ember.feature.inventory.service.InventoryIntakeService;
import dev.chojo.ember.feature.inventory.service.InventoryLossService;
import dev.chojo.ember.feature.inventory.service.InventoryService;
import dev.chojo.ember.feature.inventory.service.InventorySwitchRefusedException;
import dev.chojo.ember.feature.inventory.service.LossReportService;
import dev.chojo.ember.feature.inventory.service.MemberGearService;
import dev.chojo.ember.feature.inventory.service.SelfCheckService;
import dev.chojo.ember.feature.members.entity.NameParts;
import dev.chojo.ember.feature.members.service.MemberIdentityFactory;
import dev.chojo.ember.feature.members.service.StationMemberService;
import dev.chojo.ember.util.CsvWriter;
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
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static dev.chojo.ember.api.RouteSupport.pathInt;

/**
 * Routes for inventory management including CRUD operations on inventories, sizes, items,
 * requirements, member item assignments, and PDF export.
 */
@Singleton
public class InventoryRoutes implements Routes {
    private final InventoryService inventoryService;
    private final InventoryCheckService checkService;
    private final InventoryExportService inventoryExportService;
    private final InventoryContainerService containerService;
    private final MemberIdentityFactory memberIdentityFactory;
    private final StationMemberService memberService;
    private final InventoryLossService lossService;
    private final LossReportService lossReportService;
    private final InventoryIntakeService intakeService;
    private final BorrowedGearService borrowedGearService;
    private final SelfCheckService selfCheckService;
    private final MemberGearService memberGearService;

    @Inject
    public InventoryRoutes(
            InventoryService inventoryService,
            InventoryCheckService checkService,
            InventoryExportService inventoryExportService,
            InventoryContainerService containerService,
            MemberIdentityFactory memberIdentityFactory,
            StationMemberService memberService,
            InventoryLossService lossService,
            LossReportService lossReportService,
            InventoryIntakeService intakeService,
            BorrowedGearService borrowedGearService,
            SelfCheckService selfCheckService,
            MemberGearService memberGearService) {
        this.memberGearService = memberGearService;
        this.intakeService = intakeService;
        this.borrowedGearService = borrowedGearService;
        this.inventoryService = inventoryService;
        this.checkService = checkService;
        this.inventoryExportService = inventoryExportService;
        this.containerService = containerService;
        this.memberIdentityFactory = memberIdentityFactory;
        this.memberService = memberService;
        this.lossService = lossService;
        this.lossReportService = lossReportService;
        this.selfCheckService = selfCheckService;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    /**
     * Registers the routes.
     *
     * <p>Marking gear lost is self-service: whoever holds it may say so. A loss report declares
     * somebody else's gear gone, which is heavier than an exchange, so it needs the manager right. The
     * settings are open to every member, who has to know whether a note is expected before being
     * refused for leaving it out.
     */
    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/my-inventory-items", this::myItems, StationPermission.USER);
        routes.get(prefix + "/my-inventory-requirements", this::myRequirements, StationPermission.USER);
        routes.get(
                prefix + "/station-members/{memberId}/inventory-items",
                this::memberItems,
                StationPermission.MEMBER_READ,
                StationPermission.INVENTORY_READ);
        routes.get(
                prefix + "/station-members/{memberId}/inventory-requirements",
                this::memberRequirements,
                StationPermission.MEMBER_READ,
                StationPermission.INVENTORY_READ);
        routes.post(
                prefix + "/station-members/{memberId}/inventory-items",
                this::createAndHandOut,
                StationPermission.INVENTORY_CREATE_EXTERNAL,
                StationPermission.INVENTORY_CREATE_INTERNAL);
        routes.get(prefix + "/inventories", this::list, StationPermission.INVENTORY_READ);
        routes.post(prefix + "/inventories", this::create, StationPermission.INVENTORY_CREATE);
        routes.get(prefix + "/inventories/all-items", this::listAllItems, StationPermission.INVENTORY_READ);
        routes.get(prefix + "/inventories/all-sizes", this::listAllSizes, StationPermission.LOGIN);
        routes.get(prefix + "/inventories/summary", this::listSummaries, StationPermission.INVENTORY_READ);
        routes.get(prefix + "/inventories/{id}", this::get, StationPermission.INVENTORY_READ);
        routes.put(prefix + "/inventories/{id}", this::update, StationPermission.INVENTORY_EDIT);
        routes.delete(prefix + "/inventories/{id}", this::delete, StationPermission.INVENTORY_MANAGER);

        routes.get(prefix + "/inventories/{inventoryId}/sizes", this::listSizes, StationPermission.LOGIN);
        routes.post(prefix + "/inventories/{inventoryId}/sizes", this::createSize, StationPermission.INVENTORY_CREATE);
        routes.put(
                prefix + "/inventories/{inventoryId}/sizes/{sizeId}",
                this::updateSize,
                StationPermission.INVENTORY_EDIT);
        routes.delete(
                prefix + "/inventories/{inventoryId}/sizes/{sizeId}",
                this::deleteSize,
                StationPermission.INVENTORY_EDIT);
        routes.get(prefix + "/inventories/{inventoryId}/items", this::listItems, StationPermission.INVENTORY_READ);
        routes.post(
                prefix + "/inventories/{inventoryId}/items",
                this::createItem,
                StationPermission.INVENTORY_CREATE_EXTERNAL,
                StationPermission.INVENTORY_CREATE_INTERNAL);
        routes.post(
                prefix + "/inventories/{inventoryId}/items/batch",
                this::takeStock,
                StationPermission.INVENTORY_CREATE_EXTERNAL,
                StationPermission.INVENTORY_CREATE_INTERNAL);
        routes.get(
                prefix + "/inventory-items/by-internal-id", this::findByInternalId, StationPermission.INVENTORY_READ);
        routes.get(prefix + "/inventory-items/{id}", this::getItem, StationPermission.INVENTORY_READ);
        routes.put(prefix + "/inventory-items/{id}", this::updateItem, StationPermission.INVENTORY_EDIT);
        routes.put(
                prefix + "/inventory-items/{id}/assign",
                this::assignItem,
                StationPermission.INVENTORY_EDIT,
                StationPermission.INVENTORY_ASSIGN);
        routes.put(prefix + "/inventory-items/{id}/inventory", this::moveItem, StationPermission.INVENTORY_EDIT);
        routes.get(prefix + "/inventory-items/{id}/location", this::getItemLocation, StationPermission.INVENTORY_READ);
        routes.put(
                prefix + "/inventory-items/{id}/container",
                this::setItemContainer,
                StationPermission.INVENTORY_STORAGE);
        routes.get(prefix + "/inventory-items/{id}/history", this::getHistory, StationPermission.INVENTORY_READ);
        routes.put(prefix + "/inventory-items/{id}/lost", this::markLost, StationPermission.USER);
        routes.delete(prefix + "/inventory-items/{id}/lost", this::markFound, StationPermission.INVENTORY_EDIT);
        routes.get(
                prefix + "/inventory-items/{id}/loss-report",
                this::lossReportTerms,
                StationPermission.INVENTORY_MANAGER);
        routes.post(
                prefix + "/inventory-items/{id}/loss-report", this::reportLoss, StationPermission.INVENTORY_MANAGER);
        routes.delete(prefix + "/inventory-items/{id}", this::deleteItem, StationPermission.INVENTORY_EDIT);

        routes.get(prefix + "/inventory-borrowed", this::listBorrowed, StationPermission.INVENTORY_READ);

        routes.get(prefix + "/inventory-requirements", this::listAllRequirements, StationPermission.INVENTORY_READ);
        routes.get(prefix + "/inventory-owner-above", this::ownerAbove, StationPermission.INVENTORY_READ);
        routes.post(prefix + "/inventory-requirements", this::createRequirement, StationPermission.INVENTORY_MANAGER);
        routes.put(
                prefix + "/inventory-requirements/{id}", this::updateRequirement, StationPermission.INVENTORY_MANAGER);
        routes.patch(
                prefix + "/inventory-requirements/{id}/position",
                this::updateRequirementPosition,
                StationPermission.INVENTORY_MANAGER);
        routes.delete(
                prefix + "/inventory-requirements/{id}", this::deleteRequirement, StationPermission.INVENTORY_MANAGER);

        routes.post(prefix + "/inventories/members/export", this::exportMembers, StationPermission.INVENTORY_READ);

        routes.get(prefix + "/inventory-settings", this::getInventorySettings, StationPermission.USER);
        routes.put(prefix + "/inventory-settings", this::updateInventorySettings, StationPermission.INVENTORY_MANAGER);
    }

    /**
     * The body this caller answers for when they change how a piece of gear is described.
     *
     * <p>An association's own gear sits on the station it owns, and its screens act there, so its requests
     * arrive as ordinary station requests. What tells them apart from a station holding somebody else's
     * jacket is the association they name and the right they hold at it.
     *
     * @param session who is asking
     * @return the cluster they act for, or {@code null} when they are acting as the station alone
     */
    private @Nullable Integer describingClusterId(UserSession session) {
        if (session.clusterId() == null) return null;
        if (!session.hasClusterPermission(ClusterPermission.CLUSTER_INVENTORY_EDIT)) return null;
        return session.clusterId();
    }

    /**
     * Refuses a piece that is neither written down in one of the caller's inventories nor held by the
     * caller's station.
     *
     * <p>Held counts as much as written down: gear the association keeps in its own inventory and lends
     * to a station is on that station's lists and under its scanner, so opening it there has to work
     * too. What the station may then change on it is decided by the owner checks behind each route.
     */
    private void verifyItemOwnership(int itemId, StationSession session) {
        var item = inventoryService.findItemById(itemId).orElseThrow(InventoryRefusal.ITEM_NOT_HERE::raise);
        var inventory = inventoryService
                .findById(item.inventoryId())
                .orElseThrow(InventoryRefusal.INVENTORY_NOT_HERE_BEHIND_ITEM::raise);
        if (inventory.stationId() == session.stationId()) return;
        if (inventoryService.isHeldBy(itemId, session.stationId())) return;
        throw GeneralRefusal.NOT_YOURS_TO_OPEN.raise();
    }

    /**
     * Loads an inventory and asserts it belongs to the caller's station, returning it. Answers
     * 404 both when absent and when owned by another station.
     */
    /** Whose gear somebody may write down: their own station's, the association's, or both. */
    /**
     * Who a piece taken into this inventory belongs to, which is what says whose permission it takes
     * to create one. The service works this out the same way when it creates the piece.
     */
    private static ItemOwner ownerOf(Inventory inventory) {
        return inventory.inventoryType() == InventoryType.EXTERNAL ? ItemOwner.CLUSTER : ItemOwner.STATION;
    }

    private void requireMayCreate(StationSession session, ItemOwner owner) {
        StationPermission required = owner == ItemOwner.CLUSTER
                ? StationPermission.INVENTORY_CREATE_EXTERNAL
                : StationPermission.INVENTORY_CREATE_INTERNAL;
        if (!session.hasPermission(required)) {
            throw InventoryRefusal.GEAR_OWNER_NOT_YOURS_TO_CREATE.raise();
        }
    }

    private Inventory requireOwnedInventory(int inventoryId, StationSession session) {
        var inventory = inventoryService.findById(inventoryId).orElseThrow(InventoryRefusal.INVENTORY_NOT_HERE::raise);
        RouteSupport.requireSameStation(session.user(), inventory.stationId());
        return inventory;
    }

    /**
     * Asserts the given requirement belongs to an inventory of the caller's station.
     */
    private void verifyRequirementOwnership(int requirementId, StationSession session) {
        if (inventoryService.findAllRequirementsByStation(session.stationId()).stream()
                .noneMatch(r -> r.id() == requirementId)) {
            throw InventoryRefusal.REQUIREMENT_NOT_HERE.raise();
        }
    }

    @OpenApi(
            path = "/api/v1/my-inventory-items",
            methods = HttpMethod.GET,
            summary = "List the items handed out to the signed-in member",
            tags = {"Inventory"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = MyInventoryItem[].class)))
    private void myItems(Context ctx) {
        StationSession session = StationSession.from(ctx);
        ctx.json(memberGearService.heldBy(session.member().id()));
    }

    @OpenApi(
            path = "/api/v1/my-inventory-requirements",
            methods = HttpMethod.GET,
            summary = "List inventory requirements for the current member",
            tags = {"Inventory"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = MyRequirement[].class)))
    private void myRequirements(Context ctx) {
        StationSession session = StationSession.from(ctx);
        var required = checkService.getRequiredItems(
                session.stationId(), session.member().id());
        var result = required.stream()
                .map(r -> new MyRequirement(r.inventoryId(), r.inventoryName(), r.requiredQuantity()))
                .toList();
        ctx.json(result);
    }

    @OpenApi(
            path = "/api/v1/station-members/{memberId}/inventory-items",
            methods = HttpMethod.GET,
            summary = "List inventory items for a specific member",
            tags = {"Inventory"},
            pathParams = @OpenApiParam(name = "memberId", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = MyInventoryItem[].class)))
    private void memberItems(Context ctx) {
        StationSession session = StationSession.from(ctx);
        int memberId = pathInt(ctx, "memberId");
        ctx.json(inventoryService.findMemberEntries(memberId).stream()
                .filter(entry -> inventoryService
                        .findById(entry.item().inventoryId())
                        .map(inv -> inv.stationId() == session.stationId())
                        .orElse(false))
                .map(memberGearService::toItem)
                .toList());
    }

    @OpenApi(
            path = "/api/v1/station-members/{memberId}/inventory-requirements",
            methods = HttpMethod.GET,
            summary = "What a member is required to hold, and what is still missing",
            description =
                    "The same requirements the stock-taking works from, read without taking the member's record for a check. Carries the pieces of each inventory that are in nobody's hands, so one of them can be handed over on the spot.",
            tags = {"Inventory"},
            pathParams = @OpenApiParam(name = "memberId", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = MemberRequirements.class)))
    private void memberRequirements(Context ctx) {
        StationSession session = StationSession.from(ctx);
        int memberId = pathInt(ctx, "memberId");
        requireMemberOfStation(memberId, session);
        var required = checkService.getRequiredItems(session.stationId(), memberId);
        Map<Integer, List<InventoryItem>> unassigned = new LinkedHashMap<>();
        for (var requirement : required) {
            unassigned.put(requirement.inventoryId(), inventoryService.unassignedItems(requirement.inventoryId()));
        }
        ctx.json(new MemberRequirements(required, unassigned));
    }

    @OpenApi(
            path = "/api/v1/station-members/{memberId}/inventory-items",
            methods = HttpMethod.POST,
            summary = "Take a new piece into stock and hand it to a member",
            tags = {"Inventory"},
            pathParams = @OpenApiParam(name = "memberId", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = HandOutRequest.class)),
            responses = @OpenApiResponse(status = "201", content = @OpenApiContent(from = InventoryItem.class)))
    private void createAndHandOut(Context ctx) {
        StationSession session = StationSession.from(ctx);
        int memberId = pathInt(ctx, "memberId");
        requireMemberOfStation(memberId, session);
        var request = ctx.bodyAsClass(HandOutRequest.class);
        var inventory = inventoryService
                .findById(request.inventoryId())
                .orElseThrow(InventoryRefusal.INVENTORY_NOT_HERE_ON_HAND_OUT::raise);
        RouteSupport.requireSameStation(session.user(), inventory.stationId());
        requireMayCreate(session, ownerOf(inventory));
        String actor = NameParts.of(session.user().account()).called();
        var item = inventoryService.createAndHandOut(request.inventoryId(), request.sizeId(), memberId, actor);
        ctx.status(HttpStatus.CREATED).json(item);
    }

    private void requireMemberOfStation(int memberId, StationSession session) {
        var member = memberService.findById(memberId).orElseThrow(InventoryRefusal.MEMBER_NOT_HERE_FOR_GEAR::raise);
        RouteSupport.requireSameStation(session.user(), member.stationId());
    }

    /**
     * What a member is expected to hold, and what could be handed to them right now.
     *
     * @param required   one entry per inventory the member is required to hold something from
     * @param unassigned the pieces of each of those inventories that are in nobody's hands
     */
    public record MemberRequirements(
            List<RequiredInventoryItem> required, Map<Integer, List<InventoryItem>> unassigned) {}

    /**
     * A piece to be made and handed over in one step.
     *
     * @param inventoryId the inventory it belongs to
     * @param sizeId      the size, or {@code null} where the inventory keeps none
     */
    public record HandOutRequest(int inventoryId, @Nullable Integer sizeId) {}

    @OpenApi(
            path = "/api/v1/inventories/summary",
            methods = HttpMethod.GET,
            summary = "List the inventories of the current station with their item counts",
            tags = {"Inventory"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = InventorySummary[].class)))
    private void listSummaries(Context ctx) {
        StationSession session = StationSession.from(ctx);
        ctx.json(inventoryService.findSummaries(session.stationId()));
    }

    @OpenApi(
            path = "/api/v1/inventories",
            methods = HttpMethod.GET,
            summary = "List inventories for the current station",
            tags = {"Inventory"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = Inventory[].class)))
    private void list(Context ctx) {
        StationSession session = StationSession.from(ctx);
        ctx.json(inventoryService.findByStation(session.stationId()));
    }

    @OpenApi(
            path = "/api/v1/inventories/all-items",
            methods = HttpMethod.GET,
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = InventoryItem[].class)))
    private void listAllItems(Context ctx) {
        StationSession session = StationSession.from(ctx);
        ctx.json(inventoryService.findAllItemsByStation(session.stationId()));
    }

    @OpenApi(
            path = "/api/v1/inventories/all-sizes",
            methods = HttpMethod.GET,
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = InventorySize[].class)))
    private void listAllSizes(Context ctx) {
        StationSession session = StationSession.from(ctx);
        ctx.json(inventoryService.findAllSizesByStation(session.stationId()));
    }

    @OpenApi(
            path = "/api/v1/inventories",
            methods = HttpMethod.POST,
            summary = "Create an inventory",
            tags = {"Inventory"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = InventoryRequest.class)),
            responses = {
                @OpenApiResponse(status = "201", content = @OpenApiContent(from = Inventory.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void create(Context ctx) {
        StationSession session = StationSession.from(ctx);
        var request = ctx.bodyAsClass(InventoryRequest.class);
        if (isBlank(request.name())) {
            throw InventoryRefusal.INVENTORY_NEEDS_A_NAME.raise();
        }
        if (request.inventoryType() == null) {
            throw InventoryRefusal.INVENTORY_NEEDS_A_KIND.raise();
        }
        ctx.status(HttpStatus.CREATED)
                .json(inventoryService.create(
                        session.stationId(),
                        request.name(),
                        request.inventoryType(),
                        request.hasSizes(),
                        !Boolean.FALSE.equals(request.homogeneous()),
                        Glyph.of(request.icon(), request.color())));
    }

    @OpenApi(
            path = "/api/v1/inventories/{id}",
            methods = HttpMethod.GET,
            summary = "Get an inventory with its sizes",
            tags = {"Inventory"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = InventoryDetail.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void get(Context ctx) {
        StationSession session = StationSession.from(ctx);
        int id = pathInt(ctx, "id");
        requireOwnedInventory(id, session);
        inventoryService
                .findById(id)
                .ifPresentOrElse(
                        inventory -> {
                            var sizes = inventoryService.findSizes(id);
                            ctx.json(new InventoryDetail(
                                    inventory.id(),
                                    inventory.stationId(),
                                    inventory.name(),
                                    inventory.inventoryType(),
                                    inventory.hasSizes(),
                                    inventory.homogeneous(),
                                    sizes,
                                    inventory.icon(),
                                    inventory.color()));
                        },
                        () -> {
                            throw InventoryRefusal.INVENTORY_NOT_HERE_ON_READ.raise();
                        });
    }

    /**
     * Updates an inventory. A request that leaves out whether the inventory is homogeneous keeps the
     * current value, so renaming a drawer never turns it into one thing in many copies.
     */
    @OpenApi(
            path = "/api/v1/inventories/{id}",
            methods = HttpMethod.PUT,
            summary = "Update an inventory",
            tags = {"Inventory"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = InventoryRequest.class)),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = Inventory.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = SwitchRefusal.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void update(Context ctx) {
        int id = pathInt(ctx, "id");
        StationSession session = StationSession.from(ctx);
        Inventory current = requireOwnedInventory(id, session);
        var request = ctx.bodyAsClass(InventoryRequest.class);
        if (isBlank(request.name())) {
            throw InventoryRefusal.INVENTORY_NEEDS_A_NAME_ON_CHANGE.raise();
        }
        if (request.inventoryType() == null) {
            throw InventoryRefusal.INVENTORY_NEEDS_A_KIND_ON_CHANGE.raise();
        }
        boolean homogeneous = Objects.requireNonNullElse(request.homogeneous(), current.homogeneous());
        try {
            inventoryService
                    .update(
                            id,
                            request.name(),
                            request.inventoryType(),
                            request.hasSizes(),
                            homogeneous,
                            Glyph.of(request.icon(), request.color()))
                    .ifPresentOrElse(ctx::json, () -> {
                        throw InventoryRefusal.INVENTORY_NOT_CHANGED.raise();
                    });
        } catch (InventorySwitchRefusedException refused) {
            ctx.status(HttpStatus.BAD_REQUEST)
                    .json(new SwitchRefusal(
                            "InventorySwitchRefusedException", refused.getMessage(), refused.blockers()));
        }
    }

    @OpenApi(
            path = "/api/v1/inventories/{id}",
            methods = HttpMethod.DELETE,
            summary = "Delete an inventory",
            tags = {"Inventory"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = {
                @OpenApiResponse(status = "204"),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void delete(Context ctx) {
        int id = pathInt(ctx, "id");
        StationSession session = StationSession.from(ctx);
        requireOwnedInventory(id, session);
        if (inventoryService.delete(id)) {
            ctx.status(HttpStatus.NO_CONTENT);
        } else {
            throw InventoryRefusal.INVENTORY_NOT_DELETED.raise();
        }
    }

    @OpenApi(
            path = "/api/v1/inventories/{inventoryId}/sizes",
            methods = HttpMethod.GET,
            summary = "List sizes of an inventory",
            tags = {"Inventory"},
            pathParams = @OpenApiParam(name = "inventoryId", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = InventorySize[].class)))
    private void listSizes(Context ctx) {
        StationSession session = StationSession.from(ctx);
        int inventoryId = pathInt(ctx, "inventoryId");
        requireOwnedInventory(inventoryId, session);
        ctx.json(inventoryService.findSizes(inventoryId));
    }

    @OpenApi(
            path = "/api/v1/inventories/{inventoryId}/sizes",
            methods = HttpMethod.POST,
            summary = "Create an inventory size",
            tags = {"Inventory"},
            pathParams = @OpenApiParam(name = "inventoryId", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = SizeRequest.class)),
            responses = @OpenApiResponse(status = "201", content = @OpenApiContent(from = InventorySize[].class)))
    private void createSize(Context ctx) {
        int inventoryId = pathInt(ctx, "inventoryId");
        StationSession session = StationSession.from(ctx);
        requireOwnedInventory(inventoryId, session);
        var request = ctx.bodyAsClass(SizeRequest.class);
        if (isBlank(request.label())) {
            throw InventoryRefusal.SIZE_NEEDS_A_NAME.raise();
        }
        ctx.status(HttpStatus.CREATED)
                .json(inventoryService.createSize(inventoryId, request.label(), request.position(), request.note()));
    }

    @OpenApi(
            path = "/api/v1/inventories/{inventoryId}/sizes/{sizeId}",
            methods = HttpMethod.PUT,
            summary = "Update an inventory size",
            tags = {"Inventory"},
            pathParams = {
                @OpenApiParam(name = "inventoryId", type = Integer.class, required = true),
                @OpenApiParam(name = "sizeId", type = Integer.class, required = true)
            },
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = SizeRequest.class)),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = InventorySize[].class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void updateSize(Context ctx) {
        int inventoryId = pathInt(ctx, "inventoryId");
        int sizeId = pathInt(ctx, "sizeId");
        StationSession session = StationSession.from(ctx);
        requireOwnedInventory(inventoryId, session);
        var request = ctx.bodyAsClass(SizeRequest.class);
        if (isBlank(request.label())) {
            throw InventoryRefusal.SIZE_NEEDS_A_NAME_ON_CHANGE.raise();
        }
        inventoryService
                .updateSize(inventoryId, sizeId, request.label(), request.position(), request.note())
                .ifPresentOrElse(ctx::json, () -> {
                    throw InventoryRefusal.SIZE_NOT_CHANGED.raise();
                });
    }

    @OpenApi(
            path = "/api/v1/inventories/{inventoryId}/sizes/{sizeId}",
            methods = HttpMethod.DELETE,
            summary = "Delete an inventory size",
            tags = {"Inventory"},
            pathParams = {
                @OpenApiParam(name = "inventoryId", type = Integer.class, required = true),
                @OpenApiParam(name = "sizeId", type = Integer.class, required = true)
            },
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = InventorySize[].class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void deleteSize(Context ctx) {
        int inventoryId = pathInt(ctx, "inventoryId");
        int sizeId = pathInt(ctx, "sizeId");
        StationSession session = StationSession.from(ctx);
        requireOwnedInventory(inventoryId, session);
        inventoryService.deleteSize(inventoryId, sizeId).ifPresentOrElse(ctx::json, () -> {
            throw InventoryRefusal.SIZE_NOT_DELETED.raise();
        });
    }

    @OpenApi(
            path = "/api/v1/inventories/{inventoryId}/items",
            methods = HttpMethod.GET,
            summary = "List items in an inventory",
            tags = {"Inventory"},
            pathParams = @OpenApiParam(name = "inventoryId", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = InventoryItem[].class)))
    private void listItems(Context ctx) {
        StationSession session = StationSession.from(ctx);
        int inventoryId = pathInt(ctx, "inventoryId");
        requireOwnedInventory(inventoryId, session);
        ctx.json(inventoryService.findStock(inventoryId));
    }

    @OpenApi(
            path = "/api/v1/inventories/{inventoryId}/items",
            methods = HttpMethod.POST,
            summary = "Create an inventory item",
            tags = {"Inventory"},
            pathParams = @OpenApiParam(name = "inventoryId", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = ItemRequest.class)),
            responses = @OpenApiResponse(status = "201", content = @OpenApiContent(from = InventoryItem.class)))
    private void createItem(Context ctx) {
        int inventoryId = pathInt(ctx, "inventoryId");
        StationSession session = StationSession.from(ctx);
        requireOwnedInventory(inventoryId, session);
        var request = ctx.bodyAsClass(ItemRequest.class);
        if (isBlank(request.name())) {
            throw InventoryRefusal.ITEM_NEEDS_A_NAME.raise();
        }
        ItemOwner owner = Objects.requireNonNullElse(request.ownerKind(), ItemOwner.STATION);
        requireMayCreate(session, owner);
        ctx.status(HttpStatus.CREATED)
                .json(inventoryService.createItem(
                        inventoryId,
                        request.internalId(),
                        request.name(),
                        request.sizeId(),
                        request.artId(),
                        request.metadata(),
                        owner,
                        request.ownerClusterId()));
    }

    /**
     * Writes down an inventory the station already owns, one line per piece, and hands each piece to
     * the member on its line.
     */
    @OpenApi(
            path = "/api/v1/inventories/{inventoryId}/items/batch",
            methods = HttpMethod.POST,
            summary = "Write down several pieces at once and assign them",
            tags = {"Inventory"},
            pathParams = @OpenApiParam(name = "inventoryId", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = IntakeRequest.class)),
            responses = {
                @OpenApiResponse(status = "201", content = @OpenApiContent(from = InventoryItem[].class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void takeStock(Context ctx) {
        int inventoryId = pathInt(ctx, "inventoryId");
        StationSession session = StationSession.from(ctx);
        var inventory = requireOwnedInventory(inventoryId, session);
        var request = ctx.bodyAsClass(IntakeRequest.class);
        var rows = request.rows() != null ? request.rows() : List.<InventoryIntakeRow>of();
        for (InventoryIntakeRow row : rows) {
            requireMayCreate(session, Objects.requireNonNullElse(row.ownerKind(), ItemOwner.STATION));
        }
        if (!session.hasPermission(StationPermission.INVENTORY_ASSIGN)
                && !session.hasPermission(StationPermission.INVENTORY_EDIT)
                && rows.stream().anyMatch(row -> row.memberId() != null)) {
            throw InventoryRefusal.HANDING_OUT_NOT_ALLOWED.raise();
        }
        ctx.status(HttpStatus.CREATED)
                .json(intakeService.takeStock(inventoryId, session.stationId(), inventory.name(), rows));
    }

    @OpenApi(
            path = "/api/v1/inventory-items/by-internal-id",
            methods = HttpMethod.GET,
            summary = "Find an inventory item by internal ID",
            tags = {"Inventory"},
            queryParams = @OpenApiParam(name = "internalId", required = true),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = InventoryItem.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void findByInternalId(Context ctx) {
        StationSession session = StationSession.from(ctx);
        String internalId = ctx.queryParam("internalId");
        if (internalId == null || internalId.isBlank()) {
            throw InventoryRefusal.NO_CODE_GIVEN_FOR_ITEM.raise();
        }
        inventoryService.findByInternalId(session.stationId(), internalId).ifPresentOrElse(ctx::json, () -> {
            throw InventoryRefusal.ITEM_NOT_HERE_BY_CODE.raise();
        });
    }

    @OpenApi(
            path = "/api/v1/inventory-items/{id}",
            methods = HttpMethod.GET,
            summary = "Get an inventory item",
            tags = {"Inventory"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = InventoryItem.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void getItem(Context ctx) {
        StationSession session = StationSession.from(ctx);
        int id = pathInt(ctx, "id");
        verifyItemOwnership(id, session);
        inventoryService.findItemById(id).ifPresentOrElse(ctx::json, () -> {
            throw InventoryRefusal.ITEM_NOT_HERE_ON_READ.raise();
        });
    }

    @OpenApi(
            path = "/api/v1/inventory-items/{id}",
            methods = HttpMethod.PUT,
            summary = "Update an inventory item",
            tags = {"Inventory"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = ItemRequest.class)),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = InventoryItem.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void updateItem(Context ctx) {
        StationSession session = StationSession.from(ctx);
        int id = pathInt(ctx, "id");
        verifyItemOwnership(id, session);
        var request = ctx.bodyAsClass(ItemRequest.class);
        if (isBlank(request.name())) {
            throw InventoryRefusal.ITEM_NEEDS_A_NAME_ON_CHANGE.raise();
        }
        inventoryService
                .updateItem(
                        id,
                        request.internalId(),
                        request.name(),
                        request.sizeId(),
                        request.artId(),
                        request.metadata(),
                        describingClusterId(session.user()))
                .ifPresentOrElse(ctx::json, () -> {
                    throw InventoryRefusal.ITEM_NOT_CHANGED.raise();
                });
    }

    @OpenApi(
            path = "/api/v1/inventory-items/{id}/inventory",
            methods = HttpMethod.PUT,
            summary = "Move an item into another inventory of the same station",
            tags = {"Inventory"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = MoveItemRequest.class)),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = InventoryItem.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void moveItem(Context ctx) {
        StationSession session = StationSession.from(ctx);
        int id = pathInt(ctx, "id");
        verifyItemOwnership(id, session);
        var request = ctx.bodyAsClass(MoveItemRequest.class);
        requireOwnedInventory(request.inventoryId(), session);
        inventoryService
                .moveItem(id, request.inventoryId(), describingClusterId(session.user()))
                .ifPresentOrElse(ctx::json, () -> {
                    throw InventoryRefusal.ITEM_NOT_MOVED.raise();
                });
    }

    @OpenApi(
            path = "/api/v1/inventory-items/{id}/assign",
            methods = HttpMethod.PUT,
            summary = "Assign or unassign an inventory item to a member",
            tags = {"Inventory"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = AssignRequest.class)),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = InventoryItem.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void assignItem(Context ctx) {
        int id = pathInt(ctx, "id");
        verifyItemOwnership(id, StationSession.from(ctx));
        var request = ctx.bodyAsClass(AssignRequest.class);
        inventoryService
                .assignItem(id, request.memberId(), request.memberName())
                .ifPresentOrElse(ctx::json, () -> {
                    throw InventoryRefusal.ITEM_NOT_ASSIGNED.raise();
                });
    }

    @OpenApi(
            path = "/api/v1/inventory-items/{id}/location",
            methods = HttpMethod.GET,
            summary = "Get an item's container path",
            tags = {"Inventory"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = ItemLocationResponse.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void getItemLocation(Context ctx) {
        int id = pathInt(ctx, "id");
        verifyItemOwnership(id, StationSession.from(ctx));
        InventoryItem item =
                inventoryService.findItemById(id).orElseThrow(InventoryRefusal.ITEM_NOT_HERE_ON_LOCATION::raise);
        ContainerPath path = containerService.pathOfItem(item);
        ctx.json(new ItemLocationResponse(item.id(), item.containerId(), path.segments(), path.ids(), path.display()));
    }

    @OpenApi(
            path = "/api/v1/inventory-items/{id}/container",
            methods = HttpMethod.PUT,
            summary = "Place an item into a container, or clear its container",
            tags = {"Inventory"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = ContainerAssignRequest.class)),
            responses = {
                @OpenApiResponse(status = "204"),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void setItemContainer(Context ctx) {
        int id = pathInt(ctx, "id");
        verifyItemOwnership(id, StationSession.from(ctx));
        var body = ctx.bodyAsClass(ContainerAssignRequest.class);
        try {
            if (containerService.setItemContainer(id, body.containerId())) {
                ctx.status(HttpStatus.NO_CONTENT);
            } else {
                throw InventoryRefusal.ITEM_NOT_PUT_IN_CONTAINER.raise();
            }
        } catch (IllegalArgumentException ignored) {
            throw InventoryRefusal.ITEM_NOT_FOR_THIS_CONTAINER.raise();
        }
    }

    @OpenApi(
            path = "/api/v1/inventory-items/{id}/history",
            methods = HttpMethod.GET,
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = HistoryResponse[].class)))
    private void getHistory(Context ctx) {
        int id = pathInt(ctx, "id");
        var session = StationSession.from(ctx);
        ctx.json(inventoryService.findHistory(id).stream()
                .map(h -> new HistoryResponse(
                        h.id(),
                        h.itemId(),
                        h.memberId(),
                        h.memberName(),
                        h.memberId() != null ? memberIdentityFactory.local(session.stationId(), h.memberId()) : null,
                        h.givenOut(),
                        h.returned(),
                        h.corrected()))
                .toList());
    }

    @OpenApi(
            path = "/api/v1/inventory-items/{id}/lost",
            methods = HttpMethod.PUT,
            summary = "Mark an inventory item as lost",
            tags = {"Inventory"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = LostRequest.class)),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = InventoryItem.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void markLost(Context ctx) {
        StationSession session = StationSession.from(ctx);
        int id = pathInt(ctx, "id");
        verifyItemOwnership(id, session);
        LostRequest request = ctx.body().isBlank() ? new LostRequest(null, null) : ctx.bodyAsClass(LostRequest.class);
        ctx.json(lossService.markLost(session, id, request.note(), request.selfCheckId()));
    }

    @OpenApi(
            path = "/api/v1/inventory-items/{id}/loss-report",
            methods = HttpMethod.GET,
            summary = "What the body that owns this gear asks for with a loss report",
            tags = {"Inventory"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = LossReportTerms.class)))
    private void lossReportTerms(Context ctx) {
        StationSession session = StationSession.from(ctx);
        int id = pathInt(ctx, "id");
        verifyItemOwnership(id, session);
        var requires = lossReportService.requirementFor(id);
        ctx.json(new LossReportTerms(requires.isPresent(), requires.orElse(null)));
    }

    /**
     * Reports a missing item. The request is multipart: the owner may demand a document, and a report
     * written first with the document attached later would leave half a request standing.
     */
    @OpenApi(
            path = "/api/v1/inventory-items/{id}/loss-report",
            methods = HttpMethod.POST,
            summary = "Report a missing item to the body that owns it",
            tags = {"Inventory"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = {
                @OpenApiResponse(status = "201"),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void reportLoss(Context ctx) {
        StationSession session = StationSession.from(ctx);
        int id = pathInt(ctx, "id");
        verifyItemOwnership(id, session);

        String note = ctx.formParam("note");
        var attachment = lossReportService.evidence(session.stationId(), ctx.uploadedFile("document"));
        var movement = lossReportService.report(
                session.stationId(), id, note, attachment, session.member().id());
        ctx.status(HttpStatus.CREATED).json(movement);
    }

    @OpenApi(
            path = "/api/v1/inventory-settings",
            methods = HttpMethod.GET,
            summary = "The station's inventory settings",
            tags = {"Inventory"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = InventorySettings.class)))
    private void getInventorySettings(Context ctx) {
        StationSession session = StationSession.from(ctx);
        ctx.json(new InventorySettings(lossService.lossNoteRequired(session.stationId())));
    }

    @OpenApi(
            path = "/api/v1/inventory-settings",
            methods = HttpMethod.PUT,
            summary = "Change the station's inventory settings",
            tags = {"Inventory"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = InventorySettings.class)),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = InventorySettings.class)))
    private void updateInventorySettings(Context ctx) {
        StationSession session = StationSession.from(ctx);
        var request = ctx.bodyAsClass(InventorySettings.class);
        ctx.json(new InventorySettings(lossService.requireLossNote(session.stationId(), request.lossNoteRequired())));
    }

    @OpenApi(
            path = "/api/v1/inventory-items/{id}/lost",
            methods = HttpMethod.DELETE,
            summary = "Mark an inventory item as found",
            tags = {"Inventory"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = InventoryItem.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void markFound(Context ctx) {
        int id = pathInt(ctx, "id");
        verifyItemOwnership(id, StationSession.from(ctx));
        inventoryService.markFound(id).ifPresentOrElse(ctx::json, () -> {
            throw InventoryRefusal.ITEM_NOT_MARKED_FOUND.raise();
        });
    }

    @OpenApi(
            path = "/api/v1/inventory-items/{id}",
            methods = HttpMethod.DELETE,
            summary = "Delete an inventory item",
            tags = {"Inventory"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = {
                @OpenApiResponse(status = "204"),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void deleteItem(Context ctx) {
        StationSession session = StationSession.from(ctx);
        int id = pathInt(ctx, "id");
        verifyItemOwnership(id, session);
        if (inventoryService.deleteItem(id, describingClusterId(session.user()))) {
            ctx.status(HttpStatus.NO_CONTENT);
        } else {
            throw InventoryRefusal.ITEM_NOT_DELETED.raise();
        }
    }

    @OpenApi(
            path = "/api/v1/inventory-requirements",
            methods = HttpMethod.GET,
            summary = "List all inventory requirements for the current station",
            tags = {"Inventory"},
            description = "The station's own and those of the cluster above it, the latter named and read-only.",
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = RequirementResponse[].class)))
    private void listAllRequirements(Context ctx) {
        StationSession session = StationSession.from(ctx);
        String clusterName = inventoryService.ownerAbove(session.stationId()).orElse(null);
        ctx.json(inventoryService.findRequirementsVisibleAt(session.stationId()).stream()
                .map(visible -> new RequirementResponse(
                        visible.requirement().id(),
                        visible.requirement().inventoryId(),
                        visible.inventoryName(),
                        visible.requirement().userType(),
                        visible.requirement().groupId(),
                        visible.requirement().stationGroupId(),
                        visible.requirement().quantity(),
                        visible.requirement().position(),
                        visible.fromCluster() ? clusterName : null))
                .toList());
    }

    @OpenApi(
            path = "/api/v1/inventory-owner-above",
            methods = HttpMethod.GET,
            summary = "The body above this station that keeps its gear here",
            description = "Answers with a name when the station belongs to an association that keeps its gear in "
                    + "Ember, and with nothing when it does not. What a station may ask for follows from it.",
            tags = {"Inventory"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = OwnerAboveResponse.class)))
    private void ownerAbove(Context ctx) {
        StationSession session = StationSession.from(ctx);
        ctx.json(new OwnerAboveResponse(
                inventoryService.ownerAbove(session.stationId()).orElse(null)));
    }

    @OpenApi(
            path = "/api/v1/inventory-borrowed",
            methods = HttpMethod.GET,
            summary = "List the gear this station has borrowed from partner stations",
            description = "Each piece with the partner it belongs to and the day the loan runs to. "
                    + "The rows are copies taken at handover and are not kept in step with the owner's.",
            tags = {"Inventory"},
            responses =
                    @OpenApiResponse(status = "200", content = @OpenApiContent(from = BorrowedItemResponse[].class)))
    private void listBorrowed(Context ctx) {
        StationSession session = StationSession.from(ctx);
        ctx.json(borrowedGearService.borrowedAt(session.stationId()).stream()
                .map(borrowed -> new BorrowedItemResponse(
                        borrowed.item(),
                        borrowed.ownerStationName(),
                        borrowed.loanRequestId(),
                        borrowed.dueOn() != null ? borrowed.dueOn().toString() : null))
                .toList());
    }

    /**
     * One piece a station is holding that is not its own.
     *
     * @param item             the row as it was written at handover, which is not kept in step with
     *                         the owner's afterwards
     * @param ownerStationName the partner the gear belongs to
     * @param loanRequestId    the lending request it came in on, which is where anything about the
     *                         loan is said
     * @param dueOn            the day the loan was asked to run to, or {@code null} when none was named
     */
    public record BorrowedItemResponse(
            InventoryItem item,
            String ownerStationName,
            int loanRequestId,
            @Nullable String dueOn) {}

    @OpenApi(
            path = "/api/v1/inventory-requirements",
            methods = HttpMethod.POST,
            summary = "Create an inventory requirement",
            tags = {"Inventory"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = RequirementRequest.class)),
            responses = {
                @OpenApiResponse(status = "201", content = @OpenApiContent(from = InventoryRequirement.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void createRequirement(Context ctx) {
        StationSession session = StationSession.from(ctx);
        var request = ctx.bodyAsClass(RequirementRequest.class);
        if (request.inventoryId() == 0) {
            throw InventoryRefusal.REQUIREMENT_NEEDS_AN_INVENTORY.raise();
        }
        requireOwnedInventory(request.inventoryId(), session);
        StationUserType userType = request.userType();
        int groupId = Objects.requireNonNullElse(request.groupId(), 0);
        if (userType == null && groupId == 0) {
            throw InventoryRefusal.REQUIREMENT_NEEDS_SOMEBODY_TO_APPLY_TO.raise();
        }
        ctx.status(HttpStatus.CREATED)
                .json(inventoryService.createRequirement(
                        request.inventoryId(),
                        userType,
                        groupId,
                        request.stationGroupId(),
                        request.quantity() > 0 ? request.quantity() : 1));
    }

    @OpenApi(
            path = "/api/v1/inventory-requirements/{id}",
            methods = HttpMethod.PUT,
            summary = "Update an inventory requirement",
            tags = {"Inventory"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = UpdateRequirementRequest.class)),
            responses = {
                @OpenApiResponse(status = "204"),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void updateRequirement(Context ctx) {
        StationSession session = StationSession.from(ctx);
        int id = pathInt(ctx, "id");
        verifyRequirementOwnership(id, session);
        var request = ctx.bodyAsClass(UpdateRequirementRequest.class);
        if (inventoryService.updateRequirement(id, request.quantity() > 0 ? request.quantity() : 1)) {
            ctx.status(HttpStatus.NO_CONTENT);
        } else {
            throw InventoryRefusal.REQUIREMENT_NOT_CHANGED.raise();
        }
    }

    @OpenApi(
            path = "/api/v1/inventory-requirements/{id}/position",
            methods = HttpMethod.PATCH,
            summary = "Update the position of an inventory requirement",
            tags = {"Inventory"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = UpdatePositionRequest.class)),
            responses = {
                @OpenApiResponse(status = "204"),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void updateRequirementPosition(Context ctx) {
        StationSession session = StationSession.from(ctx);
        int id = pathInt(ctx, "id");
        verifyRequirementOwnership(id, session);
        var request = ctx.bodyAsClass(UpdatePositionRequest.class);
        if (inventoryService.updateRequirementPosition(id, request.position())) {
            ctx.status(HttpStatus.NO_CONTENT);
        } else {
            throw InventoryRefusal.REQUIREMENT_NOT_MOVED.raise();
        }
    }

    @OpenApi(
            path = "/api/v1/inventory-requirements/{id}",
            methods = HttpMethod.DELETE,
            summary = "Delete an inventory requirement",
            tags = {"Inventory"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = {
                @OpenApiResponse(status = "204"),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void deleteRequirement(Context ctx) {
        StationSession session = StationSession.from(ctx);
        int id = pathInt(ctx, "id");
        verifyRequirementOwnership(id, session);
        if (inventoryService.deleteRequirement(id)) {
            ctx.status(HttpStatus.NO_CONTENT);
        } else {
            throw InventoryRefusal.REQUIREMENT_NOT_DELETED.raise();
        }
    }

    @OpenApi(
            path = "/api/v1/inventories/members/export",
            methods = HttpMethod.POST,
            summary = "Export member inventory list as PDF",
            tags = {"Inventory"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = MemberExportRequest.class)),
            responses = {
                @OpenApiResponse(status = "200"),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class)),
            })
    private void exportMembers(Context ctx) {
        var session = StationSession.from(ctx);
        var body = ctx.bodyAsClass(MemberExportRequest.class);
        var account = session.user().account();
        String generatedBy = NameParts.of(account).official();
        boolean asSpreadsheet = "csv".equalsIgnoreCase(ctx.queryParam("format"));
        var document = inventoryExportService.export(
                session.stationId(),
                body.memberIds(),
                body.inventoryIds(),
                Objects.requireNonNullElse(body.extraFieldIds(), List.of()),
                generatedBy,
                !Boolean.FALSE.equals(body.showName()),
                Boolean.TRUE.equals(body.showInternalId()),
                !Boolean.FALSE.equals(body.showSize()),
                asSpreadsheet ? CsvWriter.Separator.of(ctx.queryParam("separator")) : null);
        if (document.isEmpty()) {
            throw InventoryRefusal.MEMBER_GEAR_LIST_EMPTY.raise();
        }
        ctx.contentType(asSpreadsheet ? "text/csv" : "application/pdf");
        ctx.header("Content-Disposition", document.get().contentDisposition());
        ctx.result(document.get().bytes());
    }

    /**
     * @param corrected whether a check ended the spell by putting the record right rather than by a hand-back
     */
    public record HistoryResponse(
            int id,
            int itemId,
            @Nullable Integer memberId,
            String memberName,
            @Nullable MemberIdentity memberIdentity,
            Instant givenOut,
            @Nullable Instant returned,
            boolean corrected) {}

    public record MyRequirement(int inventoryId, String inventoryName, int requiredQuantity) {}

    /**
     * @param homogeneous whether the inventory holds one thing in many copies rather than a drawer of
     *                    different things. Left out it means "as it was", which on creation is one thing
     *                    in many copies: that is the permissive kind and the one nothing has to opt out of
     * @param icon        the FontAwesome name every piece of it is drawn with, or {@code null} for none
     * @param color       the colour that picture is drawn in as {@code #rrggbb}, or {@code null} for none
     */
    public record InventoryRequest(
            String name,
            InventoryType inventoryType,
            boolean hasSizes,
            @Nullable Boolean homogeneous,
            @Nullable String icon,
            @Nullable String color) {}

    public record InventoryDetail(
            int id,
            int stationId,
            String name,
            InventoryType inventoryType,
            boolean hasSizes,
            boolean homogeneous,
            List<InventorySize> sizes,
            @Nullable String icon,
            @Nullable String color) {}

    /**
     * A refused change of kind, carrying everything that stands in its way.
     *
     * @param error    names the refusal, so the screen can tell it from any other bad request
     * @param message  what is being refused, in plain words
     * @param blockers what stands in the way, each named well enough to go and deal with
     */
    public record SwitchRefusal(String error, String message, List<SwitchBlocker> blockers) {}

    /**
     * @param inventoryId the inventory the piece moves into
     */
    public record MoveItemRequest(int inventoryId) {}

    public record SizeRequest(String label, int position, String note) {}

    /**
     * @param name   what the piece is called, which the kind never replaces: {@code Pager 01} is a
     *               piece of the kind {@code Pager} and both readings are wanted at once
     * @param artId  the kind of thing it is, or {@code null} when nobody has said, which is the
     *               ordinary state for most pieces
     */
    public record ItemRequest(
            @Nullable String internalId,
            String name,
            @Nullable Integer sizeId,
            @Nullable Integer artId,
            @Nullable InventoryItemMetadata metadata,
            @Nullable ItemOwner ownerKind,
            @Nullable Integer ownerClusterId) {}

    /**
     * @param rows the lines of a stock-taking, in the order they were shown. A line that names no
     *             piece is passed over, so a table opened with a row per member needs no tidying up
     *             before it is saved
     */
    public record IntakeRequest(List<InventoryIntakeRow> rows) {}

    public record AssignRequest(
            @Nullable Integer memberId, @Nullable String memberName) {}

    /** What was written when gear was reported missing. */
    /**
     * A loss as it arrives over the wire.
     *
     * @param note        what the person reporting it wrote
     * @param selfCheckId the self-check they were answering when they said it, so a reviewer reading
     *                    the submission can see it happened, or {@code null} where the loss was
     *                    raised on its own
     */
    public record LostRequest(
            @Nullable String note, @Nullable Integer selfCheckId) {}

    /** What a station has decided about its gear beyond any one inventory. */
    public record InventorySettings(boolean lossNoteRequired) {}

    /**
     * What the body that owns a piece of gear asks for before it will consider replacing it.
     *
     * @param reportable whether there is an owner here to report to at all
     * @param requires   nothing, a note, or a document as well
     */
    public record LossReportTerms(
            boolean reportable, @Nullable LossReportRequirement requires) {}

    public record ContainerAssignRequest(@Nullable Integer containerId) {}

    public record ItemLocationResponse(
            int itemId,
            @Nullable Integer containerId,
            List<String> pathSegments,
            List<Integer> pathIds,
            String pathDisplay) {}

    /**
     * A requirement as a station reads it.
     *
     * @param clusterName the cluster that wrote it, or {@code null} for one the station wrote itself. A
     *                    station may read what the cluster asks of its people and change none of it, so the
     *                    name is both the badge and the reason the controls are gone.
     */
    public record RequirementResponse(
            int id,
            int inventoryId,
            String inventoryName,
            @Nullable StationUserType userType,
            int groupId,
            @Nullable Integer stationGroupId,
            int quantity,
            int position,
            @Nullable String clusterName) {}

    /**
     * @param stationGroupId the group of stations it counts at, or null for every station reading it. Only
     *                       an association writing its own requirement may name one.
     */
    public record RequirementRequest(
            int inventoryId,
            @Nullable StationUserType userType,
            @Nullable Integer groupId,
            @Nullable Integer stationGroupId,
            int quantity) {}

    public record UpdateRequirementRequest(int quantity) {}

    /**
     * @param name the association above the station, or null when there is none keeping gear here
     */
    public record OwnerAboveResponse(@Nullable String name) {}

    public record UpdatePositionRequest(int position) {}

    public record MemberExportRequest(
            List<Integer> memberIds,
            List<Integer> inventoryIds,
            List<Integer> extraFieldIds,
            @Nullable Boolean showName,
            @Nullable Boolean showInternalId,
            @Nullable Boolean showSize) {}
}
