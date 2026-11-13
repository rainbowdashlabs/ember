/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it, vi} from 'vitest'
import {flushPromises, mount} from '@vue/test-utils'
import {createI18n} from 'vue-i18n'
import deDE from '@/i18n/de-DE'
import type {ConversationMessageResponse, ConversationResponse} from '@/api/generated/schema'
import MessageActionBar from './MessageActionBar.vue'
import MessageBody from './MessageBody.vue'
import StationChatMembersPanel from './StationChatMembersPanel.vue'
import ConversationHeader from './ConversationHeader.vue'
import ReportMessageModal from './ReportMessageModal.vue'
import {useMessageWords} from '@/composables/useMessageWords'
import {defineComponent, h} from 'vue'
import type {MessageActions} from './messageActions'

const api = vi.hoisted(() => ({reportMessage: vi.fn(async () => {})}))
vi.mock('@/api/messages', () => api)
vi.mock('@/util/toast', () => ({showToast: vi.fn()}))

const i18n = () => createI18n({legacy: false, locale: 'de-DE', messages: {'de-DE': deDE, en: {}}})

const ANNA = {uid: 'anna', firstName: 'Anna', lastName: 'Muster'}
const READER = {uid: 'reader', firstName: 'Mia', lastName: 'Muster'}

function message(overrides: Partial<ConversationMessageResponse> = {}): ConversationMessageResponse {
  return {
    id: 7, author: ANNA, content: 'Hallo', replyTo: null, systemKind: null, subject: null,
    createdAt: '2026-11-13T10:00:00Z', editedAt: null, deleted: false, reactions: [],
    removedByStation: false, removedBy: null, ...overrides,
  }
}

function actions(overrides: Partial<MessageActions> = {}): MessageActions {
  return {
    readerUid: READER.uid, blocked: [], reply: vi.fn(), edit: vi.fn(), remove: vi.fn(), react: vi.fn(),
    block: vi.fn(), jumpTo: vi.fn(), menu: vi.fn(), ...overrides,
  }
}

function bar(given: MessageActions, shown = message()) {
  return mount(MessageActionBar, {
    props: {message: shown, actions: given},
    global: {plugins: [i18n()], stubs: {Popover: true, EmojiPicker: true}},
  })
}

describe('the actions of a message in a station chat', () => {
  it('offers reporting and removing somebody else\'s message where the reader may', async () => {
    const report = vi.fn()
    const moderate = vi.fn()
    const wrapper = bar(actions({report, moderate}))

    await wrapper.get('[data-testid="message-report-7"]').trigger('click')
    await wrapper.get('[data-testid="message-moderate-7"]').trigger('click')

    expect(report).toHaveBeenCalledOnce()
    expect(moderate).toHaveBeenCalledOnce()
  })

  it('offers neither on the reader\'s own message, nor in a private conversation', () => {
    expect(bar(actions({report: vi.fn(), moderate: vi.fn()}), message({author: READER}))
      .find('[data-testid="message-report-7"]').exists()).toBe(false)
    const privateBar = bar(actions())
    expect(privateBar.find('[data-testid="message-report-7"]').exists()).toBe(false)
    expect(privateBar.find('[data-testid="message-moderate-7"]').exists()).toBe(false)
  })
})

describe('a message the station removed', () => {
  function body(shown: ConversationMessageResponse) {
    return mount(MessageBody, {
      props: {message: shown, blocked: false, readerUid: READER.uid},
      global: {plugins: [i18n()]},
    })
  }

  it('says so to everybody and names who removed it only where the reader is told', () => {
    const removed = message({deleted: true, content: '', removedByStation: true})

    expect(body(removed).text()).toBe(deDE.messages.removedByStation)
    expect(body({...removed, removedBy: READER}).text()).toContain('Mia Muster')
  })
})

