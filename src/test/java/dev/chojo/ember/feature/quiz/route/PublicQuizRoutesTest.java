/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.quiz.route;

import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.api.RouteHarness;
import dev.chojo.ember.feature.quiz.entity.QuestionConfig;
import dev.chojo.ember.feature.quiz.entity.QuizQuestionType;
import dev.chojo.ember.feature.quiz.service.PublicQuizService;
import dev.chojo.ember.feature.quiz.service.PublicQuizService.PublicQuizCatalog;
import dev.chojo.ember.feature.quiz.service.PublicQuizService.PublicQuizQuestion;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static dev.chojo.ember.api.RouteHarness.PREFIX;
import static dev.chojo.ember.api.RouteHarness.json;
import static dev.chojo.ember.api.RouteHarness.refusalOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PublicQuizRoutesTest {
    private static final UUID STATION = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final String BASE = PREFIX + "/public/quiz/" + STATION;

    @Test
    void theTeaserListsCatalogsAndPicksAQuestion() {
        var quiz = mock(PublicQuizService.class);
        when(quiz.catalogs(STATION)).thenReturn(List.of(new PublicQuizCatalog(1, "Knoten", "")));
        when(quiz.randomQuestion(STATION, List.of(1, 2)))
                .thenReturn(new PublicQuizQuestion(
                        9, QuizQuestionType.TRUE_FALSE, "Welcher?", "", null, new QuestionConfig.TrueFalse(true)));
        var harness = RouteHarness.serving(new PublicQuizRoutes(quiz));

        harness.run((server, client) -> {
            assertEquals(
                    "Knoten",
                    json(client.get(BASE + "/catalogs")).get(0).path("name").asString());
            var picked = client.get(BASE + "/random?catalogs=1,%202,");
            assertEquals(9, json(picked).path("id").asInt());
            assertEquals(Refusal.PUBLIC_QUIZ_CATALOGS_NOT_NAMED, refusalOf(client.get(BASE + "/random")));
            assertEquals(Refusal.PUBLIC_QUIZ_CATALOG_NOT_A_NUMBER, refusalOf(client.get(BASE + "/random?catalogs=x")));
            assertEquals(
                    Refusal.PUBLIC_QUIZ_STATION_LINK_NOT_GOOD,
                    refusalOf(client.get(PREFIX + "/public/quiz/nord/catalogs")));
        });
    }
}
