/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.route;

import dev.chojo.ember.api.ErrorResponseWrapper;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.inventory.entity.ContainerPath;
import dev.chojo.ember.feature.inventory.entity.InventoryContainer;
import dev.chojo.ember.feature.inventory.entity.InventoryContainerHistory;
import dev.chojo.ember.feature.inventory.entity.InventoryContainerKind;
import dev.chojo.ember.feature.inventory.entity.InventoryItem;
import dev.chojo.ember.feature.inventory.service.InventoryContainerService;
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

import java.util.List;

import static dev.chojo.ember.api.RouteSupport.pathInt;

/**
 * Routes for storage containers and their station-defined kind catalogue.
 * Items are placed in containers via {@code /inventory-items/{id}/container}
 * in {@link InventoryRoutes}.
 */
@Singleton
public class InventoryContainerRoutes implements Routes {

    private final InventoryContainerService containerService;

    @Inject
    public InventoryContainerRoutes(InventoryContainerService containerService) {
        this.containerService = containerService;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/inventory-container-kinds", this::listKinds, StationPermission.INVENTORY_READ);
        routes.post(prefix + "/inventory-container-kinds", this::createKind, StationPermission.INVENTORY_STORAGE);
        routes.put(prefix + "/inventory-container-kinds/{id}", this::updateKind, StationPermission.INVENTORY_STORAGE);
        routes.delete(
                prefix + "/inventory-container-kinds/{id}", this::deleteKind, StationPermission.INVENTORY_STORAGE);

        routes.get(prefix + "/inventory-containers", this::listContainers, StationPermission.INVENTORY_READ);
        routes.get(prefix + "/inventory-containers/roots", this::listRoots, StationPermission.INVENTORY_READ);
        routes.post(prefix + "/inventory-containers", this::createContainer, StationPermission.INVENTORY_STORAGE);
        routes.get(prefix + "/inventory-containers/by-scan", this::resolveByScan, StationPermission.INVENTORY_READ);
        routes.get(prefix + "/inventory-containers/{id}", this::getContainer, StationPermission.INVENTORY_READ);
        routes.put(prefix + "/inventory-containers/{id}", this::updateContainer, StationPermission.INVENTORY_STORAGE);
        routes.delete(
                prefix + "/inventory-containers/{id}", this::deleteContainer, StationPermission.INVENTORY_STORAGE);
        routes.get(
                prefix + "/inventory-containers/{id}/contents", this::listContents, StationPermission.INVENTORY_READ);
        routes.get(prefix + "/inventory-containers/{id}/path", this::getPath, StationPermission.INVENTORY_READ);
        routes.get(prefix + "/inventory-containers/{id}/history", this::getHistory, StationPermission.INVENTORY_READ);
    }

    private InventoryContainer verifyContainerOwnership(int id, UserSession session) {
        InventoryContainer container = containerService.findById(id).orElseThrow(NotFoundResponse::new);
        if (container.stationId() != session.stationId()) {
            throw new ForbiddenResponse("Container belongs to a different station");
        }
        return container;
    }

    @OpenApi(
            path = "/api/v1/inventory-container-kinds",
            methods = HttpMethod.GET,
            summary = "List container kinds for the current station",
            tags = {"Inventory"},
            responses =
                    @OpenApiResponse(status = "200", content = @OpenApiContent(from = InventoryContainerKind[].class)))
    private void listKinds(Context ctx) {
        UserSession session = UserSession.from(ctx);
        ctx.json(containerService.listKinds(session.stationId()));
    }

