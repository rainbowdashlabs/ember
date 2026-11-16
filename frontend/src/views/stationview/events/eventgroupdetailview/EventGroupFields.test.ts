/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {beforeEach, describe, expect, it, vi} from 'vitest'
import {flushPromises, mount} from '@vue/test-utils'
import EventGroupFields from './EventGroupFields.vue'
import {FieldType, SelfRegistration, type AppointmentField} from '@/api/generated/schema'

const api = vi.hoisted(() => ({eventGroups: {toggleFieldSelfRegistration: vi.fn()}}))
vi.mock('@/api', () => api)

function memberField(value: string, state: SelfRegistration | null = SelfRegistration.OPEN): AppointmentField {
  return {
    id: 7, eventId: 0, name: 'Betreuer', fieldType: FieldType.MEMBER_LIST, value, position: 0, overview: false,
    attendanceFieldId: null, isPublic: false, selfRegistrationState: state,
    config: {selfRegistration: true, perDate: false},
  } as AppointmentField
}

/** A member field of an event group lets the reader enter for every appointment of it at once. */
describe('EventGroupFields', () => {
  beforeEach(() => vi.clearAllMocks())

  it('offers an empty member field to enter for the whole group and reports the change', async () => {
    api.eventGroups.toggleFieldSelfRegistration.mockResolvedValue(memberField('[3]'))
    const changed = vi.fn()
    const wrapper = mount(EventGroupFields, {
      props: {fields: [memberField('')], eventGroupId: 70, currentMemberId: 3, memberNames: new Map([[3, 'Anna']]), onChanged: changed},
    })

    const enter = wrapper.get('[data-testid="self-registration-enter"]')
    expect(enter.text()).toBe('Für alle Termine eintragen')
    await enter.trigger('click')
    await flushPromises()

    expect(api.eventGroups.toggleFieldSelfRegistration).toHaveBeenCalledWith(70, 7)
    expect(changed).toHaveBeenCalledOnce()
  })

  it('lets whoever stands in it take themselves out', () => {
    const wrapper = mount(EventGroupFields, {
      props: {fields: [memberField('[3]')], eventGroupId: 70, currentMemberId: 3, memberNames: new Map([[3, 'Anna']])},
    })

    expect(wrapper.text()).toContain('Anna')
    expect(wrapper.find('[data-testid="self-registration-leave"]').exists()).toBe(true)
  })

  it('offers nothing and leaves an empty field out where the page does not know its reader', () => {
    const wrapper = mount(EventGroupFields, {props: {fields: [memberField('')]}})

    expect(wrapper.find('[data-testid="event-group-field-values"]').exists()).toBe(false)
  })
})
