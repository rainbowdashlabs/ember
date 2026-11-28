/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.service;

import dev.chojo.ember.api.refusal.FederationRefusal;
import dev.chojo.ember.feature.equipment.entity.ClaimSnapshot;
import dev.chojo.ember.feature.equipment.service.EquipmentAvailabilityService;
import dev.chojo.ember.feature.federation.contract.FederationRequest;
import dev.chojo.ember.feature.federation.entity.FederationPartner;
import dev.chojo.ember.feature.federation.entity.InventoryBlock;
import dev.chojo.ember.feature.federation.entity.SharePolicy;
import dev.chojo.ember.feature.federation.route.RemoteLendingRoutes;
import dev.chojo.ember.feature.federation.route.RemoteLendingRoutes.RemoteAddress;
import dev.chojo.ember.feature.federation.route.RemoteLendingRoutes.RemoteDay;
import dev.chojo.ember.feature.federation.route.RemoteLendingRoutes.RemoteDayCount;
import dev.chojo.ember.feature.federation.route.RemoteLendingRoutes.RemoteKind;
import dev.chojo.ember.feature.federation.route.RemoteLendingRoutes.RemotePiece;
import dev.chojo.ember.feature.federation.route.RemoteLendingRoutes.RemoteProduct;
import dev.chojo.ember.feature.federation.route.RemoteLendingRoutes.RemoteProductDays;
import dev.chojo.ember.feature.federation.route.RemoteLendingRoutes.RemoteProductSummary;
import dev.chojo.ember.feature.federation.route.RemoteLendingRoutes.RemoteProducts;
import dev.chojo.ember.feature.federation.service.LendingService.EmptyReason;
import dev.chojo.ember.feature.federation.service.LendingService.OfferedStock;
import dev.chojo.ember.feature.federation.transport.FederationEndpoints;
import dev.chojo.ember.feature.federation.transport.FederationServer;
import dev.chojo.ember.feature.federation.transport.FederationTransport;
import dev.chojo.ember.feature.federation.transport.PathParams;
import dev.chojo.ember.feature.federation.transport.ServingPartner;
import dev.chojo.ember.feature.inventory.entity.Inventory;
import dev.chojo.ember.feature.inventory.entity.InventoryArt;
import dev.chojo.ember.feature.inventory.entity.InventoryItem;
import dev.chojo.ember.feature.inventory.entity.InventoryMatch;
import dev.chojo.ember.feature.inventory.entity.ItemPresentation;
import dev.chojo.ember.feature.inventory.entity.LineTarget;
import dev.chojo.ember.feature.inventory.repository.InventoryArtRepository;
import dev.chojo.ember.feature.inventory.repository.InventoryRepository;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.repository.StationRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The lending catalog: what a station offers its partners, one product per inventory, and asking partners
 * for theirs.
 *
 * <p>A product counts its pieces by the rules a request is checked against. Without days it counts what
 * is free on the shelf today. With days it also leaves out the pieces a claim names (another request, a
 * handover, a block) and never counts more than the claims over those days leave of a kind. Pieces with
 * a kind are counted per kind, an inventory of copies is one count, and the pieces without a kind of an
 * inventory of different things are lent one by one.
 *
 * <p>Every question reads the claims and the blocks of the days it is about once and counts in memory,
 * because a catalog search reaches every partner at once and a calendar covers a whole month.
 *
 * <p>What is counted always starts from what is free on the shelf today. A piece out on loan today but
 * back before the days asked about is not offered yet.
 */
@Singleton
public class LendingCatalogService implements FederationServer {
    /** The start of a description a tile shows, short enough for two lines. */
    static final int TEASER_LENGTH = 200;

    /** The highest rank a search produces: one for a name hit plus at most one for the text match. */
    static final double MAX_RANK = 2.0;

    private final LendingService lendingService;
    private final InventoryShareService shareService;
    private final InventoryRepository inventoryRepository;
    private final InventoryArtRepository artRepository;
    private final StationRepository stationRepository;
    private final EquipmentAvailabilityService availability;
    private final FederationService federationService;
    private final FederationTransport transport;
    private final FederationFanout fanout;

