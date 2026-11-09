/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {describe, expect, it, vi} from 'vitest'
import {mountSuspended} from '@nuxt/test-utils/runtime'
import SignatureRequirementCards from './SignatureRequirementCards.vue'

const push = vi.hoisted(() => vi.fn())

vi.mock('vue-router', async (importOriginal) => ({
    ...(await importOriginal<typeof import('vue-router')>()),
    useRouter: () => ({push}),
}))

/** The open signatures among the open tasks: one card each, and one action for all of them where several wait. */
describe('SignatureRequirementCards', () => {
    it('signs every waiting signature in one go where several wait, and each on its own from its card', async () => {
        const cards = await mountSuspended(SignatureRequirementCards, {
            props: {signatures: [
                {fieldId: 1, documentTitle: 'Zeltlager', memberName: null, purpose: null, capacity: 'ACCOUNT_HOLDER'},
                {fieldId: 2, documentTitle: 'Fotoerlaubnis', memberName: 'Ben', purpose: 'Neues Mitglied aufnehmen', capacity: 'MEMBER_THROUGH_ACCOUNT'},
            ]},
        })

        await cards.get('[data-testid="requirement-sign-all"]').trigger('click')
        expect(push).toHaveBeenCalledWith({name: 'station-signing-all'})
        expect(cards.findAll('[data-testid="requirement-signature"]')).toHaveLength(2)
        expect(cards.findAll('[data-testid="requirement-signature"]')[1]?.text()).toContain('Neues Mitglied aufnehmen')
        expect(cards.findAll('[data-testid="requirement-signature"]')[0]?.text()).not.toContain('Ablauf')
    })

    it('offers no action for all where only one waits', async () => {
        const cards = await mountSuspended(SignatureRequirementCards, {
            props: {signatures: [{fieldId: 1, documentTitle: 'Zeltlager', memberName: null, purpose: null, capacity: 'ACCOUNT_HOLDER'}]},
        })

        expect(cards.find('[data-testid="requirement-sign-all"]').exists()).toBe(false)
    })
    it('tells a ward signing through the account apart from the consent as guardian on the same document', async () => {
        const cards = await mountSuspended(SignatureRequirementCards, {
            props: {signatures: [
                {fieldId: 1, documentTitle: 'Aufnahmeantrag', memberName: 'Ben', purpose: null, capacity: 'MEMBER_THROUGH_ACCOUNT'},
                {fieldId: 2, documentTitle: 'Aufnahmeantrag', memberName: 'Ben', purpose: null, capacity: 'GUARDIAN'},
            ]},
        })

        const [own, consent] = cards.findAll('[data-testid="requirement-signature"]').map(card => card.text())
        expect(own).toContain('Ben unterschreibt dieses Dokument selbst')
        expect(consent).toContain('als Erziehungsberechtigte Person')
    })
})
