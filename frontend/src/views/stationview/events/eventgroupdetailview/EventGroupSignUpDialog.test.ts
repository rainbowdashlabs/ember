/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {afterEach, describe, expect, it, vi} from 'vitest'
import {flushPromises, mount, type VueWrapper} from '@vue/test-utils'
import {computed} from 'vue'
import EventGroupSignUpDialog from './EventGroupSignUpDialog.vue'
import type {EventGroupDetail, EventGroupSignUp} from '@/api/generated/schema'
import {createIdentity} from '@/test/mocks/factories'

const signUp = vi.fn(async (..._args: unknown[]) => ({}))
const changeParts = vi.fn(async (..._args: unknown[]) => ({}))

vi.mock('@/api', () => ({eventGroups: {signUp: (...args: unknown[]) => signUp(...args), changeParts: (...args: unknown[]) => changeParts(...args)}}))
vi.mock('@/composables/useSession', () => ({useSession: () => ({stationTimezone: computed(() => 'Europe/Berlin')})}))

/** A Jugendflamme event group: three practice days of which two count, and a mandatory test day. */
function detail(): EventGroupDetail {
  return {
    eventGroup: {
      id: 70, name: 'Jugendflamme 1', description: null, registrationLimit: null, registrationDeadline: null,
      requiresConfirmation: false, cancelled: false, cancelReason: null, partCount: 4, holding: 0,
    },
    sets: [
      {id: 1, eventGroupId: 70, name: 'Übung', minimum: 2, position: 0},
      {id: 2, eventGroupId: 70, name: 'Prüfung', minimum: 1, position: 1},
    ],
    parts: [11, 12, 13].map(eventId => ({
      eventId, setId: 1, name: `Übung ${eventId}`, startTime: '2027-03-06T08:00:00Z', endTime: '2027-03-06T16:00:00Z',
      calledOff: false, tookPlace: false,
    })).concat([{
      eventId: 21, setId: 2, name: 'Prüfung', startTime: '2027-03-14T08:00:00Z', endTime: '2027-03-14T12:00:00Z',
      calledOff: false, tookPlace: false,
    }]),
    registration: {userTypes: [], groupIds: [], tagIds: [], memberIds: [], mode: 'AND'},
    view: {userTypes: [], groupIds: [], tagIds: [], memberIds: [], mode: 'AND'},
    questions: [],
    fields: [],
    household: [],
  }
}

function entry(overrides: Partial<EventGroupSignUp> = {}): EventGroupSignUp {
  return {memberId: 12, member: createIdentity({name: 'Mara'}), status: null, registeredAt: null, parts: [], sets: [], answers: [], answersMissing: false, mayRegister: true, ...overrides}
}

let wrapper: VueWrapper | null = null

afterEach(() => {
  wrapper?.unmount()
  wrapper = null
  document.body.innerHTML = ''
  signUp.mockClear()
  changeParts.mockClear()
})

function box(eventId: number): HTMLInputElement {
  return document.body.querySelector(`[data-testid="event-group-part-${eventId}"]`) as HTMLInputElement
}

function save(): HTMLButtonElement {
  return document.body.querySelector('[data-testid="event-group-sign-up-save"]') as HTMLButtonElement
}

async function tick(eventId: number) {
  box(eventId).click()
  await flushPromises()
}

