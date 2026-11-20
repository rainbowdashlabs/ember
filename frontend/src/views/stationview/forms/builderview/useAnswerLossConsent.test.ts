/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {mount} from '@vue/test-utils'
import {defineComponent} from 'vue'
import {afterEach, beforeAll, beforeEach, describe, expect, it, vi} from 'vitest'
import {followModifierKeys} from '@/util/modifierKeys'
import {useAnswerLossConsent} from './useAnswerLossConsent'

const getQuestionAnswerCounts = vi.fn()
vi.mock('@/api', () => ({forms: {getQuestionAnswerCounts: (id: number) => getQuestionAnswerCounts(id)}}))

const REMOVALS = {questionIds: [3], options: []}

function consent() {
  let api: ReturnType<typeof useAnswerLossConsent> | null = null
  mount(defineComponent({
    setup() {
      api = useAnswerLossConsent()
      return () => null
    },
  }))
  return api as unknown as ReturnType<typeof useAnswerLossConsent>
}

function holdShift(held: boolean) {
  window.dispatchEvent(new KeyboardEvent(held ? 'keydown' : 'keyup', {key: 'Shift'}))
}

/** Removing answered questions asks first, and shift held as the save starts is consent. */
describe('useAnswerLossConsent', () => {
  beforeAll(() => followModifierKeys())
  beforeEach(() => {
    getQuestionAnswerCounts.mockReset().mockResolvedValue([{questionId: 3, answers: 4, optionAnswers: {}}])
  })
  afterEach(() => holdShift(false))

  it('asks before a save that loses answers', async () => {
    const asked = consent()

    const saving = asked.requireConsent(1, REMOVALS)
    await vi.waitFor(() => expect(asked.show.value).toBe(true))
    asked.accept()

    await expect(saving).resolves.toBeUndefined()
  })

  it('gives consent at once while shift is held, without reading the counts', async () => {
    const asked = consent()

    holdShift(true)
    await asked.requireConsent(1, REMOVALS)

    expect(getQuestionAnswerCounts).not.toHaveBeenCalled()
    expect(asked.show.value).toBe(false)
  })
})
