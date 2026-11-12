/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.station.entity;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StationModuleTest {

    /** The five parts of the inventory name it as their parent, every other module stands alone. */
    @Test
    void inventoryHasFiveParts() {
        Set<StationModule> parts = EnumSet.noneOf(StationModule.class);
        for (StationModule module : StationModule.values()) {
            module.parent().ifPresent(parent -> {
                assertEquals(StationModule.INVENTORY, parent);
                parts.add(module);
            });
        }
        assertEquals(
                EnumSet.of(
                        StationModule.INVENTORY_MOVEMENTS,
                        StationModule.INVENTORY_LENDING,
                        StationModule.INVENTORY_PROCUREMENT,
                        StationModule.INVENTORY_SELF_CHECKS,
                        StationModule.INVENTORY_CHECKS),
                parts);
        assertEquals(Optional.empty(), StationModule.INVENTORY.parent());
    }

    /** Expanding a set adds the parts of every module in it and leaves a part alone. */
    @Test
    void withChildrenAddsTheParts() {
        Set<StationModule> expanded = StationModule.withChildren(List.of(StationModule.INVENTORY, StationModule.NEWS));
        assertTrue(expanded.contains(StationModule.INVENTORY_LENDING));
        assertTrue(expanded.contains(StationModule.NEWS));
        assertEquals(7, expanded.size());

        assertEquals(
                Set.of(StationModule.INVENTORY_CHECKS),
                StationModule.withChildren(Set.of(StationModule.INVENTORY_CHECKS)));
        assertTrue(StationModule.withChildren(List.of()).isEmpty());
    }

    /** A part belongs to its module and is off whenever the module is. */
    @Test
    void aPartFollowsItsModule() {
        assertTrue(StationModule.INVENTORY_MOVEMENTS.belongsTo(StationModule.INVENTORY));
        assertTrue(StationModule.INVENTORY.belongsTo(StationModule.INVENTORY));
        assertFalse(StationModule.INVENTORY.belongsTo(StationModule.INVENTORY_MOVEMENTS));
        assertFalse(StationModule.NEWS.belongsTo(StationModule.INVENTORY));

        assertTrue(StationModule.INVENTORY_MOVEMENTS.isOffIn(Set.of(StationModule.INVENTORY)));
        assertFalse(StationModule.INVENTORY.isOffIn(Set.of(StationModule.INVENTORY_MOVEMENTS)));
        assertFalse(StationModule.EVENTS.isOffIn(Set.of()));
    }
}
