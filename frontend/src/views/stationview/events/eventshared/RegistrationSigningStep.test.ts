/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {beforeEach, describe, expect, it, vi} from 'vitest'
import {flushPromises} from '@vue/test-utils'
import {mountSuspended} from '@nuxt/test-utils/runtime'
import {PaperState, RequirementSignatureState, RequirementStatus} from '@/api/generated/schema'
import {appointmentDocuments} from '@/api'
import type {SigningStep, SigningStepCopy} from '@/composables/useRegistrationSigningStep'
import RegistrationSigningStep from './RegistrationSigningStep.vue'

vi.mock('@/api', () => ({
    appointmentDocuments: {
        submitScan: vi.fn(),
    },
}))

function copy(memberId: number, name: string, yours: boolean): SigningStepCopy {
    return {
        memberId,
        name,
        document: {
            templateId: 8,
            name: 'Einverständnis',
            status: RequirementStatus.GENERATED,
            documentId: 40,
            generatedAt: '2026-09-01T10:00:00Z',
            outdated: false,
            paper: null,
            signature: {
                templateId: 8,
                memberId,
                requestUid: '0b9f5c1e-8f6d-4a39-9d55-2c1b7f3d4e10',
                state: RequirementSignatureState.OPEN,
                fields: [
                    {id: 70, name: 'participant', signerName: name, state: RequirementSignatureState.OPEN, yours},
                    {id: 71, name: 'guardian2', signerName: 'Paul Schmidt', state: RequirementSignatureState.OPEN, yours: false},
                ],
                withdrawable: false,
                withdrawnAt: null,
            },
            agreementOffered: false,
        },
    }
}

async function mountStep(step: SigningStep) {
    const mounted = await mountSuspended(RegistrationSigningStep, {
        props: {step, modelValue: true},
        global: {stubs: {Modal: {template: '<div><slot/></div>'}}},
    })
    await flushPromises()
    return mounted
}

/**
 * The step a registration ends on: each copy still waiting with its fields, the ones the reader signs
 * offered online, a scan handed in instead, and later as the way out.
 */
describe('RegistrationSigningStep', () => {
    beforeEach(() => {
        vi.mocked(appointmentDocuments.submitScan).mockReset()
    })

    it('offers the fields the reader signs online and names the others asked', async () => {
        const step = await mountStep({eventId: 3, date: '2026-10-08', copies: [copy(11, 'Lena Schmidt', true)]})

        const copies = step.findAll('[data-testid="registration-signing-copy"]')
        expect(copies).toHaveLength(1)
        expect(copies[0]!.text()).toContain('Einverständnis')
        expect(copies[0]!.text()).toContain('Erziehungsberechtigte Person 2: Paul Schmidt')
        expect(copies[0]!.findAll('[data-testid="signature-field-sign"]')).toHaveLength(1)
        expect(copies[0]!.text()).not.toContain('werden bei den genannten Personen angefragt')
    })

    it('says the signatures are asked of others where the reader signs none, and names each person', async () => {
        const step = await mountStep({
            eventId: 3,
            date: '2026-10-08',
            copies: [copy(11, 'Lena Schmidt', false), copy(12, 'Tom Schmidt', true)],
        })

        const copies = step.findAll('[data-testid="registration-signing-copy"]')
        expect(copies[0]!.text()).toContain('Einverständnis für Lena Schmidt')
        expect(copies[0]!.text()).toContain('werden bei den genannten Personen angefragt')
        expect(copies[0]!.find('[data-testid="signature-field-sign"]').exists()).toBe(false)
    })

    it('hands a scan in instead and shows it waiting', async () => {
        vi.mocked(appointmentDocuments.submitScan).mockResolvedValue({
            id: 20,
            eventId: 3,
            eventDate: '2026-10-08',
            templateId: 8,
            memberId: 11,
            documentId: 41,
            state: PaperState.SUBMITTED,
            submittedAt: '2026-10-07T10:00:00Z',
            reviewedAt: null,
            rejectReason: null,
        })
        const step = await mountStep({eventId: 3, date: '2026-10-08', copies: [copy(11, 'Lena Schmidt', true)]})

        const input = step.find('[data-testid="registration-signing-scan"] input[type="file"]')
        const file = new File(['%PDF-1.4'], 'scan.pdf', {type: 'application/pdf'})
        Object.defineProperty(input.element, 'files', {value: [file]})
        await input.trigger('change')
        await flushPromises()

        expect(appointmentDocuments.submitScan).toHaveBeenCalledWith(
            {eventId: 3, date: '2026-10-08', templateId: 8, memberId: 11}, file, 'Einverständnis, unterschrieben')
        expect(step.text()).toContain('Eingereicht, wartet auf Bestätigung')
        expect(step.find('[data-testid="registration-signing-scan"]').exists()).toBe(false)
    })

    it('closes for later', async () => {
        const step = await mountStep({eventId: 3, date: '2026-10-08', copies: [copy(11, 'Lena Schmidt', true)]})

        await step.find('[data-testid="registration-signing-later"]').trigger('click')

        expect(step.emitted('update:modelValue')?.[0]).toEqual([false])
    })
})
