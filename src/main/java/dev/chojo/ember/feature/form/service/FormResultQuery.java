/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.form.service;

import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.form.service.FormRespondents.Respondent;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Which responses of a form to count, and how to split them into groups.
 *
 * @param filter  which respondents count at all; {@code null} counts everybody
 * @param groupBy how to split them; {@code null} counts them as one group
 */
public record FormResultQuery(Filter filter, Grouping groupBy) {

    /** Whether a respondent has to belong to one of several groups or tags, or to all of them. */
    public enum Match {
        ANY,
        ALL
    }

    /** What results can be grouped by. */
    public enum Dimension {
        USER_TYPE,
        GROUP,
        TAG,
        FIELD,
        AGE
    }

    /**
     * Which respondents count. Conditions on different attributes must all hold; within groups or
     * tags the match decides between "any of" and "all of", and within user types and profile field
     * values it is always "any of", since a member has only one of each. An empty condition does not
     * restrict.
     *
     * @param userTypes  the user types that count
     * @param groupIds   the groups that count
     * @param groupMatch whether a respondent needs one of the groups or all of them
     * @param tagIds     the tags that count
     * @param tagMatch   whether a respondent needs one of the tags or all of them
     * @param fields     conditions on profile fields
     * @param ageFrom    the youngest age that counts, inclusive
     * @param ageTo      the oldest age that counts, inclusive
     */
    public record Filter(
            List<StationUserType> userTypes,
            List<Integer> groupIds,
            Match groupMatch,
            List<Integer> tagIds,
            Match tagMatch,
            List<FieldCondition> fields,
            Integer ageFrom,
            Integer ageTo) {

        /**
         * Whether a respondent passes every condition of this filter.
         *
         * <p>A respondent of unknown age passes no age condition, because nothing says they are old
         * enough; the same holds for a profile field they have not answered.
         */
        public boolean matches(Respondent respondent) {
            if (notEmpty(userTypes) && !userTypes.contains(respondent.userType())) return false;
            if (!belongs(respondent.groupIds(), groupIds, groupMatch)) return false;
            if (!belongs(respondent.tagIds(), tagIds, tagMatch)) return false;
            if ((ageFrom != null || ageTo != null) && !withinAge(respondent.age())) return false;
            if (fields != null) {
                for (var condition : fields) {
                    if (!condition.matches(respondent.fieldValues().get(condition.fieldId()))) return false;
                }
            }
            return true;
        }

        private boolean withinAge(Integer age) {
            if (age == null) return false;
            return (ageFrom == null || age >= ageFrom) && (ageTo == null || age <= ageTo);
        }

        private static boolean belongs(Set<Integer> held, List<Integer> wanted, Match match) {
            if (!notEmpty(wanted)) return true;
            return match == Match.ALL
                    ? held.containsAll(wanted)
                    : wanted.stream().anyMatch(held::contains);
        }
    }

    /**
     * A condition on one profile field. For a choice field the answer has to be one of
     * {@code values}; for a yes/no field {@code values} holds {@code "true"}, {@code "false"} or both;
     * for a number field the answer has to lie between {@code from} and {@code to}, both inclusive.
     *
     * @param fieldId the profile field
     * @param values  the answers that count, for choice and yes/no fields
     * @param from    the smallest number that counts
     * @param to      the largest number that counts
     */
    public record FieldCondition(int fieldId, List<String> values, Double from, Double to) {
        boolean matches(String answer) {
            if (answer == null) return false;
            if (notEmpty(values) && values.stream().noneMatch(value -> Objects.equals(value, answer))) return false;
            if (from == null && to == null) return true;
            var number = numberOf(answer);
            return number != null && (from == null || number >= from) && (to == null || number <= to);
        }
    }

    /**
     * How to split respondents into groups.
     *
     * @param by      what to group by
     * @param fieldId the profile field, when grouping by one
     * @param only    the group keys to show, to compare a chosen few; empty shows every group that
     *                has a response
     * @param bounds  where the brackets start, when grouping by age or a number field: {@code 14, 18}
     *                makes "0-13", "14-17" and "18+"; empty groups numbers by their value
     */
    public record Grouping(Dimension by, Integer fieldId, List<String> only, List<Integer> bounds) {}

    static boolean notEmpty(List<?> list) {
        return list != null && !list.isEmpty();
    }

    static Double numberOf(String answer) {
        try {
            return Double.parseDouble(answer.strip());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
