/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {describe, expect, it} from 'vitest'
import {mountSuspended} from '@nuxt/test-utils/runtime'
import TemplateSignUpSection from './TemplateSignUpSection.vue'

const HELD = 'Fragen werden bei der Anmeldung beantwortet. Solange es Fragen gibt, bleibt die Anmeldung eingeschaltet.'

async function mountSection(signUpHeld: boolean) {
  return mountSuspended(TemplateSignUpSection, {
    props: {
      eventType: 'ONE_TIME', signUpHeld, requiresRegistration: true, requiresConfirmation: false,
      queueOrder: 'TIME', deadlineOffsetHours: null,
    },
  })
}

/** A template that asks questions keeps its sign-up on, and says why. */
describe('TemplateSignUpSection', () => {
  it('holds sign-up on while the template asks questions and says why', async () => {
    const held = await mountSection(true)

    expect(held.get('[aria-labelledby$="-registration"]').attributes('disabled')).toBeDefined()
    expect(held.find(`[aria-label="${HELD}"]`).exists()).toBe(true)
  })

  it('leaves sign-up free where the template asks nothing', async () => {
    const free = await mountSection(false)

    expect(free.get('[aria-labelledby$="-registration"]').attributes('disabled')).toBeUndefined()
    expect(free.find(`[aria-label="${HELD}"]`).exists()).toBe(false)
  })
})
