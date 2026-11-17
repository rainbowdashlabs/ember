/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {beforeEach, describe, expect, it, vi} from 'vitest'
import {session} from '@/api'
import {useLoginConsent} from './useLoginConsent'

const setNeedsReconsent = vi.hoisted(() => vi.fn())

vi.mock('@/composables/useConsentGuard', () => ({
  useConsentGuard: () => ({setNeedsReconsent}),
}))

vi.mock('@/api', () => ({
  session: {
    getConsentText: vi.fn(async () => ({html: '<p>consent</p>', version: 'c-new'})),
    getLegalVersions: vi.fn(async () => ({
      consentVersion: 'c-new',
      privacyVersion: 'p-new',
      tosVersion: 't',
      legacyConsentVersion: 'c-legacy',
      legacyPrivacyVersion: 'p-legacy',
      legacyTosVersion: 't',
    })),
    getConsentStatus: vi.fn(async () => ({
      consented: true,
      current: false,
      currentConsentVersion: 'c-new',
      currentPrivacyVersion: 'p-new',
      currentTosVersion: 't',
    })),
    recordConsent: vi.fn(async () => undefined),
  },
}))

function acceptedUnder(consent: string, privacy: string) {
  localStorage.setItem('storage_consent', 'accepted')
  localStorage.setItem('consent_version', consent)
  localStorage.setItem('privacy_version', privacy)
  localStorage.setItem('tos_version', 't')
}

async function gateAfterLoading() {
  const legal = useLoginConsent()
  await legal.loadConsentText()
  return legal.consent.value
}

describe('useLoginConsent', () => {
  beforeEach(() => {
    localStorage.clear()
    vi.clearAllMocks()
  })

  it('keeps a consent given under the current versions', async () => {
    acceptedUnder('c-new', 'p-new')
    expect(await gateAfterLoading()).toBe('accepted')
  })

  it('keeps a consent given under the legacy hashes of the current texts', async () => {
    acceptedUnder('c-legacy', 'p-legacy')
    expect(await gateAfterLoading()).toBe('accepted')
  })

  it('asks again for a consent given to an earlier consent text', async () => {
    acceptedUnder('c-older', 'p-new')
    expect(await gateAfterLoading()).toBeNull()
  })

  it('asks again for a consent given to an earlier privacy policy', async () => {
    acceptedUnder('c-new', 'p-older')
    expect(await gateAfterLoading()).toBeNull()
  })

  it('records an agreement given just before signing in for an account that agreed to older texts', async () => {
    const legal = useLoginConsent()
    await legal.loadConsentText()
    legal.acceptCurrentVersions()

    await legal.recordAfterLogin()

    expect(session.recordConsent).toHaveBeenCalledWith({consentVersion: 'c-new', privacyVersion: 'p-new', tosVersion: 't'})
    expect(setNeedsReconsent).not.toHaveBeenCalled()
  })

  it('asks an account that agreed to older texts again when nothing was agreed to before signing in', async () => {
    acceptedUnder('c-new', 'p-new')
    const legal = useLoginConsent()
    await legal.loadConsentText()

    await legal.recordAfterLogin()

    expect(session.recordConsent).not.toHaveBeenCalled()
    expect(setNeedsReconsent).toHaveBeenCalledWith(true)
  })
})
