/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.api.refusal;

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
    private static final Set<String> CODES = Set.of(
            "M-040", "M-041", "M-042", "BO-041", "BO-049", "BO-050", "TF-033", "CL-019", "CU-067", "CU-068", "D-004",
            "FD-007", "IS-001", "L-003", "L-017", "P-012", "ST-003", "TR-009", "AT-040", "F-025", "F-031", "F-032",
            "F-036", "F-040", "F-052", "F-063", "F-066", "Q-028", "Q-046", "Q-048", "Q-051", "Q-053", "M-015", "M-051",
            "M-054", "TF-034", "TF-038", "S-023", "BO-003", "BO-005", "E-016", "E-018", "K-051", "R-019", "CU-161",
            "CU-168", "CU-169", "D-015", "D-016", "D-031", "D-054", "X-014", "X-015", "X-016", "Q-008");

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