describe('the members of a station chat', () => {
  it('says whom the chat follows and marks guardians with the people they are there for', () => {
    const conversation = {
      uid: 'chat', kind: 'STATION', name: 'Jugend', ownerUid: null, muted: false, lastReadMessageId: null,
      moderator: false,
      station: {stationUid: 'station', stationName: 'Wache Nord', audience: 'GROUP', includeGuardians: true, closed: false},
      members: [
        {...ANNA, joinedAt: '2026-11-13T10:00:00Z', guardianFor: []},
        {...READER, joinedAt: '2026-11-13T10:00:00Z', guardianFor: ['Kim Klein']},
      ],
    } satisfies ConversationResponse
    const wrapper = mount(StationChatMembersPanel, {
      props: {modelValue: true, conversation},
      global: {plugins: [i18n()], stubs: {SidePanel: {template: '<div><slot/></div>'}, PersonCardButton: {template: '<span><slot/></span>'}, UserAvatar: true}},
    })

    expect(wrapper.get('[data-testid="station-chat-follows"]').text()).toBe(deDE.messages.station.follows.GROUP)
    expect(wrapper.get('[data-testid="station-chat-member-reader"]').text()).toContain('Erziehungsberechtigt für Kim Klein')
    expect(wrapper.get('[data-testid="station-chat-member-anna"]').text()).not.toContain('Erziehungsberechtigt')
  })
})

describe('a closed station chat', () => {
  it('offers no reactions, answers or changes, but still reporting', () => {
    const wrapper = bar(actions({closed: true, report: vi.fn()}))

    expect(wrapper.text()).not.toContain('👍')
    expect(wrapper.find(`[aria-label="${deDE.messages.actions.reply}"]`).exists()).toBe(false)
    expect(wrapper.find('[data-testid="message-report-7"]').exists()).toBe(true)
  })
})

describe('the header of a station chat', () => {
  it('names the station and offers no way to leave', () => {
    const conversation = {
      uid: 'chat', kind: 'STATION', name: 'Alle', ownerUid: null, muted: false, lastReadMessageId: null, moderator: false,
      station: {stationUid: 'station', stationName: 'Wache Nord', audience: 'STATION', includeGuardians: false, closed: false},
      members: [],
    } satisfies ConversationResponse
    const wrapper = mount(ConversationHeader, {
      props: {conversation, title: 'Alle', others: [], other: null, otherBlocked: false},
      global: {plugins: [i18n()], stubs: {ActionsMenu: {template: '<div><slot/></div>'}, IconButton: true, StationChatAvatar: true}},
    })

    expect(wrapper.text()).toContain('Wachenchat · Wache Nord')
    expect(wrapper.text()).not.toContain(deDE.messages.leaveGroup)
    expect(wrapper.text()).not.toContain(deDE.messages.closeDirect)
  })
})

describe('a closed line nobody is named on', () => {
  it('says only that the chat was closed', () => {
    const Probe = defineComponent({
      setup() {
        const {systemLine} = useMessageWords()
        return () => h('p', systemLine({systemKind: 'CLOSED', author: null, text: ''}))
      },
    })

    expect(mount(Probe, {global: {plugins: [i18n()]}}).text()).toBe(deDE.messages.system.CLOSED_QUIETLY)
  })
})

describe('reporting a message', () => {
  it('sends the reason trimmed, or none where it is blank', async () => {
    const wrapper = mount(ReportMessageModal, {
      props: {modelValue: false, conversationUid: 'chat', message: message()},
      global: {plugins: [i18n()], stubs: {Modal: {template: '<div><slot/></div>'}}},
    })
    await wrapper.setProps({modelValue: true})
    await wrapper.get('[data-testid="report-reason"]').setValue('  Spam  ')
    await wrapper.get('[data-testid="report-send"]').trigger('click')
    await flushPromises()
    await wrapper.setProps({modelValue: false})
    await wrapper.setProps({modelValue: true})
    await wrapper.get('[data-testid="report-send"]').trigger('click')
    await flushPromises()

    expect(api.reportMessage).toHaveBeenNthCalledWith(1, 'chat', 7, 'Spam')
    expect(api.reportMessage).toHaveBeenNthCalledWith(2, 'chat', 7, null)
  })
})
