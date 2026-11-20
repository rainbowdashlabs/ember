/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it, vi} from 'vitest'
import {flushPromises, mount} from '@vue/test-utils'
import {computed} from 'vue'
import UpcomingEventsPanel from './UpcomingEventsPanel.vue'
import DashboardEventTile from './upcomingeventspanel/DashboardEventTile.vue'
import {shiftIsoDate, stationToday} from '@/util/format'

const BERLIN = 'Europe/Berlin'
const IN_FORTY_DAYS = shiftIsoDate(stationToday(BERLIN), 40)

const listUpcomingOccurrences = vi.fn(async (_params: Record<string, unknown>) => [{
  event: {id: 9, name: 'Zeltlager', eventType: 'ONE_TIME', startTime: `${IN_FORTY_DAYS}T08:00:00Z`, requiresRegistration: false},
  date: IN_FORTY_DAYS,
  cancellation: null,
  registrationClosesAt: null,
}])

vi.mock('@/api', () => ({
  events: {
    listUpcomingOccurrences: (params: Record<string, unknown>) => listUpcomingOccurrences(params),
    listCategories: async () => [],
    listEligibleMembers: async () => ({}),
    listMyRegistrations: async () => [],
  },
  managedMembers: {listManaged: async () => []},
}))
vi.mock('@/api/feedToken', () => ({getFeedStatus: async () => null}))
vi.mock('@/composables/useSession', () => ({
  useSession: () => ({sessionInfo: computed(() => null), isGuardian: () => false, stationTimezone: computed(() => BERLIN)}),
}))
vi.mock('vue-router', () => ({useRouter: () => ({push: vi.fn()})}))

/**
 * The panel lists the dates of a series for the next two weeks and a one-time appointment however far
 * ahead it lies, as it did while it worked the dates out itself.
 */
describe('UpcomingEventsPanel', () => {
  it('asks for series dates up to two weeks ahead and shows a one-time appointment forty days out', async () => {
    const panel = mount(UpcomingEventsPanel, {global: {stubs: {DashboardEventTile: true}}})
    await flushPromises()

    expect(listUpcomingOccurrences).toHaveBeenCalledWith({seriesTo: shiftIsoDate(stationToday(BERLIN), 14), limit: 10})
    const tiles = panel.findAllComponents(DashboardEventTile)
    expect(tiles).toHaveLength(1)
    expect(tiles[0]!.props('date')).toBe(IN_FORTY_DAYS)
  })
})