    @Inject
    public LendingCatalogService(
            LendingService lendingService,
            InventoryShareService shareService,
            InventoryRepository inventoryRepository,
            InventoryArtRepository artRepository,
            StationRepository stationRepository,
            EquipmentAvailabilityService availability,
            FederationService federationService,
            FederationTransport transport,
            FederationFanout fanout) {
        this.lendingService = lendingService;
        this.shareService = shareService;
        this.inventoryRepository = inventoryRepository;
        this.artRepository = artRepository;
        this.stationRepository = stationRepository;
        this.availability = availability;
        this.federationService = federationService;
        this.transport = transport;
        this.fanout = fanout;
    }

    /** Registers the three questions a partner asks of the lending catalog. */
    @Override
    public void serveOn(FederationEndpoints endpoints) {
        endpoints.serve(
                RemoteLendingRoutes.GET_PRODUCTS,
                (partner, params, body) -> serveProducts(
                        partner,
                        params.query("q").orElse(null),
                        LendingService.day(params, "from"),
                        LendingService.day(params, "to")));
        endpoints.serve(
                RemoteLendingRoutes.GET_PRODUCT,
                (partner, params, body) -> serveProduct(
                        partner,
                        params.integer("inventoryId"),
                        LendingService.day(params, "from"),
                        LendingService.day(params, "to")));
        endpoints.serve(
                RemoteLendingRoutes.GET_PRODUCT_DAYS,
                (partner, params, body) -> serveProductDays(
                        partner,
                        params.integer("inventoryId"),
                        month(params.query("month").orElse(null)),
                        piece(params)));
    }

    /**
     * What this station offers the asking partner, one product per inventory with something free.
     *
     * @param partner  the partnership the question arrived on
     * @param query    a search over names and descriptions, or {@code null}
     * @param dateFrom the first day the gear is wanted, or {@code null} for today's stock
     * @param dateTo   the last day, or {@code null} for the first one
     * @return the products, best search match first
     */
    public RemoteProducts serveProducts(
            ServingPartner partner, @Nullable String query, @Nullable LocalDate dateFrom, @Nullable LocalDate dateTo) {
        int stationId = partner.servingStationId();
        SharePolicy policy = shareService.policyFor(stationId, partner.askingStationUid());
        if (!policy.offersAnything()) return new RemoteProducts(List.of(), false);
        Window window = Window.of(dateFrom, dateTo);
        Counter counter = counter(stationId, window);
        if (counter.blocked(null, null)) return new RemoteProducts(List.of(), true);
        var stocks = new ArrayList<>(lendingService.offeredStock(
                stationId,
                policy,
                null,
                query,
                window == null ? null : window.first(),
                window == null ? null : window.last()));
        stocks.sort(Comparator.comparingDouble(LendingCatalogService::rankOf).reversed());
        var products = new ArrayList<RemoteProductSummary>();
        for (var stock : stocks) {
            Inventory inventory = stock.inventory();
            Split split = Split.of(stock);
            int free = 0;
            for (var row : split.counted().entrySet()) {
                free += counter.count(target(inventory.id(), row.getKey()), row.getValue());
            }
            for (var piece : split.singles()) {
                if (counter.free(piece)) free++;
            }
            if (window != null && free == 0) continue;
            products.add(new RemoteProductSummary(
                    inventory.id(),
                    inventory.name(),
                    teaser(inventory.description()),
                    inventory.pictureKey(),
                    inventory.icon(),
                    inventory.color(),
                    split.counted().size(),
                    split.singles().size(),
                    free,
                    matchedNames(stock),
                    rankOf(stock)));
        }
        return new RemoteProducts(products, true);
    }

