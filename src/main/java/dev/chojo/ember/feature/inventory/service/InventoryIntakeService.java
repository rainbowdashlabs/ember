/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.service;

import dev.chojo.ember.feature.inventory.entity.Inventory;
import dev.chojo.ember.feature.inventory.entity.InventoryIntakeRow;
import dev.chojo.ember.feature.inventory.entity.InventoryItem;
import dev.chojo.ember.feature.inventory.entity.InventoryItemMetadata;
import dev.chojo.ember.feature.inventory.entity.InventorySize;
import dev.chojo.ember.feature.inventory.entity.InventoryType;
import dev.chojo.ember.feature.inventory.entity.ItemOwner;
import dev.chojo.ember.feature.inventory.repository.InventoryRepository;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import io.javalin.http.BadRequestResponse;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Writing down an inventory a station already owns, in one pass.
 *
 * <p>Fifty jackets hang in the lockers, each belongs to somebody, and entering them one at a time is
 * the slowest hour of setting Ember up: a piece is made, a size is chosen, a number is typed, the
 * window closes, the piece is assigned, a member is searched for. This takes the other road, from
 * the member list to the pieces: one line per member, filled in and saved once.
 *
 * <p>Every line is read before a single piece is written. A size that belongs to another inventory
 * or a number that is already taken stops the whole thing with the line it is on, because a
 * stock-taking half entered is worse than one refused: nobody can tell which half.
 */
@Singleton
public class InventoryIntakeService {
    private static final Logger log = LoggerFactory.getLogger(InventoryIntakeService.class);

    private final InventoryService inventoryService;
    private final InventoryRepository inventoryRepository;
    private final StationMemberRepository memberRepository;

    @Inject
    public InventoryIntakeService(
            InventoryService inventoryService,
            InventoryRepository inventoryRepository,
            StationMemberRepository memberRepository) {
        this.inventoryService = inventoryService;
        this.inventoryRepository = inventoryRepository;
        this.memberRepository = memberRepository;
    }

    /**
     * Writes every line that names a piece, and hands each piece to the member on its line.
     *
     * @param inventoryId the inventory the pieces belong to
     * @param stationId   the station the inventory belongs to, which is who the members must be from
     * @param name        what a piece of this inventory is called
     * @param rows        the lines of the table, in the order they were shown
     * @return the pieces that were written, in the same order
     */
    public List<InventoryItem> takeStock(int inventoryId, int stationId, String name, List<InventoryIntakeRow> rows) {
        var wanted = rows.stream().filter(InventoryIntakeRow::namesAPiece).toList();
        requireEachLineIsPossible(inventoryId, stationId, wanted);
        ItemOwner theUsualOwner = ownerOfChoice(inventoryId);

        var written = new ArrayList<InventoryItem>();
        for (InventoryIntakeRow row : wanted) {
            InventoryItem item = inventoryService.createItem(
                    inventoryId,
                    blankToNull(row.internalId()),
                    name,
                    row.sizeId(),
                    row.metadata() != null ? row.metadata() : InventoryItemMetadata.empty(),
                    row.ownerKind() != null ? row.ownerKind() : theUsualOwner,
                    null);
            written.add(
                    row.memberId() != null
                            ? inventoryService
                                    .assignItem(item.id(), row.memberId(), nameOf(row.memberId()))
                                    .orElse(item)
                            : item);
        }
        log.info("Took stock of {} piece(s) in inventory {} of station {}", written.size(), inventoryId, stationId);
        return written;
    }

    /**
     * Reads every line before anything is written.
     *
     * <p>The line number is part of every refusal. A table of fifty rows answered with "that size
     * does not exist" is a search, and the reader has the answer in front of them either way.
     */
    private void requireEachLineIsPossible(int inventoryId, int stationId, List<InventoryIntakeRow> rows) {
        var sizes = inventoryRepository.findSizes(inventoryId).stream()
                .map(InventorySize::id)
                .collect(Collectors.toSet());
        var members = memberRepository.findByStation(stationId).stream()
                .map(StationMember::id)
                .collect(Collectors.toSet());
        Set<String> numbers = new HashSet<>();

        for (int line = 0; line < rows.size(); line++) {
            InventoryIntakeRow row = rows.get(line);
            int shown = line + 1;
            if (row.sizeId() != null && !sizes.contains(row.sizeId())) {
                throw refusal(shown, "the size does not belong to this inventory");
            }
            if (row.memberId() != null && !members.contains(row.memberId())) {
                throw refusal(shown, "the member does not belong to this station");
            }
            String number = blankToNull(row.internalId());
            if (number == null) continue;
            if (!numbers.add(number)) {
                throw refusal(shown, "the number %s appears twice in this list".formatted(number));
            }
            if (inventoryRepository.findByInternalId(stationId, number).isPresent()) {
                throw refusal(shown, "the number %s is already on another piece".formatted(number));
            }
        }
    }

    /**
     * Who owns a piece when the line does not say.
     *
     * <p>An inventory that only holds the association's gear cannot hold the station's, so taking
     * the station as the silent default there refuses every line of the list for a reason nobody
     * chose. Where an inventory holds both, the station's own is the usual case.
     */
    private ItemOwner ownerOfChoice(int inventoryId) {
        return inventoryRepository
                                .findById(inventoryId)
                                .map(Inventory::inventoryType)
                                .orElse(InventoryType.INTERNAL)
                        == InventoryType.EXTERNAL
                ? ItemOwner.CLUSTER
                : ItemOwner.STATION;
    }

    private String nameOf(int memberId) {
        return memberRepository
                .findById(memberId)
                .map(StationMember::displayName)
                .orElse("");
    }

    private static BadRequestResponse refusal(int line, String why) {
        return new BadRequestResponse("Line %d: %s".formatted(line, why));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
