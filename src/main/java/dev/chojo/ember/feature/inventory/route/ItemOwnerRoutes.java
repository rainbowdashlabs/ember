/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.route;

import dev.chojo.ember.api.ErrorResponseWrapper;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.StationSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.refusal.InventoryRefusal;
import dev.chojo.ember.feature.inventory.entity.InventoryItem;
import dev.chojo.ember.feature.inventory.entity.ItemOwner;
import dev.chojo.ember.feature.inventory.service.ItemOwnershipService;
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
import org.jspecify.annotations.Nullable;

import static dev.chojo.ember.api.RouteSupport.pathInt;

/**
 * Changing who owns one piece of a mixed inventory.
 *
 * <p>Whoever does it writes gear down for both owners at once, the station's and the association's, so it
 * takes both of the permissions that write either down.
 */
@Singleton
public class ItemOwnerRoutes implements Routes {
    private final ItemOwnershipService ownership;

    @Inject
    public ItemOwnerRoutes(ItemOwnershipService ownership) {
        this.ownership = ownership;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.put(prefix + "/inventory-items/{id}/owner", this::changeOwner, StationPermission.INVENTORY_EDIT);
    }

    @OpenApi(
            path = "/api/v1/inventory-items/{id}/owner",
            methods = HttpMethod.PUT,
            summary = "Give a piece of a mixed inventory to the station or to the association",
            tags = {"Inventory"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = OwnerRequest.class)),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = InventoryItem.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class)),
                @OpenApiResponse(status = "409", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void changeOwner(Context ctx) {
        StationSession session = StationSession.from(ctx);
        if (!session.hasPermission(StationPermission.INVENTORY_CREATE_INTERNAL)
                || !session.hasPermission(StationPermission.INVENTORY_CREATE_EXTERNAL)) {
            throw InventoryRefusal.GEAR_OWNER_NOT_YOURS_TO_CREATE.raise();
        }
        var request = ctx.bodyAsClass(OwnerRequest.class);
        ItemOwner owner = request.owner();
        if (owner == null) throw InventoryRefusal.INVENTORY_OWNER_SWITCH_NEEDS_AN_OWNER.raise();
        ctx.json(ownership.changeOwner(session.stationId(), pathInt(ctx, "id"), owner));
    }

    /** @param owner the station or the association */
    public record OwnerRequest(@Nullable ItemOwner owner) {}
}
