/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.form.entity;

import java.util.Map;

/**
 * How many answers a question of a form holds, which is what removing it would throw away, and how
 * many of them name each of its options, which is what removing one option would throw away.
 *
 * @param questionId    the question
 * @param answers       how many responses answered it
 * @param optionAnswers per option key, how many answers pick, rank or rate that option; empty for a
 *                      question without options
 */
public record QuestionAnswerCount(int questionId, int answers, Map<String, Integer> optionAnswers) {}
