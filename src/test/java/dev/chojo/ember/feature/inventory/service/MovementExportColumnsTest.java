/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.service;

import dev.chojo.ember.feature.inventory.entity.ItemMovement;
import dev.chojo.ember.feature.inventory.entity.MovementPurpose;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * How many columns each inventory takes on the movement sheet: one per movement of it, for the member
 * with the most.
 */
class MovementExportColumnsTest {

    private static final int SHIRTS = 1;
    private static final int TROUSERS = 2;

    @Test
    void oneMovementEachTakesOneColumn() {
        var columns = columnsFor(List.of(List.of(movementOf(SHIRTS), movementOf(TROUSERS))));

        assertEquals(Map.of(SHIRTS, 1, TROUSERS, 1), columns);
    }

    @Test
    void twoShirtsOfOneMemberTakeTwoShirtColumns() {
        var columns = columnsFor(List.of(
                List.of(movementOf(SHIRTS), movementOf(SHIRTS), movementOf(TROUSERS)), List.of(movementOf(SHIRTS))));

        assertEquals(Map.of(SHIRTS, 2, TROUSERS, 1), columns);
    }

    @Test
    void shirtsOfDifferentMembersDoNotAddUp() {
        var columns = columnsFor(List.of(List.of(movementOf(SHIRTS)), List.of(movementOf(SHIRTS))));

        assertEquals(Map.of(SHIRTS, 1), columns);
    }

    private static Map<Integer, Integer> columnsFor(List<List<ItemMovement>> movementsByMember) {
        var inventories = new LinkedHashSet<Integer>();
        movementsByMember.forEach(movements -> movements.forEach(movement -> inventories.add(movement.inventoryId())));
        return MovementExportService.columnsPerInventory(inventories, movementsByMember);
    }

    private static ItemMovement movementOf(int inventoryId) {
        return new ItemMovement(
                1,
                1,
                MovementPurpose.EXCHANGE,
                null,
                null,
                1,
                null,
                null,
                inventoryId,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                false);
    }
}
