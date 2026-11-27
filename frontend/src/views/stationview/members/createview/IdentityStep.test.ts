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
import {FindableBecause} from '@/api/generated/schema'

vi.mock('@/composables/useSession', () => ({useSession: () => ({sessionInfo: ref({canSendMail: false})})}))

const CHOICE = '[data-testid="one-time-password-choice"]'

vi.mock('@/api/members', () => ({searchInviteCandidates: vi.fn(async () => [])}))

const FOUND = {
    accountUid: '00000000-0000-0000-0000-000000000077', firstName: 'Anna', lastName: 'Schmidt',
    because: FindableBecause.SHARED_STATION, through: 'Nord', alreadyHere: false, alreadyAsked: false,
    declinedLately: false,
}

function step(offerOneTimePassword: boolean, canLogin: boolean, found: typeof FOUND | null = null) {
    return mount(IdentityStep, {
        props: {
            firstName: 'Lena', lastName: 'Weber', email: 'lena@example.org',
            canLogin, sendSetupMail: true, issueOneTimePassword: true, offerOneTimePassword, found,
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

    it('asks for no name, address or mail once somebody was found by name', () => {
        const wrapper = mount(IdentityStep, {
            props: {
                firstName: '', lastName: '', email: '',
                canLogin: true, sendSetupMail: true, issueOneTimePassword: true, offerOneTimePassword: true,
                found: FOUND,
            },
        })

        expect(wrapper.find(CHOICE).exists()).toBe(false)
        expect(wrapper.find('input[placeholder="E-Mail-Adresse"]').exists()).toBe(false)
        expect(wrapper.text()).toContain('Anna Schmidt ist ausgewählt')
        const next = wrapper.findAll('button').find(button => button.text() === 'Weiter')
        expect(next?.attributes('disabled')).toBeUndefined()
    })
})
