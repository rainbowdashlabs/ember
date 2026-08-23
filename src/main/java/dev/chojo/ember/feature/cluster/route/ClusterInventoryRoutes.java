/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.cluster.route;

import dev.chojo.ember.api.ErrorResponseWrapper;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.ClusterPermission;
import dev.chojo.ember.feature.cluster.entity.Cluster;
import dev.chojo.ember.feature.cluster.entity.LossReportRequirement;
import dev.chojo.ember.feature.cluster.service.ClusterDispatchService;
import dev.chojo.ember.feature.cluster.service.ClusterInventoryService;
import dev.chojo.ember.feature.cluster.service.ClusterService;
import dev.chojo.ember.feature.inventory.entity.MovementPurpose;
import dev.chojo.ember.feature.inventory.service.ItemMovementService;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.http.NotFoundResponse;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiRequestBody;
import io.javalin.openapi.OpenApiResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The cluster's gear: what it owns, where each piece is, and what is waiting for an answer from it.
 *
 * <p>The stock itself is ordinary inventory on the cluster's own station, so nothing here re-implements
 * items, sizes or containers. What the cluster needs and a station does not is the other view of the same
 * rows: which of its gear is out at which station, and which movements have stopped on a step only it can
 * answer.
 */
@Singleton
public class ClusterInventoryRoutes implements Routes {
    private final ClusterService clusterService;
    private final ClusterInventoryService inventoryService;
    private final ClusterDispatchService dispatchService;

    @Inject
    public ClusterInventoryRoutes(
            ClusterService clusterService,
            ClusterInventoryService inventoryService,
            ClusterDispatchService dispatchService) {
        this.clusterService = clusterService;
        this.inventoryService = inventoryService;
        this.dispatchService = dispatchService;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/cluster/inventory/items", this::listItems, ClusterPermission.CLUSTER_INVENTORY_READ);
        routes.get(prefix + "/cluster/inventory/queue", this::listQueue, ClusterPermission.CLUSTER_INVENTORY_READ);
        routes.get(prefix + "/cluster/inventory/flows", this::listFlows, ClusterPermission.CLUSTER_INVENTORY_READ);
        routes.post(prefix + "/cluster/inventory/flows", this::createFlow, ClusterPermission.CLUSTER_INVENTORY_MANAGER);
        routes.put(prefix + "/cluster/inventory/settings", this::setUsesInventory, ClusterPermission.CLUSTER_MODULES);
        routes.get(
                prefix + "/cluster/inventory/dispatch",
                this::listSendable,
                ClusterPermission.CLUSTER_INVENTORY_TRANSFER);
        routes.post(
                prefix + "/cluster/inventory/dispatch", this::dispatch, ClusterPermission.CLUSTER_INVENTORY_TRANSFER);
        routes.get(
                prefix + "/cluster/inventory/loss-report",
                this::getLossReportSettings,
                ClusterPermission.CLUSTER_INVENTORY_READ);
        routes.put(
                prefix + "/cluster/inventory/loss-report",
                this::setLossReportSettings,
                ClusterPermission.CLUSTER_INVENTORY_MANAGER);
    }

