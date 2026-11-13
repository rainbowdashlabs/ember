/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it, vi} from 'vitest'
import {mount} from '@vue/test-utils'
import RegistrationsPanel from './RegistrationsPanel.vue'
import type {StationEvent} from '@/api/generated/schema'

vi.mock('@/composables/useSession', () => ({useSession: () => ({hasPermission: () => true})}))

const event = {id: 4, name: 'Samstag', requiresRegistration: true} as StationEvent

function panel(coursePart: boolean) {
  return mount(RegistrationsPanel, {
    props: {
      coursePart,
      event,
      registrations: [],
      pendingRegistrations: [],
      nonPendingRegistrations: [],
      registrationStats: [],
      unregisteredMembers: [{value: '12', name: 'Mara'}],
    },
  })
}

describe('RegistrationsPanel', () => {
  it('offers signing somebody up on an appointment of its own and not on a part of a course', () => {
    expect(panel(false).find('[data-testid="manual-register"]').exists()).toBe(true)
    expect(panel(true).find('[data-testid="manual-register"]').exists()).toBe(false)
  })
})
