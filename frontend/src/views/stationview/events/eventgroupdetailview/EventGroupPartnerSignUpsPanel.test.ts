/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {afterEach, describe, expect, it, vi} from 'vitest'
import {flushPromises, mount, type VueWrapper} from '@vue/test-utils'
import EventGroupPartnerSignUpsPanel from './EventGroupPartnerSignUpsPanel.vue'
import type {EventGroupDetail, EventGroupPartnerSignUp} from '@/api/generated/schema'
import {createIdentity} from '@/test/mocks/factories'

const listPartnerSignUps = vi.fn(async (..._args: unknown[]): Promise<EventGroupPartnerSignUp[]> => [])
const decidePartner = vi.fn(async (..._args: unknown[]) => undefined)
const queuePartnerSignUps = vi.fn(async (..._args: unknown[]) => undefined)

vi.mock('@/api', () => ({eventGroups: {
  listPartnerSignUps: (...args: unknown[]) => listPartnerSignUps(...args),
  decidePartner: (...args: unknown[]) => decidePartner(...args),
  queuePartnerSignUps: (...args: unknown[]) => queuePartnerSignUps(...args),
}}))

function detail(): EventGroupDetail {
  return {
    eventGroup: {
      id: 70,
      details: {name: 'Jugendflamme 1', description: null, registrationLimit: null, registrationDeadline: {kind: 'NONE', at: null, offsetMinutes: null, anchor: null}, requiresConfirmation: true, color: null, minRegistrations: null, thresholdDays: null, partRegistrationLimit: null, queueOrder: null},
      cancelled: false, cancelReason: null, partCount: 0, holding: 1, queued: 0, pending: 0, firstStart: null, lastEnd: null, closesAt: null,
    },
    sets: [],
    parts: [],
    registration: {userTypes: [], groupIds: [], tagIds: [], memberIds: [], mode: 'AND'},
    view: {userTypes: [], groupIds: [], tagIds: [], memberIds: [], mode: 'AND'},
    questions: [],
    fields: [],
    household: [],
    places: null,
  }
}

let wrapper: VueWrapper | null = null

afterEach(() => {
  wrapper?.unmount()
  wrapper = null
  listPartnerSignUps.mockReset()
  decidePartner.mockReset()
  queuePartnerSignUps.mockClear()
  document.body.innerHTML = ''
})

