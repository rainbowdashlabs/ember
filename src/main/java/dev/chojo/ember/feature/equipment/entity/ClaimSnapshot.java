/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.equipment.entity;

import dev.chojo.ember.feature.inventory.entity.LineTarget;
import dev.chojo.ember.feature.inventory.entity.ResolvedTarget;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Every claim on a station's stock over a span, read once, so that any window inside the span can be
 * counted without reading again. The arithmetic is that of a single availability question: the stock
 * less what the claims touching the window take, a block taking everything it covers.
 *
 * @param claims the claims over the span
 */
public record ClaimSnapshot(List<EquipmentClaim> claims) {

    /**
     * What is free of one thing over a window inside the span.
     *
     * @param question what is asked about, with its stock
     * @param from     the first moment of the window
     * @param to       the last moment of the window
     * @return the free count, negative where more is spoken for than there is
     */
    public int free(Question question, Instant from, Instant to) {
        int claimed = claims.stream()
                .filter(claim -> claim.touches(question.resolved(), from, to))
                .mapToInt(claim -> sized(claim, question.stock()).quantity())
                .sum();
        return question.stock() - claimed;
    }

    /**
     * A claim as it counts against a stock. A block takes everything it covers, however much that is,
     * which is what setting a period aside means, so its count follows the stock rather than being
     * written down. Every other claim counts what it says.
     *
     * @param claim the claim
     * @param stock the stock it counts against
     * @return the claim with its count as it counts
     */
    public static EquipmentClaim sized(EquipmentClaim claim, int stock) {
        if (claim.origin() != ClaimOrigin.BLOCK || claim.firm()) return claim;
        return new EquipmentClaim(
                claim.origin(),
                claim.target(),
                claim.label(),
                claim.eventId(),
                claim.eventDate(),
                stock,
                claim.from(),
                claim.to(),
                claim.firm());
    }

    /**
     * The pieces a claim names over a window: handed over for another date, set aside for a partner or
     * named by a block.
     *
     * @param from the first moment of the window
     * @param to   the last moment of the window
     * @return their ids
     */
    public Set<Integer> named(Instant from, Instant to) {
        return claims.stream()
                .filter(claim -> claim.from().isBefore(to) && claim.to().isAfter(from))
                .map(EquipmentClaim::target)
                .filter(target -> target != null && target.itemId() != null)
                .map(ResolvedTarget::itemId)
                .collect(Collectors.toSet());
    }

    /**
     * Whether a block takes the whole of something over a window: one on the station or on its
     * inventory, and for a single piece also one on that piece. A block takes everything it covers, so
     * nothing of it is free then. A block on one piece leaves the rest of its kind or inventory.
     *
     * @param question what is asked about
     * @param from     the first moment of the window
     * @param to       the last moment of the window
     * @return {@code true} when a block covers it
     */
    public boolean blocked(ResolvedTarget question, Instant from, Instant to) {
        return claims.stream()
                .filter(claim -> claim.origin() == ClaimOrigin.BLOCK && (!claim.firm() || question.itemId() != null))
                .anyMatch(claim -> claim.touches(question, from, to));
    }

    /**
     * One thing a snapshot is asked about, resolved once together with how much of it the station has.
     *
     * @param target   what is asked about
     * @param resolved the same, resolved to its place in the inventory
     * @param stock    how many pieces of it the station has
     */
    public record Question(LineTarget target, ResolvedTarget resolved, int stock) {}
}
