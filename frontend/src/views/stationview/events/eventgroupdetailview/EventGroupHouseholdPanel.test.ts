/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {afterEach, describe, expect, it, vi} from 'vitest'
import {flushPromises, mount, type VueWrapper} from '@vue/test-utils'
import EventGroupHouseholdPanel from './EventGroupHouseholdPanel.vue'
import ConfirmDeleteModal from '@/components/feedback/ConfirmDeleteModal.vue'
import type {EventGroupDetail, EventGroupSignUp} from '@/api/generated/schema'
import {createIdentity} from '@/test/mocks/factories'

const withdraw = vi.fn(async (..._args: unknown[]) => ({}))
const changeAnswers = vi.fn(async (..._args: unknown[]) => ({}))

vi.mock('@/api', () => ({eventGroups: {
  withdraw: (...args: unknown[]) => withdraw(...args),
  changeAnswers: (...args: unknown[]) => changeAnswers(...args),
}}))

function entry(memberId: number, name: string, overrides: Partial<EventGroupSignUp> = {}): EventGroupSignUp {
  return {memberId, member: createIdentity({name}), status: null, registeredAt: null, parts: [], sets: [], answers: [], answersMissing: false, mayRegister: true, queuePosition: null, ...overrides}
}

function detail(household: EventGroupSignUp[], deadline: string | null = null): EventGroupDetail {
  return {
    eventGroup: {
      id: 70,
      details: {name: 'Jugendflamme 1', description: null, registrationLimit: null, registrationDeadline: deadline, requiresConfirmation: false, color: null, minRegistrations: null, thresholdDays: null, partRegistrationLimit: null, queueOrder: null},
      cancelled: false, cancelReason: null, partCount: 1, holding: 0, queued: 0, pending: 0, firstStart: null, lastEnd: null,
    },
    sets: [{id: 1, eventGroupId: 70, name: 'Teile', minimum: 1, maximum: null, position: 0}],
    parts: [{eventId: 11, setId: 1, name: 'Samstag', startTime: '2099-03-06T08:00:00Z', endTime: '2099-03-06T16:00:00Z', calledOff: false, tookPlace: false, required: false, accepted: 0, places: null}],
    registration: {userTypes: [], groupIds: [], tagIds: [], memberIds: [], mode: 'AND'},
    view: {userTypes: [], groupIds: [], tagIds: [], memberIds: [], mode: 'AND'},
    questions: [],
    fields: [],
    household,
  }
}

let wrapper: VueWrapper | null = null

afterEach(() => {
  wrapper?.unmount()
  wrapper = null
  document.body.innerHTML = ''
  withdraw.mockClear()
  changeAnswers.mockClear()
})

