/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {describe, expect, it, vi} from 'vitest'
import {mountSuspended} from '@nuxt/test-utils/runtime'
import RegistrationsPanel from './RegistrationsPanel.vue'
import {RegistrationStatus, type RegistrationResponse, type StationEvent} from '@/api/generated/schema'
import type {QueueEntry} from '@/util/registrationQueue'

vi.mock('@/composables/useSession', () => ({useSession: () => ({hasPermission: () => true})}))

const event = {id: 4, name: 'Samstag', requiresRegistration: true, requiresConfirmation: true} as StationEvent

function signUp(id: number, status: RegistrationStatus, createdAt: string): RegistrationResponse {
  return {
    id,
    eventId: 4,
    memberId: id,
    memberName: `Mitglied ${id}`,
    memberIdentity: null,
    eventDate: '2026-12-04',
    status,
    createdAt,
    createdByName: null,
    fields: [],
    eventName: null,
    answersMissing: false,
    fromField: false,
    fieldName: null,
    agreementWithdrawnAt: null,
    placeGivenUp: false,
    queuePosition: null,
    statusChangedAt: createdAt,
  }
}

const queue: QueueEntry[] = [
  {kind: 'member', id: 7, position: 1, identity: null, name: 'Jonas Becker', partnerId: null},
  {kind: 'partner', id: 9, position: 2, identity: null, name: 'Mia Wagner', partnerId: 3},
]

function panel(eventGroupPart: boolean, extra: Record<string, unknown> = {}) {
  return mountSuspended(RegistrationsPanel, {
    props: {
      eventGroupPart,
      event,
      registrations: [],
      pendingRegistrations: [],
      nonPendingRegistrations: [],
      registrationStats: [],
      unregisteredMembers: [{value: '12', name: 'Mara'}],
      ...extra,
    },
  })
}

describe('RegistrationsPanel', () => {
  it('offers signing somebody up on an appointment of its own and not on a part of an event group', async () => {
    expect((await panel(false)).find('[data-testid="manual-register"]').exists()).toBe(true)
    expect((await panel(true)).find('[data-testid="manual-register"]').exists()).toBe(false)
  })

  /** The waiting list stands in the order it moves up in, partners' members among the station's own. */
  it('lists the waiting list in order and moves somebody up', async () => {
    const shown = await panel(false, {queue, places: {taken: 12, limit: 12, waiting: 2, full: true}})

    const rows = shown.findAll('[data-testid^="queue-entry-"]')
    expect(rows.map(row => row.text())).toEqual([
      expect.stringContaining('1.'),
      expect.stringContaining('2.'),
    ])
    expect(rows[1]!.text()).toContain('Partnerwache')
    expect(shown.get('[data-testid="registrations-places"]').text()).toBe('Voll, 2 auf der Nachrückliste')

    await shown.get('[data-testid="queue-move-up-partner-9"]').trigger('click')

    expect(shown.emitted('moveUp')?.[0]?.[0]).toEqual(queue[1])
  })

  /** A partner who decides its own people keeps them, so their row offers nothing. */
  it('offers nothing on a partner\'s member the partner decides about', async () => {
    const shown = await panel(false, {queue, partnerDecides: (partnerId: number) => partnerId === 3})

    expect(shown.find('[data-testid="queue-move-up-member-7"]').exists()).toBe(true)
    expect(shown.find('[data-testid="queue-move-up-partner-9"]').exists()).toBe(false)
  })

  /** Pending sign-ups go onto the list one at a time or all together, oldest first. */
  it('puts pending sign-ups on the waiting list, one or all of them', async () => {
    const pending = [
      signUp(2, RegistrationStatus.PENDING, '2026-11-03T10:00:00Z'),
      signUp(1, RegistrationStatus.PENDING, '2026-11-01T10:00:00Z'),
    ]
    const shown = await panel(false, {
      registrations: pending,
      pendingRegistrations: pending,
      places: {taken: 12, limit: 12, waiting: 0, full: true},
    })

    await shown.get('[data-testid="queue-all-pending"]').trigger('click')
    await shown.get('[data-testid="queue-registration-2"]').trigger('click')

    expect(shown.emitted('queue')).toEqual([[[1, 2]], [[2]]])
  })

  it('offers no waiting list on an appointment without a limit', async () => {
    const pending = [signUp(1, RegistrationStatus.PENDING, '2026-11-01T10:00:00Z')]
    const shown = await panel(false, {registrations: pending, pendingRegistrations: pending, places: null})

    expect(shown.find('[data-testid="queue-registration-1"]').exists()).toBe(false)
    expect(shown.find('[data-testid="registrations-places"]').exists()).toBe(false)
  })

  /** The counts above the list name each status by the word the status badge uses, not by the button that leads to it. */
  it('counts refused and signed off answers by their status words', async () => {
    const registrations = [
      signUp(1, RegistrationStatus.DENIED, '2026-11-01T10:00:00Z'),
      signUp(2, RegistrationStatus.DECLINED, '2026-11-01T11:00:00Z'),
    ]
    const shown = await panel(false, {registrations, nonPendingRegistrations: registrations})

    expect(shown.text()).toContain('1 abgelehnt')
    expect(shown.text()).toContain('1 abgemeldet')
    expect(shown.text()).not.toContain('1 Ablehnen')
  })
})
