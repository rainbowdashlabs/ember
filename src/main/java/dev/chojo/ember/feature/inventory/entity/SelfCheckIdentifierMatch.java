/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.entity;

import java.util.List;

/**
 * Everything the number a member typed matched, and nothing decided about it.
 *
 * <p>The number is compared without regard to case and without the spaces around it, because a
 * member reading it off a label is not a scanner. Every match travels rather than one, because
 * nothing makes the number unique and answering with an arbitrary one of several would read as
 * certainty.
 *
 * @param finding   the shape of what was found, as a reviewer needs to see it at a glance
 * @param typed     the number as the member gave it, trimmed, or {@code null} where they gave none
 * @param pieces    every piece of gear carrying it
 * @param containers the name of every container carrying it, which shares the same numbering
 */
public record SelfCheckIdentifierMatch(
        SelfCheckIdentifierFinding finding, String typed, List<Piece> pieces, List<String> containers) {

    /**
     * The answer for a member who typed nothing, which is a perfectly ordinary answer.
     */
    public static SelfCheckIdentifierMatch nothingTyped() {
        return new SelfCheckIdentifierMatch(SelfCheckIdentifierFinding.NOTHING_TYPED, null, List.of(), List.of());
    }

    /**
     * Reads the shape of a set of matches, once they have all been gathered.
     *
     * @param typed      the number as the member gave it
     * @param pieces     every piece carrying it
     * @param containers every container carrying it
     * @return the finding, with everything that was found beside it
     */
    public static SelfCheckIdentifierMatch of(String typed, List<Piece> pieces, List<String> containers) {
        return new SelfCheckIdentifierMatch(finding(pieces, containers), typed, pieces, containers);
    }

    private static SelfCheckIdentifierFinding finding(List<Piece> pieces, List<String> containers) {
        if (pieces.size() + containers.size() > 1) return SelfCheckIdentifierFinding.SEVERAL;
        if (pieces.isEmpty()) {
            return containers.isEmpty() ? SelfCheckIdentifierFinding.NO_MATCH : SelfCheckIdentifierFinding.A_CONTAINER;
        }
        return pieces.getFirst().heldBy() == null ? SelfCheckIdentifierFinding.FREE : SelfCheckIdentifierFinding.HELD;
    }

    /**
     * One piece carrying the number, named as a reviewer needs to see it.
     *
     * @param itemId        the piece
     * @param name          what it is called
     * @param internalId    the number written on it, which is what matched
     * @param inventoryName the inventory it sits in, so a match of the wrong kind is visible
     * @param heldBy        whoever has it on their record, or {@code null} where it is free
     * @param heldByName    that person's name, empty where the piece is free
     */
    public record Piece(
            int itemId, String name, String internalId, String inventoryName, Integer heldBy, String heldByName) {}
}