    @OpenApi(
            path = "/api/v1/cluster/inventory/items",
            methods = HttpMethod.GET,
            summary = "The gear this cluster owns and where each piece is",
            tags = {"Cluster"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = ClusterItemResponse[].class)))
    private void listItems(Context ctx) {
        Cluster cluster = requireActive(ctx);
        ctx.json(inventoryService.findItems(cluster.id()).stream()
                .map(row -> new ClusterItemResponse(
                        row.itemId(),
                        row.internalId(),
                        row.name(),
                        row.custody().name(),
                        row.stationUid(),
                        row.stationName(),
                        row.holderName(),
                        row.sizeId(),
                        row.sizeLabel()))
                .toList());
    }

    @OpenApi(
            path = "/api/v1/cluster/inventory/queue",
            methods = HttpMethod.GET,
            summary = "The movements waiting for an answer from this cluster",
            tags = {"Cluster"},
            responses =
                    @OpenApiResponse(status = "200", content = @OpenApiContent(from = ClusterQueueResponse[].class)))
    private void listQueue(Context ctx) {
        Cluster cluster = requireActive(ctx);
        ctx.json(inventoryService.findQueue(cluster.id()).stream()
                .map(row -> new ClusterQueueResponse(
                        row.movementId(),
                        row.purpose().name(),
                        row.stationUid(),
                        row.stationName(),
                        row.stepLabel(),
                        row.itemName(),
                        row.createdAt()))
                .toList());
    }

    @OpenApi(
            path = "/api/v1/cluster/inventory/flows",
            methods = HttpMethod.GET,
            summary = "The chains this cluster's gear walks",
            tags = {"Cluster"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = ClusterFlowResponse[].class)))
    private void listFlows(Context ctx) {
        Cluster cluster = requireActive(ctx);
        ctx.json(inventoryService.findFlows(cluster.id()).stream()
                .map(flow -> new ClusterFlowResponse(
                        flow.id(), flow.name(), flow.purpose().name()))
                .toList());
    }

    @OpenApi(
            path = "/api/v1/cluster/inventory/flows",
            methods = HttpMethod.POST,
            summary = "Add a chain for this cluster's gear",
            tags = {"Cluster"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = NewClusterFlowRequest.class)),
            responses = {
                @OpenApiResponse(status = "201", content = @OpenApiContent(from = ClusterFlowResponse.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void createFlow(Context ctx) {
        Cluster cluster = requireActive(ctx);
        var request = ctx.bodyAsClass(NewClusterFlowRequest.class);
        var flow = inventoryService.createFlow(cluster.id(), request.name(), parsePurpose(request.purpose()));
        ctx.status(HttpStatus.CREATED)
                .json(new ClusterFlowResponse(
                        flow.id(), flow.name(), flow.purpose().name()));
    }

    @OpenApi(
            path = "/api/v1/cluster/inventory/settings",
            methods = HttpMethod.PUT,
            summary = "Say whether this cluster keeps its gear here",
            tags = {"Cluster"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = InventorySettingsRequest.class)),
            responses = @OpenApiResponse(status = "204"))
    private void setUsesInventory(Context ctx) {
        Cluster cluster = requireActive(ctx);
        var request = ctx.bodyAsClass(InventorySettingsRequest.class);
        inventoryService.setUsesInventory(cluster.id(), request.usesInventory());
        ctx.status(HttpStatus.NO_CONTENT);
    }

    @OpenApi(
            path = "/api/v1/cluster/inventory/dispatch",
            methods = HttpMethod.GET,
            summary = "The gear resting in this cluster's store, which is what there is to send",
            tags = {"Cluster"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = SendableItem[].class)))
    private void listSendable(Context ctx) {
        Cluster cluster = requireActive(ctx);
        ctx.json(dispatchService.sendable(cluster.id()).stream()
                .map(item -> new SendableItem(
                        item.id(),
                        item.internalId(),
                        item.name(),
                        item.inventoryId(),
                        dispatchService.inventoryName(item.inventoryId())))
                .toList());
    }

    @OpenApi(
            path = "/api/v1/cluster/inventory/dispatch",
            methods = HttpMethod.POST,
            summary = "Send a batch of this cluster's gear to one of its stations",
            tags = {"Cluster"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = DispatchRequest.class)),
            responses = {
                @OpenApiResponse(status = "201"),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void dispatch(Context ctx) {
        UserSession session = UserSession.from(ctx);
        Cluster cluster = requireActive(ctx);
        var request = ctx.bodyAsClass(DispatchRequest.class);
        if (request.stationUid() == null) throw new BadRequestResponse("stationUid is required");

        // Acting for the owner: this is the cluster's own store the gear is leaving
        var actor = new ItemMovementService.Actor(
                session.member() != null ? session.member().id() : 0, false, true);
        var movement = dispatchService.dispatch(
                cluster.id(),
                request.stationUid(),
                request.itemIds() != null ? request.itemIds() : List.of(),
                request.reason(),
                actor);
        ctx.status(HttpStatus.CREATED).json(movement);
    }

    @OpenApi(
            path = "/api/v1/cluster/inventory/loss-report",
            methods = HttpMethod.GET,
            summary = "What this cluster asks for with a loss report",
            tags = {"Cluster"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = LossReportSettings.class)))
    private void getLossReportSettings(Context ctx) {
        Cluster cluster = requireActive(ctx);
        ctx.json(new LossReportSettings(cluster.lossReportRequires()));
    }

    @OpenApi(
            path = "/api/v1/cluster/inventory/loss-report",
            methods = HttpMethod.PUT,
            summary = "Set what this cluster asks for with a loss report",
            tags = {"Cluster"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = LossReportSettings.class)),
            responses = @OpenApiResponse(status = "204"))
    private void setLossReportSettings(Context ctx) {
        Cluster cluster = requireActive(ctx);
        var request = ctx.bodyAsClass(LossReportSettings.class);
        if (request.requires() == null) throw new BadRequestResponse("requires is required");
        inventoryService.setLossReportRequires(cluster.id(), request.requires());
        ctx.status(HttpStatus.NO_CONTENT);
    }

    private Cluster requireActive(Context ctx) {
        UserSession session = UserSession.from(ctx);
        Integer clusterId = session.clusterId();
        if (clusterId == null) throw new BadRequestResponse("No cluster selected");
        return clusterService.findById(clusterId).orElseThrow(NotFoundResponse::new);
    }

    private static MovementPurpose parsePurpose(String raw) {
        if (raw == null || raw.isBlank()) throw new BadRequestResponse("A chain needs a purpose");
        try {
            return MovementPurpose.valueOf(raw);
        } catch (IllegalArgumentException e) {
            throw new BadRequestResponse("No such purpose: " + raw);
        }
    }

    public record NewClusterFlowRequest(String name, String purpose) {}

    public record InventorySettingsRequest(boolean usesInventory) {}

    /** What the cluster wants to read before it considers replacing something that was lost. */
    public record LossReportSettings(LossReportRequirement requires) {}

    /** One piece resting in the cluster's store, offered on the dispatch screen. */
    public record SendableItem(int id, String internalId, String name, Integer inventoryId, String inventoryName) {}

    /**
     * A consignment: one station, the pieces going to it, and what the cluster wrote about it.
     */
    public record DispatchRequest(UUID stationUid, List<Integer> itemIds, String reason) {}

    /**
     * @param stationUid the station holding it, or {@code null} when it rests in the cluster's own store
     * @param holderName the member wearing it, or {@code null}
     * @param sizeLabel  the size it is cut to, or {@code null} where the inventory keeps no sizes
     */
    public record ClusterItemResponse(
            int id,
            String internalId,
            String name,
            String custody,
            UUID stationUid,
            String stationName,
            String holderName,
            Integer sizeId,
            String sizeLabel) {}

    /**
     * @param stepLabel what the cluster is being asked to confirm
     */
    public record ClusterQueueResponse(
            int movementId,
            String purpose,
            UUID stationUid,
            String stationName,
            String stepLabel,
            String itemName,
            Instant createdAt) {}

    public record ClusterFlowResponse(int id, String name, String purpose) {}
}
