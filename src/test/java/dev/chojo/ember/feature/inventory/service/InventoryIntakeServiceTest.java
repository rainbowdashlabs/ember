/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.service;

import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.inventory.entity.InventoryIntakeRow;
import dev.chojo.ember.feature.inventory.entity.InventoryItemMetadata;
import dev.chojo.ember.feature.inventory.entity.InventoryType;
import dev.chojo.ember.feature.inventory.entity.ItemOwner;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import io.javalin.http.BadRequestResponse;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Writing down an inventory the station already owns.
 *
 * <p>The point of the whole thing: fifty jackets that have hung in the lockers for years are entered
 * from the member list rather than one window at a time. What has to hold is that a refusal names
 * the line it is on and that nothing is written when one line cannot be.
 */
class InventoryIntakeServiceTest extends RepositoryTestBase {

    private static InventoryIntakeService intake;
    private static Station station;
    private static Account account;
    private static StationMember member;
    private static int inventoryId;
    private static int sizeId;

    @BeforeAll
    static void setup() {
        intake = new InventoryIntakeService(inventoryService, inventoryRepo, stationMemberRepo);
        station = stationRepo.create("IntakeStation");
        account = accountRepo.create("intake@test.com", "Ina", "Take");
        member = stationMemberRepo.create(station.id(), account.id());
        inventoryId = inventoryRepo
                .create(station.id(), "Einsatzkleidung", InventoryType.MIXED, true)
                .id();
        inventoryRepo.createSize(inventoryId, "152", 0, null);
        sizeId = inventoryRepo.findSizes(inventoryId).getFirst().id();
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
        accountRepo.delete(account.id());
    }

    private static InventoryIntakeRow row(Integer memberId, String number, Integer size) {
        return new InventoryIntakeRow(memberId, number, size, ItemOwner.STATION, InventoryItemMetadata.empty());
    }

    @Test
    void everyLineThatNamesSomethingBecomesAPieceInSomebodysHands() {
        var written = intake.takeStock(
                inventoryId, station.id(), "Jacke", List.of(row(member.id(), "J-1", sizeId), row(null, "J-2", sizeId)));

        assertEquals(2, written.size());
        assertEquals(member.id(), written.getFirst().assignedTo(), "the piece is in the hands of its line's member");
        assertNull(written.get(1).assignedTo(), "a line without a member leaves the piece in the store");
        assertTrue(written.stream().allMatch(item -> "Jacke".equals(item.name())));
    }

    /** A table opens with a row per member, and the ones nobody was given anything are left alone. */
    @Test
    void aLineThatNamesNothingIsPassedOver() {
        var written = intake.takeStock(
                inventoryId,
                station.id(),
                "Hose",
                List.of(row(member.id(), null, null), row(member.id(), "H-1", sizeId)));

        assertEquals(1, written.size());
        assertEquals("H-1", written.getFirst().internalId());
    }

    @Test
    void aRefusalNamesTheLineItIsOn() {
        var wrongSize = assertThrows(
                BadRequestResponse.class,
                () -> intake.takeStock(inventoryId, station.id(), "Jacke", List.of(row(member.id(), null, 999_999))));
        assertTrue(wrongSize.getMessage().contains("Line 1"), wrongSize.getMessage());

        var stranger = assertThrows(
                BadRequestResponse.class,
                () -> intake.takeStock(
                        inventoryId,
                        station.id(),
                        "Jacke",
                        List.of(row(member.id(), "S-1", sizeId), row(999_999, "S-2", sizeId))));
        assertTrue(stranger.getMessage().contains("Line 2"), stranger.getMessage());
    }

    /** Nothing is written when one line cannot be: half a stock-taking is worse than none. */
    @Test
    void oneImpossibleLineLeavesTheWholeListUnwritten() {
        int before = inventoryRepo.findStock(inventoryId).size();

        assertThrows(
                BadRequestResponse.class,
                () -> intake.takeStock(
                        inventoryId,
                        station.id(),
                        "Helm",
                        List.of(row(member.id(), "K-1", sizeId), row(member.id(), null, 999_999))));

        assertEquals(before, inventoryRepo.findStock(inventoryId).size());
    }

    @Test
    void aNumberIsNotHandedOutTwice() {
        intake.takeStock(inventoryId, station.id(), "Stiefel", List.of(row(member.id(), "ST-1", sizeId)));

        var again = assertThrows(
                BadRequestResponse.class,
                () -> intake.takeStock(inventoryId, station.id(), "Stiefel", List.of(row(null, "ST-1", sizeId))));
        assertTrue(again.getMessage().contains("already"), again.getMessage());

        var twiceInOne = assertThrows(
                BadRequestResponse.class,
                () -> intake.takeStock(
                        inventoryId,
                        station.id(),
                        "Stiefel",
                        List.of(row(null, "ST-2", sizeId), row(null, "ST-2", sizeId))));
        assertTrue(twiceInOne.getMessage().contains("twice"), twiceInOne.getMessage());
    }
}
