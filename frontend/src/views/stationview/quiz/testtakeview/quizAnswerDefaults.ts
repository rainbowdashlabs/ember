/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {QuizQuestionTypes, type QuizQuestion} from '@/api/quiz'
import { shuffle } from '@/util/shuffle'

/**
 * Builds the empty answer payload a question starts with, so every input type
 * renders from a shape it understands before the member touches it.
 */
export function defaultAnswerFor(question: QuizQuestion): string {
  const config = question.config ?? {}
  if (question.quizQuestionType === QuizQuestionTypes.MULTIPLE_CHOICE) {
    return JSON.stringify({ selected: [] })
  }
  if (question.quizQuestionType === QuizQuestionTypes.ORDERING) {
    const items = (config.items as string[]) ?? []
    return JSON.stringify({ order: shuffle(items.map((_: string, i: number) => i)) })
  }
  if (question.quizQuestionType === QuizQuestionTypes.TRUE_FALSE) {
    return JSON.stringify({ value: null })
  }
  if (question.quizQuestionType === QuizQuestionTypes.CONNECT) {
    return JSON.stringify({ pairs: {} })
  }
  if (question.quizQuestionType === QuizQuestionTypes.FILL_IN_THE_BLANK) {
    return JSON.stringify({ gaps: {} })
  }
  return JSON.stringify({ text: '' })
}
