/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.entity;

import dev.chojo.ember.feature.question.FieldType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** A field a rule can read, with the answers a choice offers. */
class RuleFieldTest {

    @Test
    void aChoiceCarriesItsAnswers() {
        var field = RuleField.of(
                FieldOrigin.STATION,
                3,
                "Jugendflamme",
                FieldType.CHOICE,
                ProfileFieldConfig.parse("{\"options\":[\"Stufe 1\",\"Stufe 2\"]}"));
        assertEquals(List.of("Stufe 1", "Stufe 2"), field.options());
    }

    @Test
    void aFieldWithoutAnswersCarriesNone() {
        var field = RuleField.of(FieldOrigin.CLUSTER, 4, "JuLeiCa", FieldType.BOOLEAN, ProfileFieldConfig.empty());
        assertEquals(List.of(), field.options());
    }
}
