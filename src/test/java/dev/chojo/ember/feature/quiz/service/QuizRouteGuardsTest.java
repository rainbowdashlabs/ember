/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.quiz.service;

import dev.chojo.ember.api.ApiServer;
import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.api.RefusalResponse;
import dev.chojo.ember.api.StationSession;
import dev.chojo.ember.api.TestSessions;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.feature.quiz.entity.QuizCatalog;
import dev.chojo.ember.feature.quiz.entity.QuizCategory;
import dev.chojo.ember.feature.quiz.entity.QuizQuestion;
import dev.chojo.ember.feature.quiz.entity.QuizTest;
import dev.chojo.ember.feature.quiz.entity.QuizTestAttempt;
import dev.chojo.ember.feature.quiz.entity.TestStatus;
import io.javalin.http.Context;
import io.javalin.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.Arrays;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class QuizRouteGuardsTest {
    private static final int STATION = 3;

    private QuizCatalogService catalogs;
    private QuizQuestionService questions;
    private QuizTestService tests;
    private QuizAttemptService attempts;
    private QuizRouteGuards guards;
    private Context ctx;
    private UserSession session;

    private static Refusal refusalOf(Executable call) {
        return assertThrows(RefusalResponse.class, call).refusal();
    }

    @SuppressWarnings("unchecked")
    private void pathId(int id) {
        Validator<Integer> validator = mock(Validator.class);
        when(validator.get()).thenReturn(id);
        when(ctx.pathParamAsClass("id", Integer.class)).thenReturn(validator);
    }

    @BeforeEach
    void setup() {
        catalogs = mock(QuizCatalogService.class);
        questions = mock(QuizQuestionService.class);
        tests = mock(QuizTestService.class);
        attempts = mock(QuizAttemptService.class);
        guards = new QuizRouteGuards(catalogs, questions, tests, attempts);
        ctx = mock(Context.class);
        session = TestSessions.member(STATION);
        when(ctx.<UserSession>attribute(ApiServer.ATTR_SESSION)).thenReturn(session);
        when(catalogs.findCatalog(anyInt())).thenReturn(Optional.empty());
        when(tests.findTest(anyInt())).thenReturn(Optional.empty());
    }

    private QuizCatalog catalog(int id, int stationId) {
        var catalog = mock(QuizCatalog.class);
        when(catalog.stationId()).thenReturn(stationId);
        when(catalogs.findCatalog(id)).thenReturn(Optional.of(catalog));
        return catalog;
    }

    private QuizTest test(int id, int stationId, TestStatus status) {
        var test = mock(QuizTest.class);
        when(test.stationId()).thenReturn(stationId);
        when(test.status()).thenReturn(status);
        when(tests.findTest(id)).thenReturn(Optional.of(test));
        return test;
    }

    @Test
    void aCatalogAndACategoryOfTheStationAreHandedOutAndAnyOtherIsNotHere() {
        var own = catalog(1, STATION);
        catalog(2, 9);
        var category = mock(QuizCategory.class);
        when(category.stationId()).thenReturn(STATION);
        when(catalogs.findCategory(5)).thenReturn(Optional.of(category));

        assertSame(own, guards.requireOwnedCatalog(ctx, 1));
        assertSame(category, guards.requireOwnedCategory(ctx, 5));
        assertEquals(Refusal.NOT_HERE_OR_NOT_YOURS, refusalOf(() -> guards.requireOwnedCatalog(ctx, 2)));
        assertEquals(Refusal.NOT_HERE_OR_NOT_YOURS, refusalOf(() -> guards.requireOwnedCatalog(ctx, 7)));
    }

    @Test
    void aQuestionIsTheStationsWhenItsCatalogIs() {
        catalog(1, STATION);
        catalog(2, 9);
        var own = mock(QuizQuestion.class);
        when(own.catalogId()).thenReturn(1);
        var foreign = mock(QuizQuestion.class);
        when(foreign.catalogId()).thenReturn(2);
        when(questions.findQuestion(10)).thenReturn(Optional.of(own));
        when(questions.findQuestion(11)).thenReturn(Optional.of(foreign));

        assertSame(own, guards.requireOwnedQuestion(ctx, 10));
        assertEquals(Refusal.NOT_HERE_OR_NOT_YOURS, refusalOf(() -> guards.requireOwnedQuestion(ctx, 11)));
        assertEquals(Refusal.QUIZ_QUESTION_NOT_HERE, refusalOf(() -> guards.requireOwnedQuestion(ctx, 12)));
    }

    @Test
    void anAttemptIsTheStationsWhenItsTestIs() {
        test(20, STATION, TestStatus.values()[0]);
        var attempt = mock(QuizTestAttempt.class);
        when(attempt.testId()).thenReturn(20);
        when(attempts.findAttemptById(30)).thenReturn(Optional.of(attempt));

        assertSame(attempt, guards.requireOwnedAttempt(ctx, 30));
        assertEquals(Refusal.QUIZ_ATTEMPT_NOT_HERE, refusalOf(() -> guards.requireOwnedAttempt(ctx, 31)));
    }

    @Test
    void aRunningTestCannotBeChanged() {
        var idle = Arrays.stream(TestStatus.values())
                .filter(status -> status != TestStatus.ACTIVE)
                .findFirst()
                .orElseThrow();
        var modifiable = test(20, STATION, idle);
        test(21, STATION, TestStatus.ACTIVE);

        pathId(20);
        assertSame(modifiable, guards.requireModifiableTest(ctx));
        pathId(21);
        assertEquals(Refusal.QUIZ_TEST_RUNNING_CANNOT_CHANGE, refusalOf(() -> guards.requireModifiableTest(ctx)));
    }

    @Test
    void anAttemptIsHandedOnlyToTheMemberWhoMadeIt() {
        var mine = mock(QuizTestAttempt.class);
        when(mine.memberId()).thenReturn(TestSessions.MEMBER_ID);
        var theirs = mock(QuizTestAttempt.class);
        when(theirs.memberId()).thenReturn(99);
        when(attempts.findAttemptById(40)).thenReturn(Optional.of(mine));
        when(attempts.findAttemptById(41)).thenReturn(Optional.of(theirs));

        var member = StationSession.of(session);
        pathId(40);
        assertSame(mine, guards.requireMemberAttempt(ctx, member));
        pathId(41);
        assertEquals(Refusal.QUIZ_ATTEMPT_NOT_YOURS, refusalOf(() -> guards.requireMemberAttempt(ctx, member)));
        pathId(42);
        assertEquals(
                Refusal.QUIZ_ATTEMPT_NOT_HERE_FOR_MEMBER, refusalOf(() -> guards.requireMemberAttempt(ctx, member)));
    }
}
