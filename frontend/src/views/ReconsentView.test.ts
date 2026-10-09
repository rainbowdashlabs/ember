/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it, vi} from 'vitest'
import {flushPromises, mount} from '@vue/test-utils'
import type {ConsentChangesResponse} from '@/api/generated/schema'
import ReconsentView from './ReconsentView.vue'
import deDE from '@/i18n/de-DE'

const changes: ConsentChangesResponse = {
  privacyChanged: false,
  tosChanged: false,
  consentChanged: true,
  privacyDiff: null,
  tosDiff: null,
  privacyHtml: null,
  tosHtml: null,
  consentHtml: '<p>consent</p>',
  currentPrivacyVersion: 'p',
  currentTosVersion: 't',
  currentConsentVersion: 'c',
}

vi.mock('vue-router', () => ({useRouter: () => ({replace: vi.fn()})}))

vi.mock('@/composables/useConsentGuard', () => ({
  useConsentGuard: () => ({setNeedsReconsent: vi.fn()}),
}))

vi.mock('@/api', () => ({
  session: {
    getConsentStatus: vi.fn(async () => ({current: false})),
    getConsentChanges: vi.fn(async () => changes),
  },
}))

describe('ReconsentView', () => {
  it('names a changed consent text instead of an empty list', async () => {
    const view = mount(ReconsentView)
    await flushPromises()

    const text = view.text()
    expect(text).toContain(deDE.reconsent.consentChanged)
    expect(text).toContain(deDE.reconsent.consentChangedHint)
    expect(text).not.toContain(deDE.reconsent.privacyChanged)
    expect(text).not.toContain(deDE.reconsent.tosChanged)
  })
})
