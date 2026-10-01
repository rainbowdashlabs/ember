/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.quiz.service;

import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.feature.quiz.entity.QuestionConfig;
import dev.chojo.ember.feature.quiz.entity.QuizQuestionType;
import dev.chojo.ember.feature.quiz.repository.QuizCatalogRepository;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.repository.StationRepository;
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

    @Inject
    public PublicQuizService(QuizCatalogRepository catalogs, StationRepository stations) {
        this.catalogs = catalogs;
        this.stations = stations;
    }

    private Station station(UUID stationUid) {
        return stations.findByUid(stationUid).orElseThrow(Refusal.PUBLIC_QUIZ_STATION_NOT_HERE::raise);
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
        if (catalogIds.isEmpty()) throw Refusal.PUBLIC_QUIZ_CATALOGS_EMPTY.raise();
        var picked = catalogs.findRandomPublicQuestion(station(stationUid).id(), catalogIds)
                .orElseThrow(Refusal.PUBLIC_QUIZ_NO_QUESTION_HERE::raise);
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
