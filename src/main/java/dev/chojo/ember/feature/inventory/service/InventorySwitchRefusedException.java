/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.service;

import dev.chojo.ember.feature.inventory.entity.SwitchBlocker;

import java.util.List;

/**
 * Raised when an inventory is asked to change what kind of thing it holds while something live still
 * depends on the kind it is being asked to leave.
 *
 * <p>It carries the things themselves rather than how many there were. A warning somebody clicks past
 * leaves exactly the orphans a refusal exists to prevent, only with the blame moved; and a refusal
 * that says nothing more than "three requirements are in the way" sends the reader hunting for them.
 */
public class InventorySwitchRefusedException extends RuntimeException {
    private final transient List<SwitchBlocker> blockers;

    /**
     * @param message  what is being refused, in plain words
     * @param blockers everything live that stands in the way
     */
    public InventorySwitchRefusedException(String message, List<SwitchBlocker> blockers) {
        super(message);
        this.blockers = List.copyOf(blockers);
    }

    /**
     * Everything live that stands in the way, each named well enough to go and deal with.
     *
     * @return the blockers, never empty
     */
    public List<SwitchBlocker> blockers() {
        return blockers;
    }
}
