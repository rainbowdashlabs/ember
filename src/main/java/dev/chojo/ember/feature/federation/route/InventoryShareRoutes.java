/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.route;

import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.federation.entity.InventoryShare;
import dev.chojo.ember.feature.federation.entity.ShareGrant;
import dev.chojo.ember.feature.federation.entity.ShareScope;
import dev.chojo.ember.feature.federation.service.InventoryShareOverviewService;
import dev.chojo.ember.feature.federation.service.InventoryShareOverviewService.ShareDetail;
import dev.chojo.ember.feature.federation.service.InventoryShareService;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiRequestBody;
import io.javalin.openapi.OpenApiResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.util.List;

import static dev.chojo.ember.api.RouteSupport.pathInt;

/**
 * What this station puts on offer to its lending partners. Nothing is offered until one of these
 * rows says so, and the narrowest row wins, so an inventory can go out with a single item kept back.
 */
@Singleton
public class InventoryShareRoutes implements Routes {

    private final InventoryShareService service;
    private final InventoryShareOverviewService overviewService;

    @Inject
    public InventoryShareRoutes(InventoryShareService service, InventoryShareOverviewService overviewService) {
        this.service = service;
        this.overviewService = overviewService;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/lending/shares", this::listShares, StationPermission.INVENTORY_LENDING_MANAGER);
        routes.get(
                prefix + "/lending/shares/inventory/{inventoryId}",
                this::getInventoryShare,
                StationPermission.INVENTORY_LENDING_MANAGER);
        routes.put(
                prefix + "/lending/shares/inventory/{inventoryId}",
                this::setInventoryShare,
                StationPermission.INVENTORY_LENDING_MANAGER);
        routes.delete(
                prefix + "/lending/shares/inventory/{inventoryId}",
                this::deleteInventoryShare,
                StationPermission.INVENTORY_LENDING_MANAGER);
        routes.get(
                prefix + "/lending/shares/art/{artId}", this::getArtShare, StationPermission.INVENTORY_LENDING_MANAGER);
        routes.put(
                prefix + "/lending/shares/art/{artId}", this::setArtShare, StationPermission.INVENTORY_LENDING_MANAGER);
        routes.delete(
                prefix + "/lending/shares/art/{artId}",
                this::deleteArtShare,
                StationPermission.INVENTORY_LENDING_MANAGER);
        routes.get(
                prefix + "/lending/shares/item/{itemId}",
                this::getItemShare,
                StationPermission.INVENTORY_LENDING_MANAGER);
        routes.put(
                prefix + "/lending/shares/item/{itemId}",
                this::setItemShare,
                StationPermission.INVENTORY_LENDING_MANAGER);
        routes.delete(
                prefix + "/lending/shares/item/{itemId}",
                this::deleteItemShare,
                StationPermission.INVENTORY_LENDING_MANAGER);
    }

