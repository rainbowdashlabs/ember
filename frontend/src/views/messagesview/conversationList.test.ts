/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it} from 'vitest'
import {mount} from '@vue/test-utils'
import {createI18n} from 'vue-i18n'
import deDE from '@/i18n/de-DE'
import type {ConversationSummaryResponse} from '@/api/generated/schema'
import ConversationList from './ConversationList.vue'

const i18n = () => createI18n({legacy: false, locale: 'de-DE', messages: {'de-DE': deDE, en: {}}})

const ANNA = {uid: 'anna', firstName: 'Anna', lastName: 'Muster'}

function summary(uid: string, overrides: Partial<ConversationSummaryResponse> = {}): ConversationSummaryResponse {
  return {
    uid, kind: 'DIRECT', name: null, others: [ANNA], newest: null, lastActivityAt: '2026-11-13T10:00:00Z',
    unread: 0, muted: false, station: null, ...overrides,
  }
}

const STATION_CHAT = summary('chat', {
  kind: 'STATION', name: 'Alle', others: [],
  station: {stationUid: 'station', stationName: 'Wache Nord', audience: 'STATION', includeGuardians: false, closed: true},
})

function list(conversations: ConversationSummaryResponse[]) {
  return mount(ConversationList, {
    props: {conversations, loading: false, failure: null, openUid: null, onChanged: () => {}},
    global: {
      plugins: [i18n()],
      stubs: {NewConversationModal: true, MessagesSettingsModal: true, RowLink: {template: '<div><slot/></div>'}, UserAvatar: true},
    },
  })
}

describe('the list of conversations', () => {
  it('marks a station chat with its station and whether it is closed', () => {
    const marker = list([STATION_CHAT]).get('[data-testid="conversation-station-chat"]').text()

    expect(marker).toContain('Wachenchat · Wache Nord')
    expect(marker).toContain(deDE.messages.station.closed)
  })

  it('narrows to the station chats, and offers that only where there are any', async () => {
    const wrapper = list([summary('direct'), STATION_CHAT])

    await wrapper.get('[data-testid="messages-filter-station"]').trigger('click')

    expect(wrapper.find('[data-testid="conversation-direct"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="conversation-chat"]').exists()).toBe(true)
    expect(list([summary('direct')]).find('[data-testid="messages-filter-station"]').exists()).toBe(false)
  })
})

describe('the list after the last station chat is gone', () => {
  it('shows every conversation again even where the station chats were chosen', async () => {
    const wrapper = list([summary('direct'), STATION_CHAT])
    await wrapper.get('[data-testid="messages-filter-station"]').trigger('click')

    await wrapper.setProps({conversations: [summary('direct')]})

    expect(wrapper.find('[data-testid="conversation-direct"]').exists()).toBe(true)
  })

  it('previews a message the station removed as removed, not as deleted', () => {
    const removed = summary('direct', {newest: {
      id: 1, author: ANNA, text: '', systemKind: null, subject: null, deleted: true,
      createdAt: '2026-11-13T10:00:00Z', removedByStation: true,
    }})

    expect(list([removed]).get('[data-testid="conversation-direct"]').text()).toContain(deDE.messages.removedByStation)
  })
})
