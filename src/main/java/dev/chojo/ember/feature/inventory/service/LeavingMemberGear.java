/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.service;

import dev.chojo.ember.feature.inventory.entity.InventoryItem;
import dev.chojo.ember.feature.inventory.entity.ItemCustody;
import dev.chojo.ember.feature.inventory.repository.InventoryRepository;
import dev.chojo.ember.util.sql.Transactions;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Puts a member's gear in order when the member is deleted, before anything about them is erased.
 *
 * <p>Marking somebody former is refused while they hold gear, because the station can still ask for it.
 * Deleting a member, or an account on request, cannot wait for that: erasure is a right, and a refusal
 * would leave the station holding the person's data until the gear comes back. So the gear is taken back
 * instead. What the station knows stays true afterwards: every piece is on the record of nobody rather
 * than of a person who no longer exists.
 *
 * <p>Their open movements close first, settling whatever is in the post and freeing what was promised to
 * them. Then each piece on their record comes off it: a missing one stays missing, on nobody's record,
 * and every other one goes back to the store it rests in, the station's shelf for its own gear and the
 * station's hold for an association's. All of it is one transaction, so a member is never half released.
 */
@Singleton
public class LeavingMemberGear {
    private static final Logger log = LoggerFactory.getLogger(LeavingMemberGear.class);

    private final ItemMovementService movementService;
    private final ItemCustodyService custodyService;
    private final InventoryRepository inventoryRepository;

    @Inject
    public LeavingMemberGear(
            ItemMovementService movementService,
            ItemCustodyService custodyService,
            InventoryRepository inventoryRepository) {
        this.movementService = movementService;
        this.custodyService = custodyService;
        this.inventoryRepository = inventoryRepository;
    }

    /**
     * Closes the member's open movements and takes every piece off their record.
     *
     * @param memberId the member being deleted
     * @return how many pieces came off their record
     */
    public int release(int memberId) {
        int released = Transactions.call(() -> {
            movementService.closeForALeavingMember(memberId, "Mitglied wurde gelöscht");
            int count = 0;
            for (InventoryItem item : inventoryRepository.findItemsByMember(memberId)) {
                takeOff(item);
                count++;
            }
            return count;
        });
        if (released > 0) log.info("Took {} piece(s) off the record of deleted member {}", released, memberId);
        return released;
    }

    /**
     * Takes one piece off the member's record: a missing one stays missing, and anything else goes back to
     * the store it rests in. Nothing stops it, since erasing a person may not fail over their gear; a
     * movement or a loan that still names the piece goes on naming it, and is written to the log.
     */
    private void takeOff(InventoryItem item) {
        if (movementService.isOnAMovement(item.id())) {
            log.warn("Piece {} of a deleted member is still named by another open movement", item.id());
        }
        if (item.custody() == ItemCustody.LOST || item.lostAt() != null) {
            custodyService.releaseLost(item.id());
        } else {
            custodyService.takeOffTheRecord(item.id());
        }
    }
}
