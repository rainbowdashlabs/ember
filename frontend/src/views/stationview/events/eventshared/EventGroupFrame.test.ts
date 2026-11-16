/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it, vi} from 'vitest'
import {ref} from 'vue'
import {mount} from '@vue/test-utils'
import EventGroupFrame from './EventGroupFrame.vue'

vi.mock('@/composables/useSession', () => ({
  useSession: () => ({stationTimezone: ref('Europe/Berlin')}),
}))

const part = {
  eventGroupId: 70, eventGroupName: 'Jugendflamme 1', setId: 1, setName: 'Übung', number: 2, count: 4, color: '#3694ff',
  firstStart: '2026-12-12T08:00:00Z', lastEnd: '2026-12-20T15:00:00Z',
}

/** A page of an event group stands in the event group's frame; any other page stands as it is. */
describe('EventGroupFrame', () => {
  it('frames the page with the bar, the band, the message and a way to the event group', () => {
    const wrapper = mount(EventGroupFrame, {
      props: {eventGroup: part, message: 'Die Anmeldung läuft über die Terminreihe.', to: '/station/event-groups/70'},
      slots: {default: '<p>Inhalt</p>'},
    })

    expect(wrapper.find('[data-testid="event-group-frame"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="event-group-band"]').exists()).toBe(true)
    expect(wrapper.get('[data-testid="event-group-heading"]').text()).toContain('Jugendflamme 1')
    expect(wrapper.get('[data-testid="event-group-heading-link"]').attributes('href')).toBe('/station/event-groups/70')
    expect(wrapper.text()).toContain('Die Anmeldung läuft über die Terminreihe.')
    expect(wrapper.text()).toContain('Inhalt')
  })

  it('leaves a page without an event group as it is', () => {
    const wrapper = mount(EventGroupFrame, {props: {eventGroup: null}, slots: {default: '<p>Inhalt</p>'}})

    expect(wrapper.find('[data-testid="event-group-frame"]').exists()).toBe(false)
    expect(wrapper.text()).toBe('Inhalt')
  })
})
