/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {QuizQuestionType} from '@/api/generated/schema'

/**
 * Builds the empty editor config a question of the given type starts from, so
 * every type editor opens on a shape it understands.
 */
export function defaultConfigFor(type: QuizQuestionType): Record<string, unknown> {
  switch (type) {
    case QuizQuestionType.MULTIPLE_CHOICE:
      return { options: [{ text: '', correct: false }], pointsPerCorrect: 1 }
    case QuizQuestionType.FILL_IN_THE_BLANK:
      return { text: '', answers: [], distractors: [], useDropdown: false }
    case QuizQuestionType.FREE_ANSWER:
      return { lines: 3, answers: [] }
    case QuizQuestionType.CONNECT:
      return { pairs: [{ left: '', right: '' }] }
    case QuizQuestionType.IMAGE_TEXT:
      return { imageUrl: '', answer: '' }
    case QuizQuestionType.TRUE_FALSE:
      return { correctAnswer: true }
    case QuizQuestionType.ORDERING:
      return { items: [''] }
    default:
      return {}
  }
}
