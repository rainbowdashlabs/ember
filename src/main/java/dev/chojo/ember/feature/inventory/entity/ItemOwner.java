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
 * <p><b>What changed when the body above a station gained a way to run on this instance.</b> Nothing
 * here did, and that was the design: the owning cluster is a nullable id on the item, so a body
 * arriving later changed who acknowledges things rather than what anything is. Every place that grew
 * a second case has it now, and each says so where it stands:
 *
 * <ul>
 *   <li>The item's owning cluster has a foreign key, added in the patch that creates the table it
 *       points at, and a station may only name the body it answers to.
 *   <li>Joining fills that id in for the station's owner-owned items, in one statement. Nothing
 *       moves, nothing is recreated, and the chains those items walk stay the same chains.
 *   <li>{@code MovementFlowService.resolveFlow} consults the cluster's own flow under the two-part
 *       condition written there.
 *   <li>{@code ItemMovementService.Actor} carries owner rights for somebody who holds them at the
 *       owning cluster, which is the only thing standing between an asserted step and a confirmed
 *       one.
 *   <li>Releasing a station puts every cluster-owned item in its custody back with the owner, clears
 *       assignments and container placements, and cancels the movements it was one end of.
 *   <li>A station may not edit, lend or delete gear it does not own. Where the gear is stays its
 *       business; what the gear is stays the owner's.
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
