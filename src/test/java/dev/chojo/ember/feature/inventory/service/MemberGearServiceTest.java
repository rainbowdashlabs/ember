/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.service;

import dev.chojo.ember.api.MemberIdentity;
import dev.chojo.ember.feature.inventory.entity.Glyph;
import dev.chojo.ember.feature.inventory.entity.Inventory;
import dev.chojo.ember.feature.inventory.entity.InventoryItem;
import dev.chojo.ember.feature.inventory.entity.InventorySize;
import dev.chojo.ember.feature.inventory.entity.InventoryType;
import dev.chojo.ember.feature.inventory.entity.ItemCustody;
import dev.chojo.ember.feature.inventory.entity.ItemOwner;
import dev.chojo.ember.feature.inventory.entity.MemberInventoryEntry;
import dev.chojo.ember.feature.inventory.entity.MovementStanding;
import dev.chojo.ember.feature.inventory.entity.MovementState;
import dev.chojo.ember.feature.inventory.entity.StepActor;
import dev.chojo.ember.feature.members.service.MemberIdentityFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A member's gear as the member and a guardian read it: named after its inventory and size, with the
 * movement it is on, its picture and who wrote the note about a loss.
 */
class MemberGearServiceTest {
    private static final int MEMBER = 12;
    private static final int INVENTORY = 9;

    private InventoryService inventory;
    private ItemMovementService movements;
    private GlyphResolver glyphs;
    private MemberIdentityFactory identities;
    private MemberGearService service;

    private static InventoryItem item(Integer sizeId, Integer lostNoteBy) {
        return new InventoryItem(
                5,
                INVENTORY,
                "J-1",
                "Jacke",
                sizeId,
                null,
                null,
                MEMBER,
                null,
                "weg",
                lostNoteBy,
                ItemOwner.STATION,
                null,
                null,
                null,
                ItemCustody.WITH_MEMBER,
                null,
                null,
                null,
                null);
    }

    @BeforeEach
    void setup() {
        inventory = mock(InventoryService.class);
        movements = mock(ItemMovementService.class);
        glyphs = mock(GlyphResolver.class);
        identities = mock(MemberIdentityFactory.class);
        when(glyphs.forItem(any())).thenReturn(new Glyph("shirt", "#dc2626"));
        service = new MemberGearService(inventory, movements, glyphs, identities);
    }

    @Test
    void aPieceCarriesItsInventorySizeAndMovement() {
        var standing = new MovementStanding(
                40, MovementState.OPEN, "Tausch angefordert", StepActor.STATION, ItemOwner.STATION, null);
        when(movements.standingOf(40)).thenReturn(Optional.of(standing));
        when(inventory.findMemberEntries(MEMBER))
                .thenReturn(List.of(
                        new MemberInventoryEntry(item(2, null), 40), new MemberInventoryEntry(item(null, null), null)));
        when(inventory.findById(INVENTORY))
                .thenReturn(Optional.of(new Inventory(
                        INVENTORY, 3, "Jacken", InventoryType.INTERNAL, false, false, false, null, null)));
        when(inventory.findSizes(INVENTORY)).thenReturn(List.of(new InventorySize(2, INVENTORY, "M", 0, null)));

        var items = service.heldBy(MEMBER);

        var first = items.getFirst();
        assertEquals("Jacken", first.inventoryName());
        assertEquals("M", first.sizeName());
        assertSame(standing, first.movement());
        assertEquals("shirt", first.icon());
        assertEquals("#dc2626", first.color());
        assertEquals(ItemCustody.WITH_MEMBER, first.custody());
        assertNull(first.lostNoteBy());
        assertNull(items.get(1).sizeName());
        assertNull(items.get(1).movement());
    }

    @Test
    void aMovementGoneSinceTheListWasReadLeavesThePieceWithoutOne() {
        when(inventory.findById(INVENTORY)).thenReturn(Optional.empty());
        when(movements.standingOf(41)).thenReturn(Optional.empty());

        var line = service.toItem(new MemberInventoryEntry(item(null, null), 41));

        assertNull(line.movement());
    }

    @Test
    void aPieceOfAVanishedInventoryCountsAsExchangeable() {
        when(inventory.findById(INVENTORY)).thenReturn(Optional.empty());
        when(inventory.findSizes(INVENTORY)).thenReturn(List.of());

        var line = service.toItem(new MemberInventoryEntry(item(7, null), null));

        assertEquals("", line.inventoryName());
        assertTrue(line.inventoryHomogeneous());
        assertNull(line.sizeName());
    }

    @Test
    void theNoteAboutALossNamesWhoWroteIt() {
        var guardian = mock(MemberIdentity.class);
        when(identities.fromMemberId(11)).thenReturn(guardian);
        when(inventory.findById(INVENTORY)).thenReturn(Optional.empty());

        var line = service.toItem(new MemberInventoryEntry(item(null, 11), null));

        assertSame(guardian, line.lostNoteBy());
        assertEquals("weg", line.lostNote());
    }
}
