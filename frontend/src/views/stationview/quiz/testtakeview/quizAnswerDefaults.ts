/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {QuizQuestionType, type QuizQuestionRead} from '@/api/generated/schema'
import { shuffle } from '@/util/shuffle'

/**
 * Builds the empty answer payload a question starts with, so every input type
 * renders from a shape it understands before the member touches it.
 */
export function defaultAnswerFor(question: QuizQuestionRead): string {
  const config: Record<string, unknown> = question.config
  if (question.quizQuestionType === QuizQuestionType.MULTIPLE_CHOICE) {
    return JSON.stringify({ selected: [] })
  }
  if (question.quizQuestionType === QuizQuestionType.ORDERING) {
    const items = Array.isArray(config.items) ? config.items : []
    return JSON.stringify({ order: shuffle(items.map((_, i: number) => i)) })
  }
  if (question.quizQuestionType === QuizQuestionType.TRUE_FALSE) {
    return JSON.stringify({ value: null })
  }
  if (question.quizQuestionType === QuizQuestionType.CONNECT) {
    return JSON.stringify({ pairs: {} })
  }
  if (question.quizQuestionType === QuizQuestionType.FILL_IN_THE_BLANK) {
    return JSON.stringify({ gaps: {} })
  }
  return JSON.stringify({ text: '' })
}
