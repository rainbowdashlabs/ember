/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.service;

import dev.chojo.ember.api.MemberIdentity;
import dev.chojo.ember.feature.inventory.entity.Glyph;
import dev.chojo.ember.feature.inventory.entity.Inventory;
import dev.chojo.ember.feature.inventory.entity.InventorySize;
import dev.chojo.ember.feature.inventory.entity.MemberInventoryEntry;
import dev.chojo.ember.feature.inventory.entity.MovementStanding;
import dev.chojo.ember.feature.inventory.entity.MyInventoryItem;
import dev.chojo.ember.feature.members.service.MemberIdentityFactory;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * A member's own gear as it is shown to the member, and to a guardian looking after them: one list,
 * so both read the same pieces with the same movement standing, owners and pictures.
 */
@Singleton
public class MemberGearService {
    private final InventoryService inventoryService;
    private final ItemMovementService movementService;
    private final GlyphResolver glyphResolver;
    private final MemberIdentityFactory memberIdentityFactory;

    @Inject
    public MemberGearService(
            InventoryService inventoryService,
            ItemMovementService movementService,
            GlyphResolver glyphResolver,
            MemberIdentityFactory memberIdentityFactory) {
        this.inventoryService = inventoryService;
        this.movementService = movementService;
        this.glyphResolver = glyphResolver;
        this.memberIdentityFactory = memberIdentityFactory;
    }

    /**
     * What a member holds, plus whatever is on its way to or from them.
     *
     * @param memberId the member
     * @return their pieces, each with the movement it is on when there is one
     */
    public List<MyInventoryItem> heldBy(int memberId) {
        return inventoryService.findMemberEntries(memberId).stream()
                .map(this::toItem)
                .toList();
    }

    /**
     * Renders one line of a member's gear. Whether the piece can be exchanged travels with it, because
     * the screens that offer an exchange are the member's own and have no list of inventories to look
     * it up in.
     *
     * @param entry the piece and the movement it is on
     * @return the line
     */
    public MyInventoryItem toItem(MemberInventoryEntry entry) {
        var item = entry.item();
        var inventory = inventoryService.findById(item.inventoryId());
        Glyph glyph = glyphResolver.forItem(item);
        return new MyInventoryItem(
                item.id(),
                item.inventoryId(),
                item.name(),
                item.internalId(),
                inventory.map(Inventory::name).orElse(""),
                inventory.map(Inventory::homogeneous).orElse(true),
                item.sizeId(),
                sizeName(item.inventoryId(), item.sizeId()),
                item.lostAt(),
                item.custody(),
                standing(entry.movementId()),
                item.ownerKind(),
                item.ownerClusterId(),
                item.lostNote(),
                noteAuthor(item.lostNoteBy()),
                glyph.icon(),
                glyph.color());
    }

    /**
     * Where the movement on a piece stands, read from the movement itself rather than from the step it
     * is waiting on, so the piece and the movement's own row cannot tell two different stories.
     */
    private @Nullable MovementStanding standing(@Nullable Integer movementId) {
        if (movementId == null) return null;
        return movementService.standingOf(movementId).orElse(null);
    }

    private @Nullable String sizeName(int inventoryId, @Nullable Integer sizeId) {
        if (sizeId == null) return null;
        return inventoryService.findSizes(inventoryId).stream()
                .filter(s -> s.id() == sizeId)
                .map(InventorySize::label)
                .findFirst()
                .orElse(null);
    }

    /**
     * Who wrote the note about a loss, as an identity rather than a name. It matters who it was: a
     * guardian may report a loss for the person they act for, and the note then says so rather than
     * reading as if the member wrote it themselves.
     */
    private @Nullable MemberIdentity noteAuthor(@Nullable Integer memberId) {
        return memberId == null ? null : memberIdentityFactory.fromMemberId(memberId);
    }
}
