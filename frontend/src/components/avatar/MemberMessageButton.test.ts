/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {beforeEach, describe, expect, it, vi} from 'vitest'
import {flushPromises, mount} from '@vue/test-utils'
import {createI18n} from 'vue-i18n'
import deDE from '@/i18n/de-DE'
import MemberMessageButton from './MemberMessageButton.vue'

const api = vi.hoisted(() => ({
  openDirect: vi.fn(async () => ({uid: 'conversation'})),
}))
const toast = vi.hoisted(() => vi.fn())
const push = vi.hoisted(() => vi.fn())

vi.mock('@/api/messages', () => api)
vi.mock('vue-router', () => ({useRouter: () => ({push})}))
vi.mock('@/util/toast', () => ({showToast: toast}))

function button() {
  return mount(MemberMessageButton, {
    props: {person: {uid: 'ben', firstName: 'Ben', lastName: 'Muster'}},
    global: {plugins: [createI18n({legacy: false, locale: 'de-DE', messages: {'de-DE': deDE, en: {}}})]},
  })
}

beforeEach(() => vi.clearAllMocks())

describe('the message button on a member card', () => {
  it('opens the private conversation with the person', async () => {
    const wrapper = button()

    await wrapper.get('[data-testid="member-card-message"]').trigger('click')
    await flushPromises()

    expect(api.openDirect).toHaveBeenCalledWith('ben')
    expect(push).toHaveBeenCalledWith('/messages/conversation')
  })

  it('does not tell a denied request a second time', async () => {
    api.openDirect.mockRejectedValueOnce({isAxiosError: true, response: {status: 403, data: {}}})
    const wrapper = button()

    await wrapper.get('[data-testid="member-card-message"]').trigger('click')
    await flushPromises()

    expect(toast).not.toHaveBeenCalled()
  })
})
