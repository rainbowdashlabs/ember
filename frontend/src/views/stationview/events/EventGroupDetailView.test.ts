/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest'
import {enableAutoUnmount, flushPromises} from '@vue/test-utils'
import {mountSuspended} from '@nuxt/test-utils/runtime'
import {ref} from 'vue'
import {StationPermission, type EventGroupDetail} from '@/api/generated/schema'
import EventGroupDetailView from './EventGroupDetailView.vue'

const api = vi.hoisted(() => ({
  eventGroups: {getEventGroup: vi.fn(), cancelEventGroup: vi.fn(), deleteEventGroup: vi.fn()},
  stationMembers: {listCompletions: vi.fn(async () => [])},
}))
vi.mock('@/api', () => api)
vi.mock('@/api/eventGroups', () => ({eventGroupCommentSource: () => ({})}))

const permissions = vi.hoisted(() => ({granted: [] as string[]}))
vi.mock('@/composables/useSession', () => ({
  useSession: () => ({
    loaded: ref(true),
    hasPermission: (permission: string) => permissions.granted.includes(permission),
    sessionInfo: ref(null),
  }),
}))

enableAutoUnmount(afterEach)

const STUBS = Object.fromEntries([
  'EventGroupOverview', 'EventGroupMaterialsPanel', 'AgreementSignersPanel', 'EventGroupHouseholdPanel',
  'EventGroupSignUpsPanel', 'EventGroupPartnerSignUpsPanel', 'EventGroupAttendancePanel', 'EventGroupProbeVisitorsPanel',
  'CommentSection', 'NoteEditor', 'EventGroupCancelDialog',
].map(name => [name, true]))

async function settle() {
  await flushPromises()
  await new Promise(resolve => setTimeout(resolve, 0))
  await flushPromises()
}

function inPage(testId: string): HTMLElement | null {
  return document.querySelector<HTMLElement>(`[data-testid="${testId}"]`)
}

async function mountView(cancelled: boolean) {
  api.eventGroups.getEventGroup.mockResolvedValue({
    eventGroup: {id: 7, cancelled, details: {name: 'Grundlehrgang'}}, parts: [], sets: [], household: [], fields: [], questions: [],
  } as unknown as EventGroupDetail)
  await mountSuspended(EventGroupDetailView, {attachTo: document.body, global: {stubs: STUBS}})
  await settle()
  inPage('event-group-actions-trigger')?.click()
  await settle()
}

/** The head of an event group's page offers what the reader may do, as an appointment's does. */
describe('EventGroupDetailView header', () => {
  beforeEach(() => {
    permissions.granted = []
  })

  it('gives whoever edits appointments Edit, and Cancel and Delete in the menu', async () => {
    permissions.granted = [StationPermission.EVENT_EDIT]
    await mountView(false)

    expect(inPage('event-group-edit')).not.toBeNull()
    expect(inPage('event-group-cancel')).not.toBeNull()
    expect(inPage('event-group-delete')).not.toBeNull()
  })

  it('offers no Cancel once the event group is called off, but still Delete', async () => {
    permissions.granted = [StationPermission.EVENT_EDIT]
    await mountView(true)

    expect(inPage('event-group-cancel')).toBeNull()
    expect(inPage('event-group-delete')).not.toBeNull()
  })

  it('gives a reader who may not edit only Back', async () => {
    await mountView(false)

    expect(inPage('detail-page-back')).not.toBeNull()
    expect(inPage('event-group-edit')).toBeNull()
    expect(inPage('event-group-actions-trigger')).toBeNull()
    expect(inPage('event-group-delete')).toBeNull()
  })
})
