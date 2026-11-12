/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.station.entity;

import io.javalin.security.RouteRole;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

/**
 * Available feature modules that can be individually enabled or disabled per station.
 *
 * <p>A module may have a parent, which makes it a part of that module that can be switched off on its
 * own. A module that is off takes all of its parts with it, which {@link #withChildren(Collection)}
 * spells out for a set of switched-off modules.
 *
 * <p>A module is also a route role: a route every member may use declares the module it belongs to
 * beside its permission, and the route guard refuses it while that module is off.
 */
public enum StationModule implements RouteRole {
    INVENTORY,
    NEWS,
    EVENTS,
    ATTENDANCE,
    FORMS,
    LOST_AND_FOUND,
    WAITING_LIST,
    QUIZ,
    KNOWLEDGE_BASE,
    TEST_PROTOCOL,
    BOARDS,
    PROCEDURES,
    /**
     * The station's document store.
     *
     * <p>Modules are held as the set a station has switched <em>off</em>, so adding one here leaves
     * every station that already keeps documents keeping them, and no migration writes anything.
     */
    DOCUMENTS,
    /**
     * Handing items over between members, the store and partners, with its flows.
     */
    INVENTORY_MOVEMENTS(INVENTORY),
    /**
     * Lending items to and borrowing them from partner stations.
     */
    INVENTORY_LENDING(INVENTORY),
    /**
     * Ordering new items.
     */
    INVENTORY_PROCUREMENT(INVENTORY),
    /**
     * Members checking the items they hold themselves.
     */
    INVENTORY_SELF_CHECKS(INVENTORY),
    /**
     * Checking the items a member holds, done by the station.
     */
    INVENTORY_CHECKS(INVENTORY);

    private final @Nullable StationModule parent;

    StationModule() {
        this.parent = null;
    }

    StationModule(StationModule parent) {
        this.parent = parent;
    }

    /**
     * The module this one is a part of.
     *
     * @return the parent, empty for a module of its own
     */
    public Optional<StationModule> parent() {
        return Optional.ofNullable(parent);
    }

    /**
     * Whether this module is the given one or one of its parts.
     *
     * @param module the module asked about
     * @return {@code true} when switching {@code module} off switches this one off as well
     */
    public boolean belongsTo(StationModule module) {
        for (StationModule current = this; current != null; current = current.parent) {
            if (current == module) return true;
        }
        return false;
    }

    /**
     * Whether this module is off, given the modules that are switched off.
     *
     * @param disabled the switched-off modules, with or without their parts
     * @return {@code true} when this module or a module it belongs to is among them
     */
    public boolean isOffIn(Set<StationModule> disabled) {
        for (StationModule current = this; current != null; current = current.parent) {
            if (disabled.contains(current)) return true;
        }
        return false;
    }

    /**
     * The given modules together with every part of them.
     *
     * @param modules the modules
     * @return a new set holding the modules and all of their parts
     */
    public static Set<StationModule> withChildren(Collection<StationModule> modules) {
        Set<StationModule> expanded = EnumSet.noneOf(StationModule.class);
        for (StationModule candidate : values()) {
            for (StationModule module : modules) {
                if (candidate.belongsTo(module)) expanded.add(candidate);
            }
        }
        return expanded;
    }
}