    @OpenApi(
            path = "/api/v1/inventory-container-kinds",
            methods = HttpMethod.POST,
            summary = "Create a container kind for the current station",
            tags = {"Inventory"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = KindRequest.class)),
            responses = {
                @OpenApiResponse(status = "201", content = @OpenApiContent(from = InventoryContainerKind.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void createKind(Context ctx) {
        UserSession session = UserSession.from(ctx);
        var body = ctx.bodyAsClass(KindRequest.class);
        try {
            InventoryContainerKind kind = containerService.createKind(
                    session.stationId(), body.key(), body.label(), body.icon(), body.sortOrder(), body.enabled());
            ctx.status(HttpStatus.CREATED).json(kind);
        } catch (IllegalArgumentException e) {
            throw new BadRequestResponse(e.getMessage());
        }
    }

    @OpenApi(
            path = "/api/v1/inventory-container-kinds/{id}",
            methods = HttpMethod.PUT,
            summary = "Update a container kind",
            tags = {"Inventory"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = KindRequest.class)),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = InventoryContainerKind.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void updateKind(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int id = pathInt(ctx, "id");
        if (containerService.listKinds(session.stationId()).stream().noneMatch(k -> k.id() == id)) {
            throw new NotFoundResponse();
        }
        var body = ctx.bodyAsClass(KindRequest.class);
        try {
            containerService
                    .updateKind(id, body.label(), body.icon(), body.sortOrder(), body.enabled())
                    .ifPresentOrElse(ctx::json, () -> {
                        throw new NotFoundResponse();
                    });
        } catch (IllegalArgumentException e) {
            throw new BadRequestResponse(e.getMessage());
        }
    }

    @OpenApi(
            path = "/api/v1/inventory-container-kinds/{id}",
            methods = HttpMethod.DELETE,
            summary = "Delete a container kind",
            tags = {"Inventory"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = {
                @OpenApiResponse(status = "204"),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void deleteKind(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int id = pathInt(ctx, "id");
        if (containerService.listKinds(session.stationId()).stream().noneMatch(k -> k.id() == id)) {
            throw new NotFoundResponse();
        }
        if (containerService.deleteKind(id)) {
            ctx.status(HttpStatus.NO_CONTENT);
        } else {
            throw new NotFoundResponse();
        }
    }

    @OpenApi(
            path = "/api/v1/inventory-containers",
            methods = HttpMethod.GET,
            summary = "List every container in the current station",
            tags = {"Inventory"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = InventoryContainer[].class)))
    private void listContainers(Context ctx) {
        UserSession session = UserSession.from(ctx);
        ctx.json(containerService.findByStation(session.stationId()));
    }

    @OpenApi(
            path = "/api/v1/inventory-containers/roots",
            methods = HttpMethod.GET,
            summary = "List root containers for the current station",
            tags = {"Inventory"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = InventoryContainer[].class)))
    private void listRoots(Context ctx) {
        UserSession session = UserSession.from(ctx);
        ctx.json(containerService.findRoots(session.stationId()));
    }

    @OpenApi(
            path = "/api/v1/inventory-containers",
            methods = HttpMethod.POST,
            summary = "Create a container",
            tags = {"Inventory"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = ContainerRequest.class)),
            responses = {
                @OpenApiResponse(status = "201", content = @OpenApiContent(from = InventoryContainer.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void createContainer(Context ctx) {
        UserSession session = UserSession.from(ctx);
        var body = ctx.bodyAsClass(ContainerRequest.class);
        try {
            InventoryContainer created = containerService.create(
                    session.stationId(),
                    body.parentId(),
                    body.internalId(),
                    body.name(),
                    body.kindId(),
                    body.description() != null ? body.description() : "",
                    session.member().id());
            ctx.status(HttpStatus.CREATED).json(created);
        } catch (IllegalArgumentException e) {
            throw new BadRequestResponse(e.getMessage());
        }
    }

    @OpenApi(
            path = "/api/v1/inventory-containers/{id}",
            methods = HttpMethod.GET,
            summary = "Get a container by id",
            tags = {"Inventory"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = ContainerDetail.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void getContainer(Context ctx) {
        int id = pathInt(ctx, "id");
        UserSession session = UserSession.from(ctx);
        InventoryContainer container = verifyContainerOwnership(id, session);
        ContainerPath path = containerService.pathOf(id);
        ctx.json(new ContainerDetail(container, path.segments(), path.ids(), path.display()));
    }

    @OpenApi(
            path = "/api/v1/inventory-containers/{id}",
            methods = HttpMethod.PUT,
            summary = "Update a container",
            tags = {"Inventory"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = ContainerRequest.class)),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = InventoryContainer.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void updateContainer(Context ctx) {
        int id = pathInt(ctx, "id");
        UserSession session = UserSession.from(ctx);
        verifyContainerOwnership(id, session);
        var body = ctx.bodyAsClass(ContainerRequest.class);
        try {
            containerService
                    .update(
                            id,
                            body.parentId(),
                            body.internalId(),
                            body.name(),
                            body.kindId(),
                            body.description() != null ? body.description() : "",
                            session.member().id())
                    .ifPresentOrElse(ctx::json, () -> {
                        throw new NotFoundResponse();
                    });
        } catch (IllegalArgumentException e) {
            throw new BadRequestResponse(e.getMessage());
        }
    }

    @OpenApi(
            path = "/api/v1/inventory-containers/{id}",
            methods = HttpMethod.DELETE,
            summary = "Delete a container",
            tags = {"Inventory"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = {
                @OpenApiResponse(status = "204"),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void deleteContainer(Context ctx) {
        int id = pathInt(ctx, "id");
        UserSession session = UserSession.from(ctx);
        verifyContainerOwnership(id, session);
        if (containerService.delete(id, session.member().id())) {
            ctx.status(HttpStatus.NO_CONTENT);
        } else {
            throw new NotFoundResponse();
        }
    }

    @OpenApi(
            path = "/api/v1/inventory-containers/{id}/contents",
            methods = HttpMethod.GET,
            summary = "List items and child containers held by a container",
            tags = {"Inventory"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            queryParams = @OpenApiParam(name = "recursive", type = Boolean.class, description = "Include descendants"),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = ContainerContents.class)))
    private void listContents(Context ctx) {
        int id = pathInt(ctx, "id");
        UserSession session = UserSession.from(ctx);
        verifyContainerOwnership(id, session);
        boolean recursive = "true".equalsIgnoreCase(ctx.queryParam("recursive"));
        List<InventoryContainer> children =
                recursive ? containerService.findSubtree(id) : containerService.findChildren(id);
        List<InventoryItem> items =
                recursive ? containerService.findItemsInSubtree(id) : containerService.findItemsInContainer(id);
        ctx.json(new ContainerContents(children, items));
    }

    @OpenApi(
            path = "/api/v1/inventory-containers/{id}/path",
            methods = HttpMethod.GET,
            summary = "Get the root-first path leading to a container",
            tags = {"Inventory"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = ContainerPathResponse.class)))
    private void getPath(Context ctx) {
        int id = pathInt(ctx, "id");
        UserSession session = UserSession.from(ctx);
        verifyContainerOwnership(id, session);
        ContainerPath path = containerService.pathOf(id);
        ctx.json(new ContainerPathResponse(path.segments(), path.ids(), path.display()));
    }

    @OpenApi(
            path = "/api/v1/inventory-containers/{id}/history",
            methods = HttpMethod.GET,
            summary = "List lifecycle history events for a container",
            tags = {"Inventory"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses =
                    @OpenApiResponse(
                            status = "200",
                            content = @OpenApiContent(from = InventoryContainerHistory[].class)))
    private void getHistory(Context ctx) {
        int id = pathInt(ctx, "id");
        UserSession session = UserSession.from(ctx);
        verifyContainerOwnership(id, session);
        ctx.json(containerService.findHistory(id));
    }

    @OpenApi(
            path = "/api/v1/inventory-containers/by-scan",
            methods = HttpMethod.GET,
            summary = "Resolve a scanned internal id to its container",
            tags = {"Inventory"},
            queryParams = @OpenApiParam(name = "internalId", required = true),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = InventoryContainer.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void resolveByScan(Context ctx) {
        UserSession session = UserSession.from(ctx);
        String internalId = ctx.queryParam("internalId");
        if (internalId == null || internalId.isBlank()) {
            throw new BadRequestResponse("internalId is required");
        }
        containerService.resolveScan(session.stationId(), internalId).ifPresentOrElse(ctx::json, () -> {
            throw new NotFoundResponse();
        });
    }

    /**
     * Request body for container CRUD operations.
     */
    public record ContainerRequest(
            Integer parentId, String internalId, String name, Integer kindId, String description) {}

    /**
     * Request body for kind CRUD operations.
     */
    public record KindRequest(String key, String label, String icon, int sortOrder, boolean enabled) {}

    /**
     * Response body for a container plus its path.
     */
    public record ContainerDetail(
            InventoryContainer container, List<String> pathSegments, List<Integer> pathIds, String pathDisplay) {}

    /**
     * Response body for a container's contents listing.
     */
    public record ContainerContents(List<InventoryContainer> children, List<InventoryItem> items) {}

    /**
     * Response body for a container's path.
     */
    public record ContainerPathResponse(List<String> segments, List<Integer> ids, String display) {}
}
