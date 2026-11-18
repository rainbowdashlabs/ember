/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {afterEach, describe, expect, it, vi} from 'vitest'
import {flushPromises, type VueWrapper} from '@vue/test-utils'
import {mountSuspended} from '@nuxt/test-utils/runtime'
import EventGroupAttendancePanel from './EventGroupAttendancePanel.vue'
import RecordTable from '@/components/table/RecordTable.vue'
import ExportFormatModal from '@/components/documents/ExportFormatModal.vue'
import type {DataTableApi} from '@/composables/useDataTable'
import type {EventGroupAttendance, EventGroupDetail} from '@/api/generated/schema'
import {createIdentity} from '@/test/mocks/factories'

const listAttendance = vi.fn(async (..._args: unknown[]): Promise<EventGroupAttendance[]> => [])
const exportAttendance = vi.hoisted(() => vi.fn())
const presentFile = vi.hoisted(() => vi.fn())

vi.mock('@/api', () => ({eventGroups: {listAttendance: (...args: unknown[]) => listAttendance(...args), exportAttendance}}))
vi.mock('@/util/documentFile', () => ({presentFile}))

function detail(): EventGroupDetail {
  return {
    eventGroup: {
      id: 70,
      details: {name: 'Jugendflamme 1', description: null, registrationLimit: null, registrationDeadline: null, requiresConfirmation: false, color: null, minRegistrations: null, thresholdDays: null, partRegistrationLimit: null, queueOrder: null},
      cancelled: false, cancelReason: null, partCount: 4, holding: 2, queued: 0,
    },
    sets: [
      {id: 1, eventGroupId: 70, name: 'Übung', minimum: 2, maximum: null, position: 0},
      {id: 2, eventGroupId: 70, name: 'Prüfung', minimum: 1, maximum: null, position: 1},
    ],
    parts: [],
    registration: {userTypes: [], groupIds: [], tagIds: [], memberIds: [], mode: 'AND'},
    view: {userTypes: [], groupIds: [], tagIds: [], memberIds: [], mode: 'AND'},
    questions: [],
    fields: [],
    household: [],
  }
}

let wrapper: VueWrapper | null = null

afterEach(() => {
  wrapper?.unmount()
  wrapper = null
})

const ATTENDANCE: EventGroupAttendance[] = [
  {memberId: 11, member: createIdentity({name: 'Anna'}), complete: true,
    sets: [{setId: 1, name: 'Übung', required: 2, chosen: 2, missingRequired: [], maximum: null, tooMany: 0}, {setId: 2, name: 'Prüfung', required: 1, chosen: 1, missingRequired: [], maximum: null, tooMany: 0}]},
  {memberId: 12, member: createIdentity({name: 'Ben'}), complete: false,
    sets: [{setId: 1, name: 'Übung', required: 2, chosen: 1, missingRequired: [], maximum: null, tooMany: 0}, {setId: 2, name: 'Prüfung', required: 1, chosen: 0, missingRequired: [], maximum: null, tooMany: 0}]},
]

describe('EventGroupAttendancePanel', () => {
  it('names a column after every set of the event group', async () => {
    listAttendance.mockResolvedValue(ATTENDANCE)
    wrapper = await mountSuspended(EventGroupAttendancePanel, {props: {detail: detail()}, global: {stubs: {MemberName: true}}})
    await flushPromises()

    const table = (wrapper.getComponent(RecordTable).props() as {table: DataTableApi<EventGroupAttendance>}).table
    expect(table.visibleColumns.map(column => column.label)).toEqual(['Person', 'Übung', 'Prüfung', 'Alles erfüllt'])
  })

  it('narrows to the participants who have not met everything yet', async () => {
    listAttendance.mockResolvedValue(ATTENDANCE)
    wrapper = await mountSuspended(EventGroupAttendancePanel, {props: {detail: detail()}, global: {stubs: {MemberName: true}}})
    await flushPromises()

    const table = (wrapper.getComponent(RecordTable).props() as {table: DataTableApi<EventGroupAttendance>}).table
    table.setFilter('complete', new Set(['false']), false)
    await flushPromises()

    expect(wrapper.findAll('[data-testid="event-group-attendance-row"]')).toHaveLength(1)
    expect(wrapper.get('[data-testid="event-group-attendance-row"]').text()).toContain('Offen')
  })

  it('shows per participant the parts attended against each set and whether all are met', async () => {
    listAttendance.mockResolvedValue([
      {memberId: 11, member: createIdentity({name: 'Anna'}), complete: true,
        sets: [{setId: 1, name: 'Übung', required: 2, chosen: 2, missingRequired: [], maximum: null, tooMany: 0}, {setId: 2, name: 'Prüfung', required: 1, chosen: 1, missingRequired: [], maximum: null, tooMany: 0}]},
      {memberId: 12, member: createIdentity({name: 'Ben'}), complete: false,
        sets: [{setId: 1, name: 'Übung', required: 2, chosen: 1, missingRequired: [], maximum: null, tooMany: 0}, {setId: 2, name: 'Prüfung', required: 1, chosen: 0, missingRequired: [], maximum: null, tooMany: 0}]},
    ])
    wrapper = await mountSuspended(EventGroupAttendancePanel, {props: {detail: detail()}, global: {stubs: {MemberName: true}}})
    await flushPromises()

    const rows = wrapper.findAll('[data-testid="event-group-attendance-row"]')
    expect(rows).toHaveLength(2)
    expect(rows[1]!.text()).toContain('1 von 2')
    expect(listAttendance).toHaveBeenCalledWith(70)
  })

  it('offers the export as a spreadsheet and as a sheet to print, and takes the sheet away', async () => {
    listAttendance.mockResolvedValue(ATTENDANCE)
    const file = {name: 'Terminreihe.pdf'}
    exportAttendance.mockResolvedValue(file)
    wrapper = await mountSuspended(EventGroupAttendancePanel, {
      props: {detail: detail()},
      global: {stubs: {MemberName: true, Modal: {template: '<div><slot/></div>'}}},
    })
    await flushPromises()

    await wrapper.get('[data-testid="event-group-attendance-export"]').trigger('click')
    const modal = wrapper.getComponent(ExportFormatModal)
    expect(modal.props('formats')).toEqual(['csv', 'pdf'])
    modal.vm.$emit('export', 'pdf', 'semicolon')
    await flushPromises()

    expect(exportAttendance).toHaveBeenCalledWith(70, 'pdf', 'semicolon')
    expect(presentFile).toHaveBeenCalledWith(file)
  })
})
