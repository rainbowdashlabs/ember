/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.service;

import dev.chojo.ember.api.refusal.FederationRefusal;
import dev.chojo.ember.api.refusal.RefusalResponse;
import dev.chojo.ember.feature.federation.entity.FederationPartner;
import dev.chojo.ember.feature.federation.entity.LendingCart;
import dev.chojo.ember.feature.federation.entity.LendingCartLine;
import dev.chojo.ember.feature.federation.entity.LendingRequest;
import dev.chojo.ember.feature.federation.entity.LendingStatus;
import dev.chojo.ember.feature.federation.repository.LendingCartRepository;
import dev.chojo.ember.feature.federation.repository.LendingRepository;
import dev.chojo.ember.feature.federation.repository.LendingRepository.OfferedLine;
import dev.chojo.ember.feature.federation.route.RemoteLendingRoutes.RemoteKind;
import dev.chojo.ember.feature.federation.route.RemoteLendingRoutes.RemoteProduct;
import dev.chojo.ember.feature.federation.service.LendingService.RequestLine;
import dev.chojo.ember.feature.station.repository.StationRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * A station's cart of gear from its partners: what is put in it, whether it is still free, and how it
 * is sent as one request per lending station.
 *
 * <p>The cart names everything in the lending station's own ids and keeps the names it was last shown
 * under, so it can be shown without asking anybody. Whether it is still free is asked of the lending
 * stations each time, one question per product, with the cart's days.
 */
@Singleton
public class LendingCartService {
    private static final Logger log = LoggerFactory.getLogger(LendingCartService.class);

    /** The longest occasion the cart keeps, the length of a request's title. */
    static final int MAX_OCCASION = 200;

    /** The longest note the cart keeps, the length of a long message. */
    static final int MAX_NOTE = 4000;

    /** The longest name kept for a line, enough for any inventory, kind or piece name. */
    static final int MAX_NAME = 200;

    /** The most of one thing a line asks for, far beyond what any station keeps. */
    static final int MAX_QUANTITY = 999;

    private final LendingCartRepository carts;
    private final LendingRepository requests;
    private final LendingService lendingService;
    private final LendingCatalogService catalog;
    private final StationRepository stationRepository;
    private final FederationFanout fanout;

    @Inject
    public LendingCartService(
            LendingCartRepository carts,
            LendingRepository requests,
            LendingService lendingService,
            LendingCatalogService catalog,
            StationRepository stationRepository,
            FederationFanout fanout) {
        this.carts = carts;
        this.requests = requests;
        this.lendingService = lendingService;
        this.catalog = catalog;
        this.stationRepository = stationRepository;
        this.fanout = fanout;
    }

    /** The station's cart, an empty one where nothing was put in it yet. */
    public LendingCart cart(int stationId) {
        return carts.find(stationId).orElseGet(() -> LendingCart.empty(stationId));
    }

    public List<LendingCartLine> lines(int stationId) {
        return carts.lines(stationId);
    }

    public int countLines(int stationId) {
        return carts.countLines(stationId);
    }

    /** What a lending station is called for the station looking at its cart. */
    public String stationName(UUID lendingStationUid, int stationId) {
        return lendingService.stationName(lendingStationUid, stationId);
    }

    /**
     * Sets the days, the occasion and the note the cart asks with.
     *
     * @throws RefusalResponse when the last day comes before the first, or a text is too long
     */
    public void setDetails(
            int stationId, @Nullable LocalDate dateFrom, @Nullable LocalDate dateTo, String occasion, String note) {
        if (dateFrom == null && dateTo != null) dateFrom = dateTo;
        if (dateFrom != null && dateTo != null && dateTo.isBefore(dateFrom)) {
            throw FederationRefusal.LENDING_WINDOW_BACKWARDS.raise();
        }
        String occasionText = Objects.requireNonNullElse(occasion, "").strip();
        String noteText = Objects.requireNonNullElse(note, "").strip();
        if (occasionText.length() > MAX_OCCASION || noteText.length() > MAX_NOTE) {
            throw FederationRefusal.LENDING_CART_TEXT_TOO_LONG.raise();
        }
        carts.setDetails(stationId, dateFrom, dateTo, occasionText, noteText);
    }

