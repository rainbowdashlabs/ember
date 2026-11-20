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
import {noDeadline} from '../eventshared/registrationDeadline'

function detailsOf(color: string, minRegistrations?: number): EventGroupDetails {
  return {name: 'Grundlehrgang', description: null, registrationLimit: null, registrationDeadline: noDeadline(),
    requiresConfirmation: false, color: color || null, minRegistrations: minRegistrations ?? null, thresholdDays: null,
    partRegistrationLimit: null, queueOrder: null}
}

function mountForm(color: string, eventGroupId: number | null, minRegistrations?: number) {
  return mount(EventGroupColorField, {
    props: {modelValue: detailsOf(color, minRegistrations), eventGroupId},
    global: {stubs: {'font-awesome-icon': true}},
  })
}

function mountSignUp(minRegistrations?: number, parts: {places: number | null; startTime?: string}[] = []) {
  return mount(EventGroupSignUpFields, {
    props: {modelValue: detailsOf('', minRegistrations), parts, timeZone: 'Europe/Berlin'},
    global: {stubs: {'font-awesome-icon': true}},
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

/** The details come and go as one value, each field changing its own part and the deadline kept as a rule. */
describe('useEventGroupDetails', () => {
  it('changes the name and keeps every other part', () => {
    const details = ref(detailsOf('#123456', 6))

    useEventGroupDetails(details).name.value = 'Aufbaulehrgang'

    expect(details.value).toEqual({...detailsOf('#123456', 6), name: 'Aufbaulehrgang'})
  })

  it('hands on the deadline as a rule, and none when it is cleared', async () => {
    const form = mountSignUp()

    await form.get('[data-testid="registration-deadline-kind"]').setValue('RELATIVE')
    expect(lastDetails(form).registrationDeadline).toEqual({kind: 'RELATIVE', at: null, offsetMinutes: 1440, anchor: 'MIDNIGHT'})

    await form.get('[data-testid="registration-deadline-kind"]').setValue('NONE')
    expect(lastDetails(form).registrationDeadline).toEqual(noDeadline())
  })

  it('previews the deadline counted from the first part', () => {
    const form = mount(EventGroupSignUpFields, {
      props: {
        modelValue: {...detailsOf(''), registrationDeadline: {kind: 'RELATIVE', at: null, offsetMinutes: 10080, anchor: 'MIDNIGHT'}},
        parts: [{places: null, startTime: '2026-11-28T09:00:00Z'}, {places: null, startTime: '2026-11-21T09:00:00Z'}],
        timeZone: 'Europe/Berlin',
      },
      global: {stubs: {'font-awesome-icon': true}},
    })

    expect(form.get('[data-testid="registration-deadline-next"]').text()).toContain('14.11.2026, 00:00')
  })
})

/** Places per appointment and the waiting list order stand beside the places for people. */
describe('EventGroupSignUpFields places', () => {
  it('sets the places per appointment and offers the order once places are limited', async () => {
    const form = mountSignUp()
    expect(form.find('[data-testid="queue-order"]').exists()).toBe(false)

    await form.get('input[data-testid="event-group-part-limit"]').setValue('12')
    expect(lastDetails(form).partRegistrationLimit).toBe(12)

    await form.setProps({modelValue: {...detailsOf(''), partRegistrationLimit: 12}})
    await form.get('[data-testid="queue-order"] select').setValue('FAIRNESS')
    expect(lastDetails(form).queueOrder).toBe('FAIRNESS')
  })

  /** A part with places of its own keeps a waiting list too, so the order is asked for then as well. */
  it('offers the order where only a part has places of its own', () => {
    const form = mount(EventGroupSignUpFields, {
      props: {modelValue: detailsOf(''), parts: [{places: null}, {places: 20}]},
      global: {stubs: {'font-awesome-icon': true}},
    })

    expect(form.find('[data-testid="queue-order"]').exists()).toBe(true)
  })
})
