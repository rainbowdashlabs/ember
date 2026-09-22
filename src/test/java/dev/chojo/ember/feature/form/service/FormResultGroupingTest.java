/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.form.service;

import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.form.service.FormRespondents.Respondent;
import dev.chojo.ember.feature.form.service.FormResultGrouping.Bucket;
import dev.chojo.ember.feature.form.service.FormResultQuery.Dimension;
import dev.chojo.ember.feature.form.service.FormResultQuery.Grouping;
import dev.chojo.ember.feature.members.entity.MemberGroup;
import dev.chojo.ember.feature.members.entity.ProfileField;
import dev.chojo.ember.feature.members.entity.ProfileFieldConfig;
import dev.chojo.ember.feature.members.entity.ProfileFieldType;
import dev.chojo.ember.feature.members.entity.UserTag;
import dev.chojo.ember.feature.members.repository.MemberGroupRepository;
import dev.chojo.ember.feature.members.repository.ProfileFieldRepository;
import dev.chojo.ember.feature.members.repository.UserTagRepository;
import io.javalin.http.BadRequestResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * How the respondents of a form are split into the groups its results are counted by.
 */
class FormResultGroupingTest {
    private static final int STATION = 1;
    private static final int YOUTH = 10;
    private static final int ACTIVE = 11;
    private static final int OFFICERS = 12;
    private static final int DRIVER = 20;
    private static final int SHIRT = 30;
    private static final int FIRST_AID = 31;
    private static final int YEARS = 32;

    private FormResultGrouping grouping;

    @BeforeEach
    void setup() {
        var groups = mock(MemberGroupRepository.class);
        when(groups.findByStation(STATION))
                .thenReturn(List.of(
                        new MemberGroup(YOUTH, STATION, "Jugend", null, 3),
                        new MemberGroup(ACTIVE, STATION, "Aktive", null, 2),
                        new MemberGroup(OFFICERS, STATION, "Vorstand", null, 1)));
        var tags = mock(UserTagRepository.class);
        when(tags.findByStation(STATION)).thenReturn(List.of(new UserTag(DRIVER, STATION, "Fahrer", null, true, 1)));
        var fields = mock(ProfileFieldRepository.class);
        when(fields.findByStation(STATION))
                .thenReturn(List.of(
                        field(SHIRT, ProfileFieldType.ENUM, List.of("S", "M", "L")),
                        field(FIRST_AID, ProfileFieldType.BOOLEAN, null),
                        field(YEARS, ProfileFieldType.NUMBER, null),
                        field(40, ProfileFieldType.TEXT, null)));
        grouping = new FormResultGrouping(groups, tags, fields);
    }

    @Test
    void byUserTypeInTheOrderOfTheTypes() {
        var buckets = split(
                new Grouping(Dimension.USER_TYPE, null, null, null),
                person(1, StationUserType.MEMBER),
                person(2, StationUserType.TRIAL),
                person(3, StationUserType.MEMBER),
                anonymous(4));

        assertEquals(List.of("TRIAL", "MEMBER", FormResultGrouping.NONE), keys(buckets));
        assertEquals(Set.of(1, 3), buckets.get(1).ids());
        assertEquals(Set.of(4), buckets.get(2).ids(), "a response without a member lands in none");
    }

    @Test
    void aMemberOfTwoGroupsCountsInBothAndOneOfNoneCountsApart() {
        var buckets = split(
                new Grouping(Dimension.GROUP, null, null, null),
                inGroups(1, YOUTH, ACTIVE),
                inGroups(2, ACTIVE),
                inGroups(3));

        assertEquals(List.of(String.valueOf(YOUTH), String.valueOf(ACTIVE), FormResultGrouping.NONE), keys(buckets));
        assertEquals("Jugend", buckets.getFirst().label());
        assertEquals(Set.of(1, 2), buckets.get(1).ids());
        assertEquals(Set.of(3), buckets.get(2).ids());
        assertTrue(FormResultGrouping.overlaps(new Grouping(Dimension.GROUP, null, null, null)));
        assertFalse(FormResultGrouping.overlaps(new Grouping(Dimension.AGE, null, null, null)));
    }

    @Test
    void choosingTwoGroupsShowsExactlyThoseEvenWhenOneIsEmpty() {
        var only = List.of(String.valueOf(YOUTH), String.valueOf(OFFICERS));
        var buckets = split(
                new Grouping(Dimension.GROUP, null, only, null), inGroups(1, YOUTH), inGroups(2, ACTIVE), inGroups(3));

        assertEquals(only, keys(buckets), "comparing two groups must not quietly turn into looking at one");
        assertTrue(buckets.get(1).ids().isEmpty());
    }

