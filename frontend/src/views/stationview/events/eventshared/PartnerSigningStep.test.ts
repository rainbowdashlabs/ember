/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {describe, expect, it} from 'vitest'
import {flushPromises} from '@vue/test-utils'
import {mountSuspended} from '@nuxt/test-utils/runtime'
import {RequirementSignatureState, type PartnerDocumentToSign} from '@/api/generated/schema'
import PartnerSigningStep from './PartnerSigningStep.vue'

function consentFor(memberId: number, memberName: string, yours: boolean): PartnerDocumentToSign {
    return {
        name: 'Einverständnis Zeltlager',
        memberId,
        memberName,
        signature: {
            templateId: 8,
            memberId,
            requestUid: `0b9f5c1e-8f6d-4a39-9d55-2c1b7f3d4e1${memberId}`,
            state: RequirementSignatureState.OPEN,
            fields: [
                {id: 70 + memberId, name: 'participant', signerName: memberName, state: RequirementSignatureState.OPEN, yours},
            ],
        },
    }
}

async function mountStep(documents: PartnerDocumentToSign[]) {
    const mounted = await mountSuspended(PartnerSigningStep, {
        props: {documents, modelValue: true},
        global: {stubs: {Modal: {template: '<div><slot/></div>'}}},
    })
    await flushPromises()
    return mounted
}

/**
 * The step a registration for a partner station's appointment ends on: each document filed here with its
 * fields, the ones the reader signs offered online, and later as the way out.
 */
describe('PartnerSigningStep', () => {
    it('offers the reader their own field to sign online', async () => {
        const step = await mountStep([consentFor(1, 'Lena Schmidt', true)])

        const documents = step.findAll('[data-testid="partner-signing-document"]')
        expect(documents).toHaveLength(1)
        expect(documents[0]!.text()).toContain('Einverständnis Zeltlager')
        expect(documents[0]!.text()).not.toContain('für Lena Schmidt')
        expect(documents[0]!.find('[data-testid="signature-field-sign"]').exists()).toBe(true)
    })

    it('names whose copy each is where several people were registered, and says when others sign', async () => {
        const step = await mountStep([consentFor(1, 'Lena Schmidt', true), consentFor(2, 'Tom Schmidt', false)])

        const documents = step.findAll('[data-testid="partner-signing-document"]')
        expect(documents[0]!.text()).toContain('Einverständnis Zeltlager für Lena Schmidt')
        expect(documents[1]!.text()).toContain('Einverständnis Zeltlager für Tom Schmidt')
        expect(documents[1]!.find('[data-testid="signature-field-sign"]').exists()).toBe(false)
        expect(documents[1]!.text()).toContain('werden bei den genannten Personen angefragt')
    })

    it('closes on later', async () => {
        const step = await mountStep([consentFor(1, 'Lena Schmidt', true)])

        await step.find('[data-testid="partner-signing-later"]').trigger('click')

        expect(step.emitted('update:modelValue')).toEqual([[false]])
    })
})
