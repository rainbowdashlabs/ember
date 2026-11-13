/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it, vi} from 'vitest'
import {flushPromises, mount} from '@vue/test-utils'
import {computed} from 'vue'
import EventGroupPartsEditor from './EventGroupPartsEditor.vue'
import type {EventGroupDetail} from '@/api/generated/schema'

const createPart = vi.fn(async (..._args: unknown[]) => ({}))

vi.mock('@/api', () => ({
  events: {listEvents: async () => []},
  eventGroups: {createPart: (...args: unknown[]) => createPart(...args)},
}))
vi.mock('@/composables/useSession', () => ({useSession: () => ({stationTimezone: computed(() => 'Europe/Berlin')})}))

const detail: EventGroupDetail = {
  eventGroup: {
    id: 70, name: 'Jugendflamme 1', description: null, registrationLimit: null, registrationDeadline: null,
    requiresConfirmation: false, cancelled: false, cancelReason: null, partCount: 0, holding: 0,
  },
  sets: [{id: 1, eventGroupId: 70, name: 'Übung', minimum: 2, position: 0}, {id: 2, eventGroupId: 70, name: 'Prüfung', minimum: 1, position: 1}],
  parts: [],
  registration: {userTypes: [], groupIds: [], tagIds: [], memberIds: [], mode: 'AND'},
  view: {userTypes: [], groupIds: [], tagIds: [], memberIds: [], mode: 'AND'},
  questions: [],
  fields: [],
  household: [],
}

describe('EventGroupPartsEditor', () => {
  it('creates a new part with its name, times and set and reloads the event group', async () => {
    const onChanged = vi.fn()
    const editor = mount(EventGroupPartsEditor, {props: {detail, onChanged}})
    await flushPromises()

    await editor.find('#event-group-part-name').setValue('Samstag')
    await editor.find('#event-group-part-start').setValue('2027-03-06T08:00')
    await editor.find('#event-group-part-end').setValue('2027-03-06T16:00')
    await editor.find('#event-group-part-set').setValue('2')
    await editor.find('[data-testid="event-group-create-part"]').trigger('click')
    await flushPromises()

    expect(createPart).toHaveBeenCalledWith(70, expect.objectContaining({setId: 2, name: 'Samstag'}))
    const payload = createPart.mock.calls[0]![1] as {startTime: string; endTime: string}
    expect(new Date(payload.endTime).getTime()).toBeGreaterThan(new Date(payload.startTime).getTime())
    expect(onChanged).toHaveBeenCalled()
  })
})
