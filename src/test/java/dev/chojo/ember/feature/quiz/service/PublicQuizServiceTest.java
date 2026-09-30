/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.quiz.service;

import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.api.RefusalResponse;
import dev.chojo.ember.feature.quiz.entity.QuizCatalog;
import dev.chojo.ember.feature.quiz.entity.QuizQuestion;
import dev.chojo.ember.feature.quiz.entity.QuizQuestionType;
import dev.chojo.ember.feature.quiz.repository.QuizCatalogRepository;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.repository.StationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PublicQuizServiceTest {
    private static final UUID STATION = UUID.fromString("00000000-0000-0000-0000-000000000003");

    private QuizCatalogRepository catalogs;
    private StationRepository stations;
    private PublicQuizService service;

    @BeforeEach
    void setup() {
        catalogs = mock(QuizCatalogRepository.class);
        stations = mock(StationRepository.class);
        service = new PublicQuizService(catalogs, stations);
        var station = mock(Station.class);
        when(station.id()).thenReturn(3);
        when(stations.findByUid(STATION)).thenReturn(Optional.of(station));
    }

    @Test
    void thePublicCatalogsOfTheStationAreListedBareOfEverythingElse() {
        var catalog = mock(QuizCatalog.class);
        when(catalog.id()).thenReturn(1);
        when(catalog.name()).thenReturn("Knoten");
        when(catalog.description()).thenReturn("d");
        when(catalogs.findPublicByStation(3)).thenReturn(List.of(catalog));

        assertEquals(List.of(new PublicQuizService.PublicCatalog(1, "Knoten", "d")), service.catalogs(STATION));
    }

    @Test
    void aRandomQuestionComesFromTheNamedCatalogs() {
        var question = mock(QuizQuestion.class);
        when(question.id()).thenReturn(9);
        when(question.quizQuestionType()).thenReturn(QuizQuestionType.values()[0]);
        when(question.title()).thenReturn("Welcher Knoten?");
        when(catalogs.findRandomPublicQuestion(3, List.of(1, 2))).thenReturn(Optional.of(question));

        var picked = service.randomQuestion(STATION, List.of(1, 2));

        assertEquals(9, picked.id());
        assertEquals("Welcher Knoten?", picked.title());
    }

    @Test
    void nothingToPickFromOrNoStationIsRefused() {
        when(catalogs.findRandomPublicQuestion(anyInt(), any())).thenReturn(Optional.empty());

        assertEquals(
                Refusal.PUBLIC_QUIZ_CATALOGS_EMPTY,
                assertThrows(RefusalResponse.class, () -> service.randomQuestion(STATION, List.of()))
                        .refusal());
        assertEquals(
                Refusal.PUBLIC_QUIZ_NO_QUESTION_HERE,
                assertThrows(RefusalResponse.class, () -> service.randomQuestion(STATION, List.of(1)))
                        .refusal());
        assertEquals(
                Refusal.PUBLIC_QUIZ_STATION_NOT_HERE,
                assertThrows(RefusalResponse.class, () -> service.catalogs(UUID.randomUUID()))
                        .refusal());
    }
}
