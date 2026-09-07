/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {describe, expect, it} from 'vitest'
import type {StationMember} from '@/api/types'
import {SetupLink, setupLinkState} from './setupLinkState'

const NOW = new Date('2026-09-01T12:00:00Z')

function member(overrides: Partial<StationMember>): StationMember {
  return {
    id: 1,
    stationId: 'station-1',
    accountId: 1,
    name: 'Test Person',
    email: 'test@example.test',
    accountSetupPending: true,
    setupMailExpiresAt: null,
    mailReaches: 'SELF',
    ...overrides,
  }
}

describe('setupLinkState', () => {
  it('reads an account that is set up as done, whatever the link says', () => {
    const done = member({accountSetupPending: false, setupMailExpiresAt: '2020-01-01T00:00:00Z'})
    expect(setupLinkState(done, NOW)).toBe(SetupLink.DONE)
  })

  it('reads a pending account with no link as never sent', () => {
    expect(setupLinkState(member({setupMailExpiresAt: null}), NOW)).toBe(SetupLink.NEVER_SENT)
  })

  it('reads a link running out in the future as still good', () => {
    const valid = member({setupMailExpiresAt: '2026-09-30T12:00:00Z'})
    expect(setupLinkState(valid, NOW)).toBe(SetupLink.VALID)
  })

  it('reads a link whose date has passed as expired', () => {
    const expired = member({setupMailExpiresAt: '2026-08-01T12:00:00Z'})
    expect(setupLinkState(expired, NOW)).toBe(SetupLink.EXPIRED)
  })

  it('counts a link running out at this very moment as expired', () => {
    const expired = member({setupMailExpiresAt: NOW.toISOString()})
    expect(setupLinkState(expired, NOW)).toBe(SetupLink.EXPIRED)
  })

  it('falls back to never sent when the date cannot be read', () => {
    expect(setupLinkState(member({setupMailExpiresAt: 'not a date'}), NOW)).toBe(SetupLink.NEVER_SENT)
  })

  it('treats a row that says nothing about setup as done', () => {
    expect(setupLinkState(member({accountSetupPending: undefined}), NOW)).toBe(SetupLink.DONE)
  })
})
