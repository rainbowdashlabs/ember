/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.service;

import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.api.RouteSupport;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.ClusterPermission;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.inventory.entity.InventoryItem;
import dev.chojo.ember.feature.inventory.entity.ItemMovement;
import dev.chojo.ember.feature.members.service.GuardianPolicy;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Who has any business with a movement, and in which capacity they act on it.
 *
 * <p>Three kinds of caller do. Somebody who works a station's queue sees that station's movements.
 * The member a movement is about sees their own, and so does whoever answers for them, as
 * {@link GuardianPolicy} decides. And somebody acting for the cluster that owns the gear sees it
 * wherever it is: the step the movement is standing on may be theirs to answer, and a step nobody
 * can open is a step nobody can answer.
 */
@Singleton
public class MovementGuards {
    private final ItemMovementService movements;
    private final InventoryService inventory;
    private final GuardianPolicy guardians;

    @Inject
    public MovementGuards(ItemMovementService movements, InventoryService inventory, GuardianPolicy guardians) {
        this.movements = movements;
        this.inventory = inventory;
        this.guardians = guardians;
    }

    /**
     * The movements of a list the caller may see: all of them for somebody who works the queue, and
     * those about the caller or somebody in their care for everybody else.
     *
     * @param session who is asking
     * @param listed  the station's movements
     * @return the ones the caller may see
     */
    public List<ItemMovement> visibleAmong(UserSession session, List<ItemMovement> listed) {
        if (session.hasPermission(StationPermission.INVENTORY_MOVEMENTS)) return listed;
        var household = guardians.household(session);
        return listed.stream()
                .filter(movement -> movement.memberId() != null && household.contains(movement.memberId()))
                .toList();
    }

    /**
     * The movement, if this caller has any business with it. A movement that is absent and one that
     * is none of theirs answer the same, which keeps the two indistinguishable from outside.
     *
     * @param session    who is asking
     * @param movementId the movement
     * @return the movement
     */
    public ItemMovement requireVisible(UserSession session, int movementId) {
        ItemMovement movement =
                movements.findById(movementId).orElseThrow(Refusal.MOVEMENT_NOT_HERE_OR_NOT_YOURS::raise);
        if (hasOwnerRights(session, movement)) return movement;

        RouteSupport.requireSameStation(session, movement.stationId());
        if (session.hasPermission(StationPermission.INVENTORY_MOVEMENTS)) return movement;
        if (movement.memberId() != null && guardians.mayActFor(session, movement.memberId())) return movement;
        throw Refusal.MOVEMENT_NOT_HERE_OR_NOT_YOURS.raise();
    }

    /**
     * Refuses starting a movement for a member the caller does not act for. A movement about the
     * store names nobody and is left to the permission of the route.
     *
     * @param session  who is asking
     * @param memberId the member it is to be about, or {@code null}
     */
    public void requireMayStartFor(UserSession session, @Nullable Integer memberId) {
        if (memberId == null || session.hasPermission(StationPermission.INVENTORY_MOVEMENTS)) return;
        if (!guardians.mayActFor(session, memberId)) {
            throw Refusal.MEMBER_NOT_YOURS_TO_ACT_FOR.raise();
        }
    }

    /**
     * What the caller may act as, which is not one thing but two.
     *
     * <p>Somebody can be at the station, at the cluster that owns the gear, or both at once, and a
     * step belonging to the owner reads differently depending on which of those answered it. A
     * cluster manager pressing it has confirmed something they can see; the station pressing the
     * same button has asserted something on the owner's behalf, and the record keeps those apart.
     * Somebody acting for a cluster need not be at any station, and is then member zero, which is
     * nobody: member ids start at one.
     *
     * @param session  who is asking
     * @param movement the movement, or {@code null} when one is being started
     * @return the capacities they act in
     */
    public ItemMovementService.Actor actorOf(UserSession session, @Nullable ItemMovement movement) {
        return new ItemMovementService.Actor(
                session.member() != null ? session.member().id() : 0,
                session.hasPermission(StationPermission.INVENTORY_MOVEMENTS),
                hasOwnerRights(session, movement));
    }

    /**
     * Whether the caller may answer for the body that owns the gear this movement is about.
     *
     * <p>Only when they act for the cluster that owns this gear: holding the permission at some
     * other cluster says nothing about this one. A movement that has not named its piece yet is
     * about the cluster the station answers to.
     *
     * @param session  who is asking
     * @param movement the movement, or {@code null} when one is being started
     * @return {@code true} when they act for the owning cluster and hold its exchange permission
     */
    public boolean hasOwnerRights(UserSession session, @Nullable ItemMovement movement) {
        if (session.clusterId() == null) return false;
        if (!session.hasClusterPermission(ClusterPermission.CLUSTER_INVENTORY_MOVEMENTS)) return false;
        Integer outgoingItemId = movement == null ? null : movement.outgoingItemId();
        if (outgoingItemId == null) return true;
        Integer owner = inventory
                .findItemById(outgoingItemId)
                .map(InventoryItem::ownerClusterId)
                .orElse(null);
        return owner == null || owner.equals(session.clusterId());
    }
}