    /**
     * Puts gear of a partner in the cart.
     *
     * @throws RefusalResponse when the line counts nothing or names a kind and a piece at once, or the
     *                         station does not lend with that partner
     */
    public void add(int stationId, NewCartLine line) {
        if (line.lendingStationUid() == null
                || line.quantity() < 1
                || line.quantity() > MAX_QUANTITY
                || (line.artId() != null && line.itemId() != null)) {
            throw FederationRefusal.LENDING_CART_LINE_INVALID.raise();
        }
        if (lendingService.lendingPartnerOf(stationId, line.lendingStationUid()) == null) {
            throw FederationRefusal.LENDING_PARTNER_DOES_NOT_LEND.raise();
        }
        carts.addLine(
                stationId,
                line.lendingStationUid(),
                line.inventoryId(),
                line.artId(),
                line.itemId(),
                line.itemId() == null ? line.quantity() : 1,
                cut(line.inventoryName()),
                cut(line.lineName()),
                pictureKey(line.pictureKey()));
    }

    /**
     * Sets how many of a counted line are wanted.
     *
     * @throws RefusalResponse when the count is below one, or the line is not a counted line of this cart
     */
    public void setQuantity(int stationId, int lineId, int quantity) {
        if (quantity < 1 || quantity > MAX_QUANTITY) throw FederationRefusal.LENDING_CART_LINE_INVALID.raise();
        if (!carts.setQuantity(stationId, lineId, quantity)) {
            throw FederationRefusal.LENDING_CART_LINE_NOT_HERE.raise();
        }
    }

    public void remove(int stationId, int lineId) {
        if (!carts.removeLine(stationId, lineId)) throw FederationRefusal.LENDING_CART_LINE_NOT_HERE.raise();
    }

    /**
     * Asks the lending stations what of the cart is free on its days, today where none are chosen. The
     * stations are asked side by side, each about its products one after the other; once one cannot
     * be reached, the rest of its lines are marked so without asking again. A station that no longer
     * lends, or a product it no longer offers, marks its lines as gone.
     *
     * @return one answer per line, in the cart's order
     */
    public List<CartLineCheck> check(int stationId) {
        return check(stationId, cart(stationId), carts.lines(stationId));
    }

    private List<CartLineCheck> check(int stationId, LendingCart cart, List<LendingCartLine> lines) {
        var byStation = byStation(lines);
        var partners = new ArrayList<FederationPartner>();
        Set<UUID> stoppedLending = new HashSet<>();
        for (UUID lender : byStation.keySet()) {
            var partner = lendingService.lendingPartnerOf(stationId, lender);
            if (partner == null) stoppedLending.add(lender);
            else partners.add(partner);
        }
        Map<ProductKey, ProductAnswer> answers = new HashMap<>();
        var asked = fanout.fanOut(
                partners,
                partner -> askProducts(
                        stationId, partner.partnerStationId(), byStation.get(partner.partnerStationId()), cart));
        for (var answer : asked.answers()) {
            for (var product : answer.items()) answers.put(product.key(), product);
        }
        var checks = new ArrayList<CartLineCheck>();
        for (var line : lines) {
            if (stoppedLending.contains(line.lendingStationUid())) {
                checks.add(new CartLineCheck(line.id(), 0, CartLineState.GONE));
                continue;
            }
            var answer = answers.get(new ProductKey(line.lendingStationUid(), line.inventoryId()));
            checks.add(
                    answer == null
                            ? new CartLineCheck(line.id(), 0, CartLineState.UNREACHABLE)
                            : checkLine(line, answer.product()));
        }
        return checks;
    }

    /**
     * Asks one lending station about each product the cart holds of it, stopping at the first that
     * cannot be answered: what was not asked about is then not known.
     */
    private List<ProductAnswer> askProducts(int stationId, UUID lender, List<LendingCartLine> lines, LendingCart cart) {
        var answers = new ArrayList<ProductAnswer>();
        for (int inventoryId :
                lines.stream().map(LendingCartLine::inventoryId).distinct().toList()) {
            var key = new ProductKey(lender, inventoryId);
            try {
                var product = catalog.findProduct(stationId, lender, inventoryId, cart.dateFrom(), cart.dateTo())
                        .product();
                answers.add(new ProductAnswer(key, product));
            } catch (RefusalResponse refusal) {
                if (!GONE.contains(refusal.refusal())) return answers;
                answers.add(new ProductAnswer(key, null));
            } catch (RuntimeException e) {
                log.debug("Could not check the cart's product {} at {}", inventoryId, lender, e);
                return answers;
            }
        }
        return answers;
    }

    /** The refusals by which a lending station shows a product is not on offer any more. */
    private static final Set<Object> GONE =
            Set.of(FederationRefusal.LENDING_PRODUCT_NOT_OFFERED, FederationRefusal.LENDING_PARTNER_DOES_NOT_LEND);