    /**
     * One product as its page shows it: the description, where the gear is, every kind on offer with
     * what is free of it, and every single piece with whether it is free.
     *
     * @param partner     the partnership the question arrived on
     * @param inventoryId the inventory
     * @param dateFrom    the first day the gear is wanted, or {@code null} for today
     * @param dateTo      the last day, or {@code null} for the first one
     * @return the product
     * @throws dev.chojo.ember.api.refusal.RefusalResponse when the inventory offers the partner nothing
     */
    public RemoteProduct serveProduct(
            ServingPartner partner, int inventoryId, @Nullable LocalDate dateFrom, @Nullable LocalDate dateTo) {
        int stationId = partner.servingStationId();
        OfferedStock stock = requireOffered(partner, inventoryId);
        Inventory inventory = stock.inventory();
        Counter counter = counter(stationId, Window.of(dateFrom, dateTo));
        boolean blocked = counter.blocked(inventoryId, null);
        Map<Integer, InventoryArt> arts = artRepository.findByInventory(inventoryId).stream()
                .collect(Collectors.toMap(InventoryArt::id, Function.identity(), (a, b) -> a, LinkedHashMap::new));
        Split split = Split.of(stock);

        var kinds = new ArrayList<RemoteKind>();
        for (var row : split.counted().entrySet()) {
            Integer artId = row.getKey();
            InventoryArt art = artId == null ? null : arts.get(artId);
            var pieces = row.getValue().stream()
                    .filter(piece -> !counter.blocked(null, piece.id()))
                    .toList();
            int free = blocked ? 0 : counter.count(target(inventoryId, artId), pieces);
            kinds.add(new RemoteKind(
                    artId,
                    art == null ? "" : art.name(),
                    art == null ? "" : art.description(),
                    firstNamed(art == null ? null : art.pictureKey(), inventory.pictureKey()),
                    firstNamed(art == null ? null : art.icon(), inventory.icon()),
                    firstNamed(art == null ? null : art.color(), inventory.color()),
                    free));
        }
        kinds.sort(Comparator.comparing(kind -> positionOf(arts, kind.artId())));

        Map<Integer, ItemPresentation> presentations = split.singles().isEmpty()
                ? Map.of()
                : inventoryRepository.findItemPresentations(inventoryId).stream()
                        .collect(Collectors.toMap(ItemPresentation::itemId, Function.identity()));
        var pieces = new ArrayList<RemotePiece>();
        for (var piece : split.singles()) {
            ItemPresentation presentation = presentations.get(piece.id());
            pieces.add(new RemotePiece(
                    piece.id(),
                    piece.name(),
                    presentation == null ? "" : presentation.description(),
                    firstNamed(presentation == null ? null : presentation.pictureKey(), inventory.pictureKey()),
                    !blocked && !counter.blocked(null, piece.id()) && counter.free(piece)));
        }
        pieces.sort(Comparator.comparing(RemotePiece::name, String.CASE_INSENSITIVE_ORDER));
        return new RemoteProduct(
                inventory.id(),
                inventory.name(),
                inventory.description(),
                inventory.pictureKey(),
                inventory.icon(),
                inventory.color(),
                addressOf(stationId),
                kinds,
                pieces);
    }

    /**
     * What is free of a product on each day of a month: one row per kind on offer, one for an inventory
     * of copies, and one for its single pieces together; or only the one piece asked about.
     *
     * @param partner     the partnership the question arrived on
     * @param inventoryId the inventory
     * @param month       the month
     * @param itemId      the single piece asked about, or {@code null} for the whole product
     * @return one entry per day of the month
     * @throws dev.chojo.ember.api.refusal.RefusalResponse when the inventory or the piece is not on offer
     */
    public RemoteProductDays serveProductDays(
            ServingPartner partner, int inventoryId, YearMonth month, @Nullable Integer itemId) {
        int stationId = partner.servingStationId();
        OfferedStock stock = requireOffered(partner, inventoryId);
        Split split = Split.of(stock);
        InventoryItem asked = null;
        if (itemId != null) {
            asked = stock.pieces().stream()
                    .filter(piece -> piece.id() == itemId)
                    .findFirst()
                    .orElseThrow(FederationRefusal.LENDING_PRODUCT_NOT_OFFERED::raise);
        }
        var monthCounter = counter(stationId, new Window(month.atDay(1), month.atEndOfMonth()));
        var days = new ArrayList<RemoteDay>();
        for (LocalDate date = month.atDay(1); !date.isAfter(month.atEndOfMonth()); date = date.plusDays(1)) {
            Counter day = monthCounter.on(date);
            boolean blocked = day.blocked(inventoryId, itemId);
            var counts = new ArrayList<RemoteDayCount>();
            if (asked != null) {
                boolean free = !blocked && day.free(asked);
                counts.add(new RemoteDayCount(null, itemId, false, free ? 1 : 0, 1));
            } else {
                for (var row : split.counted().entrySet()) {
                    var pieces = row.getValue().stream()
                            .filter(piece -> !day.blocked(null, piece.id()))
                            .toList();
                    int free = blocked ? 0 : day.count(target(inventoryId, row.getKey()), pieces);
                    counts.add(new RemoteDayCount(
                            row.getKey(), null, false, free, row.getValue().size()));
                }
                if (!split.singles().isEmpty()) {
                    int free = blocked
                            ? 0
                            : (int) split.singles().stream()
                                    .filter(piece -> !day.blocked(null, piece.id()) && day.free(piece))
                                    .count();
                    counts.add(new RemoteDayCount(
                            null, null, true, free, split.singles().size()));
                }
            }
            days.add(new RemoteDay(date, blocked, counts));
        }
        return new RemoteProductDays(days);
    }

