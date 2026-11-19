/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it, vi} from 'vitest'
import {flushPromises, mount} from '@vue/test-utils'
import AwaitingAnswerPanel from './AwaitingAnswerPanel.vue'
import RowLink from '@/components/navigation/RowLink.vue'

const listAwaitingAnswer = vi.fn(async () => [] as unknown[])
const listAwaiting = vi.fn(async () => [{
  eventGroupId: 70, name: 'Jugendflamme 1', registrationClosesAt: new Date(Date.now() + 2 * 86400000).toISOString(),
  members: [{memberId: 12, name: 'Mara'}],
}])

vi.mock('@/api', () => ({
  events: {listAwaitingAnswer: () => listAwaitingAnswer(), listCategories: async () => []},
  eventGroups: {listAwaiting: () => listAwaiting()},
}))

/** One date of the weekly drill, closing for sign-up in two days. */
function drillOn(eventDate: string) {
  return {
    eventId: 31, eventDate, name: 'Dienstabend', categoryId: null, startTime: `${eventDate}T17:00:00Z`,
    registrationClosesAt: new Date(Date.now() + 2 * 86400000).toISOString(), members: [{memberId: 12, name: 'Mara'}],
  }
}

describe('AwaitingAnswerPanel', () => {
  it('lists an event group still waiting on an answer, even when the appointments cannot load', async () => {
    listAwaitingAnswer.mockRejectedValueOnce(new Error('offline'))
    const panel = mount(AwaitingAnswerPanel)
    await flushPromises()

    const eventGroup = panel.find('[data-testid="awaiting-group"]')
    expect(eventGroup.exists()).toBe(true)
    expect(eventGroup.text()).toContain('Jugendflamme 1')
    expect(eventGroup.text()).toContain('Mara')
  })

  it('lists two dates of one series as two rows, each naming and leading to its own date', async () => {
    listAwaitingAnswer.mockResolvedValueOnce([drillOn('2026-11-27'), drillOn('2026-12-04')])
    const panel = mount(AwaitingAnswerPanel)
    await flushPromises()

    const rows = panel.findAll('[data-testid="awaiting-answer"]')
    expect(rows).toHaveLength(2)
    expect(rows[0]!.get('[data-testid="awaiting-answer-date"]').text()).toContain('27.11.2026')
    expect(rows[1]!.get('[data-testid="awaiting-answer-date"]').text()).toContain('04.12.2026')
    expect(panel.findAllComponents(RowLink).slice(0, 2).map(link => link.props('to'))).toEqual([
      {name: 'event-detail-date', params: {id: 31, date: '2026-11-27'}},
      {name: 'event-detail-date', params: {id: 31, date: '2026-12-04'}},
    ])
  })
})
