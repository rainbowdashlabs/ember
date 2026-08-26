/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.entity;

/**
 * The end of a movement that is not the owner: the station's store, or a member.
 *
 * <p>Purpose says what a movement is for, owner says whose gear it is, and neither of them
 * distinguishes an issue that fills a shelf from one that dresses somebody. Those are different
 * chains with different steps, and without this they would be the same chain and overwrite each
 * other's binding.
 *
 * <p>It is not part of the purpose on purpose. Handing gear to a member rather than to a shelf is
 * not another intention, it is another recipient, and folding it into the purpose would double
 * every value there and every branch that reads one.
 */
public enum MovementParty {
    /**
     * The station's own store. Nothing leaves the station or arrives at a person.
     */
    STORE,
    /**
     * A member. The chain starts or ends with somebody wearing the gear, and the last word about
     * whether it arrived is theirs.
     */
    MEMBER
}
