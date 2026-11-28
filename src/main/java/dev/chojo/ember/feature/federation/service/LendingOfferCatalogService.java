/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.service;

import dev.chojo.ember.feature.federation.entity.InventoryShare;
import dev.chojo.ember.feature.federation.entity.ShareGrant;
import dev.chojo.ember.feature.federation.entity.SharePolicy;
import dev.chojo.ember.feature.federation.entity.ShareScope;
import dev.chojo.ember.feature.inventory.entity.Inventory;
import dev.chojo.ember.feature.inventory.entity.InventoryItem;
import dev.chojo.ember.feature.inventory.entity.ItemPresentation;
import dev.chojo.ember.feature.inventory.repository.InventoryArtRepository;
import dev.chojo.ember.feature.inventory.repository.InventoryRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The lending station's own offer, product by product, as the offer catalog shows it: every inventory
 * the station holds pieces of that it may lend, its kinds, and in an inventory of different things
 * each piece, with the share rule written at that level, the text and photo partners read, and where a
 * partner is named, what that partner is offered. Sharing is read through the same policy that answers
 * partners; whether a piece is free on a given day is the shop's question, not the catalog's.
 */
@Singleton
public class LendingOfferCatalogService {
    private final InventoryRepository inventoryRepository;
    private final InventoryArtRepository artRepository;
    private final InventoryShareService shareService;
    private final LendingService lendingService;

    @Inject
    public LendingOfferCatalogService(
            InventoryRepository inventoryRepository,
            InventoryArtRepository artRepository,
            InventoryShareService shareService,
            LendingService lendingService) {
        this.inventoryRepository = inventoryRepository;
        this.artRepository = artRepository;
        this.shareService = shareService;
        this.lendingService = lendingService;
    }

    /**
     * The station's offer, every inventory with pieces it may lend, in the station's order.
     *
     * @param stationId the lending station
     * @param partnerId a partnership to read the offer through, or {@code null} for none
     * @return one product per inventory
     */
    public List<CatalogProduct> catalog(int stationId, @Nullable Integer partnerId) {
        var lendable = lendingService.lendableBy(stationId);
        SharePolicy policy = partnerId == null ? null : shareService.policyForPartnership(stationId, partnerId);
        var targets = shareService.findTargetsByStation(stationId);
        var rules = Rules.of(shareService.findShares(stationId), id -> targets.getOrDefault(id, List.of()));
        var products = new ArrayList<CatalogProduct>();
        for (Inventory inventory : inventoryRepository.findByStation(stationId)) {
            var pieces = inventoryRepository.findStock(inventory.id()).stream()
                    .filter(lendable)
                    .toList();
            if (pieces.isEmpty()) continue;
            products.add(product(inventory, pieces, rules, policy));
        }
        return products;
    }

    private CatalogProduct product(
            Inventory inventory, List<InventoryItem> pieces, Rules rules, @Nullable SharePolicy policy) {
        Map<Integer, Long> perKind = pieces.stream()
                .filter(piece -> piece.artId() != null)
                .collect(Collectors.groupingBy(InventoryItem::artId, Collectors.counting()));
        var kinds = artRepository.findByInventory(inventory.id()).stream()
                .filter(art -> perKind.containsKey(art.id()))
                .map(art -> new CatalogKind(
                        art.id(),
                        art.name(),
                        art.description(),
                        art.pictureKey(),
                        perKind.get(art.id()).intValue(),
                        rules.forArt(art.id()),
                        policy == null ? null : anyAllowed(policy, pieces, art.id())))
                .toList();
        List<CatalogPiece> catalogPieces = inventory.homogeneous() ? List.of() : pieces(pieces, rules, policy);
        return new CatalogProduct(
                inventory.id(),
                inventory.name(),
                inventory.description(),
                inventory.pictureKey(),
                inventory.icon(),
                inventory.color(),
                inventory.homogeneous(),
                pieces.size(),
                rules.forInventory(inventory.id()),
                pieces.stream().anyMatch(piece -> rules.grants(piece)),
                policy == null ? null : pieces.stream().anyMatch(piece -> allows(policy, piece)),
                kinds,
                catalogPieces);
    }

    private List<CatalogPiece> pieces(List<InventoryItem> pieces, Rules rules, @Nullable SharePolicy policy) {
        if (pieces.isEmpty()) return List.of();
        Map<Integer, ItemPresentation> presentations =
                inventoryRepository.findItemPresentations(pieces.getFirst().inventoryId()).stream()
                        .collect(Collectors.toMap(ItemPresentation::itemId, Function.identity()));
        return pieces.stream()
                .map(piece -> {
                    var presentation = presentations.get(piece.id());
                    return new CatalogPiece(
                            piece.id(),
                            piece.name(),
                            piece.internalId(),
                            piece.artId(),
                            presentation == null ? "" : presentation.description(),
                            presentation == null ? null : presentation.pictureKey(),
                            rules.forItem(piece.id()),
                            policy == null ? null : allows(policy, piece));
                })
                .toList();
    }

