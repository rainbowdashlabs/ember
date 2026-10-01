/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.quiz.entity;

/**
 * A question as one reader gets it: whole for whoever may see the catalog, and as its answer-free
 * {@link QuizQuestionView} for a member who only takes a test.
 */
public sealed interface QuizQuestionRead permits QuizQuestion, QuizQuestionView {}
