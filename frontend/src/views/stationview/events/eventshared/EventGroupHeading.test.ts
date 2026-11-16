/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it, vi} from 'vitest'
import {ref} from 'vue'
import {mount} from '@vue/test-utils'
import EventGroupHeading from './EventGroupHeading.vue'

vi.mock('@/composables/useSession', () => ({
  useSession: () => ({stationTimezone: ref('Europe/Berlin')}),
}))

const part = {
  eventGroupId: 70, eventGroupName: 'Jugendflamme 1', setId: 1, setName: 'Übung', number: 2, count: 4, color: '#3694ff',
  firstStart: '2026-12-12T08:00:00Z', lastEnd: '2026-12-20T15:00:00Z',
}

/** The heading says which event group an appointment belongs to, which part it is and when the group runs. */
describe('EventGroupHeading', () => {
  it('names the event group, the part and the days it runs over', () => {
    const text = mount(EventGroupHeading, {props: {eventGroup: part}}).text()

    expect(text).toContain('Jugendflamme 1')
    expect(text).toContain('Teil 2 von 4')
    expect(text).toContain('12.12. bis 20.12.2026')
  })

  it('is a bar in the event group colour with text that stays readable on it', () => {
    const style = mount(EventGroupHeading, {props: {eventGroup: {...part, color: '#ffff00'}}}).get('[data-testid="event-group-heading"]').attributes('style')

    expect(style).toContain('background-color: #ffff00')
    expect(style).toMatch(/[^-]color: /)
    expect(style).not.toMatch(/[^-]color: (#ffffff|rgb\(255, 255, 255\))/)
  })

  it('names a single day once', () => {
    const text = mount(EventGroupHeading, {props: {eventGroup: {...part, lastEnd: '2026-12-12T15:00:00Z'}}}).text()

    expect(text).toContain('12.12.2026')
    expect(text).not.toContain('bis')
  })
})
