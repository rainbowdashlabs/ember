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
import type {ReportedMessageResponse, RestrictionAudience, StationChatResponse} from '@/api/generated/schema'
import {createGroup, createTag} from '@/test/mocks/factories'
import StationChatCard from './stationchatsview/StationChatCard.vue'
import ReportCard from './messagereportsview/ReportCard.vue'
import OpenStationChatModal from './stationchatsview/OpenStationChatModal.vue'

const api = vi.hoisted(() => ({
  closeStationChat: vi.fn(async () => {}),
  reopenStationChat: vi.fn(async () => {}),
  updateStationChat: vi.fn(async () => ({})),
  removeReported: vi.fn(async () => {}),
  dismissReport: vi.fn(async () => {}),
  openStationChat: vi.fn(async () => ({})),
}))

vi.mock('@/api/messages', () => api)

const i18n = () => createI18n({legacy: false, locale: 'de-DE', messages: {'de-DE': deDE, en: {}}})

const GROUPS = [createGroup({id: 3, name: 'Jugend'})]
const TAGS = [createTag({id: 5, name: 'Jugendleitung'})]

function audience(overrides: Partial<RestrictionAudience> = {}): RestrictionAudience {
  return {userTypes: [], groupIds: [3], tagIds: [5], memberIds: [], mode: 'OR', ...overrides}
}

function chat(overrides: Partial<StationChatResponse> = {}): StationChatResponse {
  return {
    uid: 'chat', name: 'Jugend', audience: audience(), audienceGone: false,
    includeGuardians: false, members: 4, createdAt: '2026-11-13T10:00:00Z', lastActivityAt: '2026-11-13T10:00:00Z',
    closedAt: null, ...overrides,
  }
}

function card(value: StationChatResponse, onChanged = vi.fn()) {
  return mount(StationChatCard, {props: {chat: value, groups: GROUPS, tags: TAGS, onChanged}, global: {plugins: [i18n()]}})
}

beforeEach(() => vi.clearAllMocks())

