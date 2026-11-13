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
import type {ReportedMessageResponse, StationChatResponse} from '@/api/generated/schema'
import StationChatCard from './stationchatsview/StationChatCard.vue'
import ReportCard from './messagereportsview/ReportCard.vue'
import OpenStationChatModal from './stationchatsview/OpenStationChatModal.vue'

const api = vi.hoisted(() => ({
  closeStationChat: vi.fn(async () => {}),
  reopenStationChat: vi.fn(async () => {}),
  renameStationChat: vi.fn(async () => {}),
  setStationChatGuardians: vi.fn(async () => {}),
  removeReported: vi.fn(async () => {}),
  dismissReport: vi.fn(async () => {}),
  openStationChat: vi.fn(async () => ({})),
}))

const lists = vi.hoisted(() => ({
  listGroups: vi.fn(async () => [{id: 3, name: 'Jugend'}]),
  listChoosableTags: vi.fn(async () => [{id: 5, name: 'Atemschutz'}]),
}))

vi.mock('@/api/memberGroups', () => ({listGroups: lists.listGroups}))
vi.mock('@/api/userTags', () => ({listChoosableTags: lists.listChoosableTags}))

vi.mock('@/api/messages', () => api)

const i18n = () => createI18n({legacy: false, locale: 'de-DE', messages: {'de-DE': deDE, en: {}}})

function chat(overrides: Partial<StationChatResponse> = {}): StationChatResponse {
  return {
    uid: 'chat', name: 'Jugend', audience: 'GROUP', groupId: 3, tagId: null, audienceName: 'Jugend',
    includeGuardians: false, members: 4, createdAt: '2026-11-13T10:00:00Z', lastActivityAt: '2026-11-13T10:00:00Z',
    closedAt: null, ...overrides,
  }
}

beforeEach(() => vi.clearAllMocks())

describe('a station chat in the managers\' list', () => {
  it('closes an open chat and reopens a closed one, then reloads', async () => {
    const onChanged = vi.fn()
    const open = mount(StationChatCard, {props: {chat: chat(), onChanged}, global: {plugins: [i18n()]}})
    await open.get('[data-testid="station-chat-close-chat"]').trigger('click')
    await flushPromises()
    const closed = mount(StationChatCard, {
      props: {chat: chat({closedAt: '2026-11-13T11:00:00Z'}), onChanged},
      global: {plugins: [i18n()]},
    })
    await closed.get('[data-testid="station-chat-reopen-chat"]').trigger('click')
    await flushPromises()

    expect(api.closeStationChat).toHaveBeenCalledWith('chat')
    expect(api.reopenStationChat).toHaveBeenCalledWith('chat')
    expect(onChanged).toHaveBeenCalledTimes(2)
    expect(open.text()).toContain('Gruppe: Jugend')
  })
})

describe('a reported message', () => {
  const report: ReportedMessageResponse = {
    id: 9, conversationUid: 'chat', chatName: 'Alle', reporter: {uid: 'ben', firstName: 'Ben', lastName: 'Muster'},
    reason: 'Zu laut', reportedText: 'Beleidigung', reportedAt: '2026-11-13T10:00:00Z', messageId: 7,
    context: [{
      id: 7, author: {uid: 'anna', firstName: 'Anna', lastName: 'Muster'}, content: 'ok', replyTo: null, systemKind: null,
      subject: null, createdAt: '2026-11-13T09:00:00Z', editedAt: '2026-11-13T09:30:00Z', deleted: false, reactions: [],
      removedByStation: false, removedBy: null,
    }],
  }

  it('shows the text as it was reported beside the conversation, and is removed or dismissed', async () => {
    const onHandled = vi.fn()
    const wrapper = mount(ReportCard, {
      props: {report, onHandled},
      global: {
        plugins: [i18n()],
        stubs: {ConfirmDeleteModal: {template: '<button data-testid="confirm-removal" @click="$emit(\'confirm\')"/>'}},
      },
    })

    expect(wrapper.text()).toContain('Beleidigung')
    expect(wrapper.text()).toContain('Anna Muster: ok')
    await wrapper.get('[data-testid="message-report-remove-9"]').trigger('click')
    expect(api.removeReported).not.toHaveBeenCalled()
    await wrapper.get('[data-testid="confirm-removal"]').trigger('click')
    await flushPromises()
    await wrapper.get('[data-testid="message-report-dismiss-9"]').trigger('click')
    await flushPromises()

    expect(api.removeReported).toHaveBeenCalledWith(9)
    expect(api.dismissReport).toHaveBeenCalledWith(9)
    expect(onHandled).toHaveBeenCalledTimes(2)
  })
})

describe('opening a station chat', () => {
  function dialog(onOpened = vi.fn()) {
    const wrapper = mount(OpenStationChatModal, {
      props: {modelValue: false, onOpened},
      global: {plugins: [i18n()], stubs: {Modal: {template: '<div><slot/></div>'}}},
    })
    return {wrapper, onOpened}
  }

  it('sends a group chat with its group and the guardian choice, only once a name and a group are given', async () => {
    const {wrapper, onOpened} = dialog()
    await wrapper.setProps({modelValue: true})
    await flushPromises()
    const submit = wrapper.get('[data-testid="station-chat-submit"]')

    await wrapper.get('[data-testid="station-chat-name"]').setValue(' Jugend ')
    await wrapper.get('[data-testid="station-chat-audience"]').setValue('GROUP')
    expect(submit.attributes('disabled')).toBeDefined()
    await wrapper.get('[data-testid="station-chat-target"]').setValue('3')
    await submit.trigger('click')
    await flushPromises()

    expect(api.openStationChat).toHaveBeenCalledWith(
      {name: 'Jugend', audience: 'GROUP', groupId: 3, tagId: null, includeGuardians: false})
    expect(onOpened).toHaveBeenCalledOnce()
    expect(lists.listChoosableTags).toHaveBeenCalled()
  })

  it('sends a station-wide chat without a group, tag or guardians', async () => {
    const {wrapper} = dialog()
    await wrapper.setProps({modelValue: true})
    await flushPromises()

    await wrapper.get('[data-testid="station-chat-name"]').setValue('Alle')
    await wrapper.get('[data-testid="station-chat-submit"]').trigger('click')
    await flushPromises()

    expect(api.openStationChat).toHaveBeenCalledWith(
      {name: 'Alle', audience: 'STATION', groupId: null, tagId: null, includeGuardians: false})
  })
})

describe('a chat whose group is gone', () => {
  it('offers neither reopening nor the guardians', () => {
    const wrapper = mount(StationChatCard, {
      props: {chat: chat({groupId: null, audienceName: null, closedAt: '2026-11-13T11:00:00Z'}), onChanged: vi.fn()},
      global: {plugins: [i18n()]},
    })

    expect(wrapper.find('[data-testid="station-chat-reopen-chat"]').exists()).toBe(false)
    expect(wrapper.text()).not.toContain(deDE.stationChats.guardians)
    expect(wrapper.text()).toContain('Gruppe: gelöscht')
  })
})
