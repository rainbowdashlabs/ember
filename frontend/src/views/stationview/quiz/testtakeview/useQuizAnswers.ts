/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import { computed, onUnmounted, ref, watch, type Ref } from 'vue'
import { useI18n } from 'vue-i18n'
import type { QuizAttemptDetail, QuizQuestion } from '@/api/quiz'
import { quiz } from '@/api'
import { defaultAnswerFor } from './quizAnswerDefaults'
import { moveWithin } from '@/util/reorder'
import { describeFailure, type Failure } from '@/util/failure'

/**
 * Owns the answers of the running attempt: the payload of every question, the
 * mutators the question inputs call and the debounced write back to the server.
 *
 * <p>A write that does not land is said out loud in {@code saveFailure}. Every one of them used to
 * be caught and dropped, so somebody could write a whole test, hand it in, and have none of it
 * reach the server without a word anywhere on the screen. Nothing is worse to lose silently than
 * an exam somebody has just sat.
 */
export function useQuizAnswers(
    attemptId: Ref<number | null>,
    currentQuestionId: Ref<number | null>,
) {
  const { t } = useI18n()
  const answers = ref<Map<number, string>>(new Map())

  /** What the last write was refused with, or null while every answer has landed. */
  const saveFailure = ref<Failure | null>(null)

  /**
   * Says, in the plainest words there are, that the answers are not stored.
   *
   * <p>Both halves are this screen's and neither comes off the failure. The sentence has to say that
   * nothing is saved rather than that something went wrong, and the guidance has to say not to close
   * the page, because the thing being prevented is somebody walking away from a machine believing
   * they have finished an exam that the server has never heard of.
   */
  function recordSaveFailure(e: unknown) {
    saveFailure.value = {
      ...describeFailure(e, t),
      message: t('quiz.attempt.answerNotSaved'),
      guidance: t('quiz.attempt.answerNotSavedGuidance'),
    }
  }

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
        saveFailure.value = null
      } catch (e) {
        recordSaveFailure(e)
      }
    }, 500)
  }

  /**
   * Writes every answer that has one, before the sheet is handed in.
   *
   * <p>All of them are attempted even after one is refused, because a single refused answer must
   * not cost the candidate the rest of the sheet. Whether any of them was refused is left standing
   * in {@code saveFailure}, so the caller can refuse to hand in on top of a write that did not land.
   */
  async function saveAll() {
    if (attemptId.value === null) return
    saveFailure.value = null
    for (const [questionId, answerStr] of answers.value.entries()) {
      if (!answerStr) continue
      try {
        await quiz.saveAnswer(attemptId.value, questionId, answerStr)
      } catch (e) {
        recordSaveFailure(e)
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
    saveFailure,
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
