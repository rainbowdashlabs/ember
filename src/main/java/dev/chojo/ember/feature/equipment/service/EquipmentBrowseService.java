/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.equipment.service;

import dev.chojo.ember.feature.equipment.repository.EquipmentRecommendationRepository;
import dev.chojo.ember.feature.federation.service.LendingService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Borrowing as a browser rather than a search.
 *
 * <p>Pick a thing, get shown what goes with it, keep adding, ask for the lot in one go. Nothing is
 * taken and nothing is held while the list is assembled: borrowing takes nothing from anybody until
 * the other station agrees, so a hold would need a timeout and a cleanup and would protect nothing.
 *
 * <p>What that costs is one honest step at the end. Before the list is sent, what it asks for is
 * counted again, and anything that has moved is shown rather than quietly dropped: two of the four
 * blue ones are no longer free, ask anyway or adjust.
 */
@Singleton
public class EquipmentBrowseService {

    private static final int RECOMMENDATION_LIMIT = 12;

    private final EquipmentRecommendationRepository recommendationRepository;
    private final LendingService lendingService;

    @Inject
    public EquipmentBrowseService(
            EquipmentRecommendationRepository recommendationRepository, LendingService lendingService) {
        this.recommendationRepository = recommendationRepository;
        this.lendingService = lendingService;
    }

    /**
     * What goes with a piece somebody has just picked.
     *
     * <p>Everything carrying a word that piece carries comes first, across the inventories, then the
     * other pieces filed beside it. Words win where both apply, because a word is what somebody wrote
     * down on purpose and a shelf is where a thing happened to be put.
     *
     * @param stationId the station whose gear it is
     * @param itemId    the piece that was picked
     * @return the recommendations
     */
    public List<EquipmentRecommendationRepository.Recommendation> recommendationsFor(int stationId, int itemId) {
        return recommendationRepository.forItem(stationId, itemId, RECOMMENDATION_LIMIT);
    }

    /**
     * Counts a collected list again against what the partners have free now.
     *
     * <p>The answer names what each line would still find and whether that differs from what the
     * collector saw, so the send screen can say what changed instead of silently sending less.
     *
     * @param stationId the station collecting
     * @param from      the first day the gear is needed
     * @param to        the last day
     * @param lines     the list as it stands
     * @return one answer per line, in the order given
     */
    public List<LineCheck> recheck(int stationId, LocalDate from, LocalDate to, List<CollectedLine> lines) {
        var offers =
                lendingService.findAvailableInventory(stationId, null, from, to).entries();
        var checked = new ArrayList<LineCheck>(lines.size());
        for (var line : lines) {
            int free = offers.stream()
                    .filter(entry -> entry.stationId() == line.owningStationId())
                    .filter(entry -> entry.inventoryId() == line.inventoryId())
                    .filter(entry -> line.artId() == null || Objects.equals(entry.artId(), line.artId()))
                    .mapToInt(LendingService.AvailableInventoryEntry::availableCount)
                    .sum();
            checked.add(new LineCheck(line, Math.min(line.quantity(), free), free < line.quantity()));
        }
        return checked;
    }

    /**
     * How many requests a collected list turns into.
     *
     * <p>A request has a single owning station, so a list spanning three stations is three requests.
     * The split is visible while collecting and the send button says how many it will make, because a
     * list that quietly became three letters is a list nobody can follow up.
     *
     * @param lines the list as it stands
     * @return how many requests will go out
     */
    public int requestCount(List<CollectedLine> lines) {
        return (int)
                lines.stream().map(CollectedLine::owningStationId).distinct().count();
    }

    /**
     * One line of a collected list, as it stands before anything has been sent.
     *
     * @param owningStationId the station the gear belongs to
     * @param inventoryId     the inventory it is filed in
     * @param artId           the kind asked for, or {@code null} for a count out of the whole inventory
     * @param quantity        how many pieces
     * @param needId          the line of an appointment's needs this would fill, or {@code null}
     */
    public record CollectedLine(int owningStationId, int inventoryId, Integer artId, int quantity, Integer needId) {}

    /**
     * What one collected line would still find.
     *
     * @param available how many pieces it would get now
     * @param changed   whether that is fewer than it asked for
     */
    public record LineCheck(CollectedLine line, int available, boolean changed) {}
}
