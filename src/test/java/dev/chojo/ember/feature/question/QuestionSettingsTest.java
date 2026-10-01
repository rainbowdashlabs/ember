/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.question;

import dev.chojo.ember.api.auth.StationUserType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The shared settings of a question and the question a field type asks under them.
 */
class QuestionSettingsTest {

    @Test
    void everySettingIsKeptByEveryOther() {
        var settings = QuestionSettings.none()
                .withRequired(true)
                .withDefault(5)
                .withOptions(List.of("S", "M"))
                .withBounds(1, 9)
                .withStep(new BigDecimal("0.5"))
                .withWidth("half")
                .withMembers(3, StationUserType.TEAM, 7);
        assertTrue(settings.required());
        assertEquals("5", settings.defaultValue());
        assertEquals(List.of("S", "M"), settings.options());
        assertEquals(BigDecimal.ONE, settings.min());
        assertEquals(BigDecimal.valueOf(9), settings.max());
        assertEquals(new BigDecimal("0.5"), settings.step());
        assertEquals("half", settings.width());
        assertEquals(3, settings.groupId());
        assertEquals(StationUserType.TEAM, settings.userType());
        assertEquals(7, settings.tagId());
    }

    @Test
    void nothingIsSetUntilItIsSet() {
        var settings =
                QuestionSettings.none().withOptions(null).withDefault(null).withBounds((Integer) null, null);
        assertFalse(settings.required());
        assertNull(settings.defaultValue());
        assertEquals(List.of(), settings.options());
        assertNull(settings.min());
        assertNull(settings.step());
        assertNull(settings.width());
        assertNull(settings.groupId());
    }

    /** A number is whole by default and takes a fraction only with a step below one. */
    @Test
    void aStepBelowOneMakesANumberADecimal() {
        assertFalse(QuestionSettings.none().takesFractions());
        assertFalse(QuestionSettings.none().withStep(BigDecimal.ONE).takesFractions());
        assertTrue(QuestionSettings.none().withStep(new BigDecimal("0.1")).takesFractions());

        assertEquals(QuestionKind.NUMBER, kindOf(QuestionSettings.none(), FieldType.NUMBER));
        assertEquals(
                QuestionKind.DECIMAL,
                kindOf(QuestionSettings.none().withStep(new BigDecimal("0.25")), FieldType.NUMBER));
    }

    @Test
    void aNumberCarriesItsBoundsWhetherWholeOrNot() {
        var settings = QuestionSettings.none().withBounds(1, 5).withStep(new BigDecimal("0.5"));
        var question = settings.asQuestion("Länge", FieldType.NUMBER).orElseThrow();
        assertEquals(new QuestionRules.Bounds(BigDecimal.ONE, BigDecimal.valueOf(5)), question.rules());
    }

    @Test
    void aChoiceCarriesItsOptions() {
        var question = QuestionSettings.required(true)
                .withOptions(List.of("S", "M"))
                .withDefault("S")
                .asQuestion("Größe", FieldType.CHOICE)
                .orElseThrow();
        assertEquals(new QuestionRules.Choice(List.of("S", "M")), question.rules());
        assertTrue(question.required());
        assertEquals("S", question.defaultValue());
        assertEquals("Größe", question.name());
    }

    @Test
    void aTypeThatHoldsNothingAsksNothing() {
        assertTrue(QuestionSettings.none().asQuestion("Alter", FieldType.AGE).isEmpty());
        assertTrue(QuestionSettings.none().asQuestion("Kopf", FieldType.SECTION).isEmpty());
        assertTrue(QuestionSettings.none().asQuestion("Lücke", FieldType.SPACER).isEmpty());
    }

    @Test
    void aMemberFieldCarriesTheNarrowingItsTypeNames() {
        var settings = QuestionSettings.none().withMembers(3, StationUserType.TEAM, 7);
        assertEquals(
                new QuestionRules.Members(MemberConstraint.GROUP, 3, null),
                rulesOf(settings, FieldType.MEMBER_LIST_OF_GROUP));
        assertEquals(
                new QuestionRules.Members(MemberConstraint.USER_TYPE, null, StationUserType.TEAM),
                rulesOf(settings, FieldType.MEMBER_OF_TYPE));
        assertEquals(
                new QuestionRules.Members(MemberConstraint.TAG, 7, null), rulesOf(settings, FieldType.MEMBER_OF_TAG));
        assertInstanceOf(QuestionRules.None.class, rulesOf(settings, FieldType.MEMBER));
        assertInstanceOf(QuestionRules.None.class, rulesOf(settings, FieldType.LANE_ASSIGNEE));
    }

    @Test
    void otherKindsHaveNoRules() {
        assertInstanceOf(QuestionRules.None.class, rulesOf(QuestionSettings.none(), FieldType.LOCATION));
        assertEquals(QuestionKind.TEXT, kindOf(QuestionSettings.none(), FieldType.LOCATION));
        assertEquals(QuestionKind.DATE, kindOf(QuestionSettings.none(), FieldType.BIRTH_DATE));
    }

    private static QuestionKind kindOf(QuestionSettings settings, FieldType type) {
        return settings.asQuestion("Feld", type).orElseThrow().kind();
    }

    private static QuestionRules rulesOf(QuestionSettings settings, FieldType type) {
        return settings.asQuestion("Feld", type).orElseThrow().rules();
    }
}
