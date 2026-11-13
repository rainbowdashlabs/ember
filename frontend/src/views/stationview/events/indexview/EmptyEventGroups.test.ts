/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it, vi} from 'vitest'
import {flushPromises, mount} from '@vue/test-utils'
import EmptyEventGroups from './EmptyEventGroups.vue'

const api = vi.hoisted(() => ({
  eventGroups: {listEventGroups: vi.fn()},
}))
vi.mock('@/api', () => api)

/** An event group without appointments is reached from here as long as it has none, a cancelled one marked as such. */
describe('EmptyEventGroups', () => {
  it('lists the event groups that hold no appointment yet, marking a cancelled one', async () => {
    api.eventGroups.listEventGroups.mockResolvedValue([
      {id: 1, name: 'Leer', partCount: 0, cancelled: false},
      {id: 2, name: 'Voll', partCount: 3, cancelled: false},
      {id: 3, name: 'Abgesagt', partCount: 0, cancelled: true},
    ])
    const wrapper = mount(EmptyEventGroups)
    await flushPromises()

    const rows = wrapper.findAll('[data-testid="empty-event-group-row"]')
    expect(rows.map(row => row.text())).toEqual(['Leer', 'AbgesagtAbgesagt'])
  })

  it('shows nothing while every event group has appointments', async () => {
    api.eventGroups.listEventGroups.mockResolvedValue([{id: 2, name: 'Voll', partCount: 3, cancelled: false}])
    const wrapper = mount(EmptyEventGroups)
    await flushPromises()

    expect(wrapper.find('[data-testid="empty-event-groups"]').exists()).toBe(false)
  })
})
