/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.question;

import dev.chojo.ember.api.auth.StationUserType;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The narrowing of a member field to a group, a user type or a tag, measured by the one check.
 */
class MemberEligibilityCheckTest {

    /** Members 1 and 2 are in group 10, of type TEAM and tagged 20; member 3 is none of it. */
    private static final MemberEligibility STATION = new MemberEligibility() {
        private final Set<Integer> inside = Set.of(1, 2);

        @Override
        public boolean inGroup(int memberId, int groupId) {
            return groupId == 10 && inside.contains(memberId);
        }

        @Override
        public boolean ofType(int memberId, StationUserType userType) {
            return userType == StationUserType.TEAM && inside.contains(memberId);
        }

        @Override
        public boolean hasTag(int memberId, int tagId) {
            return tagId == 20 && inside.contains(memberId);
        }
    };

    private static Question question(FieldType type, QuestionSettings settings) {
        return settings.asQuestion("Fahrer", type).orElseThrow();
    }

    private static Optional<QuestionProblem.Code> code(Optional<QuestionProblem> problem) {
        return problem.map(QuestionProblem::code);
    }

    @Test
    void aGroupFieldTakesOnlyMembersOfItsGroup() {
        var question =
                question(FieldType.MEMBER_LIST_OF_GROUP, QuestionSettings.none().withMembers(10, null, null));
        assertTrue(QuestionCheck.answer(question, "[1,2]", STATION).isEmpty());
        assertEquals(
                Optional.of(QuestionProblem.Code.NOT_ELIGIBLE), code(QuestionCheck.answer(question, "[1,3]", STATION)));
    }

    @Test
    void aUserTypeFieldTakesOnlyMembersOfItsType() {
        var question = question(
                FieldType.MEMBER_OF_TYPE, QuestionSettings.none().withMembers(null, StationUserType.TEAM, null));
        assertTrue(QuestionCheck.answerIfGiven(question, "2", STATION).isEmpty());
        assertEquals(
                Optional.of(QuestionProblem.Code.NOT_ELIGIBLE),
                code(QuestionCheck.answerIfGiven(question, "3", STATION)));
    }

    @Test
    void aTagFieldTakesOnlyMembersWithItsTag() {
        var question = question(FieldType.MEMBER_OF_TAG, QuestionSettings.none().withMembers(null, null, 20));
        assertTrue(QuestionCheck.answer(question, "1", STATION).isEmpty());
        var refused = QuestionCheck.answer(question, "3", STATION).orElseThrow();
        assertEquals(QuestionProblem.Code.NOT_ELIGIBLE, refused.code());
        assertEquals("Field 'Fahrer' only takes members with its tag", refused.message());
    }

    /** A field whose group is gone refuses everybody rather than taking anybody. */
    @Test
    void aFieldThatLostItsReferenceRefusesEverybody() {
        for (var type :
                new FieldType[] {FieldType.MEMBER_OF_GROUP, FieldType.MEMBER_OF_TYPE, FieldType.MEMBER_OF_TAG}) {
            var refused = QuestionCheck.answer(question(type, QuestionSettings.none()), "1", STATION)
                    .orElseThrow();
            assertEquals(QuestionProblem.Code.MISSING_REFERENCE, refused.code(), type.name());
        }
        var group = QuestionCheck.answer(question(FieldType.MEMBER_OF_GROUP, QuestionSettings.none()), "1", STATION);
        assertEquals(
                "Field 'Fahrer' is missing its group reference",
                group.orElseThrow().message());
    }

    @Test
    void aFieldWithoutNarrowingTakesAnyMember() {
        assertTrue(QuestionCheck.answer(question(FieldType.MEMBER, QuestionSettings.none()), "3", STATION)
                .isEmpty());
    }

    /** What the kind refuses is said first: a word is not a member, whoever is in the group. */
    @Test
    void theKindIsCheckedBeforeTheNarrowing() {
        var question =
                question(FieldType.MEMBER_OF_GROUP, QuestionSettings.none().withMembers(10, null, null));
        assertEquals(
                Optional.of(QuestionProblem.Code.NOT_A_MEMBER), code(QuestionCheck.answer(question, "Paul", STATION)));
    }

    @Test
    void nothingAnsweredIsNotMeasuredAgainstTheNarrowing() {
        var question =
                question(FieldType.MEMBER_OF_GROUP, QuestionSettings.none().withMembers(10, null, null));
        assertTrue(QuestionCheck.answer(question, "", STATION).isEmpty());
        assertTrue(QuestionCheck.answerIfGiven(question, null, STATION).isEmpty());
    }

    /** A default member is measured like an answer where nothing else was given. */
    @Test
    void aDefaultMemberIsMeasuredToo() {
        var question = question(
                FieldType.MEMBER_OF_GROUP,
                QuestionSettings.none().withMembers(10, null, null).withDefault("3"));
        assertEquals(
                Optional.of(QuestionProblem.Code.NOT_ELIGIBLE), code(QuestionCheck.answer(question, null, STATION)));
    }

    /** The two overloads without eligibility keep today's acceptance: no narrowing is checked. */
    @Test
    void withoutEligibilityTheNarrowingIsNotChecked() {
        var question =
                question(FieldType.MEMBER_OF_GROUP, QuestionSettings.none().withMembers(10, null, null));
        assertTrue(QuestionCheck.answer(question, "3").isEmpty());
    }
}
