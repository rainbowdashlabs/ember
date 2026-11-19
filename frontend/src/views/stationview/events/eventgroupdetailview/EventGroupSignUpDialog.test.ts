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
const signingStep = vi.hoisted(() => vi.fn(async (..._args: unknown[]) => null))
vi.mock('./eventGroupSigning', () => ({eventGroupSigningStep: signingStep}))
vi.mock('@/composables/useSession', () => ({useSession: () => ({stationTimezone: computed(() => 'Europe/Berlin')})}))

/** A Jugendflamme event group: three practice days of which two count, and a mandatory test day. */
function detail(): EventGroupDetail {
  return {
    eventGroup: {
      id: 70,
      details: {name: 'Jugendflamme 1', description: null, registrationLimit: null, registrationDeadline: {kind: 'NONE', at: null, offsetMinutes: null, anchor: null}, requiresConfirmation: false, color: null, minRegistrations: null, thresholdDays: null, partRegistrationLimit: null, queueOrder: null},
      cancelled: false, cancelReason: null, partCount: 4, holding: 0, queued: 0, pending: 0, firstStart: null, lastEnd: null, closesAt: null,
    },
    sets: [
      {id: 1, eventGroupId: 70, name: 'Übung', minimum: 2, maximum: null, position: 0},
      {id: 2, eventGroupId: 70, name: 'Prüfung', minimum: 1, maximum: null, position: 1},
    ],
    parts: [11, 12, 13].map(eventId => ({
      eventId, setId: 1, name: `Übung ${eventId}`, startTime: '2027-03-06T08:00:00Z', endTime: '2027-03-06T16:00:00Z',
      calledOff: false, tookPlace: false, required: false, accepted: 0, places: null,
    })).concat([{
      eventId: 21, setId: 2, name: 'Prüfung', startTime: '2027-03-14T08:00:00Z', endTime: '2027-03-14T12:00:00Z',
      calledOff: false, tookPlace: false, required: false, accepted: 0, places: null,
    }]),
    registration: {userTypes: [], groupIds: [], tagIds: [], memberIds: [], mode: 'AND'},
    view: {userTypes: [], groupIds: [], tagIds: [], memberIds: [], mode: 'AND'},
    questions: [],
    fields: [],
    household: [],
  }
}

function entry(overrides: Partial<EventGroupSignUp> = {}): EventGroupSignUp {
  return {memberId: 12, member: createIdentity({name: 'Mara'}), status: null, registeredAt: null, parts: [], sets: [], answers: [], answersMissing: false, mayRegister: true, queuePosition: null, ...overrides}
}

let wrapper: VueWrapper | null = null

