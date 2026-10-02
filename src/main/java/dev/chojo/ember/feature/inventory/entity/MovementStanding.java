/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.entity;

import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Where a movement stands, in the words every screen uses for it: the last step that has happened and
 * whose turn it is now, or how it ended.
 *
 * <p>One record so that a movement's own row and the piece of gear it runs on say the same thing. A
 * piece used to wear the label of the step being waited on, which has not happened yet: an exchange
 * asked for this morning read "taken back" on the member's list while the movement said it was waiting
 * for the station, with the jacket still on the member.
 *
 * @param id               the movement
 * @param state            whether it is still walking its chain, and how it ended when it is not
 * @param reachedStepLabel the last step whose words are already true, or {@code null} at a chain's very
 *                         beginning
 * @param currentStepActor the party being waited on, or {@code null} once the chain is over
 * @param ownerKind        whose gear it is, which is what the owner's turn is named after
 * @param ownerName        the association owning the gear by name, or {@code null} where the station owns
 *                         it or the association is not on this instance
 */
public record MovementStanding(
        int id,
        MovementState state,
        @Nullable String reachedStepLabel,
        @Nullable StepActor currentStepActor,
        ItemOwner ownerKind,
        @Nullable String ownerName) {

    /**
     * Words a movement's standing from its chain and the step it is waiting on.
     *
     * @param movement  the movement
     * @param steps     the chain it walks, in order
     * @param current   the step being waited on, or {@code null} once the chain is over
     * @param ownerKind whose gear it is
     * @param ownerName the owning association's name, where it has one here
     * @return where it stands
     */
    public static MovementStanding of(
            ItemMovement movement,
            List<MovementFlowStep> steps,
            @Nullable MovementFlowStep current,
            ItemOwner ownerKind,
            @Nullable String ownerName) {
        return new MovementStanding(
                movement.id(),
                movement.state(),
                reachedLabel(steps, current),
                current != null ? current.actor() : null,
                ownerKind,
                ownerName);
    }

    /**
     * The step whose words are true of the world right now, which is the one before the step being
     * waited on.
     *
     * <p>A step is named after the state it brings about, so the one a movement stands on has not
     * happened yet: a row wearing that label says a piece has been taken in while it is still on the
     * member. What has happened is everything before it, and the last of those is where it stands.
     *
     * @param steps   the chain, in order
     * @param current the step being waited on, or {@code null} once the chain is over
     * @return the words for where it stands, or {@code null} at a chain's very beginning
     */
    private static @Nullable String reachedLabel(List<MovementFlowStep> steps, @Nullable MovementFlowStep current) {
        if (steps.isEmpty()) return null;
        if (current == null) return steps.getLast().label();
        int standing = steps.indexOf(current);
        return standing > 0 ? steps.get(standing - 1).label() : null;
    }
}
