/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {describe, expect, it} from 'vitest'
import {flushPromises} from '@vue/test-utils'
import {mountSuspended} from '@nuxt/test-utils/runtime'
import AgreementOfferCard from './AgreementOfferCard.vue'

const OFFERS = [{templateId: 8, title: 'Einverständnis Tag der offenen Tür'}]

async function mountCard(eligibleMembers: {uid: string, name: string}[], selectedMemberUid = '') {
    const card = await mountSuspended(AgreementOfferCard, {
        props: {offers: OFFERS, eligibleMembers, busy: false, selectedMemberUid},
    })
    await flushPromises()
    return card
}

/**
 * The agreement a partner station's appointment without registrations offers: what it is called, that signing
 * says one will come, and for whom it is signed.
 */
describe('AgreementOfferCard', () => {
    it('signs for the reader where nobody else is in their care', async () => {
        const card = await mountCard([{uid: 'me', name: 'Ich'}])

        expect(card.text()).toContain('Einverständnis Tag der offenen Tür')
        await card.find('[data-testid="partner-agreement-sign"]').trigger('click')

        expect(card.emitted('sign')).toEqual([['me']])
    })

    it('waits for a choice where several people could sign', async () => {
        const members = [{uid: 'me', name: 'Ich'}, {uid: 'ward', name: 'Ben'}]
        const waiting = await mountCard(members)
        expect(waiting.find('[data-testid="partner-agreement-sign"]').attributes('disabled')).toBeDefined()

        const chosen = await mountCard(members, 'ward')
        await chosen.find('[data-testid="partner-agreement-sign"]').trigger('click')

        expect(chosen.emitted('sign')).toEqual([['ward']])
    })
})
