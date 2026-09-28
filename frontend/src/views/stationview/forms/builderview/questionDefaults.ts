/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import { QuestionTypes, type QuestionType } from '@/api/forms'
import { blankOption } from '@/util/formOptions'

/** The settings a question of the given kind starts with, one empty option included where it has options. */
export function defaultConfig(type: QuestionType): Record<string, unknown> {
  switch (type) {
    case QuestionTypes.CHOICE:
      return { multiSelect: false, dropdown: false, allowOther: false, options: [blankOption([])], multiLimitType: 'NONE', multiLimit: null }
    case QuestionTypes.TEXT: return { longAnswer: false }
    case QuestionTypes.RATING: return { scale: 5, icon: 'STAR' }
    case QuestionTypes.DATE: return {}
    case QuestionTypes.RANKING: return { options: [blankOption([])] }
    case QuestionTypes.LIKERT: return { statements: [blankOption([])], scaleMin: 1, scaleMax: 5, scaleLabels: [] }
  }
}
