/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import { computed, onUnmounted, ref, watch, type Ref } from 'vue'
import type { QuizAttemptDetail, QuizQuestion } from '@/api/quiz'
import { quiz } from '@/api'
import { defaultAnswerFor } from './quizAnswerDefaults'
import { moveWithin } from '@/util/reorder'

/**
 * Owns the answers of the running attempt: the payload of every question, the
 * mutators the question inputs call and the debounced write back to the server.
 */
export function useQuizAnswers(
    attemptId: Ref<number | null>,
    currentQuestionId: Ref<number | null>,
) {
  const answers = ref<Map<number, string>>(new Map())

  const currentAnswer = computed({
    get: () => {
      if (currentQuestionId.value === null) return ''
      return answers.value.get(currentQuestionId.value) ?? ''
    },
    set: (val: string) => {
      if (currentQuestionId.value === null) return
      answers.value.set(currentQuestionId.value, val)
    },
  })

  const currentAnswerParsed = computed(() => {
    try { return JSON.parse(currentAnswer.value || '{}') } catch { return {} }
  })

  function hydrate(detail: QuizAttemptDetail, questionDetails: Map<number, QuizQuestion>) {
    for (const ans of detail.answers) {
      answers.value.set(ans.questionId, ans.answer)
    }
    for (const aq of detail.questions) {
      if (answers.value.has(aq.questionId)) continue
      const question = questionDetails.get(aq.questionId)
      if (!question) continue
      answers.value.set(aq.questionId, defaultAnswerFor(question))
    }
  }

  let saveDebounce: ReturnType<typeof setTimeout> | null = null

  function autoSaveCurrentAnswer() {
    if (attemptId.value === null || currentQuestionId.value === null) return
    const answerStr = answers.value.get(currentQuestionId.value) ?? ''
    if (!answerStr) return

    if (saveDebounce) clearTimeout(saveDebounce)
    saveDebounce = setTimeout(async () => {
      try {
        await quiz.saveAnswer(attemptId.value!, currentQuestionId.value!, answerStr)
      } catch {
        void 0
      }
    }, 500)
  }

  async function saveAll() {
    if (attemptId.value === null) return
    for (const [questionId, answerStr] of answers.value.entries()) {
      if (answerStr) {
        try {
          await quiz.saveAnswer(attemptId.value, questionId, answerStr)
        } catch { void 0 }
      }
    }
  }

  function setMCAnswer(optionIndex: number, isMulti: boolean) {
    const parsed = currentAnswerParsed.value
    let selected: number[] = Array.isArray(parsed.selected) ? [...parsed.selected] : []

    if (isMulti) {
      const idx = selected.indexOf(optionIndex)
      if (idx >= 0) selected.splice(idx, 1)
      else selected.push(optionIndex)
    } else {
      selected = [optionIndex]
    }

    currentAnswer.value = JSON.stringify({ selected })
  }

  function setFillBlankGap(gapIndex: number, value: string) {
    const parsed = currentAnswerParsed.value
    const gaps: Record<string, string> = parsed.gaps ?? {}
    gaps[String(gapIndex)] = value
    currentAnswer.value = JSON.stringify({ gaps })
  }

  function setFreeAnswer(text: string) {
    currentAnswer.value = JSON.stringify({ text })
  }

  function setConnectPair(leftIndex: number, rightValue: string) {
    const parsed = currentAnswerParsed.value
    const pairs: Record<string, string> = parsed.pairs ?? {}
    pairs[String(leftIndex)] = rightValue
    currentAnswer.value = JSON.stringify({ pairs })
  }

  function setImageTextAnswer(text: string) {
    currentAnswer.value = JSON.stringify({ text })
  }

  function setTrueFalse(value: boolean) {
    currentAnswer.value = JSON.stringify({ value })
  }

  function reorderItems(fromIndex: number, toIndex: number) {
    const parsed = currentAnswerParsed.value
    const current: number[] = Array.isArray(parsed.order) ? parsed.order : []
    currentAnswer.value = JSON.stringify({ order: moveWithin(current, fromIndex, toIndex) })
  }


  watch(currentAnswer, () => {
    autoSaveCurrentAnswer()
  })

  onUnmounted(() => {
    if (saveDebounce) clearTimeout(saveDebounce)
  })

  return {
    answers,
    currentAnswerParsed,
    hydrate,
    autoSaveCurrentAnswer,
    saveAll,
    setMCAnswer,
    setFillBlankGap,
    setFreeAnswer,
    setConnectPair,
    setImageTextAnswer,
    setTrueFalse,
    reorderItems,
  }
}
