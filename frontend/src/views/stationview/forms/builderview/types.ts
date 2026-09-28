/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type { PageTarget, QuestionType } from '@/api/forms'

export interface QuestionDraft {
  id: string
  questionType: QuestionType
  title: string
  description: string
  required: boolean
  shuffle: boolean
  config: Record<string, unknown>
}

/** One page of the form being edited, holding its questions in their order. */
export interface PageDraft {
  key: string
  title: string
  description: string
  after: PageTarget
  questions: QuestionDraft[]
}

const STORED_PREFIX = 'existing-'

/** The draft id of a question the server already holds. */
export function storedDraftId(questionId: number): string {
  return `${STORED_PREFIX}${questionId}`
}

/**
 * The stored question a draft edits, or `undefined` for a question that is new.
 *
 * <p>The server keeps a question, and the answers given to it, only when it is sent back with this
 * id. A question sent without one is added as a new question.
 */
export function storedQuestionId(draft: QuestionDraft): number | undefined {
  return draft.id.startsWith(STORED_PREFIX) ? Number(draft.id.slice(STORED_PREFIX.length)) : undefined
}
