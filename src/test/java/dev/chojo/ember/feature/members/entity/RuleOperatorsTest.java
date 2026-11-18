/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.entity;

import dev.chojo.ember.feature.question.FieldType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Which operators a check of a member rule offers, and what each one needs. */
class RuleOperatorsTest {

    @Test
    void groupsAndTagsAskForMembershipOnly() {
        for (var subject : List.of(RuleSubject.GROUP, RuleSubject.TAG)) {
            assertEquals(
                    List.of(RuleOperator.IN, RuleOperator.NOT_IN),
                    List.copyOf(RuleOperators.offered(subject, null).keySet()));
        }
        assertEquals(Optional.of(RuleNeed.NOTHING), RuleOperators.need(RuleSubject.GROUP, null, RuleOperator.IN));
    }

    @Test
    void userTypesAreChosenFromAList() {
        assertEquals(
                Optional.of(RuleNeed.USER_TYPES),
                RuleOperators.need(RuleSubject.USER_TYPE, null, RuleOperator.NONE_OF));
        assertEquals(Optional.empty(), RuleOperators.need(RuleSubject.USER_TYPE, null, RuleOperator.EQ));
    }

    @Test
    void membershipLengthIsADuration() {
        assertEquals(
                Optional.of(RuleNeed.DURATION),
                RuleOperators.need(RuleSubject.MEMBER_SINCE, null, RuleOperator.AT_LEAST));
        assertEquals(
                Optional.of(RuleNeed.DURATION),
                RuleOperators.need(RuleSubject.MEMBER_SINCE, null, RuleOperator.LESS_THAN));
    }

    /** An age counts from a date, so it offers nothing on a field that holds no date. */
    @Test
    void anAgeCountsOnlyFromADate() {
        assertEquals(
                Optional.of(RuleNeed.YEARS_RANGE),
                RuleOperators.need(RuleSubject.AGE, FieldType.BIRTH_DATE, RuleOperator.BETWEEN));
        assertEquals(
                Optional.of(RuleNeed.YEARS),
                RuleOperators.need(RuleSubject.AGE, FieldType.DATE, RuleOperator.AT_LEAST));
        assertTrue(RuleOperators.offered(RuleSubject.AGE, FieldType.TEXT).isEmpty());
        assertTrue(RuleOperators.offered(RuleSubject.AGE, null).isEmpty());
        assertTrue(RuleOperators.countsAgeFrom(FieldType.BIRTH_DATE));
        assertFalse(RuleOperators.countsAgeFrom(FieldType.EXPIRY_DATE));
    }

    @Test
    void aYesOrNoFieldIsAnsweredYesNoOrNotYes() {
        assertEquals(
                List.of(
                        RuleOperator.IS_YES,
                        RuleOperator.IS_NO,
                        RuleOperator.IS_NOT_YES,
                        RuleOperator.EMPTY,
                        RuleOperator.NOT_EMPTY),
                List.copyOf(RuleOperators.offered(RuleSubject.FIELD, FieldType.BOOLEAN)
                        .keySet()));
    }

    @Test
    void aChoiceComparesWithItsAnswers() {
        for (var type : List.of(FieldType.CHOICE, FieldType.GENDER)) {
            assertEquals(
                    Optional.of(RuleNeed.ANSWERS), RuleOperators.need(RuleSubject.FIELD, type, RuleOperator.ONE_OF));
            assertEquals(
                    Optional.of(RuleNeed.NOTHING), RuleOperators.need(RuleSubject.FIELD, type, RuleOperator.EMPTY));
        }
    }

    @Test
    void textsAndNumbersCompareAsTheyAre() {
        assertEquals(
                Optional.of(RuleNeed.TEXT),
                RuleOperators.need(RuleSubject.FIELD, FieldType.URL, RuleOperator.CONTAINS));
        assertEquals(
                Optional.of(RuleNeed.NUMBER_RANGE),
                RuleOperators.need(RuleSubject.CLUSTER_FIELD, FieldType.NUMBER, RuleOperator.BETWEEN));
        assertEquals(Optional.empty(), RuleOperators.need(RuleSubject.FIELD, FieldType.TEXT, RuleOperator.GT));
    }