describe('EventGroupPartnerSignUpsPanel', () => {
  it('shows nothing while no partner member signed up', async () => {
    listPartnerSignUps.mockResolvedValue([])
    wrapper = mount(EventGroupPartnerSignUpsPanel, {props: {detail: detail()}, global: {stubs: {MemberName: true}}})
    await flushPromises()

    expect(wrapper.find('[data-testid="event-group-partner-sign-ups"]').exists()).toBe(false)
  })

  it('accepts a partner member and reloads the page', async () => {
    listPartnerSignUps.mockResolvedValue([{
      id: 9, partnerId: 4, partnerDecides: false, member: createIdentity({name: 'Mara'}), status: 'PENDING', registeredAt: '2026-11-13T10:00:00Z',
      parts: [11, 12], sets: [], queuePosition: null,
    }])
    const onChanged = vi.fn()
    wrapper = mount(EventGroupPartnerSignUpsPanel, {
      props: {detail: detail(), onChanged},
      global: {stubs: {MemberName: true}},
    })
    await flushPromises()

    expect(wrapper.findAll('[data-testid="event-group-partner-sign-up-row"]')).toHaveLength(1)
    await wrapper.find('[data-testid="event-group-partner-accept"]').trigger('click')
    await flushPromises()

    expect(decidePartner).toHaveBeenCalledWith(70, 9, 'ACCEPTED', false)
    expect(onChanged).toHaveBeenCalled()
  })

  /** Where places are limited, a pending partner sign-up goes onto the waiting list, and a waiting one moves up. */
  it('puts a partner member on the waiting list and moves a waiting one up', async () => {
    listPartnerSignUps.mockResolvedValue([
      {id: 9, partnerId: 4, partnerDecides: false, member: createIdentity({name: 'Mara'}), status: 'PENDING', registeredAt: null, parts: [11], sets: [], queuePosition: null},
      {id: 10, partnerId: 4, partnerDecides: false, member: createIdentity({name: 'Jonas'}), status: 'QUEUED', registeredAt: null, parts: [11], sets: [], queuePosition: 1},
    ])
    const onChanged = vi.fn()
    const limited = detail()
    limited.eventGroup.details = {...limited.eventGroup.details, registrationLimit: 10}
    wrapper = mount(EventGroupPartnerSignUpsPanel, {props: {detail: limited, onChanged}, global: {stubs: {MemberName: true}}})
    await flushPromises()

    await wrapper.get('[data-testid="event-group-partner-queue-9"]').trigger('click')
    await flushPromises()
    expect(queuePartnerSignUps).toHaveBeenCalledWith(70, [9])

    const moveUp = wrapper.findAll('[data-testid="event-group-partner-accept"]')[1]!
    expect(moveUp.text()).toContain('Nachrücken')
    await moveUp.trigger('click')
    await flushPromises()
    expect(decidePartner).toHaveBeenCalledWith(70, 10, 'ACCEPTED', false)
    expect(onChanged).toHaveBeenCalledTimes(2)
  })

  /** Where every place for people is taken, accepting asks first and is sent again beyond the limit. */
  it('asks before accepting beyond the limit and sends it again with the answer', async () => {
    listPartnerSignUps.mockResolvedValue([
      {id: 9, partnerId: 4, partnerDecides: false, member: createIdentity({name: 'Mara'}), status: 'PENDING', registeredAt: null, parts: [11], sets: [], queuePosition: null},
    ])
    decidePartner.mockRejectedValueOnce({response: {status: 409, data: {code: 'E-226', error: 'full'}}})
    const full = detail()
    full.eventGroup = {...full.eventGroup, holding: 10, details: {...full.eventGroup.details, registrationLimit: 10}}
    wrapper = mount(EventGroupPartnerSignUpsPanel, {props: {detail: full}, attachTo: document.body, global: {stubs: {MemberName: true}}})
    await flushPromises()

    await wrapper.get('[data-testid="event-group-partner-accept"]').trigger('click')
    await flushPromises()
    expect(document.body.textContent).toContain('10 von 10 Plätzen belegt')
    document.body.querySelector<HTMLElement>('[data-testid="full-date-over-limit"]')!.click()
    await flushPromises()

    expect(decidePartner).toHaveBeenLastCalledWith(70, 9, 'ACCEPTED', true)
  })

  /** A partner that decides its own sign-ups gets no accepting, denying or queueing here; another partner's rows keep theirs. */
  it('leaves the decision to a partner that decides its own sign-ups', async () => {
    listPartnerSignUps.mockResolvedValue([
      {id: 9, partnerId: 4, partnerDecides: true, member: createIdentity({name: 'Mara'}), status: 'PENDING', registeredAt: null, parts: [11], sets: [], queuePosition: null},
      {id: 10, partnerId: 4, partnerDecides: true, member: createIdentity({name: 'Jonas'}), status: 'QUEUED', registeredAt: null, parts: [11], sets: [], queuePosition: 1},
      {id: 11, partnerId: 5, partnerDecides: false, member: createIdentity({name: 'Lea'}), status: 'PENDING', registeredAt: null, parts: [11], sets: [], queuePosition: null},
    ])
    const limited = detail()
    limited.eventGroup.details = {...limited.eventGroup.details, registrationLimit: 10}
    wrapper = mount(EventGroupPartnerSignUpsPanel, {props: {detail: limited}, global: {stubs: {MemberName: true}}})
    await flushPromises()

    const [deciding, waiting, other] = wrapper.findAll('[data-testid="event-group-partner-sign-up-row"]')
    for (const row of [deciding!, waiting!]) {
      expect(row.text()).toContain('Die Partnerwache entscheidet selbst')
      expect(row.find('[data-testid="event-group-partner-accept"]').exists()).toBe(false)
      expect(row.find('[data-testid="event-group-partner-deny"]').exists()).toBe(false)
    }
    expect(wrapper.find('[data-testid="event-group-partner-queue-9"]').exists()).toBe(false)
    expect(other!.find('[data-testid="event-group-partner-accept"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="event-group-partner-queue-11"]').exists()).toBe(true)
  })
})
