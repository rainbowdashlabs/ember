/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it, vi} from 'vitest'
import {mount} from '@vue/test-utils'
import {createI18n} from 'vue-i18n'
import {ref} from 'vue'
import deDE from '@/i18n/de-DE'
import type {MessagesStatus, StationDigest} from '@/api/generated/schema'
import MessagesSettingsModal from './MessagesSettingsModal.vue'

const status = ref<MessagesStatus>({available: true, unread: 0, mail: true, stationDigest: 'MENTIONS'})
const setStationDigest = vi.fn(async (digest: StationDigest) => { status.value = {...status.value, stationDigest: digest} })

vi.mock('@/composables/useMessagesStatus', () => ({
  useMessagesStatus: () => ({status, setMail: vi.fn(), setStationDigest}),
}))
vi.mock('@/api/messages', () => ({getBlocks: vi.fn(async () => []), unblock: vi.fn()}))

describe('the message settings', () => {
  it('words the mail as daily and chooses what station chats add to it', async () => {
    const wrapper = mount(MessagesSettingsModal, {
      props: {modelValue: true},
      global: {
        plugins: [createI18n({legacy: false, locale: 'de-DE', messages: {'de-DE': deDE, en: {}}})],
        stubs: {Modal: {template: '<div><slot/></div>'}, UserAvatar: true},
      },
    })

    expect(wrapper.text()).toContain(deDE.messages.settings.mail)
    await wrapper.get('[data-testid="messages-station-digest"]').setValue('ALL')

    expect(setStationDigest).toHaveBeenCalledWith('ALL')
  })

  it('hides the station chat choice while the mail is off', async () => {
    status.value = {...status.value, mail: false}
    const wrapper = mount(MessagesSettingsModal, {
      props: {modelValue: true},
      global: {
        plugins: [createI18n({legacy: false, locale: 'de-DE', messages: {'de-DE': deDE, en: {}}})],
        stubs: {Modal: {template: '<div><slot/></div>'}, UserAvatar: true},
      },
    })

    expect(wrapper.find('[data-testid="messages-station-digest"]').exists()).toBe(false)
  })
})
