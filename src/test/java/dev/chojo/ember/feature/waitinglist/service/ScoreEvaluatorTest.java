/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.waitinglist.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ScoreEvaluatorTest {

    @Test
    void simpleAddition() {
        assertEquals(5.0, ScoreEvaluator.evaluate("2 + 3", Map.of()));
    }

    @Test
    void multiplication() {
        assertEquals(12.0, ScoreEvaluator.evaluate("3 * 4", Map.of()));
    }

    @Test
    void division() {
        assertEquals(2.5, ScoreEvaluator.evaluate("5 / 2", Map.of()));
    }

    @Test
    void divisionByZero() {
        assertEquals(0.0, ScoreEvaluator.evaluate("5 / 0", Map.of()));
    }

    @Test
    void operatorPrecedence() {
        assertEquals(14.0, ScoreEvaluator.evaluate("2 + 3 * 4", Map.of()));
    }

    @Test
    void parentheses() {
        assertEquals(20.0, ScoreEvaluator.evaluate("(2 + 3) * 4", Map.of()));
    }

    @Test
    void variableSubstitution() {
        assertEquals(16.0, ScoreEvaluator.evaluate("[age] * 2", Map.of("age", "8")));
    }

    @Test
    void multipleVariables() {
        assertEquals(15.0, ScoreEvaluator.evaluate("[a] + [b]", Map.of("a", "10", "b", "5")));
    }

    @Test
    void missingVariableDefaultsToZero() {
        assertEquals(0.0, ScoreEvaluator.evaluate("[missing] * 2", Map.of()));
    }

    @Test
    void equalityComparison() {
        assertEquals(1.0, ScoreEvaluator.evaluate("5 == 5", Map.of()));
        assertEquals(0.0, ScoreEvaluator.evaluate("5 == 3", Map.of()));
    }

    @Test
    void inequalityComparison() {
        assertEquals(1.0, ScoreEvaluator.evaluate("5 != 3", Map.of()));
        assertEquals(0.0, ScoreEvaluator.evaluate("5 != 5", Map.of()));
    }

    @Test
    void greaterLessComparison() {
        assertEquals(1.0, ScoreEvaluator.evaluate("5 > 3", Map.of()));
        assertEquals(0.0, ScoreEvaluator.evaluate("3 > 5", Map.of()));
        assertEquals(1.0, ScoreEvaluator.evaluate("3 < 5", Map.of()));
        assertEquals(1.0, ScoreEvaluator.evaluate("5 >= 5", Map.of()));
        assertEquals(1.0, ScoreEvaluator.evaluate("3 <= 5", Map.of()));
    }

    @Test
    void stringEquality() {
        // String comparison: "m" == "m" should be 1.0 (true)
        assertEquals(1.0, ScoreEvaluator.evaluate("\"m\" == \"m\"", Map.of()));
        assertEquals(0.0, ScoreEvaluator.evaluate("\"m\" == \"f\"", Map.of()));
    }

    @Test
    void stringVariableComparison() {
        // [Geschlecht] substituted with m, wrapped in quotes by user: "[Geschlecht]" == "m"
        assertEquals(1.0, ScoreEvaluator.evaluate("\"[gender]\" == \"m\"", Map.of("gender", "m")));
        assertEquals(0.0, ScoreEvaluator.evaluate("\"[gender]\" == \"m\"", Map.of("gender", "f")));
    }

    @Test
    void ternaryExpression() {
        assertEquals(10.0, ScoreEvaluator.evaluate("1 ? 10 : 20", Map.of()));
        assertEquals(20.0, ScoreEvaluator.evaluate("0 ? 10 : 20", Map.of()));
    }

    @Test
    void ternaryWithComparison() {
        assertEquals(16.0, ScoreEvaluator.evaluate("[age] > 5 ? [age] * 2 : [age]", Map.of("age", "8")));
        assertEquals(3.0, ScoreEvaluator.evaluate("[age] > 5 ? [age] * 2 : [age]", Map.of("age", "3")));
    }

    @Test
    void negation() {
        assertEquals(-5.0, ScoreEvaluator.evaluate("-5", Map.of()));
        assertEquals(-3.0, ScoreEvaluator.evaluate("-(1 + 2)", Map.of()));
    }

    @Test
    void complexFormula() {
        // Realistic scoring: [Alter] * ("[Geschlecht]" == "m" ? 1 : 1.2)
        assertEquals(
                8.0,
                ScoreEvaluator.evaluate(
                        "[Alter] * (\"[Geschlecht]\" == \"m\" ? 1 : 1.2)", Map.of("Alter", "8", "Geschlecht", "m")));
        assertEquals(
                8.4,
                ScoreEvaluator.evaluate(
                        "[Alter] * (\"[Geschlecht]\" == \"m\" ? 1 : 1.2)", Map.of("Alter", "7", "Geschlecht", "w")),
                0.001);
    }

    @Test
    void booleanMultiplication() {
        // (condition) * value + value pattern: 1.0 * 10 + 5 = 15, 0.0 * 10 + 5 = 5
        assertEquals(15.0, ScoreEvaluator.evaluate("(\"[g]\" == \"m\") * 10 + [a]", Map.of("g", "m", "a", "5")));
        assertEquals(5.0, ScoreEvaluator.evaluate("(\"[g]\" == \"m\") * 10 + [a]", Map.of("g", "f", "a", "5")));
    }

    @Test
    void emptyFormulaReturnsZero() {
        assertEquals(0.0, ScoreEvaluator.evaluate("", Map.of()));
        assertEquals(0.0, ScoreEvaluator.evaluate(null, Map.of()));
    }

    @Test
    void invalidFormulaReturnsZero() {
        assertEquals(0.0, ScoreEvaluator.evaluate("invalid stuff", Map.of()));
    }

    @Test
    void validateValidFormula() {
        assertDoesNotThrow(() -> ScoreEvaluator.validate("2 + 3", List.of()));
        assertDoesNotThrow(() -> ScoreEvaluator.validate("[age] * 2", List.of("age")));
        assertDoesNotThrow(() -> ScoreEvaluator.validate("[a] > 5 ? [a] : 0", List.of("a")));
    }

    @Test
    void validateNullOrBlank() {
        assertDoesNotThrow(() -> ScoreEvaluator.validate(null, List.of()));
        assertDoesNotThrow(() -> ScoreEvaluator.validate("", List.of()));
        assertDoesNotThrow(() -> ScoreEvaluator.validate("   ", List.of()));
    }

    @Test
    void validateInvalidFormula() {
        assertThrows(IllegalArgumentException.class, () -> ScoreEvaluator.validate("invalid stuff", List.of()));
    }

    @Test
    void validateUnexpectedTokenAfterExpression() {
        assertThrows(IllegalArgumentException.class, () -> ScoreEvaluator.validate("2 + 3 4", List.of()));
    }

    @Test
    void validateWithBuiltInVariables() {
        assertDoesNotThrow(() -> ScoreEvaluator.validate("[wartezeit_tage] * 2", List.of()));
        assertDoesNotThrow(() -> ScoreEvaluator.validate("[wartezeit_monate] + [wartezeit_jahre]", List.of()));
    }

    @Test
    void validateWithAgeFunction() {
        assertDoesNotThrow(() -> ScoreEvaluator.validate("age([birthdate]) * 2", List.of("birthdate")));
    }

    @Test
    void unclosedBracket() {
        assertEquals(0.0, ScoreEvaluator.evaluate("[unclosed", Map.of()));
    }

    @Test
    void unclosedString() {
        assertEquals(0.0, ScoreEvaluator.evaluate("\"unclosed", Map.of()));
    }

    @Test
    void decimalNumber() {
        assertEquals(3.14, ScoreEvaluator.evaluate("3.14", Map.of()), 0.001);
        assertEquals(6.28, ScoreEvaluator.evaluate(".14 + 6.14", Map.of()), 0.001);
    }

    @Test
    void subtraction() {
        assertEquals(2.0, ScoreEvaluator.evaluate("5 - 3", Map.of()));
    }

    @Test
    void lessThanComparison() {
        assertEquals(0.0, ScoreEvaluator.evaluate("5 < 3", Map.of()));
    }

    @Test
    void nestedParentheses() {
        assertEquals(9.0, ScoreEvaluator.evaluate("((1 + 2)) * 3", Map.of()));
    }

    @Test
    void variableWithNonNumericValue() {
        assertEquals(0.0, ScoreEvaluator.evaluate("[name] + 1", Map.of("name", "abc")));
    }
}
