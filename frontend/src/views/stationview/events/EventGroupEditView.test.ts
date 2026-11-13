/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {beforeEach, describe, expect, it, vi} from 'vitest'
import {flushPromises, mount} from '@vue/test-utils'
import {ref} from 'vue'
import EventGroupEditView from './EventGroupEditView.vue'
import {EventType, RestrictionMode, type RestrictionAudience} from '@/api/generated/schema'

const YOUTH: RestrictionAudience = {groupIds: [4], memberIds: [], mode: RestrictionMode.OR, tagIds: [], userTypes: []}

const api = vi.hoisted(() => ({
  eventGroups: {
    createEventGroup: vi.fn(),
    getEventGroup: vi.fn(),
  },
  events: {getEvent: vi.fn(), getRestrictions: vi.fn()},
  memberGroups: {listGroups: vi.fn(async () => [])},
  userTags: {listChoosableTags: vi.fn(async () => [])},
  federation: {listPartners: vi.fn(async () => [])},
}))
vi.mock('@/api', () => api)
vi.mock('@/composables/useSession', () => ({
  useSession: () => ({loaded: ref(true), hasPermission: () => false}),
}))
const route = vi.hoisted(() => ({params: {} as Record<string, string>, query: {} as Record<string, string>}))
const replace = vi.hoisted(() => vi.fn())
vi.mock('vue-router', () => ({useRoute: () => route, useRouter: () => ({replace})}))

function mountEditor() {
  return mount(EventGroupEditView, {
    global: {stubs: {ViewContent: {template: '<div><slot/></div>'}, EventGroupDetailsForm: true, RestrictionsField: true, EventGroupSetsEditor: true,
      EventGroupQuestionsCard: true, EventGroupFieldsCard: true, EventGroupShareCard: true, EventGroupPartsEditor: true}},
  })
}

/**
 * A new event group started from an appointment takes its name and audiences and sends the
 * appointment along with the creation, so the server takes it in or creates nothing. Anything but a
 * one-time appointment is ignored.
 */
describe('EventGroupEditView started from an appointment', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    route.query = {appointment: '12'}
    api.events.getEvent.mockResolvedValue({id: 12, name: 'Grundlehrgang 1', eventType: EventType.ONE_TIME})
    api.events.getRestrictions.mockResolvedValue({register: YOUTH, view: YOUTH})
    api.eventGroups.createEventGroup.mockResolvedValue({eventGroup: {id: 7}, sets: [], parts: [], questions: [], fields: [],
      registration: YOUTH, view: YOUTH})
  })

  it('names the appointment, takes over its audiences and creates the group with it', async () => {
    const wrapper = mountEditor()
    await flushPromises()

    expect(wrapper.text()).toContain('Grundlehrgang 1')
    await wrapper.find('[data-testid="event-group-save"]').trigger('click')
    await flushPromises()

    expect(api.eventGroups.createEventGroup).toHaveBeenCalledWith(expect.objectContaining({
      appointmentId: 12,
      registration: expect.objectContaining({groupIds: [4], mode: RestrictionMode.OR}),
      view: expect.objectContaining({groupIds: [4]}),
    }))
  })

  it('creates the group without the appointment once it is dropped', async () => {
    const wrapper = mountEditor()
    await flushPromises()

    await wrapper.find('[role="status"] button').trigger('click')
    await wrapper.find('[data-testid="event-group-save"]').trigger('click')
    await flushPromises()

    expect(api.eventGroups.createEventGroup).toHaveBeenCalledWith(expect.objectContaining({appointmentId: null}))
  })

  it('ignores an appointment that is not a one-time one', async () => {
    api.events.getEvent.mockResolvedValue({id: 12, name: 'Dienstabend', eventType: EventType.RECURRING})
    const wrapper = mountEditor()
    await flushPromises()

    expect(wrapper.find('[role="status"]').exists()).toBe(false)
  })
})
