/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it, vi} from 'vitest'
import {mount} from '@vue/test-utils'
import {ref} from 'vue'
import IdentityStep from './IdentityStep.vue'

vi.mock('@/composables/useSession', () => ({useSession: () => ({sessionInfo: ref({canSendMail: false})})}))

const CHOICE = '[data-testid="one-time-password-choice"]'

function step(offerOneTimePassword: boolean, canLogin: boolean) {
    return mount(IdentityStep, {
        props: {
            firstName: 'Lena', lastName: 'Weber', email: 'lena@example.org',
            canLogin, sendSetupMail: true, issueOneTimePassword: true, offerOneTimePassword,
        },
    })
}

describe('IdentityStep', () => {
    it('offers a one-time password for a login where no mail goes out', () => {
        const wrapper = step(true, true)

        expect(wrapper.get(CHOICE).text()).toContain('Einmalpasswort')
        expect(wrapper.text()).not.toContain('Gib den Einrichtungs-Link selbst weiter')
    })

    it('offers none where it is not on offer', () => {
        expect(step(false, true).find(CHOICE).exists()).toBe(false)
    })

    it('offers none to a member who will not sign in', () => {
        expect(step(true, false).find(CHOICE).exists()).toBe(false)
    })

    it('carries the answer back out', async () => {
        const wrapper = step(true, true)

        await wrapper.get(CHOICE).get('[role="switch"]').trigger('click')

        expect(wrapper.emitted('update:issueOneTimePassword')?.at(-1)).toEqual([false])
    })
})
