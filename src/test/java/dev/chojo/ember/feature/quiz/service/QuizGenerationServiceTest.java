/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.quiz.service;

import dev.chojo.ember.api.refusal.QuizRefusal;
import dev.chojo.ember.api.refusal.Refusal;
import dev.chojo.ember.api.refusal.RefusalResponse;
import dev.chojo.ember.feature.quiz.entity.QuestionConfig;
import dev.chojo.ember.feature.quiz.entity.QuestionConfig.MultipleChoice.ChoiceOption;
import dev.chojo.ember.feature.quiz.entity.QuizCatalog;
import dev.chojo.ember.feature.quiz.entity.QuizCategory;
import dev.chojo.ember.feature.quiz.entity.QuizQuestion;
import dev.chojo.ember.feature.quiz.entity.QuizQuestionType;
import dev.chojo.ember.feature.quiz.service.AiService.ChatSession;
import dev.chojo.ember.feature.quiz.service.AiService.GeneratedQuestion;
import dev.chojo.ember.feature.quiz.service.QuizGenerationService.BatchGenerateRequest;
import dev.chojo.ember.feature.quiz.service.QuizGenerationService.GenerateEntry;
import dev.chojo.ember.feature.quiz.service.QuizGenerationService.GenerateQuestionsRequest;
import dev.chojo.ember.lifecycle.TaskScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Questions are written into a job only the asking station can collect, wrong answers are added
 * only to multiple-choice questions short of options, and another station's catalog is refused
 * before anything reaches the provider.
 */
class QuizGenerationServiceTest {
    private static final int STATION = 4;
    private static final int ACCOUNT = 9;
    private static final int CATALOG = 30;

    private final AiService ai = mock(AiService.class);
    private final QuizCatalogService catalogs = mock(QuizCatalogService.class);
    private final QuizQuestionService questions = mock(QuizQuestionService.class);
    private final TaskScheduler scheduler = mock(TaskScheduler.class);
    private final QuizGenerationService service = new QuizGenerationService(ai, catalogs, questions, scheduler);

    @BeforeEach
    void runBackgroundWorkAtOnce() {
        doAnswer(call -> {
                    call.<Runnable>getArgument(1).run();
                    return null;
                })
                .when(scheduler)
                .background(anyString(), any());
    }

    @Test
    void writtenQuestionsAreCollectedByTheAskingStationOnly() {
        catalog(STATION);
        var existing = question(QuizQuestionType.FREE_ANSWER, null);
        when(existing.title()).thenReturn("Old question");
        when(questions.findQuestions(CATALOG)).thenReturn(List.of(existing));
        when(catalogs.findCategories(STATION)).thenReturn(List.of(new QuizCategory(2, STATION, "Knots", "Ropes", 0)));
        var chat = mock(ChatSession.class);
        var titlesSeen = new ArrayList<String>();
        when(ai.createQuestionSession(
                        eq(STATION),
                        eq(ACCOUNT),
                        eq("openai"),
                        isNull(),
                        eq(QuizQuestionType.FREE_ANSWER),
                        isNull(),
                        eq("de"),
                        eq("Knots"),
                        eq("Ropes"),
                        any()))
                .thenAnswer(call -> {
                    titlesSeen.addAll(call.getArgument(9));
                    return chat;
                });
        when(ai.generateNextQuestion(chat, QuizQuestionType.FREE_ANSWER))
                .thenReturn(List.of(new GeneratedQuestion("Bowline?", "{}")))
                .thenThrow(new IllegalStateException("offline"));

        String jobId =
                service.startQuestions(STATION, ACCOUNT, order(new GenerateEntry(QuizQuestionType.FREE_ANSWER, 2, 2)));

        assertEquals(List.of("Old question"), titlesSeen);
        assertEquals(QuizRefusal.AI_GENERATION_NOT_HERE, refusalOf(() -> service.poll(STATION + 1, jobId)));
        var poll = service.poll(STATION, jobId);
        assertTrue(poll.done());
        assertEquals("Bowline?", poll.questions().getFirst().title());
        assertEquals(2, poll.questions().getFirst().categoryId());
        assertEquals(QuizRefusal.AI_GENERATION_NOT_HERE, refusalOf(() -> service.poll(STATION, jobId)));
    }

