/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.quiz.route;

import dev.chojo.ember.api.RouteHarness;
import dev.chojo.ember.api.TestSessions;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.refusal.BodyRefusal;
import dev.chojo.ember.api.refusal.QuizRefusal;
import dev.chojo.ember.feature.quiz.entity.AiVendor;
import dev.chojo.ember.feature.quiz.entity.StationAiProvider;
import dev.chojo.ember.feature.quiz.service.AiService;
import dev.chojo.ember.feature.quiz.service.QuizGenerationService;
import dev.chojo.ember.feature.quiz.service.QuizGenerationService.BatchGenerateRequest;
import dev.chojo.ember.feature.quiz.service.QuizGenerationService.BatchResult;
import dev.chojo.ember.feature.quiz.service.QuizGenerationService.GenerationPollResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static dev.chojo.ember.api.RouteHarness.PREFIX;
import static dev.chojo.ember.api.RouteHarness.body;
import static dev.chojo.ember.api.RouteHarness.json;
import static dev.chojo.ember.api.RouteHarness.refusalOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Question and answer generation over HTTP: each request is handed to the generation service
 * together with the reader's station and account.
 */
class AiRoutesTest {
    private static final int STATION = 3;

    private final AiService ai = mock(AiService.class);
    private final QuizGenerationService generation = mock(QuizGenerationService.class);
    private final RouteHarness harness = RouteHarness.serving(new AiRoutes(ai, generation));

    @Test
    void aProviderIsNamedAndAnsweredByItsConstant() {
        when(ai.getProviders(STATION))
                .thenReturn(List.of(new StationAiProvider(1, STATION, AiVendor.CLAUDE, "k", null)));

        harness.run((server, client) -> {
            var editor = harness.as(TestSessions.member(STATION, StationPermission.TEST_CATALOG_EDIT));
            assertEquals(
                    200,
                    client.put(PREFIX + "/ai/providers/GEMINI", body("""
                            {"apiKey":"sk-g","model":"gemini-pro"}"""), editor)
                            .code());
            assertEquals(
                    "CLAUDE",
                    json(client.get(PREFIX + "/ai/settings", editor))
                            .get("providers")
                            .get(0)
                            .get("provider")
                            .asString());
        });

        verify(ai).saveProvider(STATION, AiVendor.GEMINI, "sk-g", "gemini-pro");
    }

    @Test
    void aProviderThisInstanceCannotCallIsRefused() {
        harness.run((server, client) -> {
            var editor = harness.as(TestSessions.member(STATION, StationPermission.TEST_CATALOG_EDIT));
            assertEquals(
                    QuizRefusal.AI_KEY_PROVIDER_UNKNOWN,
                    refusalOf(client.put(PREFIX + "/ai/providers/mystery", body("""
                            {"apiKey":"sk"}"""), editor)));
            assertEquals(
                    QuizRefusal.AI_KEY_PROVIDER_UNKNOWN,
                    refusalOf(client.put(PREFIX + "/ai/providers/openai", body("""
                            {"apiKey":"sk"}"""), editor)));
            assertEquals(
                    BodyRefusal.BODY_DOES_NOT_MATCH,
                    refusalOf(client.post(PREFIX + "/ai/generate", body("""
                            {"provider":"mystery","question":"Q","correctAnswer":"A"}"""), editor)));
        });

        verifyNoInteractions(ai);
    }

    @Test
    void questionsAreStartedAndCollectedForTheReadersStation() {
        when(generation.startQuestions(eq(STATION), eq(TestSessions.ACCOUNT_ID), any()))
                .thenReturn("job-1");
        when(generation.poll(STATION, "job-1")).thenReturn(new GenerationPollResponse(List.of(), true));
        when(generation.poll(STATION, "job-2")).thenThrow(QuizRefusal.AI_GENERATION_NOT_HERE.raise());

        harness.run((server, client) -> {
            var editor = harness.as(TestSessions.member(STATION, StationPermission.TEST_CATALOG_EDIT));
            var started = client.post(PREFIX + "/ai/generate-questions", body("""
                    {"entries":[{"quizQuestionType":"FREE_ANSWER","count":2}]}"""), editor);
            assertEquals("job-1", json(started).get("jobId").asString());
            assertTrue(json(client.get(PREFIX + "/ai/generate-questions/job-1", editor))
                    .get("done")
                    .asBoolean());
            assertEquals(
                    QuizRefusal.AI_GENERATION_NOT_HERE,
                    refusalOf(client.get(PREFIX + "/ai/generate-questions/job-2", editor)));
        });
    }

    @Test
    void wrongAnswersAreAddedToTheNamedCatalog() {
        when(generation.fillDistractors(
                        STATION, TestSessions.ACCOUNT_ID, 30, new BatchGenerateRequest(AiVendor.CLAUDE, null, 4)))
                .thenReturn(new BatchResult(2, List.of()));
        when(generation.fillDistractors(eq(STATION), eq(TestSessions.ACCOUNT_ID), eq(31), any()))
                .thenThrow(QuizRefusal.AI_GENERATION_CATALOG_NOT_HERE.raise());

        harness.run((server, client) -> {
            var editor = harness.as(TestSessions.member(STATION, StationPermission.TEST_CATALOG_EDIT));
            var filled = client.post(PREFIX + "/ai/batch-generate/30", body("""
                    {"provider":"CLAUDE","targetTotalOptions":4}"""), editor);
            assertEquals(2, json(filled).get("generatedCount").asInt());
            assertEquals(
                    QuizRefusal.AI_GENERATION_CATALOG_NOT_HERE,
                    refusalOf(client.post(PREFIX + "/ai/batch-generate/31", body("{}"), editor)));
        });
    }
}