    /**
     * What every lending partner of a station offers it, one product per inventory: best search match
     * first where something was searched for, nearest first otherwise and among equal matches.
     *
     * @param stationId the asking station
     * @param query     a search over names and descriptions, or {@code null}
     * @param dateFrom  the first day the gear is wanted, or {@code null}
     * @param dateTo    the last day, or {@code null}
     * @return the products, and why none came back where none did
     */
    public ProductList findProducts(
            int stationId, @Nullable String query, @Nullable LocalDate dateFrom, @Nullable LocalDate dateTo) {
        Window.of(dateFrom, dateTo);
        var partners = federationService.findPartners(stationId).stream()
                .filter(partner -> partner.status() == FederationPartner.FederationStatus.ACTIVE)
                .filter(lendingService::lendsWith)
                .toList();
        var answers = fanout.fanOut(partners, partner -> List.of(productsAt(partner, query, dateFrom, dateTo)))
                .items();
        Station local = stationRepository.findById(stationId).orElse(null);
        var products = new ArrayList<ProductSummary>();
        boolean anyOffer = false;
        for (var answer : answers) {
            anyOffer |= answer.offers().offersAnything();
            Double distance = lendingService.distanceKm(local, answer.partner().partnerStationId());
            for (var product : answer.offers().products()) {
                products.add(new ProductSummary(
                        answer.partner().partnerStationId(), answer.stationName(), distance, product));
            }
        }
        Comparator<ProductSummary> nearest =
                Comparator.comparing(ProductSummary::distanceKm, Comparator.nullsLast(Comparator.naturalOrder()));
        boolean searched = query != null && !query.isBlank();
        Comparator<ProductSummary> best = Comparator.comparingDouble((ProductSummary summary) ->
                        trustedRank(summary.product().rank()))
                .reversed();
        products.sort(searched ? best.thenComparing(nearest) : nearest);
        if (!products.isEmpty()) return new ProductList(products, null);
        return new ProductList(products, anyOffer ? EmptyReason.NOTHING_FREE : EmptyReason.NOTHING_SHARED);
    }

    /**
     * One product of a lending partner.
     *
     * @param stationId         the asking station
     * @param partnerStationUid the partner station
     * @param inventoryId       the partner's inventory
     * @param dateFrom          the first day the gear is wanted, or {@code null}
     * @param dateTo            the last day, or {@code null}
     * @return the product with the partner's name and distance
     */
    public ProductDetail findProduct(
            int stationId,
            UUID partnerStationUid,
            int inventoryId,
            @Nullable LocalDate dateFrom,
            @Nullable LocalDate dateTo) {
        Window.of(dateFrom, dateTo);
        FederationPartner partner = lendingPartner(stationId, partnerStationUid);
        var request = withDays(RemoteLendingRoutes.GET_PRODUCT.at(String.valueOf(inventoryId)), dateFrom, dateTo);
        RemoteProduct product = transport.get(partner, request, RemoteProduct.class);
        Station local = stationRepository.findById(stationId).orElse(null);
        return new ProductDetail(
                partnerStationUid,
                FederationDisplayNames.partnerName(stationRepository, partner, "?"),
                lendingService.distanceKm(local, partnerStationUid),
                product);
    }

