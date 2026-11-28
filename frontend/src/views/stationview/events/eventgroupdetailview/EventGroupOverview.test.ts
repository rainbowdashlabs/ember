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
import type {EventGroupDetail, EventGroupPartState, RegistrationDeadline} from '@/api/generated/schema'

vi.mock('@/composables/useSession', () => ({useSession: () => ({stationTimezone: computed(() => 'Europe/Berlin')})}))

function part(eventId: number, overrides: Partial<EventGroupPartState> = {}): EventGroupPartState {
  return {
    eventId, setId: 1, name: `Tag ${eventId}`, startTime: '2027-03-06T08:00:00Z', endTime: '2027-03-06T16:00:00Z',
    calledOff: false, tookPlace: false, required: false, accepted: 0, places: null, ...overrides,
  }
}

function detail(
    registrationLimit: number | null, holding: number, queued: number, parts: EventGroupPartState[],
    registrationDeadline: RegistrationDeadline = {kind: 'NONE', at: null, offsetMinutes: null, anchor: null},
    closesAt: string | null = null,
): EventGroupDetail {
  return {
    eventGroup: {
      id: 70,
      details: {
        name: 'Jugendflamme 1', description: null, registrationLimit, registrationDeadline, requiresConfirmation: false,
        color: null, minRegistrations: null, thresholdDays: null, partRegistrationLimit: null, queueOrder: null,
      },
      cancelled: false, cancelReason: null, partCount: parts.length, holding, queued, pending: 0,
      firstStart: parts[0]?.startTime ?? null, lastEnd: null, closesAt,
    },
    sets: [{id: 1, eventGroupId: 70, name: 'Übung', minimum: 1, maximum: null, position: 0}],
    parts,
    registration: {userTypes: [], groupIds: [], tagIds: [], memberIds: [], mode: 'AND'},
    view: {userTypes: [], groupIds: [], tagIds: [], memberIds: [], mode: 'AND'},
    questions: [],
    fields: [],
    household: [],
    places: null,
  }
}

function overview(shown: EventGroupDetail) {
  return mount(EventGroupOverview, {props: {detail: shown}, global: {stubs: {EventGroupFields: true, RowLink: {template: '<a><slot/></a>'}}}})
}

/** The overview shows how the places for people stand and how each part's own places stand. */
describe('EventGroupOverview places', () => {
  it('says how the places for people stand, with whoever waits', () => {
    const shown = overview(detail(10, 4, 2, [part(11)]))

    expect(shown.get('[data-testid="event-group-places"]').text()).toBe('4 von 10 Plätzen belegt, 2 auf der Nachrückliste')
    expect(shown.find('[data-testid="event-group-waiting"]').exists()).toBe(false)
  })

  /** Without a people limit the count of sign-ups stands alone, and whoever waits is shown beside it. */
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

/** The overview names when sign-ups close, as the server worked it out from the rule and the first part. */
describe('EventGroupOverview deadline', () => {
  it('names the closing moment the server sends', () => {
    const weekBefore: RegistrationDeadline = {kind: 'RELATIVE', at: null, offsetMinutes: 7 * 1440, anchor: 'MIDNIGHT'}
    const shown = overview(detail(null, 0, 0, [part(11)], weekBefore, '2027-02-26T23:00:00Z'))

    expect(shown.get('[data-testid="event-group-closes-at"]').text()).toContain('27.02.2027, 00:00')
  })

  it('names no deadline where sign-ups do not close', () => {
    expect(overview(detail(null, 0, 0, [part(11)])).find('[data-testid="event-group-closes-at"]').exists()).toBe(false)
  })
})
