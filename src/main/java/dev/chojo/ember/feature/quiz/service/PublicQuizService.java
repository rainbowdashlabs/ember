/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.quiz.service;

import dev.chojo.ember.api.refusal.QuizRefusal;
import dev.chojo.ember.feature.quiz.entity.QuestionConfig;
import dev.chojo.ember.feature.quiz.entity.QuizQuestionType;
import dev.chojo.ember.feature.quiz.repository.QuizCatalogRepository;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.entity.StationModule;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.feature.station.service.ModuleAvailability;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * What the open web may see of a station's quiz: the catalogs it made public and one random
 * question from them, for the quiz teaser on a public page.
 */
@Singleton
public class PublicQuizService {
    private final QuizCatalogRepository catalogs;
    private final StationRepository stations;
    private final ModuleAvailability modules;

    @Inject
    public PublicQuizService(QuizCatalogRepository catalogs, StationRepository stations, ModuleAvailability modules) {
        this.catalogs = catalogs;
        this.stations = stations;
        this.modules = modules;
    }

    /**
     * The station behind a public quiz address. One whose quiz is switched off, by itself or its
     * cluster, has no public quiz and answers as if it were not here.
     */
    private Station station(UUID stationUid) {
        var station = stations.findByUid(stationUid).orElseThrow(QuizRefusal.PUBLIC_QUIZ_STATION_NOT_HERE::raise);
        if (StationModule.QUIZ.isOffIn(modules.disabledAt(station.id()))) {
            throw QuizRefusal.PUBLIC_QUIZ_STATION_NOT_HERE.raise();
        }
        return station;
    }

    /**
     * The catalogs the station made public.
     */
    public List<PublicQuizCatalog> catalogs(UUID stationUid) {
        return catalogs.findPublicByStation(station(stationUid).id()).stream()
                .map(c -> new PublicQuizCatalog(c.id(), c.name(), c.description()))
                .toList();
    }

    /**
     * One question picked at random from the named public catalogs of the station.
     *
     * @param catalogIds the catalogs to pick from; any that is not public is passed over
     */
    public PublicQuizQuestion randomQuestion(UUID stationUid, List<Integer> catalogIds) {
        if (catalogIds.isEmpty()) throw QuizRefusal.PUBLIC_QUIZ_CATALOGS_EMPTY.raise();
        var picked = catalogs.findRandomPublicQuestion(station(stationUid).id(), catalogIds)
                .orElseThrow(QuizRefusal.PUBLIC_QUIZ_NO_QUESTION_HERE::raise);
        return new PublicQuizQuestion(
                picked.id(),
                picked.quizQuestionType(),
                picked.title(),
                picked.description(),
                picked.imageUrl(),
                picked.config());
    }

    /**
     * Public catalog shape: only the bits the editor's catalog picker needs. No question counts,
     * no audit fields.
     */
    public record PublicQuizCatalog(int id, String name, String description) {}

    /**
     * Single random question payload. The {@code config} node intentionally carries every field
     * the question type uses, including the {@code correct} marker on options: the teaser reveals
     * the answer on click, so the client legitimately needs it.
     */
    public record PublicQuizQuestion(
            int id,
            QuizQuestionType questionType,
            String title,
            String description,
            @Nullable String imageUrl,
            QuestionConfig config) {}
}