    /**
     * What is free of a partner's product on each day of a month.
     *
     * @param stationId         the asking station
     * @param partnerStationUid the partner station
     * @param inventoryId       the partner's inventory
     * @param month             the month, as {@code yyyy-MM}
     * @param itemId            one single piece, or {@code null} for the whole product
     * @return one entry per day
     */
    public RemoteProductDays findProductDays(
            int stationId, UUID partnerStationUid, int inventoryId, @Nullable String month, @Nullable Integer itemId) {
        YearMonth asked = month(month);
        FederationPartner partner = lendingPartner(stationId, partnerStationUid);
        var request = RemoteLendingRoutes.GET_PRODUCT_DAYS
                .at(String.valueOf(inventoryId))
                .query("month", asked.toString());
        if (itemId != null) request = request.query("itemId", itemId);
        return transport.get(partner, request, RemoteProductDays.class);
    }

    private PartnerProducts productsAt(
            FederationPartner partner,
            @Nullable String query,
            @Nullable LocalDate dateFrom,
            @Nullable LocalDate dateTo) {
        var request = RemoteLendingRoutes.GET_PRODUCTS.at();
        if (query != null && !query.isBlank()) request = request.query("q", query);
        RemoteProducts answer = transport.get(partner, withDays(request, dateFrom, dateTo), RemoteProducts.class);
        return new PartnerProducts(
                partner, FederationDisplayNames.partnerName(stationRepository, partner, "?"), answer);
    }

    private static FederationRequest withDays(
            FederationRequest request, @Nullable LocalDate dateFrom, @Nullable LocalDate dateTo) {
        var asked = dateFrom == null ? request : request.query("from", dateFrom);
        return dateTo == null ? asked : asked.query("to", dateTo);
    }

    private FederationPartner lendingPartner(int stationId, UUID partnerStationUid) {
        FederationPartner partner = lendingService.lendingPartnerOf(stationId, partnerStationUid);
        if (partner == null) throw FederationRefusal.LENDING_PARTNER_DOES_NOT_LEND.raise();
        return partner;
    }

    private OfferedStock requireOffered(ServingPartner partner, int inventoryId) {
        int stationId = partner.servingStationId();
        SharePolicy policy = shareService.policyFor(stationId, partner.askingStationUid());
        if (!policy.offersAnything()) throw FederationRefusal.LENDING_PRODUCT_NOT_OFFERED.raise();
        return lendingService.offeredStock(stationId, policy, inventoryId, null, null, null).stream()
                .findFirst()
                .orElseThrow(FederationRefusal.LENDING_PRODUCT_NOT_OFFERED::raise);
    }

    /** Reads the claims and blocks of a window once; without a window it counts today's shelf. */
    private Counter counter(int stationId, @Nullable Window window) {
        if (window == null) return new Counter(stationId, null, null, List.of());
        ClaimSnapshot snapshot = availability.snapshot(stationId, window.from(), window.to());
        return new Counter(
                stationId, window, snapshot, lendingService.blocksBetween(stationId, window.first(), window.last()));
    }

    private static LineTarget target(int inventoryId, @Nullable Integer artId) {
        return artId == null ? LineTarget.inventory(inventoryId) : LineTarget.art(artId);
    }

    /**
     * Counts over one window from claims and blocks read once.
     *
     * @param stationId the station counting
     * @param window    the days, or {@code null} for today's shelf
     * @param snapshot  the claims over the window, or {@code null} without one
     * @param blocks    the blocks touching the window
     */
    private final class Counter {
        private final int stationId;
        private final @Nullable Window window;
        private final @Nullable ClaimSnapshot snapshot;
        private final List<InventoryBlock> blocks;
        private final Map<LineTarget, Optional<ClaimSnapshot.Question>> questions;
        private @Nullable Set<Integer> named;

        private Counter(
                int stationId, @Nullable Window window, @Nullable ClaimSnapshot snapshot, List<InventoryBlock> blocks) {
            this(stationId, window, snapshot, blocks, new LinkedHashMap<>());
        }

        private Counter(
                int stationId,
                @Nullable Window window,
                @Nullable ClaimSnapshot snapshot,
                List<InventoryBlock> blocks,
                Map<LineTarget, Optional<ClaimSnapshot.Question>> questions) {
            this.stationId = stationId;
            this.window = window;
            this.snapshot = snapshot;
            this.blocks = blocks;
            this.questions = questions;
        }

