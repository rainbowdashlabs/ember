/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.entity;

/**
 * Names the party that owns an inventory item. There is never more than one body above a station,
 * and members never own tracked items: gear a member bought is the member's business and is not
 * recorded here at all.
 *
 * <p><b>What changes when the body above a station runs on this instance.</b> Nothing here does, and
 * that is the design: the owning cluster is a nullable id on the item, so a body arriving later
 * changes who acknowledges things rather than what anything is. The places that grow a second case
 * are all of them, and each says so where it stands:
 *
 * <ul>
 *   <li>The item's owning cluster gains a foreign key, in the patch that creates the table it points
 *       at.
 *   <li>Adoption is one statement: fill that id in for the station's cluster-owned items. Nothing
 *       moves, nothing is recreated, and the chains those items walk stay the same chains.
 *   <li>{@code MovementFlowService.resolveFlow} starts consulting the cluster's own flow, under the
 *       two-part condition written there.
 *   <li>{@code ItemMovementService.Actor} starts carrying owner rights for somebody, which is the
 *       only thing standing between an asserted step and a confirmed one.
 *   <li>Releasing a station puts every cluster-owned item in its custody back with the owner, clears
 *       assignments and container placements, and cancels the movements it was one end of.
 *   <li>A station stops being allowed to edit, lend or delete gear it does not own, which today it
 *       may do because there is no owner to object.
 * </ul>
 */
public enum ItemOwner {
    /**
     * The station that runs the item's inventory owns it.
     */
    STATION,
    /**
     * The one body above that station owns it: the municipality, the district association or the
     * umbrella organisation. Whether that body runs on this instance is told by the item's owning
     * cluster, which is set when it does and null when it does not.
     */
    CLUSTER
}