    /** Only an expiry date knows when it runs out; a plain date offers the date comparisons alone. */
    @Test
    void onlyAnExpiryDateExpires() {
        assertEquals(
                Optional.of(RuleNeed.DURATION),
                RuleOperators.need(RuleSubject.FIELD, FieldType.EXPIRY_DATE, RuleOperator.EXPIRES_WITHIN));
        assertEquals(
                Optional.of(RuleNeed.STATES),
                RuleOperators.need(RuleSubject.FIELD, FieldType.EXPIRY_DATE, RuleOperator.STATE_IN));
        assertEquals(Optional.empty(), RuleOperators.need(RuleSubject.FIELD, FieldType.DATE, RuleOperator.EXPIRED));
        assertEquals(
                Optional.of(RuleNeed.DATE),
                RuleOperators.need(RuleSubject.FIELD, FieldType.BIRTH_DATE, RuleOperator.BEFORE));
    }

    @Test
    void membersAndTimesAreOnlyEmptyOrNot() {
        for (var type : List.of(FieldType.MEMBER, FieldType.MEMBER_LIST, FieldType.TIME)) {
            assertEquals(
                    List.of(RuleOperator.EMPTY, RuleOperator.NOT_EMPTY),
                    List.copyOf(RuleOperators.offered(RuleSubject.FIELD, type).keySet()));
        }
    }

    /** A calculated age and the layout pieces hold nothing a check could read. */
    @ParameterizedTest
    @EnumSource(
            value = FieldType.class,
            names = {"AGE", "SECTION", "SPACER"})
    void fieldsWithoutAnAnswerOfferNothing(FieldType type) {
        assertFalse(RuleOperators.readable(type));
        assertTrue(RuleOperators.offered(RuleSubject.FIELD, type).isEmpty());
        assertTrue(RuleOperators.offered(RuleSubject.CLUSTER_FIELD, type).isEmpty());
        assertTrue(RuleOperators.offered(RuleSubject.FIELD, null).isEmpty());
    }

    /** The schema's check constraints list every subject and operator the code knows, and nothing else. */
    @Test
    void theSchemaKnowsEverySubjectAndOperator() throws Exception {
        String patch = Files.readString(Path.of("src/main/resources/database/postgresql/1/patch_91.sql"));
        assertEquals(
                Arrays.stream(RuleSubject.values()).map(Enum::name).collect(Collectors.toSet()),
                quoted(patch, "subject"));
        assertEquals(
                Arrays.stream(RuleOperator.values()).map(Enum::name).collect(Collectors.toSet()),
                quoted(patch, "operator"));
    }

    private static Set<String> quoted(String patch, String column) {
        var matcher = Pattern.compile("(?s)\\b" + column + "\\s+TEXT\\s+NULL CHECK \\(" + column + " IN \\((.*?)\\)\\)")
                .matcher(patch);
        assertTrue(matcher.find(), column);
        var names = Pattern.compile("'([A-Z_]+)'").matcher(matcher.group(1));
        var found = new HashSet<String>();
        while (names.find()) found.add(names.group(1));
        return found;
    }

    @Test
    void dateChecksCountFromAReferenceDay() {
        assertTrue(RuleOperators.countsFromReference(RuleSubject.AGE, RuleOperator.AT_LEAST));
        assertTrue(RuleOperators.countsFromReference(RuleSubject.MEMBER_SINCE, RuleOperator.LESS_THAN));
        assertTrue(RuleOperators.countsFromReference(RuleSubject.FIELD, RuleOperator.EXPIRES_WITHIN));
        assertTrue(RuleOperators.countsFromReference(RuleSubject.FIELD, RuleOperator.WITHIN_LAST));
        assertFalse(RuleOperators.countsFromReference(RuleSubject.FIELD, RuleOperator.BEFORE));
        assertFalse(RuleOperators.countsFromReference(RuleSubject.GROUP, RuleOperator.IN));
    }
}
