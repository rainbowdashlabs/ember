/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {afterEach, beforeAll, describe, expect, it, vi} from 'vitest'
import {flushPromises, mount} from '@vue/test-utils'
import {computed} from 'vue'
import EventGroupPartsEditor from './EventGroupPartsEditor.vue'
import EditButton from '@/components/button/EditButton.vue'
import type {EventGroupDetail} from '@/api/generated/schema'
import {noDeadline} from '../eventshared/registrationDeadline'
import {followModifierKeys} from '@/util/modifierKeys'

const createPart = vi.fn(async (..._args: unknown[]) => ({}))
const setPartRequired = vi.fn(async (..._args: unknown[]) => ({}))
const removePart = vi.fn(async (..._args: unknown[]) => ({}))

vi.mock('@/api', () => ({
  events: {listEvents: async () => []},
  eventGroups: {
    createPart: (...args: unknown[]) => createPart(...args),
    setPartRequired: (...args: unknown[]) => setPartRequired(...args),
    removePart: (...args: unknown[]) => removePart(...args),
  },
}))
vi.mock('@/composables/useSession', () => ({useSession: () => ({stationTimezone: computed(() => 'Europe/Berlin')})}))

const detail: EventGroupDetail = {
  eventGroup: {
    id: 70,
    details: {name: 'Jugendflamme 1', description: null, registrationLimit: null, registrationDeadline: noDeadline(), requiresConfirmation: false, color: null, minRegistrations: null, thresholdDays: null, partRegistrationLimit: null, queueOrder: null},
    cancelled: false, cancelReason: null, partCount: 0, holding: 0, queued: 0, pending: 0, firstStart: null, lastEnd: null, closesAt: null,
  },
  sets: [{id: 1, eventGroupId: 70, name: 'Übung', minimum: 2, maximum: null, position: 0}, {id: 2, eventGroupId: 70, name: 'Prüfung', minimum: 1, maximum: null, position: 1}],
  parts: [],
  registration: {userTypes: [], groupIds: [], tagIds: [], memberIds: [], mode: 'AND'},
  view: {userTypes: [], groupIds: [], tagIds: [], memberIds: [], mode: 'AND'},
  questions: [],
  fields: [],
  household: [],
  places: null,
}

/** Presses and releases shift the way a reader holding it down would. */
function holdShift(held: boolean) {
  window.dispatchEvent(new KeyboardEvent(held ? 'keydown' : 'keyup', {key: 'Shift'}))
}

const SATURDAY = {
  eventId: 11, setId: 1, name: 'Samstag', startTime: '2027-03-06T08:00:00Z', endTime: '2027-03-06T16:00:00Z',
  calledOff: false, tookPlace: false, required: false, accepted: 0, places: null,
}

describe('EventGroupPartsEditor', () => {
  beforeAll(() => followModifierKeys())
  afterEach(() => {
    holdShift(false)
    removePart.mockClear()
  })

  it('asks before taking a part out of the event group', async () => {
    const editor = mount(EventGroupPartsEditor, {props: {detail: {...detail, parts: [SATURDAY]}}})
    await flushPromises()

    await editor.find('[aria-label="Aus der Terminreihe nehmen"]').trigger('click')

    expect(editor.findComponent({name: 'ConfirmDeleteModal'}).props('modelValue')).toBe(true)
    expect(removePart).not.toHaveBeenCalled()
  })

  it('takes a part out at once while shift is held and reloads the event group', async () => {
    const onChanged = vi.fn()
    const editor = mount(EventGroupPartsEditor, {props: {detail: {...detail, parts: [SATURDAY]}, onChanged}})
    await flushPromises()

    holdShift(true)
    await editor.find('[aria-label="Aus der Terminreihe nehmen"]').trigger('click', {shiftKey: true})
    await flushPromises()

    expect(removePart).toHaveBeenCalledWith(70, 11)
    expect(onChanged).toHaveBeenCalled()
    expect(editor.findComponent({name: 'ConfirmDeleteModal'}).props('modelValue')).toBe(false)
  })

  it('creates a new part with its name, times and set and reloads the event group', async () => {
    const onChanged = vi.fn()
    const editor = mount(EventGroupPartsEditor, {props: {detail, onChanged}})
    await flushPromises()

    await editor.find('#event-group-part-name').setValue('Samstag')
    const [startDay, startTime, endDay, endTime] = editor.findAll('[data-moment] input')
    await startDay!.setValue('2027-03-06')
    await startTime!.setValue('08:00')
    await endDay!.setValue('2027-03-06')
    await endTime!.setValue('16:00')
    await editor.find('#event-group-part-set').setValue('2')
    await editor.find('[data-testid="event-group-create-part"]').trigger('click')
    await flushPromises()

    expect(createPart).toHaveBeenCalledWith(70, expect.objectContaining({setId: 2, name: 'Samstag'}))
    const payload = createPart.mock.calls[0]![1] as {startTime: string; endTime: string}
    expect(new Date(payload.endTime).getTime()).toBeGreaterThan(new Date(payload.startTime).getTime())
    expect(onChanged).toHaveBeenCalled()
  })

  it('marks a part as required and reloads the event group', async () => {
    const onChanged = vi.fn()
    const withPart: EventGroupDetail = {...detail, parts: [{
      eventId: 11, setId: 1, name: 'Samstag', startTime: '2027-03-06T08:00:00Z', endTime: '2027-03-06T16:00:00Z',
      calledOff: false, tookPlace: false, required: false, accepted: 0, places: null,
    }]}
    const editor = mount(EventGroupPartsEditor, {props: {detail: withPart, onChanged}})
    await flushPromises()

    await editor.find('[data-testid="event-group-part-required-11"]').trigger('click')
    await flushPromises()

    expect(setPartRequired).toHaveBeenCalledWith(70, 11, true)
    expect(onChanged).toHaveBeenCalled()
  })

  it('shows when each part runs, links it to its appointment\'s editor and marks a called off part', async () => {
    const withParts: EventGroupDetail = {...detail, parts: [
      {eventId: 11, setId: 1, name: 'Samstag', startTime: '2027-03-06T07:00:00Z', endTime: '2027-03-06T15:00:00Z',
        calledOff: false, tookPlace: false, required: false, accepted: 0, places: null},
      {eventId: 12, setId: 1, name: 'Sonntag', startTime: '2027-03-07T07:00:00Z', endTime: '2027-03-07T15:00:00Z',
        calledOff: true, tookPlace: false, required: false, accepted: 0, places: null},
    ]}
    const editor = mount(EventGroupPartsEditor, {props: {detail: withParts}})
    await flushPromises()

    expect(editor.find('[data-testid="event-group-part-when-11"]').text()).toContain('06.03.2027')
    expect(editor.find('[data-testid="event-group-part-when-11"]').text()).toContain('08:00')
    expect(editor.find('[data-testid="event-group-part-when-11"]').text()).toContain('16:00')
    expect(editor.findAllComponents(EditButton).map(button => button.props('to'))).toEqual([
      {name: 'event-edit', params: {id: 11}},
      {name: 'event-edit', params: {id: 12}},
    ])
    expect(editor.find('[data-testid="event-group-part-called-off-11"]').exists()).toBe(false)
    expect(editor.find('[data-testid="event-group-part-called-off-12"]').exists()).toBe(true)
  })
})
