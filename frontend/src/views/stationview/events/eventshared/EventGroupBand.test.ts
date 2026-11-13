/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it} from 'vitest'
import {mount} from '@vue/test-utils'
import EventGroupBand from './EventGroupBand.vue'
import {EVENT_GROUP_PALETTE, eventGroupColor} from './eventGroupColor'

const part = {eventGroupId: 70, eventGroupName: 'Jugendflamme 1', setId: 1, setName: 'Übung', number: 2, count: 4, color: '#3694ff'}

/** The band says in words what its colour says, and only ever paints a colour of the expected shape. */
describe('EventGroupBand', () => {
  it('names the event group and the part as its label and tooltip', () => {
    const band = mount(EventGroupBand, {props: {eventGroup: part}}).get('[data-testid="event-group-band"]')

    expect(band.attributes('role')).toBe('img')
    expect(band.attributes('aria-label')).toBe('Jugendflamme 1, Teil 2 von 4')
    expect(band.attributes('title')).toBe('Jugendflamme 1, Teil 2 von 4')
  })

  it('paints the event group colour', () => {
    const band = mount(EventGroupBand, {props: {eventGroup: part}}).get('[data-testid="event-group-band"]')

    expect(band.attributes('style')).toContain('background-color: #3694ff')
  })

  it('stands in a line of text as a mark', () => {
    const band = mount(EventGroupBand, {props: {eventGroup: part, variant: 'mark'}}).get('[data-testid="event-group-band"]')

    expect(band.classes()).toContain('inline-block')
    expect(band.classes()).not.toContain('absolute')
  })
})

describe('eventGroupColor', () => {
  it('takes the event group colour where it has one', () => {
    expect(eventGroupColor(70, '#3694ff')).toBe('#3694ff')
  })

  it('picks a palette colour by id where it has none, different for neighbours', () => {
    expect(eventGroupColor(1, null)).toBe(EVENT_GROUP_PALETTE[1])
    expect(eventGroupColor(2, null)).not.toBe(eventGroupColor(1, null))
  })

  it('never uses a value of another shape', () => {
    expect(eventGroupColor(1, 'red; background-image: url(x)')).toBe(EVENT_GROUP_PALETTE[1])
  })
})
