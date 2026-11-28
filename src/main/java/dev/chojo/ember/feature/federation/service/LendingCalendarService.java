/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.service;

import dev.chojo.ember.api.refusal.FederationRefusal;
import dev.chojo.ember.feature.federation.entity.InventoryBlock;
import dev.chojo.ember.feature.federation.entity.LendingRequest;
import dev.chojo.ember.feature.federation.entity.LendingStatus;
import dev.chojo.ember.feature.federation.service.LendingOfferService.OfferProduct;
import dev.chojo.ember.feature.inventory.repository.InventoryRepository;
import dev.chojo.ember.feature.station.repository.StationRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The lending station's calendar over a window: its products with their kinds, the requests for its
 * gear as bars on the rows they draw from, and the block periods. Whether a day asks for more than
 * is on offer is left to the reader of the rows, who has the counts and the bars side by side.
 */
@Singleton
public class LendingCalendarService {
    /** The longest window one look reads, a quarter of a year. */
    static final int MAX_DAYS = 92;

    private final LendingService lendingService;
    private final LendingOfferService offer;
    private final InventoryRepository inventoryRepository;
    private final StationRepository stationRepository;

    @Inject
    public LendingCalendarService(
            LendingService lendingService,
            LendingOfferService offer,
            InventoryRepository inventoryRepository,
            StationRepository stationRepository) {
        this.lendingService = lendingService;
        this.offer = offer;
        this.inventoryRepository = inventoryRepository;
        this.stationRepository = stationRepository;
    }

    /**
     * The calendar of a station from one day to another.
     *
     * @param today the station's day, which decides what is overdue
     * @throws dev.chojo.ember.api.refusal.RefusalResponse when the window runs backwards or longer than a quarter
     */
    public LendingCalendar calendar(int stationId, LocalDate from, LocalDate to, LocalDate today) {
        if (to.isBefore(from)) throw FederationRefusal.LENDING_WINDOW_BACKWARDS.raise();
        if (from.plusDays(MAX_DAYS - 1).isBefore(to)) throw FederationRefusal.LENDING_CALENDAR_TOO_LONG.raise();
        UUID own = stationRepository.requireUid(stationId);
        var products = offer.offer(stationId, null);
        var rows = products.stream().map(LendingCalendarService::row).toList();
        var pieces = new Pieces(products);
        Map<UUID, String> partners = new HashMap<>();
        var bars = new ArrayList<CalendarBar>();
        for (LendingRequest request : lendingService.findRequestsByStation(stationId)) {
            if (!request.owningStationUid().equals(own) || request.status() == LendingStatus.DECLINED) continue;
            LocalDate last = request.requestedDateTo();
            if (last.isBefore(from) || request.requestedDateFrom().isAfter(to)) continue;
            String partner = partners.computeIfAbsent(
                    request.requestingStationUid(), uid -> lendingService.stationName(uid, stationId));
            boolean overdue = LendingReturnService.isOverdue(request, today);
            for (var line : lendingService.findRequestItems(request.id())) {
                Integer inventoryId = line.inventoryId();
                if (inventoryId == null) continue;
                Integer artId = line.artId();
                bars.add(new CalendarBar(
                        request.id(),
                        inventoryId,
                        artId != null ? artId : pieces.artOf(line.itemId()),
                        partner,
                        line.quantity(),
                        request.status(),
                        overdue,
                        request.requestedDateFrom(),
                        last));
            }
        }
        var blocks = lendingService.blocksBetween(stationId, from, to).stream()
                .map(block -> block(block, pieces))
                .toList();
        return new LendingCalendar(rows, bars, blocks);
    }

    private CalendarBlock block(InventoryBlock block, Pieces pieces) {
        Integer itemId = block.itemId();
        Integer inventoryId = block.inventoryId();
        Integer artId = null;
        String pieceName = null;
        if (itemId != null) {
            var piece = pieces.find(itemId);
            if (piece != null) {
                inventoryId = piece.inventoryId();
                artId = piece.artId();
                pieceName = piece.name();
            }
        }
        return new CalendarBlock(
                block.id(), inventoryId, artId, itemId, pieceName, block.blockFrom(), block.blockTo(), block.reason());
    }

    /**
     * The pieces a calendar names by id, read from the offer where it lists them and from the
     * inventory once each otherwise, so a request for many pieces asks once per piece at most.
     */
    private final class Pieces {
        private final Map<Integer, Optional<PieceRef>> known = new HashMap<>();

        Pieces(List<OfferProduct> products) {
            for (var product : products) {
                for (var piece : product.pieces()) {
                    known.put(
                            piece.itemId(),
                            Optional.of(new PieceRef(product.inventoryId(), piece.artId(), piece.name())));
                }
            }
        }

        @Nullable
        PieceRef find(int itemId) {
            return known.computeIfAbsent(itemId, id -> inventoryRepository
                            .findItemById(id)
                            .map(item -> new PieceRef(item.inventoryId(), item.artId(), item.name())))
                    .orElse(null);
        }

        @Nullable
        Integer artOf(@Nullable Integer itemId) {
            if (itemId == null) return null;
            var piece = find(itemId);
            return piece == null ? null : piece.artId();
        }
    }

    private record PieceRef(int inventoryId, @Nullable Integer artId, String name) {}

    private static CalendarRow row(OfferProduct product) {
        return new CalendarRow(
                product.inventoryId(),
                product.name(),
                product.pieceCount(),
                product.kinds().stream()
                        .map(kind -> new CalendarKind(kind.artId(), kind.name(), kind.pieceCount()))
                        .toList());
    }

    /**
     * What the calendar shows over its window.
     *
     * @param rows   the station's products, in its order
     * @param bars   one bar per line of a request for the station's gear
     * @param blocks the block periods touching the window
     */
    public record LendingCalendar(List<CalendarRow> rows, List<CalendarBar> bars, List<CalendarBlock> blocks) {}

    /**
     * One product of the calendar.
     *
     * @param pieceCount how many pieces of it the station holds and may lend
     */
    public record CalendarRow(int inventoryId, String name, int pieceCount, List<CalendarKind> kinds) {}

    public record CalendarKind(int artId, String name, int pieceCount) {}

    /**
     * One line of a request as a bar.
     *
     * @param artId   the kind the line counts or its piece belongs to, or {@code null} for the whole product
     * @param partner the asking station, by name
     * @param overdue whether the gear is out past its last day
     * @param to      the last day, the first where the request names none
     */
    public record CalendarBar(
            int requestId,
            int inventoryId,
            @Nullable Integer artId,
            String partner,
            int quantity,
            LendingStatus status,
            boolean overdue,
            LocalDate from,
            LocalDate to) {}

    /**
     * One block period.
     *
     * @param inventoryId the product it covers, or {@code null} for the whole station or a piece that is gone
     * @param artId       the kind of the single piece it covers, or {@code null}
     * @param itemId      the single piece it covers, or {@code null}
     * @param pieceName   what that piece is called, or {@code null}
     */
    public record CalendarBlock(
            int id,
            @Nullable Integer inventoryId,
            @Nullable Integer artId,
            @Nullable Integer itemId,
            @Nullable String pieceName,
            LocalDate from,
            LocalDate to,
            String reason) {}
}
