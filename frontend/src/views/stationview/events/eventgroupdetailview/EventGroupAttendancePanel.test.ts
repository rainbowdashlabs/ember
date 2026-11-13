/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {afterEach, describe, expect, it, vi} from 'vitest'
import {flushPromises, mount, type VueWrapper} from '@vue/test-utils'
import EventGroupAttendancePanel from './EventGroupAttendancePanel.vue'
import type {EventGroupAttendance, EventGroupDetail} from '@/api/generated/schema'
import {createIdentity} from '@/test/mocks/factories'

const listAttendance = vi.fn(async (..._args: unknown[]): Promise<EventGroupAttendance[]> => [])

vi.mock('@/api', () => ({eventGroups: {listAttendance: (...args: unknown[]) => listAttendance(...args)}}))

function detail(): EventGroupDetail {
  return {
    eventGroup: {
      id: 70, name: 'Jugendflamme 1', description: null, registrationLimit: null, registrationDeadline: null,
      requiresConfirmation: false, cancelled: false, cancelReason: null, partCount: 4, holding: 2,
    },
    sets: [],
    parts: [],
    registration: {userTypes: [], groupIds: [], tagIds: [], memberIds: [], mode: 'AND'},
    view: {userTypes: [], groupIds: [], tagIds: [], memberIds: [], mode: 'AND'},
    questions: [],
    household: [],
  }
}

let wrapper: VueWrapper | null = null

afterEach(() => {
  wrapper?.unmount()
  wrapper = null
})

describe('EventGroupAttendancePanel', () => {
  it('shows per participant the parts attended against each set and whether all are met', async () => {
    listAttendance.mockResolvedValue([
      {memberId: 11, member: createIdentity({name: 'Anna'}), complete: true,
        sets: [{setId: 1, name: 'Übung', required: 2, chosen: 2}, {setId: 2, name: 'Prüfung', required: 1, chosen: 1}]},
      {memberId: 12, member: createIdentity({name: 'Ben'}), complete: false,
        sets: [{setId: 1, name: 'Übung', required: 2, chosen: 1}, {setId: 2, name: 'Prüfung', required: 1, chosen: 0}]},
    ])
    wrapper = mount(EventGroupAttendancePanel, {props: {detail: detail()}, global: {stubs: {MemberName: true}}})
    await flushPromises()

    const rows = wrapper.findAll('[data-testid="event-group-attendance-row"]')
    expect(rows).toHaveLength(2)
    expect(rows[1]!.text()).toContain('Übung: 1 von 2')
    expect(listAttendance).toHaveBeenCalledWith(70)
  })
})
