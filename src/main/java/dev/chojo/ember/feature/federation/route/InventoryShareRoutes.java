/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.route;

import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.federation.entity.InventoryShare;
import dev.chojo.ember.feature.federation.entity.ShareGrant;
import dev.chojo.ember.feature.federation.entity.ShareScope;
import dev.chojo.ember.feature.federation.service.FederationService;
import dev.chojo.ember.feature.federation.service.InventoryShareService;
import dev.chojo.ember.feature.inventory.entity.InventoryArt;
import dev.chojo.ember.feature.inventory.repository.InventoryArtRepository;
import dev.chojo.ember.feature.inventory.repository.InventoryRepository;
import dev.chojo.ember.feature.station.repository.StationRepository;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static dev.chojo.ember.api.RouteSupport.pathInt;

/**
 * What this station puts on offer to its lending partners. Nothing is offered until one of these
 * rows says so, and the narrowest row wins, so an inventory can go out with a single item kept back.
 */
@Singleton
public class InventoryShareRoutes implements Routes {

    private final InventoryShareService service;
    private final InventoryRepository inventoryRepository;
    private final InventoryArtRepository artRepository;
    private final FederationService federationService;
    private final StationRepository stationRepository;

    @Inject
    public InventoryShareRoutes(
            InventoryShareService service,
            InventoryRepository inventoryRepository,
            InventoryArtRepository artRepository,
            FederationService federationService,
            StationRepository stationRepository) {
        this.service = service;
        this.inventoryRepository = inventoryRepository;
        this.artRepository = artRepository;
        this.federationService = federationService;
        this.stationRepository = stationRepository;
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

    private void listShares(Context ctx) {
        var session = UserSession.from(ctx);
        int stationId = session.stationId();
        var inventoryNames = new HashMap<Integer, String>();
        for (var inventory : inventoryRepository.findByStation(stationId)) {
            inventoryNames.put(inventory.id(), inventory.name());
        }
        var partnerNames = partnerNames(stationId);
        ctx.json(service.findShares(stationId).stream()
                .map(share -> describe(share, inventoryNames, partnerNames))
                .toList());
    }

    private ShareDetail describe(
            InventoryShare share, Map<Integer, String> inventoryNames, Map<Integer, String> partnerNames) {
        String inventoryName = null;
        String artName = null;
        String itemName = null;
        String itemInternalId = null;
        switch (share.level()) {
            case ITEM -> {
                var item = inventoryRepository.findItemById(share.itemId()).orElse(null);
                if (item != null) {
                    itemName = item.name();
                    itemInternalId = item.internalId();
                    inventoryName = inventoryNames.get(item.inventoryId());
                    if (item.artId() != null) {
                        artName = artRepository
                                .findById(item.artId())
                                .map(InventoryArt::name)
                                .orElse(null);
                    }
                }
            }
            case ART -> {
                var art = artRepository.findById(share.artId()).orElse(null);
                if (art != null) {
                    artName = art.name();
                    inventoryName = inventoryNames.get(art.inventoryId());
                }
            }
            case INVENTORY -> inventoryName = inventoryNames.get(share.inventoryId());
        }
        var targets = service.findTargets(share.id()).stream()
                .map(partnerId -> new SharePartner(partnerId, partnerNames.getOrDefault(partnerId, "?")))
                .toList();
        return new ShareDetail(share, inventoryName, artName, itemName, itemInternalId, targets);
    }

    private Map<Integer, String> partnerNames(int stationId) {
        var names = new HashMap<Integer, String>();
        for (var partner : federationService.findPartners(stationId)) {
            String name = stationRepository
                    .findByUid(partner.partnerStationId())
                    .map(station -> station.name())
                    .orElse(partner.partnerStationName());
            names.put(partner.id(), name != null ? name : "?");
        }
        return names;
    }

    private void getInventoryShare(Context ctx) {
        var session = UserSession.from(ctx);
        int inventoryId = pathInt(ctx, "inventoryId");
        ctx.json(service.findForInventory(session.stationId(), inventoryId)
                .map(this::toSetting)
                .orElseGet(ShareSetting::unshared));
    }

    private void getArtShare(Context ctx) {
        var session = UserSession.from(ctx);
        int artId = pathInt(ctx, "artId");
        ctx.json(service.findForArt(session.stationId(), artId)
                .map(this::toSetting)
                .orElseGet(ShareSetting::unshared));
    }

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

    private void setInventoryShare(Context ctx) {
        var session = UserSession.from(ctx);
        int inventoryId = pathInt(ctx, "inventoryId");
        var body = readBody(ctx);
        var share = service.setInventoryShare(
                session.stationId(), inventoryId, body.scope(), body.grant(), body.partnerIdList());
        ctx.json(toSetting(share));
    }

    private void setArtShare(Context ctx) {
        var session = UserSession.from(ctx);
        int artId = pathInt(ctx, "artId");
        var body = readBody(ctx);
        var share = service.setArtShare(session.stationId(), artId, body.scope(), body.grant(), body.partnerIdList());
        ctx.json(toSetting(share));
    }

    private void setItemShare(Context ctx) {
        var session = UserSession.from(ctx);
        int itemId = pathInt(ctx, "itemId");
        var body = readBody(ctx);
        var share = service.setItemShare(session.stationId(), itemId, body.scope(), body.grant(), body.partnerIdList());
        ctx.json(toSetting(share));
    }

    private void deleteInventoryShare(Context ctx) {
        var session = UserSession.from(ctx);
        service.removeInventoryShare(session.stationId(), pathInt(ctx, "inventoryId"));
        ctx.status(HttpStatus.NO_CONTENT);
    }

    private void deleteArtShare(Context ctx) {
        var session = UserSession.from(ctx);
        service.removeArtShare(session.stationId(), pathInt(ctx, "artId"));
        ctx.status(HttpStatus.NO_CONTENT);
    }

    private void deleteItemShare(Context ctx) {
        var session = UserSession.from(ctx);
        service.removeItemShare(session.stationId(), pathInt(ctx, "itemId"));
        ctx.status(HttpStatus.NO_CONTENT);
    }

    private SetShareRequest readBody(Context ctx) {
        var body = ctx.bodyAsClass(SetShareRequest.class);
        if (body.grant() == null) throw new BadRequestResponse("grant is required");
        if (body.scope() == null) throw new BadRequestResponse("scope is required");
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
    public record ShareSetting(boolean shared, ShareGrant grant, ShareScope scope, List<Integer> partnerIds) {
        static ShareSetting unshared() {
            return new ShareSetting(false, null, null, List.of());
        }
    }

    /** One row of the overview of everything this station offers. */
    public record ShareDetail(
            InventoryShare share,
            String inventoryName,
            String artName,
            String itemName,
            String itemInternalId,
            List<SharePartner> partners) {}

    /** A partner named by a share, with the name to show for it. */
    public record SharePartner(int partnerId, String stationName) {}
}
