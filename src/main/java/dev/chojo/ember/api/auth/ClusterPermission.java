/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.api.auth;

import io.javalin.security.RouteRole;

import java.util.EnumSet;
import java.util.Set;

/**
 * What a cluster member may do. A mirror of {@link StationPermission} rather than a reuse of it, because a
 * cluster has its own members, its own user types and its own grants, and none of them are station rows
 * wearing a hat.
 *
 * <p>The content permissions gate the cluster's screens. What a cluster manager does on those screens is
 * carried out against the home station by the ordinary station services, with the check already made at the
 * cluster boundary; no station membership is invented to carry it.
 */
public enum ClusterPermission implements RouteRole {

    /**
     * The general user permission. Grants access to most things.
     */
    USER,

    /**
     * Allows the member to act for the cluster at all.
     */
    LOGIN(USER),

    /**
     * The cluster's own name, description and identity.
     */
    CLUSTER_GENERAL,

    /**
     * How far a member station's look and feel may drift.
     */
    CLUSTER_LOOK_AND_FEEL,

    /**
     * Who the cluster and its stations are paired with.
     */
    CLUSTER_FEDERATION,

    /**
     * Which modules a member station may use.
     */
    CLUSTER_MODULES,

    /**
     * How much storage each member station gets out of the pool.
     */
    CLUSTER_STORAGE,

    /**
     * Which stations belong to the cluster, and letting them in or out.
     */
    CLUSTER_STATIONS,

    CLUSTER_MEMBER_READ,
    CLUSTER_MEMBER_EDIT(CLUSTER_MEMBER_READ),
    CLUSTER_MEMBER_FIELDS,
    CLUSTER_MEMBER_EXPORT(CLUSTER_MEMBER_READ),
    CLUSTER_MEMBER_MANAGER(CLUSTER_MEMBER_EDIT, CLUSTER_MEMBER_FIELDS, CLUSTER_MEMBER_EXPORT),

    CLUSTER_INVENTORY_READ,
    CLUSTER_INVENTORY_EDIT(CLUSTER_INVENTORY_READ),

    /**
     * Sending gear to a member station and taking it back.
     */
    CLUSTER_INVENTORY_TRANSFER(CLUSTER_INVENTORY_READ),

    /**
     * Acknowledging the cluster's own steps of a movement.
     */
    CLUSTER_INVENTORY_EXCHANGE(CLUSTER_INVENTORY_READ),

    CLUSTER_INVENTORY_MANAGER(CLUSTER_INVENTORY_EDIT, CLUSTER_INVENTORY_TRANSFER, CLUSTER_INVENTORY_EXCHANGE),

    CLUSTER_FIELD_EDIT,
    CLUSTER_FIELD_MANAGER(CLUSTER_FIELD_EDIT),

    CLUSTER_KNOWLEDGE_EDIT,
    CLUSTER_KNOWLEDGE_MANAGER(CLUSTER_KNOWLEDGE_EDIT),
    CLUSTER_NEWS_EDIT,
    CLUSTER_NEWS_MANAGER(CLUSTER_NEWS_EDIT),
    CLUSTER_EVENT_EDIT,
    CLUSTER_EVENT_MANAGER(CLUSTER_EVENT_EDIT),

    /**
     * Everything about how the cluster governs itself and its stations.
     */
    CLUSTER_MANAGER(
            CLUSTER_GENERAL,
            CLUSTER_LOOK_AND_FEEL,
            CLUSTER_FEDERATION,
            CLUSTER_MODULES,
            CLUSTER_STORAGE,
            CLUSTER_STATIONS),

    /**
     * Everything.
     */
    CLUSTER_ADMINISTRATOR(
            CLUSTER_MANAGER,
            CLUSTER_MEMBER_MANAGER,
            CLUSTER_INVENTORY_MANAGER,
            CLUSTER_FIELD_MANAGER,
            CLUSTER_KNOWLEDGE_MANAGER,
            CLUSTER_NEWS_MANAGER,
            CLUSTER_EVENT_MANAGER,
            LOGIN);

    private final ClusterPermission[] children;
    private Set<ClusterPermission> allChildren;

    ClusterPermission(ClusterPermission... children) {
        this.children = children;
    }

    /**
     * Expands a set of permissions to include everything they transitively contain.
     *
     * @param permissions the permissions held directly
     * @return those permissions and everything they carry
     */
    public static Set<ClusterPermission> expand(Set<ClusterPermission> permissions) {
        Set<ClusterPermission> expanded = EnumSet.noneOf(ClusterPermission.class);
        expanded.addAll(permissions);
        for (ClusterPermission permission : permissions) {
            expanded.addAll(permission.allChildren());
        }
        return expanded;
    }