    @OpenApi(
            path = "/api/v1/lending/shares",
            methods = HttpMethod.GET,
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = ShareDetail[].class)))
    private void listShares(Context ctx) {
        var session = UserSession.from(ctx);
        ctx.json(overviewService.overview(session.stationId()));
    }

    @OpenApi(
            path = "/api/v1/lending/shares/inventory/{inventoryId}",
            methods = HttpMethod.GET,
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = ShareSetting.class)))
    private void getInventoryShare(Context ctx) {
        var session = UserSession.from(ctx);
        int inventoryId = pathInt(ctx, "inventoryId");
        ctx.json(service.findForInventory(session.stationId(), inventoryId)
                .map(this::toSetting)
                .orElseGet(ShareSetting::unshared));
    }

    @OpenApi(
            path = "/api/v1/lending/shares/art/{artId}",
            methods = HttpMethod.GET,
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = ShareSetting.class)))
    private void getArtShare(Context ctx) {
        var session = UserSession.from(ctx);
        int artId = pathInt(ctx, "artId");
        ctx.json(service.findForArt(session.stationId(), artId)
                .map(this::toSetting)
                .orElseGet(ShareSetting::unshared));
    }

    @OpenApi(
            path = "/api/v1/lending/shares/item/{itemId}",
            methods = HttpMethod.GET,
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = ShareSetting.class)))
    private void getItemShare(Context ctx) {
        var session = UserSession.from(ctx);
        int itemId = pathInt(ctx, "itemId");
        ctx.json(service.findForItem(session.stationId(), itemId)
                .map(this::toSetting)
                .orElseGet(ShareSetting::unshared));
    }

    private ShareSetting toSetting(InventoryShare share) {
        return new ShareSetting(true, share.shareGrant(), share.shareScope(), service.findTargets(share.id()));
    }

    @OpenApi(
            path = "/api/v1/lending/shares/inventory/{inventoryId}",
            methods = HttpMethod.PUT,
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = SetShareRequest.class)),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = ShareSetting.class)))
    private void setInventoryShare(Context ctx) {
        var session = UserSession.from(ctx);
        int inventoryId = pathInt(ctx, "inventoryId");
        var body = readBody(ctx);
        var share = service.setInventoryShare(
                session.stationId(), inventoryId, body.scope(), body.grant(), body.partnerIdList());
        ctx.json(toSetting(share));
    }

    @OpenApi(
            path = "/api/v1/lending/shares/art/{artId}",
            methods = HttpMethod.PUT,
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = SetShareRequest.class)),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = ShareSetting.class)))
    private void setArtShare(Context ctx) {
        var session = UserSession.from(ctx);
        int artId = pathInt(ctx, "artId");
        var body = readBody(ctx);
        var share = service.setArtShare(session.stationId(), artId, body.scope(), body.grant(), body.partnerIdList());
        ctx.json(toSetting(share));
    }

    @OpenApi(
            path = "/api/v1/lending/shares/item/{itemId}",
            methods = HttpMethod.PUT,
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = SetShareRequest.class)),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = ShareSetting.class)))
    private void setItemShare(Context ctx) {
        var session = UserSession.from(ctx);
        int itemId = pathInt(ctx, "itemId");
        var body = readBody(ctx);
        var share = service.setItemShare(session.stationId(), itemId, body.scope(), body.grant(), body.partnerIdList());
        ctx.json(toSetting(share));
    }

    @OpenApi(
            path = "/api/v1/lending/shares/inventory/{inventoryId}",
            methods = HttpMethod.DELETE,
            responses = @OpenApiResponse(status = "204"))
    private void deleteInventoryShare(Context ctx) {
        var session = UserSession.from(ctx);
        service.removeInventoryShare(session.stationId(), pathInt(ctx, "inventoryId"));
        ctx.status(HttpStatus.NO_CONTENT);
    }

    @OpenApi(
            path = "/api/v1/lending/shares/art/{artId}",
            methods = HttpMethod.DELETE,
            responses = @OpenApiResponse(status = "204"))
    private void deleteArtShare(Context ctx) {
        var session = UserSession.from(ctx);
        service.removeArtShare(session.stationId(), pathInt(ctx, "artId"));
        ctx.status(HttpStatus.NO_CONTENT);
    }

    @OpenApi(
            path = "/api/v1/lending/shares/item/{itemId}",
            methods = HttpMethod.DELETE,
            responses = @OpenApiResponse(status = "204"))
    private void deleteItemShare(Context ctx) {
        var session = UserSession.from(ctx);
        service.removeItemShare(session.stationId(), pathInt(ctx, "itemId"));
        ctx.status(HttpStatus.NO_CONTENT);
    }

    private SetShareRequest readBody(Context ctx) {
        var body = ctx.bodyAsClass(SetShareRequest.class);
        if (body.grant() == null) throw Refusal.SHARE_GRANT_MISSING.raise();
        if (body.scope() == null) throw Refusal.SHARE_SCOPE_MISSING.raise();
        return body;
    }

    /**
     * A sharing decision as written from the interface.
     *
     * @param grant      whether the gear goes on offer or is held back
     * @param scope      whether the row reaches every partner or the named ones
     * @param partnerIds the partners it names, read only when the scope is the narrow one
     */
    public record SetShareRequest(ShareGrant grant, ShareScope scope, List<Integer> partnerIds) {
        public List<Integer> partnerIdList() {
            return partnerIds != null ? partnerIds : List.of();
        }
    }

    /** What is currently said about one inventory or one item. */
    public record ShareSetting(
            boolean shared,
            @Nullable ShareGrant grant,
            @Nullable ShareScope scope,
            List<Integer> partnerIds) {
        static ShareSetting unshared() {
            return new ShareSetting(false, null, null, List.of());
        }
    }
}
