/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
// @vitest-environment happy-dom
import {describe, expect, it} from 'vitest'
import {ref} from 'vue'
import {RegistrationStatus, type EventRegistrationEntry, type FederatedEventRegistration, type StationEvent} from '@/api/events'
import {useSignupMemberSet} from './useSignupMemberSet'

const TUESDAY = '2026-05-05'
const NEXT_TUESDAY = '2026-05-12'

function event(requiresRegistration: boolean): StationEvent {
  return {id: 7, stationId: 'station', name: 'Dienst', requiresRegistration}
}

function signup(memberId: number, status: string, eventDate: string): EventRegistrationEntry {
  return {
    id: memberId * 100,
    eventId: 7,
    memberId,
    memberName: `Mitglied ${memberId}`,
    eventDate,
    status,
    createdAt: '2026-05-01T10:00:00Z',
  }
}

function guest(status: string, eventDate: string): FederatedEventRegistration {
  return {
    registration: {
      id: 1,
      eventId: 7,
      partnerId: 2,
      remoteMemberId: 'remote-1',
      eventDate,
      status,
      createdAt: '2026-05-01T10:00:00Z',
    },
    memberIdentity: null,
  }
}

function resolve(options: {
  requiresRegistration?: boolean
  date?: string | null
  registrations?: EventRegistrationEntry[]
  federated?: FederatedEventRegistration[]
  members?: number[]
}) {
  const set = useSignupMemberSet({
    event: () => event(options.requiresRegistration ?? true),
    effectiveDate: () => (options.date === undefined ? TUESDAY : options.date),
    registrations: () => options.registrations ?? [],
    federatedRegistrations: () => options.federated ?? [],
    currentMemberIds: () => options.members ?? [1, 2, 3],
  })
  return set.value
}

describe('useSignupMemberSet', () => {
  it('holds only the people who took a place on the evening in view', () => {
    const set = resolve({
      registrations: [
        signup(1, RegistrationStatus.ACCEPTED, TUESDAY),
        signup(2, RegistrationStatus.PENDING, TUESDAY),
        signup(3, RegistrationStatus.ACCEPTED, NEXT_TUESDAY),
      ],
    })

    expect(set.memberIds, 'the pending one and the other Tuesday stay out').toEqual([1])
    expect(set.count).toBe(1)
    expect(set.usable).toBe(true)
  })

  it('counts partner-station guests instead of listing them', () => {
    const set = resolve({
      registrations: [signup(1, RegistrationStatus.ACCEPTED, TUESDAY)],
      federated: [guest(RegistrationStatus.ACCEPTED, TUESDAY), guest(RegistrationStatus.DECLINED, TUESDAY)],
    })

    expect(set.count).toBe(1)
    expect(set.guestCount, 'only the guest who took a place').toBe(1)
  })

  it('counts people who have left instead of listing them', () => {
    const set = resolve({
      registrations: [
        signup(1, RegistrationStatus.ACCEPTED, TUESDAY),
        signup(9, RegistrationStatus.ACCEPTED, TUESDAY),
      ],
      members: [1, 2, 3],
    })

    expect(set.memberIds).toEqual([1])
    expect(set.formerCount, 'the one who is no longer a member').toBe(1)
  })

  it('holds nobody when the appointment is not signed up for', () => {
    const set = resolve({
      requiresRegistration: false,
      registrations: [signup(1, RegistrationStatus.ACCEPTED, TUESDAY)],
    })

    expect(set.usable).toBe(false)
    expect(set.count).toBe(0)
  })

  it('holds nobody while no occurrence is in view', () => {
    const set = resolve({
      date: null,
      registrations: [signup(1, RegistrationStatus.ACCEPTED, TUESDAY)],
    })

    expect(set.usable).toBe(false)
  })

  it('follows the date it is given', () => {
    const date = ref(TUESDAY)
    const set = useSignupMemberSet({
      event: () => event(true),
      effectiveDate: () => date.value,
      registrations: () => [
        signup(1, RegistrationStatus.ACCEPTED, TUESDAY),
        signup(2, RegistrationStatus.ACCEPTED, NEXT_TUESDAY),
      ],
      federatedRegistrations: () => [],
      currentMemberIds: () => [1, 2],
    })

    expect(set.value.memberIds).toEqual([1])
    date.value = NEXT_TUESDAY
    expect(set.value.memberIds, 'the next evening is a different set').toEqual([2])
  })
})
