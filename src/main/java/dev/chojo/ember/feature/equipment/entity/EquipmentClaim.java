/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.equipment.entity;

import dev.chojo.ember.feature.inventory.entity.ResolvedTarget;

import java.time.Instant;
import java.time.LocalDate;

/**
 * A hold on stock over a window, and how many pieces it takes.
 *
 * <p>A claim is not a status on a piece. Custody is a statement about now, and there is no room in it
 * for a thing that is standing here and promised for Saturday. A claim holds from the moment it is
 * written and it holds nothing outside its own window: a need written in March for June takes nothing
 * away in March, and a piece claimed in March is free in April.
 *
 * <p>Most claims are loose and say four blue ones without saying which four, because picking fourteen
 * particular jackets in March for a June exercise is busywork that will be redone on the day. A claim
 * firms up at handover, when the pieces that went are known.
 *
 * @param origin    where the claim comes from
 * @param target    what it holds, or {@code null} where it holds everything the station has
 * @param label     what to call it on screen: the appointment, the partner, the reason for a block
 * @param eventId   the appointment behind the claim, or {@code null}
 * @param eventDate the evening the claim is for, or {@code null}
 * @param quantity  how many pieces it takes
 * @param from      when the gear goes
 * @param to        when it is back
 * @param firm      whether the pieces that went are known
 */
public record EquipmentClaim(
        ClaimOrigin origin,
        ResolvedTarget target,
        String label,
        Integer eventId,
        LocalDate eventDate,
        int quantity,
        Instant from,
        Instant to,
        boolean firm) {

    /**
     * Whether this claim takes anything away from a question about the given target over the given
     * window.
     *
     * <p>The overlap test is the whole mechanism, and it is the same one the existing date blocks and
     * lending requests already use, only carried out on instants rather than on days: a claim running
     * to Monday morning and one starting Monday afternoon do not collide, which on days alone they
     * would.
     *
     * @param question   the target being asked about
     * @param windowFrom the first moment of the window
     * @param windowTo   the last moment of the window
     * @return {@code true} when the claim counts against the question
     */
    public boolean touches(ResolvedTarget question, Instant windowFrom, Instant windowTo) {
        if (target != null && !target.counts(question)) return false;
        return from.isBefore(windowTo) && to.isAfter(windowFrom);
    }
}