    @Test
    void byTag() {
        var buckets = split(new Grouping(Dimension.TAG, null, null, null), tagged(1, DRIVER), tagged(2));

        assertEquals(List.of(String.valueOf(DRIVER), FormResultGrouping.NONE), keys(buckets));
        assertEquals("Fahrer", buckets.getFirst().label());
    }

    @Test
    void ageBracketsEndOneBelowTheNextBound() {
        var buckets = split(
                new Grouping(Dimension.AGE, null, null, null),
                aged(1, 13),
                aged(2, 14),
                aged(3, 17),
                aged(4, 18),
                aged(5, 60),
                aged(6, null));

        assertEquals(List.of("< 14", "14-17", "18-26", "60+", FormResultGrouping.NONE), keys(buckets));
        assertEquals(Set.of(1), buckets.getFirst().ids());
        assertEquals(Set.of(2, 3), buckets.get(1).ids());
        assertEquals(Set.of(6), buckets.getLast().ids(), "an unknown age is a group of its own");
    }

    @Test
    void theReaderChoosesTheBrackets() {
        var buckets = split(new Grouping(Dimension.AGE, null, null, List.of(30, 16)), aged(1, 15), aged(2, 40));

        assertEquals(List.of("< 16", "30+"), keys(buckets));
    }

    @Test
    void aChoiceFieldGroupsByItsOptionsAndKeepsAnAnswerNoLongerOffered() {
        var buckets = split(
                new Grouping(Dimension.FIELD, SHIRT, null, null),
                answered(1, SHIRT, "L"),
                answered(2, SHIRT, "XXL"),
                answered(3, SHIRT, "M"),
                answered(4, SHIRT, null));

        assertEquals(List.of("M", "L", "XXL", FormResultGrouping.NONE), keys(buckets));
    }

    @Test
    void aYesNoFieldGroupsByItsAnswer() {
        var buckets = split(
                new Grouping(Dimension.FIELD, FIRST_AID, null, null),
                answered(1, FIRST_AID, "true"),
                answered(2, FIRST_AID, "false"));

        assertEquals(List.of("true", "false"), keys(buckets));
    }

    @Test
    void aNumberFieldGroupsByValueOrByBrackets() {
        var byValue = split(
                new Grouping(Dimension.FIELD, YEARS, null, null),
                answered(1, YEARS, "10"),
                answered(2, YEARS, "2"),
                answered(3, YEARS, "10"));
        var byBracket = split(
                new Grouping(Dimension.FIELD, YEARS, null, List.of(5)),
                answered(1, YEARS, "10"),
                answered(2, YEARS, "2"));

        assertEquals(List.of("2", "10"), keys(byValue), "values sort as numbers, not as text");
        assertEquals(List.of("< 5", "5+"), keys(byBracket));
    }

    @Test
    void aFieldThatCannotBeGroupedIsRefused() {
        assertThrows(BadRequestResponse.class, () -> split(new Grouping(Dimension.FIELD, 40, null, null)));
        assertThrows(BadRequestResponse.class, () -> split(new Grouping(Dimension.FIELD, 999, null, null)));
        assertThrows(BadRequestResponse.class, () -> split(new Grouping(null, null, null, null)));
    }

    private List<Bucket> split(Grouping by, Respondent... respondents) {
        return grouping.split(STATION, List.of(respondents), by);
    }

    private static List<String> keys(List<Bucket> buckets) {
        return buckets.stream().map(Bucket::key).toList();
    }

    private static ProfileField field(int id, ProfileFieldType type, List<String> options) {
        var config = new ProfileFieldConfig(null, false, false, options, null, false, null, null, null);
        return new ProfileField(id, STATION, "Feld " + id, type, config, false, false, null, false);
    }

    private static Respondent person(int id, StationUserType type) {
        return new Respondent(id, type, Set.of(), Set.of(), Map.of(), null);
    }

    private static Respondent anonymous(int id) {
        return new Respondent(id, null, Set.of(), Set.of(), Map.of(), null);
    }

    private static Respondent inGroups(int id, Integer... groups) {
        return new Respondent(id, StationUserType.MEMBER, Set.of(groups), Set.of(), Map.of(), null);
    }

    private static Respondent tagged(int id, Integer... tags) {
        return new Respondent(id, StationUserType.MEMBER, Set.of(), Set.of(tags), Map.of(), null);
    }

    private static Respondent aged(int id, Integer age) {
        return new Respondent(id, StationUserType.MEMBER, Set.of(), Set.of(), Map.of(), age);
    }

    private static Respondent answered(int id, int fieldId, String answer) {
        Map<Integer, String> values = answer == null ? Map.of() : Map.of(fieldId, answer);
        return new Respondent(id, StationUserType.MEMBER, Set.of(), Set.of(), values, null);
    }
}
