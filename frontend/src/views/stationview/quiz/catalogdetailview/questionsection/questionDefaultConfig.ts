/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {QuizQuestionTypes, type QuizQuestionTypeName} from '@/api/quiz'

/**
 * Builds the empty editor config a question of the given type starts from, so
 * every type editor opens on a shape it understands.
 */
export function defaultConfigFor(type: QuizQuestionTypeName): Record<string, unknown> {
  switch (type) {
    case QuizQuestionTypes.MULTIPLE_CHOICE:
      return { options: [{ text: '', correct: false }], pointsPerCorrect: 1 }
    case QuizQuestionTypes.FILL_IN_THE_BLANK:
      return { text: '', answers: [], distractors: [], useDropdown: false }
    case QuizQuestionTypes.FREE_ANSWER:
      return { lines: 3, answers: [] }
    case QuizQuestionTypes.CONNECT:
      return { pairs: [{ left: '', right: '' }] }
    case QuizQuestionTypes.IMAGE_TEXT:
      return { imageUrl: '', answer: '' }
    case QuizQuestionTypes.TRUE_FALSE:
      return { correctAnswer: true }
    case QuizQuestionTypes.ORDERING:
      return { items: [''] }
    default:
      return {}
  }
}