describe('EventGroupHouseholdPanel', () => {
  it('says why nobody can sign up once the deadline passed', () => {
    wrapper = mount(EventGroupHouseholdPanel, {
      props: {detail: detail([entry(11, 'Lea')], '2000-01-01T00:00:00Z')},
      global: {stubs: {MemberName: true}},
    })

    expect(wrapper.find('[data-testid="event-group-sign-up"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="event-group-closed"]').exists()).toBe(true)
  })

  it('withdraws the child it was asked for after a neutral question, since leaving deletes nothing', async () => {
    const onChanged = vi.fn()
    wrapper = mount(EventGroupHouseholdPanel, {
      props: {detail: detail([entry(11, 'Lea'), entry(12, 'Mara', {status: 'ACCEPTED', parts: [11]})]), onChanged},
      attachTo: document.body,
      global: {stubs: {MemberName: true}},
    })

    await wrapper.find('[data-testid="event-group-withdraw"]').trigger('click')
    await flushPromises()
    expect(wrapper.findComponent(ConfirmDeleteModal).props('neutral')).toBe(true)
    ;(document.body.querySelector('[data-confirm]') as HTMLButtonElement).click()
    await flushPromises()

    expect(withdraw).toHaveBeenCalledWith(70, 12)
    expect(onChanged).toHaveBeenCalled()
  })

  it('shows the answers of a standing sign-up, marks one still owed and changes them', async () => {
    const onChanged = vi.fn()
    const asking = detail([entry(12, 'Mara', {
      status: 'ACCEPTED', parts: [11], answers: [{fieldId: 5, value: 'Nüsse'}], answersMissing: true,
    })])
    asking.questions = [
      {id: 5, eventId: null, eventGroupId: 70, name: 'Allergien', fieldType: 'TEXT', config: {}, position: 0, overview: false},
      {id: 6, eventId: null, eventGroupId: 70, name: 'T-Shirt', fieldType: 'TEXT', config: {required: true}, position: 1, overview: false},
    ] as EventGroupDetail['questions']
    wrapper = mount(EventGroupHouseholdPanel, {
      props: {detail: asking, onChanged},
      attachTo: document.body,
      global: {stubs: {MemberName: true}},
    })

    expect(wrapper.text()).toContain('Nüsse')
    expect(wrapper.find('[data-testid="event-group-answer-missing"]').exists()).toBe(true)

    await wrapper.find('[data-testid="event-group-change-answers"]').trigger('click')
    await flushPromises()
    const inputs = document.body.querySelectorAll('input')
    const shirt = inputs[inputs.length - 1] as HTMLInputElement
    shirt.value = 'M'
    shirt.dispatchEvent(new Event('input'))
    await flushPromises()
    ;(document.body.querySelector('[data-onboarding="events.registration-fields.submit"]') as HTMLButtonElement).click()
    await flushPromises()

    expect(changeAnswers).toHaveBeenCalledWith(70, [{fieldId: 5, value: 'Nüsse'}, {fieldId: 6, value: 'M'}], 12)
    expect(onChanged).toHaveBeenCalled()
  })

  /** A full event group still takes sign-ups: they wait on the waiting list, which the button says. */
  it('offers the waiting list once every place for people is taken', () => {
    const full = detail([entry(11, 'Lea')])
    full.eventGroup = {...full.eventGroup, holding: 10, details: {...full.eventGroup.details, registrationLimit: 10}}
    wrapper = mount(EventGroupHouseholdPanel, {props: {detail: full}, global: {stubs: {MemberName: true}}})

    expect(wrapper.get('[data-testid="event-group-sign-up"]').text()).toContain('Auf die Nachrückliste')
  })

  /** Whoever waits sees their place in line and leaves the list, which the confirmation says too. */
  it('shows the place in line and leaves the waiting list', async () => {
    wrapper = mount(EventGroupHouseholdPanel, {
      props: {detail: detail([entry(12, 'Mara', {status: 'QUEUED', parts: [11], queuePosition: 2})])},
      attachTo: document.body,
      global: {stubs: {MemberName: true}},
    })

    expect(wrapper.get('[data-testid="registration-status"]').text()).toBe('Nachrückliste, Platz 2')
    const leave = wrapper.get('[data-testid="event-group-withdraw"]')
    expect(leave.text()).toContain('Nachrückliste verlassen')
    await leave.trigger('click')
    await flushPromises()

    expect((document.body.querySelector('[data-confirm]') as HTMLButtonElement).textContent).toContain('Nachrückliste verlassen')
  })

  /** Once no part is left, a wait nobody moved up from reads as no place, with nothing left to leave. */
  it('reads a wait as no place once every part is over', () => {
    const over = detail([entry(12, 'Mara', {status: 'QUEUED', parts: [11], queuePosition: 1})])
    over.parts = over.parts.map(part => ({...part, tookPlace: true}))
    wrapper = mount(EventGroupHouseholdPanel, {props: {detail: over}, global: {stubs: {MemberName: true}}})

    expect(wrapper.get('[data-testid="registration-status"]').text()).toBe('Keinen Platz bekommen')
    expect(wrapper.find('[data-testid="event-group-withdraw"]').exists()).toBe(false)
  })
})
