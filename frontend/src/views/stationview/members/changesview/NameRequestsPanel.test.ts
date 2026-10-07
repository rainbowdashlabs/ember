/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest'
import {flushPromises, mount} from '@vue/test-utils'
import NameRequestsPanel from './NameRequestsPanel.vue'
import type {NameChangeView} from '@/api/generated/schema'
import {createIdentity} from '@/test/mocks/factories'

const api = vi.hoisted(() => ({
  listNameChangeRequests: vi.fn<() => Promise<NameChangeView[]>>(),
  approveNameChange: vi.fn<(id: number) => Promise<void>>(),
  denyNameChange: vi.fn<(id: number, reason: string | null) => Promise<void>>(),
}))

vi.mock('@/api', () => ({members: api}))

function request(id: number): NameChangeView {
  return {
    id,
    member: createIdentity({name: 'Mara Nager'}),
    currentName: 'Mara Nager',
    requestedName: 'Mia Neu',
    requestedAt: '2026-10-01T10:00:00Z',
  }
}

function mountPanel() {
  return mount(NameRequestsPanel, {
    attachTo: document.body,
    global: {stubs: {MemberName: true}},
  })
}

beforeEach(() => {
  api.approveNameChange.mockResolvedValue()
  api.denyNameChange.mockResolvedValue()
})

afterEach(() => {
  vi.resetAllMocks()
  document.body.innerHTML = ''
})

describe('NameRequestsPanel', () => {
  it('shows nothing while nobody waits', async () => {
    api.listNameChangeRequests.mockResolvedValue([])
    const wrapper = mountPanel()
    await flushPromises()

    expect(wrapper.find('[data-testid="name-requests"]').exists()).toBe(false)
  })

  it('approves a request and reads the list again', async () => {
    api.listNameChangeRequests.mockResolvedValueOnce([request(5)]).mockResolvedValueOnce([])
    const wrapper = mountPanel()
    await flushPromises()

    expect(wrapper.text()).toContain('Mara Nager möchte Mia Neu heißen')
    await wrapper.get('[data-testid="approve-name"]').trigger('click')
    await flushPromises()

    expect(api.approveNameChange).toHaveBeenCalledWith(5)
    expect(wrapper.find('[data-testid="name-request"]').exists()).toBe(false)
  })

  it('denies with the reason written, or with none when the field stays empty', async () => {
    api.listNameChangeRequests.mockResolvedValue([request(5)])
    const wrapper = mountPanel()
    await flushPromises()

    await wrapper.get('[data-testid="deny-name"]').trigger('click')
    await flushPromises()
    const reason = document.body.querySelector('textarea')!
    reason.value = '  Bitte mit Ausweis  '
    reason.dispatchEvent(new Event('input'))
    document.body.querySelector('form')!.dispatchEvent(new Event('submit'))
    await flushPromises()

    await wrapper.get('[data-testid="deny-name"]').trigger('click')
    await flushPromises()
    document.body.querySelector('form')!.dispatchEvent(new Event('submit'))
    await flushPromises()

    expect(api.denyNameChange).toHaveBeenNthCalledWith(1, 5, 'Bitte mit Ausweis')
    expect(api.denyNameChange).toHaveBeenNthCalledWith(2, 5, null)
  })
})
