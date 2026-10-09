/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {beforeEach, describe, expect, it, vi} from 'vitest'
import {defineComponent, h} from 'vue'
import {mount} from '@vue/test-utils'
import ConsentStep from './ConsentStep.vue'
import {useLoginConsent} from '@/composables/useLoginConsent'
import deDE from '@/i18n/de-DE'

vi.mock('@/composables/useConsentGuard', () => ({
  useConsentGuard: () => ({setNeedsReconsent: vi.fn()}),
}))

vi.mock('@/api', () => ({session: {}}))

/** A way in as the sign-in page builds it: the consent step, and the form once the consent is given. */
const SignInPage = defineComponent({
  setup() {
    const legal = useLoginConsent()
    return () => h('div', [
      h(ConsentStep, {legal}),
      legal.consent.value === 'accepted' ? h('form', {'data-testid': 'sign-in-form'}) : null,
    ])
  },
})

function buttonLabelled(wrapper: ReturnType<typeof mount>, label: string) {
  const button = wrapper.findAll('button').find(candidate => candidate.text() === label)
  if (!button) throw new Error(`no button labelled ${label}`)
  return button
}

describe('ConsentStep', () => {
  beforeEach(() => {
    localStorage.clear()
    localStorage.setItem('storage_consent', 'denied')
  })

  it('offers a way back to the choice after a refusal', () => {
    const page = mount(SignInPage)

    expect(page.text()).toContain(deDE.login.storageDenied)
    expect(page.text()).toContain(deDE.login.storageReconsider)
    expect(page.text()).not.toContain(deDE.storageConsent.title)
    expect(page.find('[data-testid="sign-in-form"]').exists()).toBe(false)
  })

  it('brings the gate back and keeps the refusal until it is answered', async () => {
    const page = mount(SignInPage)

    await buttonLabelled(page, deDE.login.storageReconsider).trigger('click')

    expect(page.text()).toContain(deDE.storageConsent.title)
    expect(page.text()).not.toContain(deDE.login.storageDenied)
    expect(localStorage.getItem('storage_consent')).toBe('denied')
  })

  it('shows the sign-in form once the consent is given after all', async () => {
    const page = mount(SignInPage)

    await buttonLabelled(page, deDE.login.storageReconsider).trigger('click')
    await buttonLabelled(page, deDE.storageConsent.necessaryOnly).trigger('click')

    expect(page.find('[data-testid="sign-in-form"]').exists()).toBe(true)
    expect(page.text()).not.toContain(deDE.storageConsent.title)
    expect(localStorage.getItem('storage_consent')).toBe('accepted')
  })
})
