/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it, vi} from 'vitest'
import {mount} from '@vue/test-utils'
import {computed} from 'vue'
import EventGroupOverview from './EventGroupOverview.vue'
import type {EventGroupDetail, EventGroupPartState} from '@/api/generated/schema'

vi.mock('@/composables/useSession', () => ({useSession: () => ({stationTimezone: computed(() => 'Europe/Berlin')})}))

function part(eventId: number, overrides: Partial<EventGroupPartState> = {}): EventGroupPartState {
  return {
    eventId, setId: 1, name: `Tag ${eventId}`, startTime: '2027-03-06T08:00:00Z', endTime: '2027-03-06T16:00:00Z',
    calledOff: false, tookPlace: false, required: false, accepted: 0, places: null, ...overrides,
  }
}

function detail(registrationLimit: number | null, holding: number, queued: number, parts: EventGroupPartState[]): EventGroupDetail {
  return {
    eventGroup: {
      id: 70,
      details: {
        name: 'Jugendflamme 1', description: null, registrationLimit, registrationDeadline: null, requiresConfirmation: false,
        color: null, minRegistrations: null, thresholdDays: null, partRegistrationLimit: null, queueOrder: null,
      },
      cancelled: false, cancelReason: null, partCount: parts.length, holding, queued, pending: 0, firstStart: null, lastEnd: null,
    },
    sets: [{id: 1, eventGroupId: 70, name: 'Übung', minimum: 1, maximum: null, position: 0}],
    parts,
    registration: {userTypes: [], groupIds: [], tagIds: [], memberIds: [], mode: 'AND'},
    view: {userTypes: [], groupIds: [], tagIds: [], memberIds: [], mode: 'AND'},
    questions: [],
    fields: [],
    household: [],
  }
}

function overview(shown: EventGroupDetail) {
  return mount(EventGroupOverview, {props: {detail: shown}, global: {stubs: {EventGroupFields: true, RowLink: {template: '<a><slot/></a>'}}}})
}

/** The overview says how the places for people stand and how each part's own places stand. */
describe('EventGroupOverview places', () => {
  it('says how the places for people stand, with whoever waits', () => {
    const shown = overview(detail(10, 4, 2, [part(11)]))

    expect(shown.get('[data-testid="event-group-places"]').text()).toBe('4 von 10 Plätzen belegt, 2 auf der Nachrückliste')
    expect(shown.find('[data-testid="event-group-waiting"]').exists()).toBe(false)
  })

  /** Without a people limit the count of sign-ups stands alone, and whoever waits is said beside it. */
  it('names the waiting count beside the sign-ups where no people limit is set', () => {
    const shown = overview(detail(null, 4, 3, [part(11)]))

    expect(shown.get('[data-testid="event-group-places"]').text()).toBe('Anmeldungen: 4')
    expect(shown.get('[data-testid="event-group-waiting"]').text()).toBe('3 auf der Nachrückliste')
  })

  it('shows the places of each part with a limit and none for a part called off', () => {
    const shown = overview(detail(null, 0, 0, [
      part(11, {accepted: 8, places: 12}),
      part(12, {accepted: 12, places: 12}),
      part(13, {accepted: 2, places: 12, calledOff: true}),
      part(14),
    ]))

    expect(shown.get('[data-testid="event-group-part-places-11"]').text()).toBe('8 von 12 Plätzen belegt')
    expect(shown.get('[data-testid="event-group-part-places-12"]').text()).toBe('Voll')
    expect(shown.find('[data-testid="event-group-part-places-13"]').exists()).toBe(false)
    expect(shown.find('[data-testid="event-group-part-places-14"]').exists()).toBe(false)
  })
})
