/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.quiz.service;

import dev.chojo.ember.feature.quiz.entity.QuestionConfig;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class QuizSheetBodyTest {

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void unreadableConfigPrintsNothingBelowTheTitle(boolean withAnswers) {
        assertInstanceOf(QuizSheetBody.None.class, QuizSheetBody.of(new QuestionConfig.Unknown(), withAnswers));
    }
}
