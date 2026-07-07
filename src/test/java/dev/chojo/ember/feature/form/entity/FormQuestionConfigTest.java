/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.form.entity;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class FormQuestionConfigTest {

    @Test
    void parsesConfigWithoutDiscriminator() {
        var config = FormQuestionConfig.parse(FormQuestionType.LIKERT, """
                {"statements":["A","B"],"scaleMin":1,"scaleMax":5,"scaleLabels":[]}""");
        var likert = assertInstanceOf(FormQuestionConfig.Likert.class, config);
        assertEquals(List.of("A", "B"), likert.statements());
    }

    @Test
    void parsesConfigWithDiscriminator() {
        var config = FormQuestionConfig.parse(FormQuestionType.CHOICE, """
                {"questionType":"CHOICE","options":["Ja","Nein"],"multiSelect":false}""");
        var choice = assertInstanceOf(FormQuestionConfig.Choice.class, config);
        assertEquals(List.of("Ja", "Nein"), choice.options());
    }

    @Test
    void staleDiscriminatorIsOverriddenByQuestionType() {
        var config = FormQuestionConfig.parse(FormQuestionType.DATE, """
                {"questionType": "FormQuestionConfig$Unknown"}""");
        assertInstanceOf(FormQuestionConfig.Date.class, config);
    }

    @Test
    void roundTripKeepsDiscriminatorParseable() {
        var original = new FormQuestionConfig.Rating(5, FormQuestionConfig.Rating.RatingIcon.STAR);
        var parsed = FormQuestionConfig.parse(FormQuestionType.RATING, original.toJson());
        assertEquals(original, parsed);
    }

    @Test
    void unknownSerializesToEmptyObject() {
        assertEquals("{}", new FormQuestionConfig.Unknown().toJson());
    }

    @Test
    void blankAndEmptyConfigsFallBackToUnknown() {
        assertInstanceOf(FormQuestionConfig.Unknown.class, FormQuestionConfig.parse(FormQuestionType.TEXT, null));
        assertInstanceOf(FormQuestionConfig.Unknown.class, FormQuestionConfig.parse(FormQuestionType.TEXT, ""));
        assertInstanceOf(FormQuestionConfig.Unknown.class, FormQuestionConfig.parse(FormQuestionType.TEXT, "{}"));
        assertInstanceOf(FormQuestionConfig.Unknown.class, FormQuestionConfig.parse(FormQuestionType.TEXT, "[1,2]"));
        assertInstanceOf(FormQuestionConfig.Unknown.class, FormQuestionConfig.parse(FormQuestionType.TEXT, "not json"));
    }
}
