/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import { QuestionTypes } from '@/api/forms'

const EMPTY = '–'

/**
 * A question's stored configuration, which travels either as an object or as the JSON string it
 * was persisted as. Malformed configuration reads as empty rather than throwing — a broken
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
 * Renders one stored answer as the single line the analytics table and the CSV export both show.
 *
 * Answers are stored per question type, so each type is unpacked differently: a choice answer
 * holds option indices that have to be resolved against the question's option list, a ranking
 * holds the order they were placed in, and a Likert answer holds one rating per statement. An
 * unknown type falls through to the raw stored value rather than showing nothing.
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
    const selected = (parsed as { selected?: number[] }).selected ?? []
    const options = (cfg.options as string[]) || []
    const labels = selected.map(i => options[i] ?? `#${i}`)
    const other = (parsed as { other?: string }).other
    if (other) labels.push(`Sonstige: ${other}`)
    return labels.join(', ') || EMPTY
  }

  if (questionType === QuestionTypes.RANKING) {
    const order = (parsed as { order?: number[] }).order ?? []
    const options = (cfg.options as string[]) || []
    return order.map((idx, rank) => `${rank + 1}. ${options[idx] ?? ''}`).join(', ')
  }

  if (questionType === QuestionTypes.LIKERT) {
    const ratings = (parsed as { ratings?: Record<string, number> }).ratings ?? {}
    const statements = (cfg.statements as string[]) || []
    return Object.entries(ratings)
      .map(([index, rating]) => `${statements[Number(index)] || `Option ${Number(index) + 1}`}: ${rating}`)
      .join(', ')
  }

  return value
}
