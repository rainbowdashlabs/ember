/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.question;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * What a refusal says, code by code.
 */
class QuestionProblemTest {

    private static String message(QuestionProblem.Code code, String detail) {
        return new QuestionProblem(code, "Größe", detail).message();
    }

    @Test
    void everyCodeNamesTheField() {
        assertEquals("Field 'Größe' is required", message(QuestionProblem.Code.REQUIRED, null));
        assertEquals(
                "Field 'Größe' does not allow the value 'XXL'", message(QuestionProblem.Code.NOT_AN_OPTION, "XXL"));
        assertEquals("Field 'Größe' expects a number", message(QuestionProblem.Code.NOT_A_NUMBER, null));
        assertEquals(
                "Field 'Größe' expects a number of at most 5",
                message(QuestionProblem.Code.OUT_OF_RANGE, "of at most 5"));
        assertEquals("Field 'Größe' expects a date", message(QuestionProblem.Code.NOT_A_DATE, null));
        assertEquals("Field 'Größe' expects a time", message(QuestionProblem.Code.NOT_A_TIME, null));
        assertEquals("Field 'Größe' expects yes or no", message(QuestionProblem.Code.NOT_A_BOOLEAN, null));
        assertEquals("Field 'Größe' expects a web address", message(QuestionProblem.Code.NOT_A_URL, null));
        assertEquals("Field 'Größe' expects a member", message(QuestionProblem.Code.NOT_A_MEMBER, "x"));
        assertEquals(
                "Field 'Größe' only takes members of its group",
                message(QuestionProblem.Code.NOT_ELIGIBLE, "of its group"));
        assertEquals(
                "Field 'Größe' is missing its tag reference", message(QuestionProblem.Code.MISSING_REFERENCE, "tag"));
    }
}
