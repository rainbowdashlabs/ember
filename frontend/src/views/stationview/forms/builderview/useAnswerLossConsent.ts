/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {ref, watch} from 'vue'
import {forms} from '@/api'

/** The editor was asked whether answers may go and said no, so nothing was saved. */
export class AnswerLossDeclined extends Error {
  constructor() {
    super('Removing answered questions was declined')
    this.name = 'AnswerLossDeclined'
  }
}

/**
 * Asks before a save throws answers away.
 *
 * <p>Saving the questions removes every stored question that is no longer in the list, and the
 * answers given to it go with it. That is right when somebody means it and a loss nobody can undo
 * when they do not, so where the removed questions carry answers the editor is told how many and
 * asked first. Removing questions nobody answered asks nothing.
 *
 * <p>The counts are read when the save is pressed rather than when the form was opened, because a
 * running poll goes on collecting answers while it is being edited.
 */
export function useAnswerLossConsent() {
  const show = ref(false)
  const lostAnswers = ref(0)
  let settle: ((accepted: boolean) => void) | null = null

  /**
   * Resolves once the removal may go ahead, and rejects with {@link AnswerLossDeclined} where the
   * editor declines.
   *
   * @param formId the form being saved
   * @param removedQuestionIds the stored questions the save is about to remove
   */
  async function requireConsent(formId: number, removedQuestionIds: number[]): Promise<void> {
    if (removedQuestionIds.length === 0) return
    const counts = await forms.getQuestionAnswerCounts(formId)
    const lost = counts
        .filter(count => removedQuestionIds.includes(count.questionId))
        .reduce((sum, count) => sum + count.answers, 0)
    if (lost === 0) return
    lostAnswers.value = lost
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

  return {show, lostAnswers, requireConsent, accept: () => answer(true)}
}