    public ClusterPermission[] getChildren() {
        return children;
    }

    /**
     * Everything this permission transitively includes.
     *
     * <p>The set is gathered into a local and published in one assignment, so a reader arriving mid-flight
     * either sees no cache or sees a complete one. Filling a field in place is how the station copy of this
     * could hand out a half-built set.
     *
     * @return the permissions carried by this one, directly or through another
     */
    public Set<ClusterPermission> allChildren() {
        Set<ClusterPermission> cached = allChildren;
        if (cached != null) return cached;

        Set<ClusterPermission> gathered = EnumSet.noneOf(ClusterPermission.class);
        for (ClusterPermission child : children) {
            gathered.add(child);
            gathered.addAll(child.allChildren());
        }
        allChildren = gathered;
        return gathered;
    }

    /**
     * Whether this permission transitively includes the given one.
     *
     * @param permission the permission to look for
     * @return {@code true} when holding this one means holding that one
     */
    public boolean includes(ClusterPermission permission) {
        return allChildren().contains(permission);
    }

    /**
     * What these permissions come to on the cluster's own station.
     *
     * <p>The cluster's knowledge base, news list, calendar and gear are a station's, kept on the station the
     * cluster owns, so the screens that edit them are a station's too. For those screens to work, somebody
     * trusted with the cluster's knowledge has to arrive at that station holding the right to edit knowledge
     * there, and somebody trusted with its gear has to arrive holding the right to that.
     *
     * <p>It translates nothing beyond those: the right to run the cluster is not the right to run a station, so
     * nothing here reaches members or settings. The station in question has no members to manage, but the
     * reason to keep the list short is that the two jobs are different ones.
     *
     * <p>Lending is the one gear right deliberately left out, which is why the manager row is written out
     * rather than mapped to {@code INVENTORY_MANAGER}: that one carries lending, and a cluster hands gear to a
     * station rather than lending it to a person.
     *
     * @param held every cluster permission the member holds, already expanded
     * @return what they may do at the station the cluster owns
     */
    public static Set<StationPermission> atOwnStation(Set<ClusterPermission> held) {
        Set<StationPermission> granted = EnumSet.noneOf(StationPermission.class);
        if (held.isEmpty()) return granted;

        granted.add(StationPermission.LOGIN);
        granted.add(StationPermission.USER);

        if (held.contains(CLUSTER_KNOWLEDGE_MANAGER)) granted.add(StationPermission.KNOWLEDGE_MANAGER);
        else if (held.contains(CLUSTER_KNOWLEDGE_EDIT)) granted.add(StationPermission.KNOWLEDGE_EDIT);

        if (held.contains(CLUSTER_NEWS_MANAGER)) granted.add(StationPermission.NEWS_MANAGER);
        else if (held.contains(CLUSTER_NEWS_EDIT)) granted.add(StationPermission.NEWS_EDIT);

        if (held.contains(CLUSTER_EVENT_MANAGER)) granted.add(StationPermission.EVENT_MANAGER);
        else if (held.contains(CLUSTER_EVENT_EDIT)) granted.add(StationPermission.EVENT_EDIT);

        // The cluster's gear lives on this station, so the rights over it are the station's rights over it.
        if (held.contains(CLUSTER_INVENTORY_MANAGER)) {
            granted.add(StationPermission.INVENTORY_ASSIGN);
            granted.add(StationPermission.INVENTORY_CHECK);
            granted.add(StationPermission.INVENTORY_EDIT);
            granted.add(StationPermission.INVENTORY_EXCHANGE);
            granted.add(StationPermission.INVENTORY_PROCUREMENT);
            granted.add(StationPermission.INVENTORY_STORAGE);
        } else {
            if (held.contains(CLUSTER_INVENTORY_EDIT)) granted.add(StationPermission.INVENTORY_EDIT);
            else if (held.contains(CLUSTER_INVENTORY_READ)) granted.add(StationPermission.INVENTORY_READ);

            if (held.contains(CLUSTER_INVENTORY_TRANSFER)) {
                granted.add(StationPermission.INVENTORY_ASSIGN);
                granted.add(StationPermission.INVENTORY_STORAGE);
            }
            if (held.contains(CLUSTER_INVENTORY_EXCHANGE)) granted.add(StationPermission.INVENTORY_EXCHANGE);
        }

        // Sharing the cluster's content out to its stations is the cluster's federation right, read here.
        if (held.contains(CLUSTER_FEDERATION)) {
            granted.add(StationPermission.KNOWLEDGE_FEDERATE);
            granted.add(StationPermission.NEWS_FEDERATE);
            granted.add(StationPermission.EVENTS_FEDERATE);
        }

        return StationPermission.expand(granted);
    }
}