describe('EventGroupSignUpDialog', () => {
  it('keeps saving off and names the short set until the rules are met', async () => {
    wrapper = mount(EventGroupSignUpDialog, {props: {detail: detail(), entry: entry()}, attachTo: document.body})
    await flushPromises()

    expect(save().disabled).toBe(true)
    await tick(11)
    await tick(21)
    expect(save().disabled).toBe(true)
    expect(document.body.querySelector('[data-testid="event-group-shortfalls"]')!.textContent).toContain('Übung')

    await tick(13)
    expect(save().disabled).toBe(false)
    save().click()
    await flushPromises()

    expect(signUp).toHaveBeenCalledWith(70, expect.arrayContaining([11, 13, 21]), [], 12)
    expect(wrapper.emitted('saved')).toHaveLength(1)
  })

  it('changes the parts of a sign-up that holds a place, starting from the parts it holds', async () => {
    wrapper = mount(EventGroupSignUpDialog, {
      props: {detail: detail(), entry: entry({status: 'ACCEPTED', parts: [11, 12, 21]})},
      attachTo: document.body,
    })
    await flushPromises()

    expect(box(12).checked).toBe(true)
    await tick(12)
    await tick(13)
    save().click()
    await flushPromises()

    expect(changeParts).toHaveBeenCalledWith(70, expect.arrayContaining([11, 13, 21]), 12)
    expect(signUp).not.toHaveBeenCalled()
  })

  it('asks the event group questions with a new sign-up and keeps saving off while a required one is open', async () => {
    const asking = detail()
    asking.questions = [
      {id: 5, eventId: null, eventGroupId: 70, name: 'T-Shirt', fieldType: 'TEXT', config: {required: true}, position: 0, overview: false},
      {id: 6, eventId: null, eventGroupId: 70, name: 'Allergien', fieldType: 'TEXT', config: {defaultValue: 'keine'}, position: 1, overview: false},
    ] as EventGroupDetail['questions']
    wrapper = mount(EventGroupSignUpDialog, {props: {detail: asking, entry: entry()}, attachTo: document.body})
    await flushPromises()
    await tick(11)
    await tick(12)
    await tick(21)

    expect(save().disabled).toBe(true)
    const shirt = document.body.querySelector('[data-testid="event-group-question-5"] input, input[data-testid="event-group-question-5"]') as HTMLInputElement
    shirt.value = 'M'
    shirt.dispatchEvent(new Event('input'))
    await flushPromises()
    expect(save().disabled).toBe(false)

    save().click()
    await flushPromises()
    expect(signUp).toHaveBeenCalledWith(
        70, expect.arrayContaining([11, 12, 21]), [{fieldId: 5, value: 'M'}, {fieldId: 6, value: 'keine'}], 12)
  })

  it('leaves the questions out when only the parts change', async () => {
    const asking = detail()
    asking.questions = [
      {id: 5, eventId: null, eventGroupId: 70, name: 'T-Shirt', fieldType: 'TEXT', config: {required: true}, position: 0, overview: false},
    ] as EventGroupDetail['questions']
    wrapper = mount(EventGroupSignUpDialog, {
      props: {detail: asking, entry: entry({status: 'ACCEPTED', parts: [11, 12, 21]})},
      attachTo: document.body,
    })
    await flushPromises()

    expect(document.body.querySelector('[data-testid="event-group-questions"]')).toBeNull()
    expect(save().disabled).toBe(false)
  })

  it('offers no called-off part that was not held already', async () => {
    const withCalledOff = detail()
    withCalledOff.parts[2] = {...withCalledOff.parts[2]!, calledOff: true}
    wrapper = mount(EventGroupSignUpDialog, {props: {detail: withCalledOff, entry: entry()}, attachTo: document.body})
    await flushPromises()

    expect(box(13).disabled).toBe(true)
    expect(box(12).disabled).toBe(false)
  })

  it('keeps a part that took place as it is', async () => {
    const past = detail()
    past.parts[0] = {...past.parts[0]!, tookPlace: true}
    wrapper = mount(EventGroupSignUpDialog, {
      props: {detail: past, entry: entry({status: 'ACCEPTED', parts: [11, 12, 21]})},
      attachTo: document.body,
    })
    await flushPromises()

    expect(box(11).checked).toBe(true)
    expect(box(11).disabled).toBe(true)
  })

  it('lets whoever runs events save a choice that breaks the rules', async () => {
    wrapper = mount(EventGroupSignUpDialog, {props: {detail: detail(), entry: entry(), override: true}, attachTo: document.body})
    await flushPromises()

    await tick(11)
    expect(save().disabled).toBe(false)
    save().click()
    await flushPromises()

    expect(signUp).toHaveBeenCalledWith(70, [11], [], 12)
  })
})