    /**
     * Sends the cart: one request per lending station, each with all its lines, the cart's days and
     * occasion, and the note as the first message. A request once opened takes its lines out of the
     * cart even where the note could not follow it; lines of a station that refused or could not be
     * reached stay. One sending at a time: a second one while the first runs is refused. Once nothing
     * is left the cart goes, days and note included.
     *
     * @throws RefusalResponse when the cart is empty, being sent, has no first day, or a line is not
     *                         free in full
     */
    public CheckoutResult checkout(int stationId, int memberId, String memberName) {
        if (!carts.claimSending(stationId)) {
            if (carts.lines(stationId).isEmpty()) throw FederationRefusal.LENDING_CART_EMPTY.raise();
            throw FederationRefusal.LENDING_CART_BEING_SENT.raise();
        }
        try {
            var lines = carts.lines(stationId);
            if (lines.isEmpty()) throw FederationRefusal.LENDING_CART_EMPTY.raise();
            return send(stationId, memberId, memberName, lines);
        } finally {
            carts.releaseSending(stationId);
        }
    }

    private CheckoutResult send(int stationId, int memberId, String memberName, List<LendingCartLine> lines) {
        LendingCart cart = cart(stationId);
        LocalDate dateFrom = cart.dateFrom();
        if (dateFrom == null) throw FederationRefusal.LENDING_FIRST_DAY_MISSING.raise();
        if (check(stationId, cart, lines).stream().anyMatch(check -> check.state() != CartLineState.FREE)) {
            throw FederationRefusal.LENDING_CART_NOT_FREE.raise();
        }
        LocalDate dateTo = Objects.requireNonNullElse(cart.dateTo(), dateFrom);
        var sent = new ArrayList<LendingRequest>();
        var failed = new ArrayList<String>();
        var notesMissing = new ArrayList<String>();
        for (var entry : byStation(lines).entrySet()) {
            LendingRequest request;
            try {
                request = lendingService.createRequest(
                        stationId,
                        entry.getKey(),
                        dateFrom,
                        dateTo,
                        memberId,
                        null,
                        null,
                        cart.occasion(),
                        entry.getValue().stream()
                                .map(LendingCartService::requestLine)
                                .toList());
            } catch (RuntimeException e) {
                log.info("Cart of station {} could not be sent to {}", stationId, entry.getKey(), e);
                failed.add(lendingService.stationName(entry.getKey(), stationId));
                continue;
            }
            carts.removeLines(
                    stationId,
                    entry.getValue().stream().map(LendingCartLine::id).toList());
            sent.add(request);
            if (cart.note().isBlank()) continue;
            try {
                lendingService.sendMessage(request.id(), stationId, memberId, memberName, cart.note());
            } catch (RuntimeException e) {
                log.warn("The cart's note did not reach request {}", request.id(), e);
                notesMissing.add(lendingService.stationName(entry.getKey(), stationId));
            }
        }
        carts.clearIfEmpty(stationId);
        return new CheckoutResult(sent, failed, notesMissing);
    }

    private static Map<UUID, List<LendingCartLine>> byStation(List<LendingCartLine> lines) {
        Map<UUID, List<LendingCartLine>> byStation = new LinkedHashMap<>();
        for (var line : lines)
            byStation
                    .computeIfAbsent(line.lendingStationUid(), uid -> new ArrayList<>())
                    .add(line);
        return byStation;
    }

    /**
     * Puts the lines of an answered request of this station back in the cart, adding to what is there.
     * The days are not copied. A line whose product, kind or piece the lending station no longer offers,
     * or that was written before its ids were kept, is left out and named.
     *
     * @throws RefusalResponse when the request is not this station's or was never answered
     */
    public BorrowAgainResult borrowAgain(int stationId, int requestId) {
        LendingRequest request = requests.findRequestById(requestId)
                .orElseThrow(FederationRefusal.LENDING_REQUEST_NOT_HERE_OR_NOT_YOURS::raise);
        UUID own = stationRepository.requireUid(stationId);
        if (!request.requestingStationUid().equals(own)) {
            throw FederationRefusal.LENDING_REQUEST_NOT_HERE_OR_NOT_YOURS.raise();
        }
        if (request.status() == LendingStatus.REQUESTED) throw FederationRefusal.LENDING_NOT_BORROWED_AGAIN.raise();

        UUID lender = request.owningStationUid();
        Map<Integer, Optional<RemoteProduct>> products = new HashMap<>();
        int added = 0;
        var skipped = new ArrayList<String>();
        for (OfferedLine line : requests.findOfferedLines(requestId)) {
            if (line.inventoryId() == null) {
                skipped.add(line.label());
                continue;
            }
            var product = products.computeIfAbsent(line.inventoryId(), id -> productNow(stationId, lender, id));
            var again = product.flatMap(found -> againLine(lender, found, line));
            if (again.isEmpty()) {
                skipped.add(line.label());
                continue;
            }
            add(stationId, again.get());
            added++;
        }
        return new BorrowAgainResult(added, skipped);
    }

