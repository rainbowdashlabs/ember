/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.repository;

import dev.chojo.ember.feature.inventory.entity.Inventory;
import dev.chojo.ember.feature.inventory.entity.InventoryType;
import dev.chojo.ember.feature.inventory.entity.ItemOwner;
import dev.chojo.ember.feature.inventory.entity.MovementParty;
import dev.chojo.ember.feature.inventory.entity.MovementPurpose;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pointing a binding at a flow, alone and with several saves of the same binding arriving together. */
class MovementFlowRepositoryTest extends RepositoryTestBase {
    private static Station station;
    private static Inventory inventory;

    @BeforeAll
    static void setup() {
        station = stationRepo.create("Bindungswache");
        inventory = inventoryRepo.create(station.id(), "Gemeindematerial", InventoryType.EXTERNAL, false);
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
    }

    /** A second binding of the same inventory replaces the first rather than standing beside it. */
    @Test
    void bindReplacesTheFlowOfAnInventory() {
        int first = movementFlowRepo
                .createFlow(station.id(), "Erste", MovementPurpose.RETURN)
                .id();
        int second = movementFlowRepo
                .createFlow(station.id(), "Zweite", MovementPurpose.RETURN)
                .id();

        bindInventory(first);
        bindInventory(second);

        assertEquals(Optional.of(second), boundToInventory());
    }

    /** The station-wide binding, which carries no inventory, is replaced the same way. */
    @Test
    void bindReplacesTheStationWideFlow() {
        int first = movementFlowRepo
                .createFlow(station.id(), "Weit eins", MovementPurpose.ISSUE)
                .id();
        int second = movementFlowRepo
                .createFlow(station.id(), "Weit zwei", MovementPurpose.ISSUE)
                .id();

        movementFlowRepo.bind(station.id(), null, ItemOwner.STATION, MovementPurpose.ISSUE, MovementParty.STORE, first);
        movementFlowRepo.bind(
                station.id(), null, ItemOwner.STATION, MovementPurpose.ISSUE, MovementParty.STORE, second);

        assertEquals(
                Optional.of(second),
                movementFlowRepo.findBoundFlow(
                        station.id(), null, ItemOwner.STATION, MovementPurpose.ISSUE, MovementParty.STORE));
    }

    /** Saves of the same binding arriving together all succeed, and one of their flows is what stays bound. */
    @Test
    void bindSurvivesSavesArrivingTogether() throws Exception {
        var flows = IntStream.range(0, 8)
                .map(index -> movementFlowRepo
                        .createFlow(station.id(), "Gleichzeitig " + index, MovementPurpose.RETURN)
                        .id())
                .boxed()
                .toList();
        var start = new CountDownLatch(1);
        var saves = flows.stream()
                .map(flowId -> CompletableFuture.runAsync(() -> {
                    try {
                        start.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(e);
                    }
                    bindInventory(flowId);
                }))
                .toList();
        start.countDown();
        CompletableFuture.allOf(saves.toArray(CompletableFuture[]::new)).get();

        assertTrue(flows.contains(boundToInventory().orElseThrow()));
    }

    private static void bindInventory(int flowId) {
        movementFlowRepo.bind(
                station.id(), inventory.id(), ItemOwner.CLUSTER, MovementPurpose.RETURN, MovementParty.STORE, flowId);
    }

    private static Optional<Integer> boundToInventory() {
        return movementFlowRepo.findBoundFlow(
                station.id(), inventory.id(), ItemOwner.CLUSTER, MovementPurpose.RETURN, MovementParty.STORE);
    }
}
