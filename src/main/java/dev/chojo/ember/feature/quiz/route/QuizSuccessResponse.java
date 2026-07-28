/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.quiz.route;

/**
 * Acknowledgement for quiz endpoints that have nothing to return but must not answer
 * with an empty body.
 *
 * @param success always {@code true}
 */
public record QuizSuccessResponse(boolean success) {}
