/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it} from 'vitest'
import {mount} from '@vue/test-utils'
import {ref} from 'vue'
import EventGroupColorField from './EventGroupColorField.vue'
import EventGroupSignUpFields from './EventGroupSignUpFields.vue'
import {useEventGroupDetails} from './useEventGroupDetails'
import {EVENT_GROUP_PALETTE} from '../eventshared/eventGroupColor'
import type {EventGroupDetails} from '@/api/generated/schema'

function detailsOf(color: string, minRegistrations?: number): EventGroupDetails {
  return {name: 'Grundlehrgang', description: null, registrationLimit: null, registrationDeadline: null,
    requiresConfirmation: false, color: color || null, minRegistrations: minRegistrations ?? null, thresholdDays: null}
}

function mountForm(color: string, eventGroupId: number | null, minRegistrations?: number) {
  return mount(EventGroupColorField, {
    props: {modelValue: detailsOf(color, minRegistrations), eventGroupId},
    global: {stubs: {'font-awesome-icon': true}},
  })
}

function mountSignUp(minRegistrations?: number) {
  return mount(EventGroupSignUpFields, {
    props: {modelValue: detailsOf('', minRegistrations)},
    global: {stubs: {'font-awesome-icon': true, DateTimeInput: true}},
  })
}

function lastDetails(form: {emitted: (event: string) => unknown[][] | undefined}): EventGroupDetails {
  return form.emitted('update:modelValue')?.at(-1)?.[0] as EventGroupDetails
}

/** Without a colour of its own the form shows the one the id picks and offers nothing to reset. */
describe('EventGroupColorField', () => {
  it('shows the palette colour the id picks while none is set', () => {
    const form = mountForm('', 2)

    expect(form.get('[data-testid="event-group-color"]').attributes('style')).toContain(EVENT_GROUP_PALETTE[2])
    expect(form.find('[data-testid="event-group-color-reset"]').exists()).toBe(false)
  })

  it('shows its own colour and offers to go back to the palette', async () => {
    const form = mountForm('#123456', 2)

    expect(form.get('[data-testid="event-group-color"]').attributes('style')).toContain('#123456')
    await form.get('[data-testid="event-group-color-reset"]').trigger('click')
    expect(lastDetails(form)).toEqual({...detailsOf('#123456'), color: null})
  })

  it('stays neutral for a new event group and says the colour is picked on saving', () => {
    const form = mountForm('', null)

    const style = form.get('[data-testid="event-group-color"]').attributes('style') ?? ''
    expect(EVENT_GROUP_PALETTE.some(color => style.includes(color))).toBe(false)
    expect(form.get('[data-testid="hint-icon"]').attributes('aria-label')).toContain('wird beim Speichern eine aus der Palette gewählt')
  })
})

/** The days before the first part are asked only once a minimum is given, and a typed count is kept whole. */
describe('EventGroupSignUpFields minimum', () => {
  it('asks for the days only with a minimum', () => {
    expect(mountSignUp().find('[data-testid="event-group-threshold"]').exists()).toBe(false)
    expect(mountSignUp(6).find('[data-testid="event-group-threshold"]').exists()).toBe(true)
  })

  it('hands on the minimum as a whole number of zero or more', async () => {
    const form = mountSignUp()

    await form.get('[data-testid="event-group-minimum"]').setValue('-3')

    expect(lastDetails(form).minRegistrations).toBe(0)
  })
})

/** The details come and go as one value, each field changing its own part and the deadline kept as an instant. */
describe('useEventGroupDetails', () => {
  it('changes the name and keeps every other part', () => {
    const details = ref(detailsOf('#123456', 6))

    useEventGroupDetails(details).name.value = 'Aufbaulehrgang'

    expect(details.value).toEqual({...detailsOf('#123456', 6), name: 'Aufbaulehrgang'})
  })

  it('hands on the deadline typed in local time as an instant, and none when it is cleared', async () => {
    const form = mountSignUp()
    const deadline = form.getComponent({name: 'DateTimeInput'})

    deadline.vm.$emit('update:modelValue', '2026-12-01T18:00')
    expect(lastDetails(form).registrationDeadline).toBe(new Date('2026-12-01T18:00').toISOString())

    deadline.vm.$emit('update:modelValue', '')
    expect(lastDetails(form).registrationDeadline).toBeNull()
  })
})
