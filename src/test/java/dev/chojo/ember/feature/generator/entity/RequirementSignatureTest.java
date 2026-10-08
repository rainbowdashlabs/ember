/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.entity;

import org.junit.jupiter.api.Test;

import java.util.List;

import static dev.chojo.ember.feature.generator.entity.RequirementSignatureState.OPEN;
import static dev.chojo.ember.feature.generator.entity.RequirementSignatureState.PAPER_CONFIRMED;
import static dev.chojo.ember.feature.generator.entity.RequirementSignatureState.SIGNED;
import static dev.chojo.ember.feature.generator.entity.RequirementSignatureState.WAIVED;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** Where a copy stands as a whole, read from its fields. */
class RequirementSignatureTest {

    @Test
    void anOpenFieldKeepsTheCopyOpen() {
        assertEquals(OPEN, RequirementSignature.overall(List.of(SIGNED, OPEN, WAIVED)));
    }

    @Test
    void everyFieldLetGoIsWaived() {
        assertEquals(WAIVED, RequirementSignature.overall(List.of(WAIVED, WAIVED)));
    }

    @Test
    void anyFieldOnPaperMakesItPaper() {
        assertEquals(PAPER_CONFIRMED, RequirementSignature.overall(List.of(SIGNED, PAPER_CONFIRMED, WAIVED)));
    }

    @Test
    void otherwiseItIsSigned() {
        assertEquals(SIGNED, RequirementSignature.overall(List.of(SIGNED, WAIVED)));
    }
}
