/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it, vi} from 'vitest'
import {ref} from 'vue'
import {mount} from '@vue/test-utils'
import EventGroupTile from './EventGroupTile.vue'

vi.mock('@/composables/useSession', () => ({
  useSession: () => ({stationTimezone: ref('Europe/Berlin')}),
}))

const part = {
  eventGroupId: 70, eventGroupName: 'Jugendflamme 1', setId: 1, setName: 'Übung', number: 2, count: 4, color: '#3694ff',
  firstStart: '2026-12-12T08:00:00Z', lastEnd: '2026-12-20T15:00:00Z',
}

/** A tile of a part of an event group carries the group's band and heading; any other tile is plain. */
describe('EventGroupTile', () => {
  it('marks a part with the band and the heading and makes room for both', () => {
    const wrapper = mount(EventGroupTile, {
      props: {eventGroup: part},
      attrs: {'data-testid': 'tile', class: 'space-y-2'},
      slots: {default: '<p>Inhalt</p>'},
    })

    const tile = wrapper.get('[data-testid="tile"]')
    expect(tile.classes()).toEqual(expect.arrayContaining(['relative', 'pl-5', 'pt-9', 'space-y-2']))
    expect(wrapper.find('[data-testid="event-group-band"]').exists()).toBe(true)
    expect(wrapper.get('[data-testid="event-group-heading"]').text()).toContain('Jugendflamme 1')
    expect(wrapper.text()).toContain('Inhalt')
  })

  it('leaves a tile of an appointment of its own plain', () => {
    const wrapper = mount(EventGroupTile, {props: {eventGroup: null}, attrs: {'data-testid': 'tile'}, slots: {default: '<p>Inhalt</p>'}})

    expect(wrapper.get('[data-testid="tile"]').classes()).not.toContain('pt-9')
    expect(wrapper.find('[data-testid="event-group-band"]').exists()).toBe(false)
    expect(wrapper.text()).toContain('Inhalt')
  })
})
