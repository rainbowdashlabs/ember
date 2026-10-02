/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import { QuestionTypes } from '@/api/forms'
import type { ChoiceAnswer, LikertAnswer, RankingAnswer } from '@/api/generated/schema'
import { optionLabel, optionsOf } from '@/util/formOptions'

const EMPTY = '–'

/**
 * A question's stored configuration, which travels either as an object or as the JSON string it
 * was persisted as. Malformed configuration reads as empty rather than throwing - a broken
 * question should render blank, not take the page down.
 */
function parseConfig(config: Record<string, unknown> | string): Record<string, unknown> {
  if (typeof config === 'object' && config !== null) return config
  try {
    return JSON.parse(config || '{}')
  } catch {
    return {}
  }
}

function parseValue(value: string): Record<string, unknown> {
  try {
    return JSON.parse(value || '{}')
  } catch {
    return {}
  }
}

/**
 * Renders one stored answer as the single line the analytics table, the CSV export and the contact
 * submissions all show.
 *
 * Answers are stored per question type, so each type is unpacked differently: a choice answer
 * holds option keys that are resolved against the question's options, a ranking holds the keys in
 * the order they were placed, and a Likert answer holds one rating per statement key. A key the
 * question no longer has shows as itself rather than vanishing. An unknown type falls through to the
 * raw stored value rather than showing nothing.
 */
export function formatAnswerDisplay(
  questionType: string,
  config: string | Record<string, unknown>,
  value: string,
): string {
  if (!value) return EMPTY
  const parsed = parseValue(value)
  const cfg = parseConfig(config)

  if (questionType === QuestionTypes.TEXT) return (parsed as { text?: string }).text || EMPTY
  if (questionType === QuestionTypes.DATE) return (parsed as { date?: string }).date || EMPTY
  if (questionType === QuestionTypes.RATING) return String((parsed as { rating?: number }).rating ?? EMPTY)

  if (questionType === QuestionTypes.CHOICE) {
    const selected = (parsed as Partial<ChoiceAnswer>).selected ?? []
    const options = optionsOf(cfg)
    const labels = selected.map(key => optionLabel(options, key) ?? `#${key}`)
    const other = (parsed as Partial<ChoiceAnswer>).other
    if (other) labels.push(`Sonstige: ${other}`)
    return labels.join(', ') || EMPTY
  }

  if (questionType === QuestionTypes.RANKING) {
    const order = (parsed as Partial<RankingAnswer>).order ?? []
    const options = optionsOf(cfg)
    return order.map((key, rank) => `${rank + 1}. ${optionLabel(options, key) ?? ''}`).join(', ')
  }

  if (questionType === QuestionTypes.LIKERT) {
    const ratings = (parsed as Partial<LikertAnswer>).ratings ?? {}
    const statements = optionsOf(cfg, 'statements')
    return statements
      .map((statement, index) => ({ name: statement.label || `Option ${index + 1}`, rating: ratings[statement.key] }))
      .filter(entry => entry.rating !== undefined)
      .map(entry => `${entry.name}: ${entry.rating}`)
      .join(', ')
  }

  return value
}