    @Test
    void entriesThatAskForNothingAreSkipped() {
        when(catalogs.findCategories(STATION)).thenReturn(List.of());
        var request = new GenerateQuestionsRequest(
                null,
                null,
                null,
                null,
                null,
                List.of(new GenerateEntry(null, 2, null), new GenerateEntry(QuizQuestionType.FREE_ANSWER, 0, null)));

        String jobId = service.startQuestions(STATION, ACCOUNT, request);

        assertTrue(service.poll(STATION, jobId).questions().isEmpty());
        verify(ai, never())
                .createQuestionSession(anyInt(), anyInt(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void aRequestWithoutEntriesOrForAnotherStationsCatalogIsRefused() {
        catalog(STATION + 1);

        assertEquals(
                QuizRefusal.AI_GENERATION_NEEDS_ENTRIES,
                refusalOf(() -> service.startQuestions(STATION, ACCOUNT, order())));
        assertEquals(
                QuizRefusal.AI_GENERATION_CATALOG_NOT_HERE,
                refusalOf(() -> service.startQuestions(
                        STATION, ACCOUNT, order(new GenerateEntry(QuizQuestionType.FREE_ANSWER, 1, null)))));
        verifyNoInteractions(scheduler);
    }

    @Test
    void wrongAnswersFillMultipleChoiceQuestionsUpToTheTarget() {
        catalog(STATION);
        var shortOfOptions = question(
                QuizQuestionType.MULTIPLE_CHOICE,
                new QuestionConfig.MultipleChoice(
                        List.of(new ChoiceOption("Red", true), new ChoiceOption("Blue", false)), 1));
        when(shortOfOptions.title()).thenReturn("Colour?");
        var full = question(
                QuizQuestionType.MULTIPLE_CHOICE,
                new QuestionConfig.MultipleChoice(
                        List.of(
                                new ChoiceOption("A", true),
                                new ChoiceOption("B", false),
                                new ChoiceOption("C", false)),
                        1));
        var noRightAnswer = question(
                QuizQuestionType.MULTIPLE_CHOICE,
                new QuestionConfig.MultipleChoice(List.of(new ChoiceOption("A", false)), 1));
        var free = question(QuizQuestionType.FREE_ANSWER, null);
        var failing = question(
                QuizQuestionType.MULTIPLE_CHOICE,
                new QuestionConfig.MultipleChoice(List.of(new ChoiceOption("Yes", true)), 1));
        when(failing.title()).thenReturn("Broken?");
        when(questions.findQuestions(CATALOG)).thenReturn(List.of(shortOfOptions, full, noRightAnswer, free, failing));
        when(ai.generate(STATION, ACCOUNT, "claude", null, "Colour?", "Red", 1)).thenReturn(List.of("Green"));
        when(ai.generate(STATION, ACCOUNT, "claude", null, "Broken?", "Yes", 2))
                .thenThrow(new IllegalStateException("offline"));

        var result = service.fillDistractors(STATION, ACCOUNT, CATALOG, new BatchGenerateRequest("claude", null, 3));

        assertEquals(1, result.generatedCount());
        assertEquals(List.of("Broken?: offline"), result.errors());
        verify(questions)
                .updateQuestion(
                        anyInt(), any(), eq("Colour?"), any(), any(), eq(0.0), eq(false), contains("Green"), anyInt());
    }

    @Test
    void anotherStationsCatalogGetsNoWrongAnswers() {
        catalog(STATION + 1);

        assertEquals(
                QuizRefusal.AI_GENERATION_CATALOG_NOT_HERE,
                refusalOf(() -> service.fillDistractors(
                        STATION, ACCOUNT, CATALOG, new BatchGenerateRequest(null, null, null))));
        verify(questions, never()).findQuestions(anyInt());
        verifyNoInteractions(ai);
    }

    @Test
    void aMissingCatalogIsRefused() {
        when(catalogs.findCatalog(CATALOG)).thenReturn(Optional.empty());

        assertEquals(
                QuizRefusal.AI_GENERATION_CATALOG_NOT_HERE,
                refusalOf(() -> service.fillDistractors(
                        STATION, ACCOUNT, CATALOG, new BatchGenerateRequest(null, null, null))));
        verify(questions, never()).findQuestions(anyInt());
    }

    private void catalog(int stationId) {
        var catalog = mock(QuizCatalog.class);
        when(catalog.stationId()).thenReturn(stationId);
        when(catalogs.findCatalog(CATALOG)).thenReturn(Optional.of(catalog));
    }

    private static QuizQuestion question(QuizQuestionType type, QuestionConfig config) {
        var question = mock(QuizQuestion.class);
        when(question.quizQuestionType()).thenReturn(type);
        when(question.config()).thenReturn(config);
        return question;
    }

    private static GenerateQuestionsRequest order(GenerateEntry... entries) {
        return new GenerateQuestionsRequest(null, null, null, "de", CATALOG, List.of(entries));
    }

    private static Refusal refusalOf(Executable call) {
        return assertThrows(RefusalResponse.class, call).refusal();
    }
}
