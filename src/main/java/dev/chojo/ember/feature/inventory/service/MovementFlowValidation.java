/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.service;

import dev.chojo.ember.feature.inventory.entity.ItemCustody;
import dev.chojo.ember.feature.inventory.entity.MovementFlowStep;
import dev.chojo.ember.feature.inventory.entity.MovementPurpose;
import dev.chojo.ember.feature.inventory.entity.StepSubject;
import io.javalin.http.BadRequestResponse;

import java.util.List;
import java.util.Optional;

/**
 * What a chain has to look like before anybody may save it.
 *
 * <p>A chain is walked once by real people moving real gear, and a chain that cannot be walked to
 * the end is found out at the worst moment: halfway, with the gear already somewhere else and no
 * way forward but throwing the whole movement away. Every rule here is one of those dead ends,
 * turned into something the editor refuses instead.
 *
 * <p>The rules are deliberately about shape and not about wording. What a step is called is the
 * station's business; that the chain ends somewhere the gear can rest is not.
 *
 * <p>What a single step may say is checked where that step is written, not here. This is only about
 * what the steps add up to.
 */
final class MovementFlowValidation {

    private MovementFlowValidation() {}

    /**
     * Checks the chain a save would leave behind.
     *
     * @param purpose what the chain is for, which decides whether both directions have to appear
     * @param steps   the active steps in the order they would be walked
     * @throws BadRequestResponse naming the one thing that is wrong, in the words the editor shows
     */
    static void requireWalkable(MovementPurpose purpose, List<MovementFlowStep> steps) {
        problemOf(purpose, steps).ifPresent(problem -> {
            throw new BadRequestResponse(problem);
        });
    }

    /**
     * The one thing that stops this chain from being walked, in the words the editor shows.
     *
     * <p>Reported rather than only thrown, because a chain under construction is briefly unwalkable
     * on the way to being finished. The editor says what is still missing while somebody builds it,
     * and binding it or starting a movement on it is where the same answer becomes a refusal.
     *
     * @param purpose what the chain is for
     * @param steps   the active steps in the order they would be walked
     * @return the problem, or empty when the chain can be walked from end to end
     */
    static Optional<String> problemOf(MovementPurpose purpose, List<MovementFlowStep> steps) {
        if (steps.size() < 2) {
            return Optional.of(
                    "A chain needs at least two steps: one that asks for the gear and one that confirms it arrived");
        }

        if (steps.getLast().custodyAfter() == ItemCustody.IN_TRANSIT) {
            return Optional.of("The last step leaves the gear in the post. End the chain where somebody has it: "
                    + "with the owner, at the station or with a member");
        }

        var namesOutgoing = steps.stream()
                .filter(MovementFlowStep::picksItem)
                .filter(step -> step.subject() != StepSubject.INCOMING)
                .findFirst();
        if (namesOutgoing.isPresent()) {
            return Optional.of("'%s' names the arriving piece, so it has to be about the one coming in"
                    .formatted(namesOutgoing.get().label()));
        }

        boolean goes = steps.stream().anyMatch(step -> step.subject() == StepSubject.OUTGOING);
        boolean comes = steps.stream().anyMatch(step -> step.subject() == StepSubject.INCOMING);
        if (purpose == MovementPurpose.EXCHANGE && (!goes || !comes)) {
            return Optional.of("An exchange has one piece going and one coming, so the chain needs a step for each");
        }

        if (comes && steps.stream().noneMatch(MovementFlowStep::picksItem)) {
            return Optional.of("Something arrives in this chain, so one step has to name which piece it is");
        }

        return Optional.empty();
    }
}