describe('a station chat in the managers\' list', () => {
  it('closes an open chat and reopens a closed one, then reloads', async () => {
    const onChanged = vi.fn()
    const open = card(chat(), onChanged)
    await open.get('[data-testid="station-chat-close-chat"]').trigger('click')
    await flushPromises()
    const closed = card(chat({closedAt: '2026-11-13T11:00:00Z'}), onChanged)
    await closed.get('[data-testid="station-chat-reopen-chat"]').trigger('click')
    await flushPromises()

    expect(api.closeStationChat).toHaveBeenCalledWith('chat')
    expect(api.reopenStationChat).toHaveBeenCalledWith('chat')
    expect(onChanged).toHaveBeenCalledTimes(2)
  })

  it('names its audience the way a restricted appointment does', () => {
    expect(card(chat()).get('[data-testid="station-chat-audience-chat"]').text()).toContain('Für: Jugend oder Jugendleitung')
  })

  it('says it is for the whole station when its audience names nobody', () => {
    const wrapper = card(chat({audience: audience({groupIds: [], tagIds: [], mode: 'AND'})}))

    expect(wrapper.get('[data-testid="station-chat-audience-chat"]').text())
      .toContain('Für: die ganze Wache ohne Mitglieder auf Probe')
  })

  it('hands itself over for editing', async () => {
    const wrapper = card(chat())

    await wrapper.get('[data-testid="station-chat-edit-chat"]').trigger('click')

    expect(wrapper.emitted('edit')?.[0]?.[0]).toMatchObject({uid: 'chat'})
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

const RestrictionsFieldStub = {
  name: 'RestrictionsField',
  props: ['modelValue', 'groups', 'tags'],
  emits: ['update:modelValue'],
  template: '<div data-testid="station-chat-audience"/>',
}

describe('opening a station chat', () => {
  function dialog(existing: StationChatResponse | null = null, onSaved = vi.fn()) {
    const wrapper = mount(OpenStationChatModal, {
      props: {modelValue: false, chat: existing, groups: GROUPS, tags: TAGS, onSaved},
      global: {plugins: [i18n()], stubs: {Modal: {template: '<div><slot/></div>'}, RestrictionsField: RestrictionsFieldStub}},
    })
    return {wrapper, onSaved}
  }

  it('sends the picked audience and the guardian choice, only once a name is given', async () => {
    const {wrapper, onSaved} = dialog()
    await wrapper.setProps({modelValue: true})
    await flushPromises()
    const submit = wrapper.get('[data-testid="station-chat-submit"]')

    expect(submit.attributes('disabled')).toBeDefined()
    await wrapper.get('[data-testid="station-chat-name"]').setValue(' Jugend ')
    wrapper.getComponent(RestrictionsFieldStub).vm.$emit('update:modelValue', audience())
    await wrapper.get('[role="switch"], input[type="checkbox"]').trigger('click')
    await submit.trigger('click')
    await flushPromises()

    expect(api.openStationChat).toHaveBeenCalledWith({name: 'Jugend', audience: audience(), includeGuardians: true})
    expect(onSaved).toHaveBeenCalledOnce()
  })

  it('asks for the leftover audience to be checked before it is saved', async () => {
    const {wrapper} = dialog(chat({audienceGone: true, closedAt: '2026-11-13T11:00:00Z'}))
    await wrapper.setProps({modelValue: true})
    await flushPromises()

    expect(wrapper.get('[data-testid="station-chat-audience-gone-check"]').text())
      .toBe(deDE.stationChats.open.audienceGoneCheck)
  })

  it('opens without the check for a chat whose audience is whole', async () => {
    const {wrapper} = dialog(chat())
    await wrapper.setProps({modelValue: true})
    await flushPromises()

    expect(wrapper.find('[data-testid="station-chat-audience-gone-check"]').exists()).toBe(false)
  })

  it('sends a chat naming nobody, which is the whole station', async () => {
    const {wrapper} = dialog()
    await wrapper.setProps({modelValue: true})
    await flushPromises()

    await wrapper.get('[data-testid="station-chat-name"]').setValue('Alle')
    await wrapper.get('[data-testid="station-chat-submit"]').trigger('click')
    await flushPromises()

    expect(api.openStationChat).toHaveBeenCalledWith({
      name: 'Alle', audience: {userTypes: [], groupIds: [], tagIds: [], memberIds: [], mode: 'AND'}, includeGuardians: false,
    })
  })

  it('opens filled in for an existing chat and saves the whole chat in its place', async () => {
    const existing = chat({includeGuardians: true, closedAt: '2026-11-13T11:00:00Z'})
    const {wrapper, onSaved} = dialog(existing)
    await wrapper.setProps({modelValue: true})
    await flushPromises()

    expect((wrapper.get('[data-testid="station-chat-name"]').element as HTMLInputElement).value).toBe('Jugend')
    expect(wrapper.getComponent(RestrictionsFieldStub).props('modelValue')).toEqual(audience())
    await wrapper.get('[data-testid="station-chat-name"]').setValue('Jugend und Leitung')
    await wrapper.get('[data-testid="station-chat-submit"]').trigger('click')
    await flushPromises()

    expect(api.updateStationChat).toHaveBeenCalledWith('chat', {name: 'Jugend und Leitung', audience: audience(), includeGuardians: true})
    expect(api.openStationChat).not.toHaveBeenCalled()
    expect(onSaved).toHaveBeenCalledOnce()
  })
})

describe('a chat whose audience is gone', () => {
  it('says so and offers editing, not reopening', () => {
    const wrapper = card(chat({audienceGone: true, closedAt: '2026-11-13T11:00:00Z'}))

    expect(wrapper.find('[data-testid="station-chat-reopen-chat"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="station-chat-audience-gone-chat"]').text()).toBe(deDE.stationChats.audienceGoneHint)
    expect(wrapper.find('[data-testid="station-chat-edit-chat"]').exists()).toBe(true)
  })
})
