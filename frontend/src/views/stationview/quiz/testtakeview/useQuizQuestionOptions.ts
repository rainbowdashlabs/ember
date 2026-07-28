/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import { computed, ref, type Ref } from 'vue'
import type { QuizQuestion, QuizTestAttemptQuestion } from '@/api/quiz'
import { QuizQuestionTypes } from '@/api/quiz'
import { shuffle } from '@/util/shuffle'

/**
 * Derives how the options of the current question are presented, keeping one
 * stable random order per question for the lifetime of the attempt.
 */
export function useQuizQuestionOptions(
    currentQuestion: Ref<QuizTestAttemptQuestion | null>,
    currentQuestionDetail: Ref<QuizQuestion | null>,
) {
  const displayOrders = ref<Map<string, number[]>>(new Map())

  function getDisplayOrder(key: string, length: number): number[] {
    if (displayOrders.value.has(key)) return displayOrders.value.get(key)!
    const order = shuffle(Array.from({ length }, (_, i) => i))
    displayOrders.value.set(key, order)
    return order
  }

  const currentConfig = computed<Record<string, unknown>>(() => {
    if (!currentQuestionDetail.value) return {}
    return currentQuestionDetail.value.config ?? {}
  })

  const mcDisplayOrder = computed<number[]>(() => {
    if (!currentQuestion.value || !currentQuestionDetail.value) return []
    if (currentQuestionDetail.value.quizQuestionType !== QuizQuestionTypes.MULTIPLE_CHOICE) return []
    const opts = (currentQuestionDetail.value.config?.options as unknown[]) ?? []
    return getDisplayOrder(`mc-${currentQuestion.value.questionId}`, opts.length)
  })

  const connectLeftItems = computed<string[]>(() => {
    const cfg = currentConfig.value
    if (cfg.leftItems) return cfg.leftItems as string[]
    if (cfg.pairs) return (cfg.pairs as { left: string; right: string }[]).map(p => p.left)
    return []
  })

  const connectRightItemsRaw = computed<string[]>(() => {
    const cfg = currentConfig.value
    if (cfg.rightItems) return cfg.rightItems as string[]
    if (cfg.pairs) return (cfg.pairs as { left: string; right: string }[]).map(p => p.right)
    return []
  })

  const connectRightItems = computed<string[]>(() => {
    const raw = connectRightItemsRaw.value
    if (!currentQuestion.value || raw.length === 0) return raw
    const order = getDisplayOrder(`connect-${currentQuestion.value.questionId}`, raw.length)
    return order.map(i => raw[i] as string)
  })

  return {
    currentConfig,
    mcDisplayOrder,
    connectLeftItems,
    connectRightItems,
  }
}