    private static boolean anyAllowed(SharePolicy policy, List<InventoryItem> pieces, int artId) {
        return pieces.stream()
                .filter(piece -> Objects.equals(piece.artId(), artId))
                .anyMatch(piece -> allows(policy, piece));
    }

    private static boolean allows(SharePolicy policy, InventoryItem piece) {
        return policy.allows(piece.inventoryId(), piece.artId(), piece.id());
    }

    /** The share rules of a station, by the level they are written at. */
    private record Rules(
            Map<Integer, CatalogShare> inventories, Map<Integer, CatalogShare> arts, Map<Integer, CatalogShare> items) {

        static Rules of(List<InventoryShare> shares, Function<Integer, List<Integer>> targets) {
            var inventories = new HashMap<Integer, CatalogShare>();
            var arts = new HashMap<Integer, CatalogShare>();
            var items = new HashMap<Integer, CatalogShare>();
            for (var share : shares) {
                var rule = new CatalogShare(share.shareGrant(), share.shareScope(), targets.apply(share.id()));
                if (share.itemId() != null) items.put(share.itemId(), rule);
                else if (share.artId() != null) arts.put(share.artId(), rule);
                else if (share.inventoryId() != null) inventories.put(share.inventoryId(), rule);
            }
            return new Rules(inventories, arts, items);
        }

        @Nullable
        CatalogShare forInventory(int inventoryId) {
            return inventories.get(inventoryId);
        }

        @Nullable
        CatalogShare forArt(int artId) {
            return arts.get(artId);
        }

        @Nullable
        CatalogShare forItem(int itemId) {
            return items.get(itemId);
        }

        /**
         * Whether the narrowest rule over a piece offers it, the way a partner's policy resolves it:
         * the piece's own rule, else its kind's, else its inventory's. Who a rule is for is left aside.
         */
        boolean grants(InventoryItem piece) {
            CatalogShare rule = items.get(piece.id());
            if (rule == null && piece.artId() != null) rule = arts.get(piece.artId());
            if (rule == null) rule = inventories.get(piece.inventoryId());
            return rule != null && rule.grant() == ShareGrant.GRANT;
        }
    }

    /**
     * A share rule written at one level.
     *
     * @param partnerIds the partnerships it names, where its scope is specific partners
     */
    public record CatalogShare(ShareGrant grant, ShareScope scope, List<Integer> partnerIds) {}

    /**
     * One inventory as a product of the offer.
     *
     * @param pieceCount how many pieces of it the station holds and may lend
     * @param share      the rule written for the inventory, or {@code null} for none
     * @param offered    whether some piece of it is offered to some partner by its narrowest rule
     * @param visible    whether the partner read through is offered anything of it, or {@code null};
     *                   what is out today, blocked or on a movement is offered all the same and only
     *                   missing from the shop while it is
     * @param pieces     each piece, in an inventory of different things; empty for one of copies
     */
    public record CatalogProduct(
            int inventoryId,
            String name,
            String description,
            @Nullable String pictureKey,
            @Nullable String icon,
            @Nullable String color,
            boolean homogeneous,
            int pieceCount,
            @Nullable CatalogShare share,
            boolean offered,
            @Nullable Boolean visible,
            List<CatalogKind> kinds,
            List<CatalogPiece> pieces) {}

    /**
     * One kind of a product.
     *
     * @param share   the rule written for the kind, or {@code null} where it follows the product
     * @param visible whether the partner read through sees any piece of it, or {@code null}
     */
    public record CatalogKind(
            int artId,
            String name,
            String description,
            @Nullable String pictureKey,
            int pieceCount,
            @Nullable CatalogShare share,
            @Nullable Boolean visible) {}

    /**
     * One piece of a product of different things.
     *
     * @param artId      the kind it belongs to, or {@code null}
     * @param pictureKey its own photo, or {@code null} where it shows its kind's or inventory's
     * @param share      the rule written for the piece, or {@code null} where it follows its kind
     * @param visible    whether the partner read through sees it, or {@code null}
     */
    public record CatalogPiece(
            int itemId,
            String name,
            @Nullable String internalId,
            @Nullable Integer artId,
            String description,
            @Nullable String pictureKey,
            @Nullable CatalogShare share,
            @Nullable Boolean visible) {}
}
