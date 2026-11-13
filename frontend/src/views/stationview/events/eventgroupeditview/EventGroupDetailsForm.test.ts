/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it} from 'vitest'
import {mount} from '@vue/test-utils'
import EventGroupDetailsForm from './EventGroupDetailsForm.vue'
import {EVENT_GROUP_PALETTE} from '../eventshared/eventGroupColor'

function mountForm(color: string, eventGroupId: number | null) {
  return mount(EventGroupDetailsForm, {
    props: {
      name: 'Grundlehrgang', description: '', limit: undefined, deadline: '', confirm: false, color, eventGroupId,
      'onUpdate:color': () => undefined,
    },
    global: {stubs: {MarkdownEditor: true, DateTimeInput: true}},
  })
}

/** Without a colour of its own the form shows the one the id picks and offers nothing to reset. */
describe('EventGroupDetailsForm colour', () => {
  it('shows the palette colour the id picks while none is set', () => {
    const form = mountForm('', 2)

    expect(form.get('[data-testid="event-group-color"]').attributes('style')).toContain(EVENT_GROUP_PALETTE[2])
    expect(form.find('[data-testid="event-group-color-reset"]').exists()).toBe(false)
  })

  it('shows its own colour and offers to go back to the palette', async () => {
    const form = mountForm('#123456', 2)

    expect(form.get('[data-testid="event-group-color"]').attributes('style')).toContain('#123456')
    await form.get('[data-testid="event-group-color-reset"]').trigger('click')
    expect(form.emitted('update:color')?.[0]).toEqual([''])
  })

  it('stays neutral for a new event group and says the colour is picked on saving', () => {
    const form = mountForm('', null)

    const style = form.get('[data-testid="event-group-color"]').attributes('style') ?? ''
    expect(EVENT_GROUP_PALETTE.some(color => style.includes(color))).toBe(false)
    expect(form.text()).toContain('wird beim Speichern eine aus der Palette gewählt')
  })
})
