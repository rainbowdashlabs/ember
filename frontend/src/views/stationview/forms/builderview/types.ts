/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type { QuestionType } from '@/api/types'

export interface QuestionDraft {
  id: string
  questionType: QuestionType
  title: string
  description: string
  required: boolean
  shuffle: boolean
  config: Record<string, unknown>
}