afterEach(() => {
  wrapper?.unmount()
  wrapper = null
  document.body.innerHTML = ''
  signUp.mockClear()
  changeParts.mockClear()
  signingStep.mockClear()
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

    expect(signUp).toHaveBeenCalledWith(70, expect.arrayContaining([11, 13, 21]), [], 12, undefined)
    expect(wrapper.emitted('saved')).toHaveLength(1)
  })

  it('offers the signing step for an own event group and leaves a partner\'s documents to the partner', async () => {
    const own = {signsHere: true, signUp: vi.fn(async () => ({})), changeParts: vi.fn(), withdraw: vi.fn()}
    const partner = {...own, signsHere: false}
    for (const port of [own, partner]) {
      wrapper?.unmount()
      wrapper = mount(EventGroupSignUpDialog, {props: {detail: detail(), entry: entry(), override: true, port}, attachTo: document.body})
      await flushPromises()
      await tick(11)
      save().click()
      await flushPromises()
    }

    expect(own.signUp).toHaveBeenCalledTimes(2)
    expect(signingStep).toHaveBeenCalledTimes(1)
  })

  it('ticks the required parts of a new sign-up and keeps a member from unticking them', async () => {
    const required = detail()
    required.parts = required.parts.map(part => (part.eventId === 11 ? {...part, required: true} : part))
    wrapper = mount(EventGroupSignUpDialog, {props: {detail: required, entry: entry()}, attachTo: document.body})
    await flushPromises()

    expect(box(11).checked).toBe(true)
    expect(box(11).disabled).toBe(true)
    expect(document.body.textContent).toContain('Übung 11')
  })

  it('lets whoever runs events untick a required part', async () => {
    const required = detail()
    required.parts = required.parts.map(part => (part.eventId === 11 ? {...part, required: true} : part))
    wrapper = mount(EventGroupSignUpDialog, {props: {detail: required, entry: entry(), override: true}, attachTo: document.body})
    await flushPromises()

    expect(box(11).disabled).toBe(false)
    await tick(11)
    expect(box(11).checked).toBe(false)
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
        70, expect.arrayContaining([11, 12, 21]), [{fieldId: 5, value: 'M'}, {fieldId: 6, value: 'keine'}], 12, undefined)
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

    expect(signUp).toHaveBeenCalledWith(70, [11], [], 12, undefined)
  })

  /** A block of exactly one reads as a choice between alternatives: ticking one takes the other off. */
  it('takes the other part of a block of exactly one off when one is ticked', async () => {
    const alternatives = detail()
    alternatives.sets = [{...alternatives.sets[0]!, minimum: 1, maximum: 1}, alternatives.sets[1]!]
    wrapper = mount(EventGroupSignUpDialog, {props: {detail: alternatives, entry: entry()}, attachTo: document.body})
    await flushPromises()

    await tick(11)
    await tick(12)

    expect(box(11).checked).toBe(false)
    expect(box(12).checked).toBe(true)
    expect(document.body.textContent).toContain('genau einer von 3')
  })

  /** A block at its maximum takes no further part. */
  it('turns the remaining parts of a block at its maximum off', async () => {
    const limited = detail()
    limited.sets = [{...limited.sets[0]!, minimum: 1, maximum: 2}, limited.sets[1]!]
    wrapper = mount(EventGroupSignUpDialog, {props: {detail: limited, entry: entry()}, attachTo: document.body})
    await flushPromises()

    await tick(11)
    await tick(12)

    expect(box(13).disabled).toBe(true)
    expect(box(11).disabled).toBe(false)
  })

  /** A full part says so, and choosing it says the whole sign-up waits on the waiting list. */
  it('marks a full part and says choosing it puts the sign-up on the waiting list', async () => {
    const full = detail()
    full.parts = full.parts.map(part => (part.eventId === 13 ? {...part, accepted: 8, places: 8} : part))
    wrapper = mount(EventGroupSignUpDialog, {props: {detail: full, entry: entry()}, attachTo: document.body})
    await flushPromises()

    expect(document.body.querySelector('[data-testid="event-group-part-full-13"]')).not.toBeNull()
    expect(document.body.querySelector('[data-testid="event-group-sign-up-waits"]')).toBeNull()
    await tick(13)

    expect(document.body.querySelector('[data-testid="event-group-sign-up-waits"]')!.textContent).toContain('Nachrückliste')
  })

  /** An accepted sign-up never gives up a place for a full part, so a full part stays off for it. */
  it('keeps a full part off for a sign-up already accepted', async () => {
    const full = detail()
    full.parts = full.parts.map(part => (part.eventId === 13 ? {...part, accepted: 8, places: 8} : part))
    wrapper = mount(EventGroupSignUpDialog, {
      props: {detail: full, entry: entry({status: 'ACCEPTED', parts: [11, 12, 21]})},
      attachTo: document.body,
    })
    await flushPromises()

    expect(box(13).disabled).toBe(true)
  })

  /** Whoever runs events signing somebody up on a full choice is asked, and the sign-up goes again with the answer. */
  it('asks a manager where a full sign-up goes and sends it again with the answer', async () => {
    signUp.mockRejectedValueOnce({response: {status: 409, data: {code: 'E-226', error: 'full'}}})
    wrapper = mount(EventGroupSignUpDialog, {props: {detail: detail(), entry: entry(), override: true}, attachTo: document.body})
    await flushPromises()

    await tick(11)
    save().click()
    await flushPromises()
    document.body.querySelector<HTMLElement>('[data-testid="full-date-queue"]')!.click()
    await flushPromises()

    expect(signUp).toHaveBeenLastCalledWith(70, [11], [], 12, 'QUEUE')
    expect(wrapper.emitted('saved')).toHaveLength(1)
  })

  /** Whoever runs events passes a maximum as they pass a minimum, and sees the excess as a shortfall. */
  it('lets whoever runs events choose beyond a block of exactly one and shows the excess', async () => {
    const alternatives = detail()
    alternatives.sets = [{...alternatives.sets[0]!, minimum: 1, maximum: 1}, alternatives.sets[1]!]
    wrapper = mount(EventGroupSignUpDialog, {props: {detail: alternatives, entry: entry(), override: true}, attachTo: document.body})
    await flushPromises()

    await tick(11)
    await tick(12)

    expect(box(11).checked).toBe(true)
    expect(box(12).checked).toBe(true)
    expect(box(13).disabled).toBe(false)
    expect(document.body.querySelector('[data-testid="event-group-shortfalls"]')!.textContent).toContain('Übung: höchstens 1')
    expect(save().disabled).toBe(false)
  })

  /** Changing the parts of a sign-up keeps where it stands, so it is never told it will wait. */
  it('never says a change of parts will wait', async () => {
    const full = detail()
    full.parts = full.parts.map(part => (part.eventId === 13 ? {...part, accepted: 8, places: 8} : part))
    wrapper = mount(EventGroupSignUpDialog, {
      props: {detail: full, entry: entry({status: 'QUEUED', parts: [11, 12, 21], queuePosition: 1})},
      attachTo: document.body,
    })
    await flushPromises()

    await tick(13)

    expect(document.body.querySelector('[data-testid="event-group-sign-up-waits"]')).toBeNull()
  })

  /** Whoever runs events is asked when the server finds the choice full, so they are not told beforehand. */
  it('does not tell a manager the sign-up will wait', async () => {
    const full = detail()
    full.parts = full.parts.map(part => (part.eventId === 13 ? {...part, accepted: 8, places: 8} : part))
    wrapper = mount(EventGroupSignUpDialog, {props: {detail: full, entry: entry(), override: true}, attachTo: document.body})
    await flushPromises()

    await tick(13)

    expect(document.body.querySelector('[data-testid="event-group-sign-up-waits"]')).toBeNull()
  })

  /** Whoever runs events may add a full part to an accepted sign-up, as the server lets them. */
  it('lets a manager add a full part to an accepted sign-up', async () => {
    const full = detail()
    full.parts = full.parts.map(part => (part.eventId === 13 ? {...part, accepted: 8, places: 8} : part))
    wrapper = mount(EventGroupSignUpDialog, {
      props: {detail: full, entry: entry({status: 'ACCEPTED', parts: [11, 12, 21]}), override: true},
      attachTo: document.body,
    })
    await flushPromises()

    expect(box(13).disabled).toBe(false)
  })

  /** A block over its maximum but not short says only that it is over, not that parts are missing. */
  it('names only the excess of a block that holds enough parts', async () => {
    const limited = detail()
    limited.sets = [{...limited.sets[0]!, minimum: 1, maximum: 1}, limited.sets[1]!]
    wrapper = mount(EventGroupSignUpDialog, {props: {detail: limited, entry: entry(), override: true}, attachTo: document.body})
    await flushPromises()

    await tick(11)
    await tick(12)
    await tick(21)

    const shortfalls = document.body.querySelector('[data-testid="event-group-shortfalls"]')!.textContent ?? ''
    expect(shortfalls).toContain('Übung: höchstens 1')
    expect(shortfalls).not.toContain('noch')
  })
})
