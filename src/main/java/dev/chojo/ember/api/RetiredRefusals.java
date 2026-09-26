/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.api;

import java.util.Set;

/**
 * The codes of refusals that have been deleted, which must never be handed out again.
 *
 * <p>A report outlives the code that produced it. Somebody writes {@code F-021} down, the ticket
 * sits for a month, the constant that raised it is deleted, and the next refusal written in that
 * area takes the free number: now the report points at a line that has nothing to do with what
 * happened, and nothing anywhere says so. A gap in the numbering costs nothing; a number that means
 * two different things costs an investigation.
 *
 * <p>So deleting a {@link Refusal} is two edits: the constant goes, and its code is written here.
 * {@code RefusalTest} then holds the rule, refusing any live constant that has taken a retired
 * number back.
 */
public final class RetiredRefusals {
    private static final Set<String> CODES = Set.of();

    private RetiredRefusals() {}

    /**
     * Every code that has been retired.
     *
     * @return the retired codes, which no live refusal may use
     */
    public static Set<String> codes() {
        return CODES;
    }
}
