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
                {fieldId: 1, documentTitle: 'Zeltlager', memberName: null},
                {fieldId: 2, documentTitle: 'Fotoerlaubnis', memberName: 'Ben'},
            ]},
        })

        await cards.get('[data-testid="requirement-sign-all"]').trigger('click')
        expect(push).toHaveBeenCalledWith({name: 'station-signing-all'})
        expect(cards.findAll('[data-testid="requirement-signature"]')).toHaveLength(2)
    })

    it('offers no action for all where only one waits', async () => {
        const cards = await mountSuspended(SignatureRequirementCards, {
            props: {signatures: [{fieldId: 1, documentTitle: 'Zeltlager', memberName: null}]},
        })

        expect(cards.find('[data-testid="requirement-sign-all"]').exists()).toBe(false)
    })
})
