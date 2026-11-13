/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {afterEach, describe, expect, it, vi} from 'vitest'
import {flushPromises, mount, type VueWrapper} from '@vue/test-utils'
import EventGroupHouseholdPanel from './EventGroupHouseholdPanel.vue'
import type {EventGroupDetail, EventGroupSignUp} from '@/api/generated/schema'
import {createIdentity} from '@/test/mocks/factories'

const withdraw = vi.fn(async (..._args: unknown[]) => ({}))
const changeAnswers = vi.fn(async (..._args: unknown[]) => ({}))

vi.mock('@/api', () => ({eventGroups: {
  withdraw: (...args: unknown[]) => withdraw(...args),
  changeAnswers: (...args: unknown[]) => changeAnswers(...args),
}}))

function entry(memberId: number, name: string, overrides: Partial<EventGroupSignUp> = {}): EventGroupSignUp {
  return {memberId, member: createIdentity({name}), status: null, registeredAt: null, parts: [], sets: [], answers: [], answersMissing: false, mayRegister: true, ...overrides}
}

function detail(household: EventGroupSignUp[], deadline: string | null = null): EventGroupDetail {
  return {
    eventGroup: {
      id: 70, name: 'Jugendflamme 1', description: null, registrationLimit: null, registrationDeadline: deadline,
      requiresConfirmation: false, cancelled: false, cancelReason: null, partCount: 1, holding: 0,
    },
    sets: [{id: 1, eventGroupId: 70, name: 'Teile', minimum: 1, position: 0}],
    parts: [{eventId: 11, setId: 1, name: 'Samstag', startTime: '2099-03-06T08:00:00Z', endTime: '2099-03-06T16:00:00Z', calledOff: false, tookPlace: false}],
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

  it('withdraws the child it was asked for', async () => {
    const onChanged = vi.fn()
    wrapper = mount(EventGroupHouseholdPanel, {
      props: {detail: detail([entry(11, 'Lea'), entry(12, 'Mara', {status: 'ACCEPTED', parts: [11]})]), onChanged},
      attachTo: document.body,
      global: {stubs: {MemberName: true}},
    })

    await wrapper.find('[data-testid="event-group-withdraw"]').trigger('click')
    await flushPromises()
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
})
