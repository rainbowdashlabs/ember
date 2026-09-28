/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.form.entity;

import dev.chojo.ember.feature.form.entity.FormQuestionConfig.Option;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FormQuestionConfigTest {

    private static final Option YES = new Option("k1", "Ja");
    private static final Option NO = new Option("k2", "Nein");

    @Test
    void parsesConfigWithoutDiscriminator() {
        var config = FormQuestionConfig.parse(FormQuestionType.LIKERT, """
                {"statements":[{"key":"a","label":"A"},{"key":"b","label":"B"}],"scaleMin":1,"scaleMax":5,"scaleLabels":[]}""");
        var likert = assertInstanceOf(FormQuestionConfig.Likert.class, config);
        assertEquals(List.of(new Option("a", "A"), new Option("b", "B")), likert.statements());
    }

    @Test
    void parsesConfigWithDiscriminator() {
        var config = FormQuestionConfig.parse(FormQuestionType.CHOICE, """
                {"questionType":"CHOICE","options":[{"key":"k1","label":"Ja"},{"key":"k2","label":"Nein"}],"multiSelect":false}""");
        var choice = assertInstanceOf(FormQuestionConfig.Choice.class, config);
        assertEquals(List.of(YES, NO), choice.options());
    }

    @Test
    void numberedOptionsAreKeyedByTheirPosition() {
        assertEquals(List.of(new Option("o0", "Ja"), new Option("o1", "Nein")), Option.numbered("Ja", "Nein"));
    }

    @Test
    void aChoiceAcceptsKnownKeysAndRefusesUnknownOrRepeatedOnes() {
        var choice = new FormQuestionConfig.Choice(List.of(YES, NO), true, false, false, null, null);

        assertTrue(choice.validate(new FormAnswerValue.Choice(List.of("k2", "k1"), null))
                .isEmpty());
        assertFalse(
                choice.validate(new FormAnswerValue.Choice(List.of("0"), null)).isEmpty(), "a position is no key");
        assertFalse(choice.validate(new FormAnswerValue.Choice(List.of("k1", "k1"), null))
                .isEmpty());
    }

    @Test
    void aRankingNeedsEveryOptionExactlyOnce() {
        var ranking = new FormQuestionConfig.Ranking(List.of(YES, NO));

        assertTrue(ranking.validate(new FormAnswerValue.Ranking(List.of("k2", "k1")))
                .isEmpty());
        assertFalse(ranking.validate(new FormAnswerValue.Ranking(List.of("k1", "k1")))
                .isEmpty());
        assertFalse(ranking.validate(new FormAnswerValue.Ranking(List.of("k1", "k3")))
                .isEmpty());
        assertFalse(ranking.validate(new FormAnswerValue.Ranking(List.of("k1"))).isEmpty());
    }

    @Test
    void aLikertRatingMustNameARealStatement() {
        var likert = new FormQuestionConfig.Likert(List.of(YES, NO), 1, 5, List.of());

        assertTrue(likert.validate(new FormAnswerValue.Likert(Map.of("k1", 3))).isEmpty());
        assertEquals(List.of("Unknown statement: 0"), likert.validate(new FormAnswerValue.Likert(Map.of("0", 3))));
        assertFalse(likert.validate(new FormAnswerValue.Likert(Map.of("k2", 9))).isEmpty());
    }

    @Test
    void optionKeysMustBePresentAndDistinct() {
        assertTrue(new FormQuestionConfig.Ranking(List.of(YES, NO)).hasDistinctOptionKeys());
        assertFalse(new FormQuestionConfig.Ranking(List.of(YES, new Option("k1", "Nein"))).hasDistinctOptionKeys());
        assertFalse(new FormQuestionConfig.Ranking(List.of(YES, new Option(null, "Nein"))).hasDistinctOptionKeys());
        assertTrue(new FormQuestionConfig.Text(false).hasDistinctOptionKeys(), "no options, nothing to clash");
    }

    @Test
    void anAnswerLosesOnlyTheRemovedOptions() {
        assertEquals(
                Optional.of(new FormAnswerValue.Choice(List.of("k1"), null)),
                new FormAnswerValue.Choice(List.of("k1", "k2"), null).withoutOptions(Set.of("k2")));
        assertEquals(
                Optional.empty(),
                new FormAnswerValue.Choice(List.of("k2"), " ").withoutOptions(Set.of("k2")),
                "nothing selected and no other text leaves nothing");
        assertEquals(
                Optional.of(new FormAnswerValue.Ranking(List.of("k1"))),
                new FormAnswerValue.Ranking(List.of("k2", "k1")).withoutOptions(Set.of("k2")));
        assertEquals(Optional.empty(), new FormAnswerValue.Likert(Map.of("k2", 1)).withoutOptions(Set.of("k2")));
        var text = new FormAnswerValue.Text("x");
        assertEquals(Optional.of(text), text.withoutOptions(Set.of("k2")));
        assertEquals(Set.of(), text.optionKeys());
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
