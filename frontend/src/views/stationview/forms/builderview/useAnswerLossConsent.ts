/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {computed, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import {forms} from '@/api'
import type {QuestionAnswerCount} from '@/api/forms'

/** The editor was asked whether answers may go and said no, so nothing was saved. */
export class AnswerLossDeclined extends Error {
  constructor() {
    super('Removing answered questions was declined')
    this.name = 'AnswerLossDeclined'
  }
}

/** The options a save removes from one question it keeps. */
export interface RemovedOptions {
  questionId: number
  keys: string[]
}

/** What a save removes: whole questions, and single options of the questions it keeps. */
export interface Removals {
  questionIds: number[]
  options: RemovedOptions[]
}

/** What a save would lose: the answers of removed questions, and the choices of removed options. */
export interface AnswerLoss {
  answers: number
  selections: number
}

/**
 * Adds up what the given removals throw away, from the counts the server gave.
 *
 * @param counts the answers per question and per option, as the server counts them
 * @param removals what the save removes
 */
export function answerLoss(counts: QuestionAnswerCount[], removals: Removals): AnswerLoss {
  const answers = counts
      .filter(count => removals.questionIds.includes(count.questionId))
      .reduce((sum, count) => sum + count.answers, 0)
  const selections = removals.options.reduce((sum, question) => {
    const perOption = counts.find(count => count.questionId === question.questionId)?.optionAnswers ?? {}
    return sum + question.keys.reduce((inQuestion, key) => inQuestion + (perOption[key] ?? 0), 0)
  }, 0)
  return {answers, selections}
}

/**
 * Asks before a save throws answers away.
 *
 * <p>Saving the questions removes every stored question that is no longer in the list, and the
 * answers given to it go with it. Removing an option of a question that stays takes that option out
 * of every answer that chose, ranked or rated it. Both are right when somebody means it and a loss
 * nobody can undo when they do not, so where the save would lose anything the editor is told how
 * much, in one question for the whole save, and asked first. Removing what nobody answered asks
 * nothing.
 *
 * <p>The counts are read when the save is pressed rather than when the form was opened, because a
 * running poll goes on collecting answers while it is being edited.
 */
export function useAnswerLossConsent() {
  const {t} = useI18n()
  const show = ref(false)
  const lostAnswers = ref(0)
  const lostOptionSelections = ref(0)
  let settle: ((accepted: boolean) => void) | null = null

  /** What the dialog says, naming only the kinds of loss the save actually has. */
  const message = computed(() => {
    if (lostOptionSelections.value === 0) return t('forms.removedQuestionsLoseAnswers', {count: lostAnswers.value})
    if (lostAnswers.value === 0) return t('forms.removedOptionsLoseSelections', {count: lostOptionSelections.value})
    return t('forms.removedQuestionsAndOptionsLoseAnswers', {
      answers: lostAnswers.value,
      selections: lostOptionSelections.value,
    })
  })

  /**
   * Resolves once the removal may go ahead, and rejects with {@link AnswerLossDeclined} where the
   * editor declines.
   *
   * @param formId the form being saved
   * @param removals the stored questions and options the save is about to remove
   */
  async function requireConsent(formId: number, removals: Removals): Promise<void> {
    if (removals.questionIds.length === 0 && removals.options.length === 0) return
    const {answers, selections} = answerLoss(await forms.getQuestionAnswerCounts(formId), removals)
    if (answers === 0 && selections === 0) return
    lostAnswers.value = answers
    lostOptionSelections.value = selections
    show.value = true
    const accepted = await new Promise<boolean>(resolve => { settle = resolve })
    if (!accepted) throw new AnswerLossDeclined()
  }

  function answer(accepted: boolean) {
    const resolve = settle
    settle = null
    show.value = false
    resolve?.(accepted)
  }

  watch(show, open => {
    if (!open) answer(false)
  })

  return {show, message, requireConsent, accept: () => answer(true)}
}