        /** The same claims and blocks narrowed to one day, sharing what was resolved already. */
        Counter on(LocalDate day) {
            var dayBlocks = blocks.stream()
                    .filter(block ->
                            !block.blockFrom().isAfter(day) && !block.blockTo().isBefore(day))
                    .toList();
            return new Counter(stationId, new Window(day, day), snapshot, dayBlocks, questions);
        }

        boolean blocked(@Nullable Integer inventoryId, @Nullable Integer itemId) {
            return LendingService.blocks(blocks, inventoryId, itemId);
        }

        /** Whether a single piece is free: today always, over days when no claim names it. */
        boolean free(InventoryItem piece) {
            return window == null || !named().contains(piece.id());
        }

        /**
         * How many of a row's pieces are free: all of them today, over days those no claim names, never
         * more than the claims leave of the row.
         */
        int count(LineTarget target, List<InventoryItem> pieces) {
            if (window == null || snapshot == null) return pieces.size();
            var named = named();
            int unclaimed = (int)
                    pieces.stream().filter(piece -> !named.contains(piece.id())).count();
            var question = questions.computeIfAbsent(target, key -> availability.question(stationId, key));
            if (question.isEmpty()) return 0;
            int left = snapshot.free(question.get(), window.from(), window.to());
            return Math.max(0, Math.min(unclaimed, left));
        }

        private Set<Integer> named() {
            if (named == null) {
                named = snapshot == null || window == null ? Set.of() : snapshot.named(window.from(), window.to());
            }
            return named;
        }
    }

    /**
     * The pieces of a product as the catalog lends them. An inventory of copies is one count row without a
     * kind. In an inventory of different things the pieces with a kind are counted per kind and the
     * pieces without one are lent one by one.
     *
     * @param counted the count rows, keyed by kind, {@code null} for the one row of an inventory of copies
     * @param singles the pieces lent one by one
     */
    private record Split(Map<Integer, List<InventoryItem>> counted, List<InventoryItem> singles) {
        static Split of(OfferedStock stock) {
            var counted = new LinkedHashMap<Integer, List<InventoryItem>>();
            var singles = new ArrayList<InventoryItem>();
            boolean copies = stock.inventory().homogeneous();
            for (var piece : stock.pieces()) {
                if (copies) {
                    counted.computeIfAbsent(null, key -> new ArrayList<>()).add(piece);
                } else if (piece.artId() == null) {
                    singles.add(piece);
                } else {
                    counted.computeIfAbsent(piece.artId(), key -> new ArrayList<>())
                            .add(piece);
                }
            }
            return new Split(counted, singles);
        }
    }

    /** The names of the kinds and pieces on offer a search reached the inventory through. */
    private static List<String> matchedNames(OfferedStock stock) {
        InventoryMatch match = stock.match();
        if (match == null || match.itself()) return List.of();
        var offeredArts = stock.offeredArtIds();
        var offeredPieces = stock.offeredPieceIds();
        var names = new ArrayList<String>();
        match.kinds().stream()
                .filter(hit -> offeredArts.contains(hit.id()))
                .map(InventoryMatch.Hit::name)
                .forEach(names::add);
        match.pieces().stream()
                .filter(hit -> offeredPieces.contains(hit.id()))
                .map(InventoryMatch.Hit::name)
                .forEach(names::add);
        return names.stream().distinct().limit(3).toList();
    }

    /**
     * A partner's rank held to what the search can produce. A partner on another instance sending more
     * would otherwise put its products first in every search.
     *
     * @param rank the rank as the partner sent it
     * @return the rank between {@code 0} and {@link #MAX_RANK}
     */
    static double trustedRank(double rank) {
        if (!Double.isFinite(rank) || rank < 0) return 0;
        return Math.min(rank, MAX_RANK);
    }

    private static double rankOf(OfferedStock stock) {
        InventoryMatch match = stock.match();
        return match == null ? 0 : match.rank();
    }

    private static int positionOf(Map<Integer, InventoryArt> arts, @Nullable Integer artId) {
        InventoryArt art = artId == null ? null : arts.get(artId);
        return art == null ? Integer.MAX_VALUE : art.position();
    }

