/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {beforeEach, describe, expect, it, vi} from 'vitest'
import {useLoginConsent} from './useLoginConsent'

vi.mock('@/composables/useConsentGuard', () => ({
  useConsentGuard: () => ({setNeedsReconsent: vi.fn()}),
}))

vi.mock('@/api', () => ({
  session: {
    getConsentText: vi.fn(async () => ({html: '<p>consent</p>', version: 'c-new'})),
    getLegalVersions: vi.fn(async () => ({
      consentVersion: 'c-new',
      legacyConsentVersion: 'c-legacy',
      privacyVersion: 'p',
      tosVersion: 't',
    })),
  },
}))

function acceptedUnder(consentVersion: string) {
  localStorage.setItem('storage_consent', 'accepted')
  localStorage.setItem('consent_version', consentVersion)
  localStorage.setItem('privacy_version', 'p')
  localStorage.setItem('tos_version', 't')
}

describe('useLoginConsent', () => {
  beforeEach(() => localStorage.clear())

  it('keeps a consent given under the current version', async () => {
    acceptedUnder('c-new')
    const legal = useLoginConsent()

    await legal.loadConsentText()

    expect(legal.consent.value).toBe('accepted')
  })

  it('keeps a consent given under the legacy hash of the current consent text', async () => {
    acceptedUnder('c-legacy')
    const legal = useLoginConsent()

    await legal.loadConsentText()

    expect(legal.consent.value).toBe('accepted')
  })

  it('asks again for a consent given to an earlier consent text', async () => {
    acceptedUnder('c-older')
    const legal = useLoginConsent()

    await legal.loadConsentText()

    expect(legal.consent.value).toBeNull()
  })
})