    private Optional<RemoteProduct> productNow(int stationId, UUID lender, int inventoryId) {
        try {
            return Optional.of(catalog.findProduct(stationId, lender, inventoryId, null, null)
                    .product());
        } catch (RuntimeException e) {
            log.debug("Product {} of {} not offered for borrowing again", inventoryId, lender, e);
            return Optional.empty();
        }
    }

    /**
     * A line of an earlier request as a cart line, where the product still offers its kind or piece.
     */
    static Optional<NewCartLine> againLine(UUID lender, RemoteProduct product, OfferedLine line) {
        Integer itemId = line.itemId();
        if (itemId != null) {
            return product.piece(itemId)
                    .map(piece -> new NewCartLine(
                            lender,
                            product.inventoryId(),
                            null,
                            piece.itemId(),
                            1,
                            product.name(),
                            piece.name(),
                            piece.pictureKey()));
        }
        return product.kind(line.artId())
                .map(kind -> new NewCartLine(
                        lender,
                        product.inventoryId(),
                        kind.artId(),
                        null,
                        line.quantity(),
                        product.name(),
                        kind.name(),
                        kind.pictureKey() != null ? kind.pictureKey() : product.pictureKey()));
    }

    /**
     * How much of a line is free in a product's answer: a piece is free or not, a count is the free
     * count of its kind, or of the whole inventory for a line without kind.
     */
    static CartLineCheck checkLine(LendingCartLine line, @Nullable RemoteProduct product) {
        if (product == null) return new CartLineCheck(line.id(), 0, CartLineState.GONE);
        Integer itemId = line.itemId();
        Integer free = itemId != null
                ? product.piece(itemId).map(piece -> piece.free() ? 1 : 0).orElse(null)
                : product.kind(line.artId()).map(RemoteKind::free).orElse(null);
        if (free == null) return new CartLineCheck(line.id(), 0, CartLineState.GONE);
        return new CartLineCheck(line.id(), free, free >= line.quantity() ? CartLineState.FREE : CartLineState.SHORT);
    }

    private static RequestLine requestLine(LendingCartLine line) {
        return new RequestLine(line.inventoryId(), line.itemId(), line.artId(), line.quantity(), null);
    }

    private static @Nullable String pictureKey(@Nullable String key) {
        return key == null || key.length() > MAX_NAME ? null : key;
    }

    private static String cut(@Nullable String name) {
        String text = Objects.requireNonNullElse(name, "").strip();
        return text.length() > MAX_NAME ? text.substring(0, MAX_NAME) : text;
    }

    private record ProductKey(UUID stationUid, int inventoryId) {}

    /** A lending station's answer about one product, {@code null} where it no longer offers it. */
    private record ProductAnswer(ProductKey key, @Nullable RemoteProduct product) {}

    /**
     * Something to put in the cart, in the lending station's ids, with the names it was shown under.
     *
     * @param artId  the kind counted, or {@code null} for the whole inventory or a piece
     * @param itemId the single piece, or {@code null} for a count
     */
    public record NewCartLine(
            UUID lendingStationUid,
            int inventoryId,
            @Nullable Integer artId,
            @Nullable Integer itemId,
            int quantity,
            @Nullable String inventoryName,
            @Nullable String lineName,
            @Nullable String pictureKey) {}

    /** How a cart line stands at its lending station. */
    public enum CartLineState {
        /** Free in full on the cart's days. */
        FREE,
        /** Free, but fewer than the line asks for. */
        SHORT,
        /** No longer offered: the product, its kind or the piece is gone. */
        GONE,
        /** The lending station could not be asked. */
        UNREACHABLE
    }

    /**
     * What is free of one cart line.
     *
     * @param lineId the line
     * @param free   how many are free, 0 where the line is gone or could not be asked about
     * @param state  how the line stands
     */
    public record CartLineCheck(int lineId, int free, CartLineState state) {}

    /**
     * What sending the cart did.
     *
     * @param sent         the requests opened, one per lending station
     * @param failed       the lending stations whose lines stayed in the cart, by name
     * @param notesMissing the lending stations whose request was opened but did not get the note
     */
    public record CheckoutResult(List<LendingRequest> sent, List<String> failed, List<String> notesMissing) {}

    /**
     * What borrowing again put in the cart.
     *
     * @param added   how many lines were put in or added to
     * @param skipped the lines left out because they are no longer offered, by name
     */
    public record BorrowAgainResult(int added, List<String> skipped) {}
}