    private @Nullable RemoteAddress addressOf(int stationId) {
        Station station = stationRepository.findById(stationId).orElse(null);
        if (station == null) return null;
        String line = Objects.requireNonNullElse(station.addressLine(), "").strip();
        String city = Objects.requireNonNullElse(station.city(), "").strip();
        if (line.isEmpty() && city.isEmpty()) return null;
        return new RemoteAddress(
                line,
                Objects.requireNonNullElse(station.postalCode(), "").strip(),
                city,
                Objects.requireNonNullElse(station.country(), "").strip());
    }

    /**
     * The start of a description without its Markdown, cut at a word near {@link #TEASER_LENGTH}.
     *
     * @param description the description in Markdown
     * @return plain text, empty where there is no description
     */
    static String teaser(@Nullable String description) {
        if (description == null || description.isBlank()) return "";
        String plain = description
                .replaceAll("!?\\[([^\\]]*)]\\([^)]*\\)", "$1")
                .replaceAll("[#*_`>~|]", "")
                .replaceAll("(?m)^\\s*[-+]\\s+", "")
                .replaceAll("\\s+", " ")
                .strip();
        if (plain.length() <= TEASER_LENGTH) return plain;
        int cut = plain.lastIndexOf(' ', TEASER_LENGTH);
        return plain.substring(0, cut > TEASER_LENGTH / 2 ? cut : TEASER_LENGTH).strip() + "…";
    }

    @SafeVarargs
    private static <T> @Nullable T firstNamed(@Nullable T... candidates) {
        for (T candidate : candidates) {
            if (candidate instanceof String text && text.isBlank()) continue;
            if (candidate != null) return candidate;
        }
        return null;
    }

    private static @Nullable Integer piece(PathParams params) {
        try {
            return params.query("itemId")
                    .filter(value -> !value.isBlank())
                    .map(Integer::valueOf)
                    .orElse(null);
        } catch (NumberFormatException e) {
            throw FederationRefusal.LENDING_PRODUCT_NOT_OFFERED.raise();
        }
    }

    private static YearMonth month(@Nullable String value) {
        if (value == null || value.isBlank()) throw FederationRefusal.LENDING_MONTH_NOT_A_MONTH.raise();
        try {
            return YearMonth.parse(value.strip());
        } catch (DateTimeParseException e) {
            throw FederationRefusal.LENDING_MONTH_NOT_A_MONTH.raise();
        }
    }

    /**
     * The days gear is wanted for, as the request rules read them: whole days in UTC, the last one
     * included.
     */
    private record Window(LocalDate first, LocalDate last) {
        /**
         * The window of the days asked about.
         *
         * @return the window, or {@code null} without a first day
         * @throws dev.chojo.ember.api.refusal.RefusalResponse when the last day comes before the first
         */
        static @Nullable Window of(@Nullable LocalDate from, @Nullable LocalDate to) {
            if (from == null) return null;
            if (to != null && to.isBefore(from)) throw FederationRefusal.LENDING_WINDOW_BACKWARDS.raise();
            return new Window(from, to == null ? from : to);
        }

        Instant from() {
            return first.atStartOfDay(ZoneOffset.UTC).toInstant();
        }

        Instant to() {
            return last.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        }
    }

    private record PartnerProducts(FederationPartner partner, String stationName, RemoteProducts offers) {}

    /**
     * One product of a partner as the catalog lists it.
     *
     * @param stationId   the partner station
     * @param stationName what the partner station is called
     * @param distanceKm  how far away it is, or {@code null} where either station gives no location
     * @param product     the product
     */
    public record ProductSummary(
            UUID stationId, String stationName, @Nullable Double distanceKm, RemoteProductSummary product) {}

    /**
     * The catalog's answer, with the reason an empty one is empty.
     *
     * @param products    what the partners offer, best match or nearest first
     * @param emptyReason why nothing came back, or {@code null} when something did
     */
    public record ProductList(
            List<ProductSummary> products, @Nullable EmptyReason emptyReason) {}

    /**
     * One product of a partner as its page shows it.
     *
     * @param stationId   the partner station
     * @param stationName what the partner station is called
     * @param distanceKm  how far away it is, or {@code null}
     * @param product     the product
     */
    public record ProductDetail(
            UUID stationId, String stationName, @Nullable Double distanceKm, RemoteProduct product) {}
}
