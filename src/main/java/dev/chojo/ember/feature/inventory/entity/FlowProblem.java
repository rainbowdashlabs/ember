/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.entity;

import org.jspecify.annotations.Nullable;

/**
 * What is wrong with a chain, named rather than worded.
 *
 * <p>A code and not a sentence, because the sentence belongs to whoever shows it. The rules live
 * here and the wording lives with the reader, which is also the only way the same fault reads in
 * the reader's language rather than in the one the rule happened to be written in.
 *
 * <p>The same codes serve two answers: what stops a chain from being walked, shown on the chain
 * while somebody writes it, and why a change to a chain was refused. They are one list because they
 * are one set of rules, asked at two moments.
 *
 * @param code   what is wrong
 * @param detail what the fault is about where naming it helps, a step's label for instance,
 *               otherwise null
 */
public record FlowProblem(Code code, @Nullable String detail) {

    /** The faults a chain can carry, each one a dead end somebody would otherwise walk into. */
    public enum Code {
        /** Fewer than two steps: nothing asks for the gear, or nothing confirms it arrived. */
        TOO_SHORT,
        /** The chain ends with the gear still in the post rather than with somebody holding it. */
        ENDS_IN_TRANSIT,
        /** A step that names the arriving piece is about a piece going out. */
        OUTGOING_NAMES_ITEM,
        /** An exchange without a piece going or without one coming. */
        EXCHANGE_NEEDS_BOTH_DIRECTIONS,
        /** Something arrives and no step says which piece it is. */
        ARRIVAL_UNNAMED,
        /** A movement is walking the chain, so its steps cannot change under it. */
        FLOW_IN_USE,
        /** A chain was to be saved without a name. */
        FLOW_NAME_REQUIRED,
        /** A step was to be saved without a label. */
        STEP_LABEL_REQUIRED,
        /** A step would leave the gear somewhere a movement cannot rest. */
        ILLEGAL_STEP_CUSTODY,
        /** A step about a piece going out was to name the arriving one. */
        ONLY_ARRIVAL_NAMES_ITEM,
        /** A second step was to name the arriving piece, where one already does. */
        ITEM_ALREADY_NAMED,
        /** An order was given that does not name every step of the chain exactly once. */
        ORDER_MUST_NAME_EVERY_STEP
    }

    /** A fault that is about the chain as a whole rather than about one part of it. */
    public static FlowProblem of(Code code) {
        return new FlowProblem(code, null);
    }
}
