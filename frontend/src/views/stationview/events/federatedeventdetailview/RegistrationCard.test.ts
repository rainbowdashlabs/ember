/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {describe, expect, it} from 'vitest'
import {flushPromises} from '@vue/test-utils'
import {mountSuspended} from '@nuxt/test-utils/runtime'
import RegistrationCard from './RegistrationCard.vue'
import {RegistrationStatus, type RemoteMemberRegistration} from '@/api/generated/schema'

const ME = {uid: 'me', name: 'Ich'}

function registration(status: RegistrationStatus): RemoteMemberRegistration {
    return {eventId: 4, remoteMemberId: 'me', eventDate: '2026-12-01', status, partnerId: 0}
}

async function mountCard(registrations: RemoteMemberRegistration[], weDecide = false, date: {full?: boolean, joinsQueue?: boolean} = {}) {
    const card = await mountSuspended(RegistrationCard, {
        props: {
            eligibleMembers: [ME], registrations, eventId: 4, registering: false, weDecide, placesSummary: '',
            selectedMemberUid: '', ...date,
        },
    })
    await flushPromises()
    return card
}

/**
 * Signing members up for a partner station's appointment: the actions on a held place say what they do
 * in words, and a place on the waiting list reads as one.
 */
describe('RegistrationCard', () => {
    it('names confirming and signing off instead of showing bare icons', async () => {
        const card = await mountCard([registration(RegistrationStatus.PENDING)], true)

        expect(card.get('[data-testid="confirm-own-member"]').text()).toContain('Bestätigen')
        expect(card.get('[data-testid="sign-off-own-member"]').text()).toContain('Abmelden')
    })

    it('confirms one of its own members where the partner handed over the choosing', async () => {
        const card = await mountCard([registration(RegistrationStatus.PENDING)], true)

        await card.get('[data-testid="confirm-own-member"]').trigger('click')

        expect(card.emitted('confirm')).toEqual([['me']])
    })

    it('offers no confirming where the partner decides itself', async () => {
        const card = await mountCard([registration(RegistrationStatus.PENDING)])

        expect(card.find('[data-testid="confirm-own-member"]').exists()).toBe(false)
        expect(card.find('[data-testid="sign-off-own-member"]').exists()).toBe(true)
    })

    it('shows a place on the waiting list as such, with no sign-up offered for that member', async () => {
        const card = await mountCard([registration(RegistrationStatus.QUEUED)])

        expect(card.text()).toContain('Nachrückliste')
        expect(card.find('[data-testid="register-own-member"]').exists()).toBe(false)
    })

    it('offers the sign-up to a member without a registration', async () => {
        const card = await mountCard([])

        await card.get('[data-testid="register-own-member"]').trigger('click')

        expect(card.get('[data-testid="register-own-member"]').text()).toContain('Anmelden')
        expect(card.emitted('register')).toHaveLength(1)
    })

    it('says a full date is full and offers the waiting list instead of the sign-up', async () => {
        const card = await mountCard([], false, {full: true, joinsQueue: true})

        expect(card.get('[data-testid="partner-date-full"]').text()).toBe('Voll')
        expect(card.get('[data-testid="register-own-member"]').text()).toContain('Auf die Nachrückliste')
        expect(card.get('[data-testid="register-own-member"]').text()).not.toContain('Anmelden')
    })

    it('says nothing of fullness on a date with places left', async () => {
        const card = await mountCard([])

        expect(card.find('[data-testid="partner-date-full"]').exists()).toBe(false)
    })
})
