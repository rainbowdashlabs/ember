/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
// @vitest-environment happy-dom
import {mount} from '@vue/test-utils'
import {defineComponent} from 'vue'
import {afterEach, describe, expect, it} from 'vitest'
import {useConfirmAction} from './useConfirmAction'

/** The composable reaches for the locale, so it is used from inside a component as the app does. */
function actionOn(done: string[]) {
  let api: ReturnType<typeof useConfirmAction<string>> | null = null
  mount(defineComponent({
    setup() {
      api = useConfirmAction<string>({
        onConfirm: async (item: string) => {
          done.push(item)
        },
      })
      return () => null
    },
  }))
  return api as unknown as ReturnType<typeof useConfirmAction<string>>
}

/** Presses and releases shift the way a reader holding it down would. */
function holdShift(held: boolean) {
  window.dispatchEvent(new KeyboardEvent(held ? 'keydown' : 'keyup', {key: 'Shift'}))
}

describe('useConfirmAction', () => {
  afterEach(() => holdShift(false))

  it('asks before acting', async () => {
    const done: string[] = []
    const action = actionOn(done)

    action.request('Jacke')

    expect(action.show.value, 'the question is on screen').toBe(true)
    expect(done, 'and nothing has happened yet').toEqual([])

    await action.confirm()
    expect(done).toEqual(['Jacke'])
  })

  /**
   * Somebody clearing out twenty rows knows what the question says by the third one. Holding shift
   * says they have read it.
   */
  it('acts at once while shift is held, without asking', async () => {
    const done: string[] = []
    const action = actionOn(done)

    holdShift(true)
    action.request('Helm')
    await Promise.resolve()

    expect(action.show.value, 'nothing was asked').toBe(false)
    expect(done, 'and it happened').toEqual(['Helm'])
  })

  it('asks again once shift is let go', async () => {
    const done: string[] = []
    const action = actionOn(done)

    holdShift(true)
    holdShift(false)
    action.request('Hose')

    expect(action.show.value).toBe(true)
    expect(done).toEqual([])
  })
})
