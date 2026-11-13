/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.api.auth;

import dev.chojo.ember.feature.station.entity.StationModule;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Which module a permission belongs to, and what it grants while that module is off. */
class StationPermissionModuleTest {
    private static final Set<String> STATION_WIDE_PREFIXES =
            Set.of("USER", "LOGIN", "MEMBER_", "STATION_", "PAGE_", "CHECKLIST_", "MESSAGE_");

    /**
     * Every permission either names a module or is one of the station's own. A new permission has to
     * decide, or a switched-off module would keep granting it.
     */
    @Test
    void everyPermissionOutsideTheStationsOwnNamesAModule() {
        for (StationPermission permission : StationPermission.values()) {
            boolean stationWide = STATION_WIDE_PREFIXES.stream().anyMatch(permission.name()::startsWith);
            assertEquals(stationWide, permission.module().isEmpty(), permission.name());
        }
    }

    /** A permission of a module implies only permissions of the same module, its parent or its parts. */
    @Test
    void aModulePermissionStaysWithinItsFamily() {
        for (StationPermission permission : StationPermission.values()) {
            Optional<StationModule> module = permission.module();
            if (module.isEmpty()) continue;
            StationModule family = module.get().parent().orElse(module.get());
            for (StationPermission child : permission.getChildren()) {
                assertTrue(
                        child.module().map(m -> m.belongsTo(family)).orElse(false),
                        permission + " implies " + child + " outside " + family);
            }
        }
    }

    /** The parts of the inventory have their own permissions, which name the part. */
    @Test
    void inventoryPartsNameTheirPart() {
        assertEquals(Optional.of(StationModule.INVENTORY_MOVEMENTS), StationPermission.INVENTORY_MOVEMENTS.module());
        assertEquals(
                Optional.of(StationModule.INVENTORY_PROCUREMENT), StationPermission.INVENTORY_PROCUREMENT.module());
        assertEquals(
                Optional.of(StationModule.INVENTORY_LENDING), StationPermission.INVENTORY_LENDING_MANAGER.module());
        assertEquals(Optional.of(StationModule.INVENTORY), StationPermission.INVENTORY_CHECK.module());
        assertEquals(Optional.empty(), StationPermission.MEMBER_READ.module());
    }

    /** With nothing switched off, the filtered expansion is the plain one. */
    @Test
    void nothingOffExpandsAsBefore() {
        for (StationPermission permission : StationPermission.values()) {
            assertEquals(
                    StationPermission.expand(EnumSet.of(permission)),
                    StationPermission.expand(EnumSet.of(permission), Set.of()),
                    permission.name());
        }
    }

    /** A switched-off module takes its permissions and what they imply, and leaves the rest. */
    @Test
    void aSwitchedOffModuleGrantsNothing() {
        Set<StationPermission> held = StationPermission.expand(
                EnumSet.of(StationPermission.STATION_ADMINISTRATOR), Set.of(StationModule.DOCUMENTS));

        assertFalse(held.contains(StationPermission.DOCUMENT_MANAGER));
        assertFalse(held.contains(StationPermission.DOCUMENT_READ_MEMBER), "not even through the member rights");
        assertTrue(held.contains(StationPermission.MEMBER_READ));
        assertTrue(held.contains(StationPermission.EVENT_MANAGER));
    }

    /** A part that is off takes only its own permissions; one held alone grants nothing beneath it either. */
    @Test
    void aSwitchedOffPartGrantsNothingButTheRestOfTheInventoryStays() {
        Set<StationModule> off = StationModule.withChildren(Set.of(StationModule.INVENTORY_MOVEMENTS));

        Set<StationPermission> manager = StationPermission.expand(EnumSet.of(StationPermission.INVENTORY_MANAGER), off);
        assertFalse(manager.contains(StationPermission.INVENTORY_MOVEMENTS));
        assertTrue(manager.contains(StationPermission.INVENTORY_READ));
        assertTrue(manager.contains(StationPermission.INVENTORY_PROCUREMENT));

        Set<StationPermission> movementsOnly =
                StationPermission.expand(EnumSet.of(StationPermission.INVENTORY_MOVEMENTS), off);
        assertTrue(movementsOnly.isEmpty());

        Set<StationPermission> inventoryOff = StationPermission.expand(
                EnumSet.of(StationPermission.INVENTORY_MANAGER, StationPermission.LOGIN),
                StationModule.withChildren(Set.of(StationModule.INVENTORY)));
        assertEquals(EnumSet.of(StationPermission.LOGIN, StationPermission.USER), inventoryOff);
    }

    /**
     * A cluster right on the cluster's own station follows that station's switches like a member's: what
     * it reached only through a switched-off part is gone too.
     */
    @Test
    void clusterRightsAtTheOwnStationFollowItsSwitches() {
        Set<StationPermission> granted = ClusterPermission.atOwnStation(
                EnumSet.of(ClusterPermission.CLUSTER_INVENTORY_MOVEMENTS),
                StationModule.withChildren(Set.of(StationModule.INVENTORY_MOVEMENTS)));

        assertFalse(granted.contains(StationPermission.INVENTORY_MOVEMENTS));
        assertFalse(granted.contains(StationPermission.INVENTORY_READ));
        assertTrue(granted.contains(StationPermission.LOGIN));
    }
}
