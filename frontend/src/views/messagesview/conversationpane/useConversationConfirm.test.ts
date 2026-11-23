/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {mount} from '@vue/test-utils'
import {defineComponent} from 'vue'
import {afterEach, beforeAll, describe, expect, it, vi} from 'vitest'
import {followModifierKeys} from '@/util/modifierKeys'
import {useConversationConfirm} from './useConversationConfirm'

const showToast = vi.fn()
vi.mock('@/util/toast', () => ({showToast: (message: string, kind: string) => showToast(message, kind)}))

function confirmer() {
  let api: ReturnType<typeof useConversationConfirm> | null = null
  mount(defineComponent({
    setup() {
      api = useConversationConfirm()
      return () => null
    },
  }))
  return api as unknown as ReturnType<typeof useConversationConfirm>
}

function holdShift(held: boolean) {
  window.dispatchEvent(new KeyboardEvent(held ? 'keydown' : 'keyup', {key: 'Shift'}))
}

function question(run: () => Promise<void>) {
  return {title: 'Blockieren', message: 'Sicher?', label: 'Blockieren', run}
}

describe('useConversationConfirm', () => {
  beforeAll(() => followModifierKeys())
  afterEach(() => holdShift(false))

  it('asks before blocking and blocks once agreed', async () => {
    const run = vi.fn(async () => {})
    const confirm = confirmer()

    confirm.ask(question(run))
    expect(confirm.confirming.value).toBe(true)
    expect(run).not.toHaveBeenCalled()

    await confirm.confirm()
    expect(run).toHaveBeenCalledOnce()
    expect(confirm.confirming.value).toBe(false)
  })

  it('blocks at once while shift is held, without asking', async () => {
    const run = vi.fn(async () => {})
    const confirm = confirmer()

    holdShift(true)
    confirm.ask(question(run))

    await vi.waitFor(() => expect(run).toHaveBeenCalledOnce())
    expect(confirm.confirming.value).toBe(false)
  })

  it('says a refusal in a toast', async () => {
    const confirm = confirmer()

    holdShift(true)
    confirm.ask(question(async () => { throw new Error('refused') }))

    await vi.waitFor(() => expect(showToast).toHaveBeenCalledWith(expect.any(String), 'error'))
  })
})
