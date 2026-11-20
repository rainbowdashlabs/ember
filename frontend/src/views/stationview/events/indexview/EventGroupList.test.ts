/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it, vi} from 'vitest'
import {flushPromises, mount} from '@vue/test-utils'
import {computed} from 'vue'
import EventGroupList from './EventGroupList.vue'

const api = vi.hoisted(() => ({
  eventGroups: {listEventGroups: vi.fn()},
}))
vi.mock('@/api', () => api)
const push = vi.hoisted(() => vi.fn())
vi.mock('vue-router', () => ({useRouter: () => ({push})}))
vi.mock('@/composables/useSession', () => ({useSession: () => ({stationTimezone: computed(() => 'Europe/Berlin')})}))

/** Every event group is listed with its counts and leads to its page, a cancelled one marked as such. */
describe('EventGroupList', () => {
  it('lists every event group with its days and counts, linking to its page and marking a cancelled one', async () => {
    api.eventGroups.listEventGroups.mockResolvedValue([
      {id: 1, details: {name: 'Jugendflamme'}, partCount: 4, holding: 7, pending: 5, queued: 2, cancelled: false,
        firstStart: '2027-03-06T07:00:00Z', lastEnd: '2027-03-20T15:00:00Z'},
      {id: 3, details: {name: 'Leistungsabzeichen'}, partCount: 0, holding: 0, pending: 0, queued: 0, cancelled: true,
        firstStart: null, lastEnd: null},
    ])
    const wrapper = mount(EventGroupList)
    await flushPromises()

    const first = wrapper.find('[data-testid="event-group-list-row-1"]')
    expect(first.text()).toContain('Jugendflamme')
    expect(first.text()).toContain('4')
    expect(first.text()).toContain('7')
    expect(first.text()).toContain('2')
    expect(first.text()).toContain('Offen: 5')
    expect(wrapper.find('[data-testid="event-group-list-days-1"]').text()).toBe('06.03.2027 bis 20.03.2027')
    expect(wrapper.find('[data-testid="event-group-list-days-3"]').exists()).toBe(false)
    expect(first.text()).not.toContain('Abgesagt')
    expect(wrapper.find('[data-testid="event-group-list-row-3"]').text()).toContain('Abgesagt')
    const links = wrapper.findAllComponents({name: 'RowLink'})
    expect(links.map(link => link.props('to'))).toEqual([
      {name: 'event-group', params: {eventGroupId: 1}},
      {name: 'event-group', params: {eventGroupId: 3}},
    ])
  })

  it('says so when there is no event group', async () => {
    api.eventGroups.listEventGroups.mockResolvedValue([])
    const wrapper = mount(EventGroupList)
    await flushPromises()

    expect(wrapper.text()).toContain('Es gibt noch keine Terminreihen.')
  })

  it('starts a new event group from the tab', async () => {
    api.eventGroups.listEventGroups.mockResolvedValue([])
    const wrapper = mount(EventGroupList)
    await flushPromises()

    await wrapper.find('[data-testid="event-group-list-create"]').trigger('click')

    expect(push).toHaveBeenCalledWith({name: 'event-group-new'})
  })
})
