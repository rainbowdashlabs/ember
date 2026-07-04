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
import dev.chojo.ember.feature.inventory.entity.FieldConfig;
import dev.chojo.ember.feature.inventory.entity.FieldType;
import dev.chojo.ember.feature.inventory.entity.Inventory;
import dev.chojo.ember.feature.inventory.entity.InventoryFieldDefinition;
import dev.chojo.ember.feature.inventory.service.InventoryFieldDefinitionService;
import dev.chojo.ember.feature.inventory.service.InventoryService;
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

/**
 * Routes for managing per-inventory custom field definitions.
 */
@Singleton
public class InventoryFieldDefinitionRoutes implements Routes {

    private final InventoryFieldDefinitionService fieldService;
    private final InventoryService inventoryService;

    @Inject
    public InventoryFieldDefinitionRoutes(
            InventoryFieldDefinitionService fieldService, InventoryService inventoryService) {
        this.fieldService = fieldService;
        this.inventoryService = inventoryService;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/inventories/{inventoryId}/fields", this::listFields, StationPermission.INVENTORY_READ);
        routes.post(prefix + "/inventories/{inventoryId}/fields", this::createField, StationPermission.INVENTORY_EDIT);
        routes.put(
                prefix + "/inventories/{inventoryId}/fields/{fieldId}",
                this::updateField,
                StationPermission.INVENTORY_EDIT);
        routes.delete(
                prefix + "/inventories/{inventoryId}/fields/{fieldId}",
                this::deleteField,
                StationPermission.INVENTORY_EDIT);
    }

    private Inventory ownedInventory(int inventoryId, UserSession session) {
        Inventory inventory = inventoryService.findById(inventoryId).orElseThrow(NotFoundResponse::new);
        if (inventory.stationId() != session.stationId()) {
            throw new ForbiddenResponse("Inventory belongs to a different station");
        }
        return inventory;
    }

    /**
     * Asserts the given field belongs to the given inventory, so a field id from another
     * inventory cannot be edited or deleted by pairing it with an owned inventory id.
     */
    private void verifyFieldInInventory(int inventoryId, int fieldId) {
        if (fieldService.findByInventory(inventoryId).stream().noneMatch(f -> f.id() == fieldId)) {
            throw new NotFoundResponse();
        }
    }

    @OpenApi(
            path = "/api/v1/inventories/{inventoryId}/fields",
            methods = HttpMethod.GET,
            summary = "List custom field definitions for an inventory",
            tags = {"Inventory"},
            pathParams = @OpenApiParam(name = "inventoryId", type = Integer.class, required = true),
            responses =
                    @OpenApiResponse(
                            status = "200",
                            content = @OpenApiContent(from = InventoryFieldDefinition[].class)))
    private void listFields(Context ctx) {
        int inventoryId = ctx.pathParamAsClass("inventoryId", Integer.class).get();
        UserSession session = UserSession.from(ctx);
        ownedInventory(inventoryId, session);
        ctx.json(fieldService.findByInventory(inventoryId));
    }

    @OpenApi(
            path = "/api/v1/inventories/{inventoryId}/fields",
            methods = HttpMethod.POST,
            summary = "Add a custom field definition to an inventory",
            tags = {"Inventory"},
            pathParams = @OpenApiParam(name = "inventoryId", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = FieldDefinitionRequest.class)),
            responses = {
                @OpenApiResponse(status = "201", content = @OpenApiContent(from = InventoryFieldDefinition.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void createField(Context ctx) {
        int inventoryId = ctx.pathParamAsClass("inventoryId", Integer.class).get();
        UserSession session = UserSession.from(ctx);
        ownedInventory(inventoryId, session);
        var body = ctx.bodyAsClass(FieldDefinitionRequest.class);
        try {
            InventoryFieldDefinition created = fieldService.create(
                    inventoryId,
                    body.key(),
                    body.label(),
                    body.fieldType(),
                    body.required(),
                    body.sortOrder(),
                    body.config());
            ctx.status(HttpStatus.CREATED).json(created);
        } catch (IllegalArgumentException e) {
            throw new BadRequestResponse(e.getMessage());
        }
    }

    @OpenApi(
            path = "/api/v1/inventories/{inventoryId}/fields/{fieldId}",
            methods = HttpMethod.PUT,
            summary = "Update a custom field definition",
            tags = {"Inventory"},
            pathParams = {
                @OpenApiParam(name = "inventoryId", type = Integer.class, required = true),
                @OpenApiParam(name = "fieldId", type = Integer.class, required = true)
            },
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = FieldUpdateRequest.class)),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = InventoryFieldDefinition.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void updateField(Context ctx) {
        int inventoryId = ctx.pathParamAsClass("inventoryId", Integer.class).get();
        int fieldId = ctx.pathParamAsClass("fieldId", Integer.class).get();
        UserSession session = UserSession.from(ctx);
        ownedInventory(inventoryId, session);
        verifyFieldInInventory(inventoryId, fieldId);
        var body = ctx.bodyAsClass(FieldUpdateRequest.class);
        try {
            fieldService
                    .update(fieldId, body.label(), body.required(), body.sortOrder(), body.config())
                    .ifPresentOrElse(ctx::json, () -> {
                        throw new NotFoundResponse();
                    });
        } catch (IllegalArgumentException e) {
            throw new BadRequestResponse(e.getMessage());
        }
    }

    @OpenApi(
            path = "/api/v1/inventories/{inventoryId}/fields/{fieldId}",
            methods = HttpMethod.DELETE,
            summary = "Delete a custom field definition",
            tags = {"Inventory"},
            pathParams = {
                @OpenApiParam(name = "inventoryId", type = Integer.class, required = true),
                @OpenApiParam(name = "fieldId", type = Integer.class, required = true)
            },
            responses = {
                @OpenApiResponse(status = "204"),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void deleteField(Context ctx) {
        int inventoryId = ctx.pathParamAsClass("inventoryId", Integer.class).get();
        int fieldId = ctx.pathParamAsClass("fieldId", Integer.class).get();
        UserSession session = UserSession.from(ctx);
        ownedInventory(inventoryId, session);
        verifyFieldInInventory(inventoryId, fieldId);
        if (fieldService.delete(fieldId)) {
            ctx.status(HttpStatus.NO_CONTENT);
        } else {
            throw new NotFoundResponse();
        }
    }

    /**
     * Request body for creating a field definition.
     */
    public record FieldDefinitionRequest(
            String key, String label, FieldType fieldType, boolean required, int sortOrder, FieldConfig config) {}

    /**
     * Request body for updating a field definition.
     */
    public record FieldUpdateRequest(String label, boolean required, int sortOrder, FieldConfig config) {}
}
