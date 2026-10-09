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
import {fetchCopy} from '../eventdetailview/documentsToBring'
import RegistrationSigningStep from './RegistrationSigningStep.vue'

const push = vi.hoisted(() => vi.fn())

vi.mock('vue-router', async (importOriginal) => ({
    ...(await importOriginal<typeof import('vue-router')>()),
    useRouter: () => ({push}),
}))

vi.mock('@/api', () => ({
    appointmentDocuments: {
        submitScan: vi.fn(),
    },
}))

vi.mock('../eventdetailview/documentsToBring', () => ({
    fetchCopy: vi.fn(),
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
                    {id: 70, name: 'participant', signerName: name, state: RequirementSignatureState.OPEN, yours, nobodyCanSign: false},
                    {id: 71, name: 'guardian2', signerName: 'Paul Schmidt', state: RequirementSignatureState.OPEN, yours: false, nobodyCanSign: false},
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
 * offered online, each copy to download, a scan handed in instead, and later as the way out.
 */
describe('RegistrationSigningStep', () => {
    beforeEach(() => {
        vi.mocked(appointmentDocuments.submitScan).mockReset()
        vi.mocked(fetchCopy).mockReset()
    })

    it('downloads each copy on its own line to read or print', async () => {
        vi.mocked(fetchCopy).mockResolvedValue(40)
        const tim = copy(12, 'Tim Schmidt', true)
        const step = await mountStep({eventId: 3, date: '2026-10-08', copies: [copy(11, 'Lena Schmidt', true), tim]})

        const downloads = step.findAll('[data-testid="registration-signing-download"]')
        expect(downloads).toHaveLength(2)
        expect(downloads[1]!.text()).toBe('Herunterladen')
        await downloads[1]!.trigger('click')
        await flushPromises()

        expect(fetchCopy).toHaveBeenCalledWith(3, '2026-10-08', 12, tim.document)
    })

    it('signs every field the reader may sign on the copies in one go and names the others asked', async () => {
        const step = await mountStep({
            eventId: 3,
            date: '2026-10-08',
            copies: [copy(11, 'Lena Schmidt', true), {...copy(12, 'Tim Schmidt', true), document: {
                ...copy(12, 'Tim Schmidt', true).document,
                signature: {...copy(12, 'Tim Schmidt', true).document.signature!, fields: [
                    {id: 80, name: 'participant', signerName: 'Tim Schmidt', state: RequirementSignatureState.OPEN, yours: true, nobodyCanSign: false},
                ]},
            }}],
        })

        const copies = step.findAll('[data-testid="registration-signing-copy"]')
        expect(copies).toHaveLength(2)
        expect(copies[0]!.text()).toContain('Einverständnis')
        expect(copies[0]!.text()).toContain('Erziehungsberechtigte Person 2: Paul Schmidt')
        expect(step.findAll('[data-testid="signature-field-sign"]')).toHaveLength(0)
        await step.get('[data-testid="registration-signing-now"]').trigger('click')
        expect(push).toHaveBeenCalledWith({name: 'station-signing-all', query: {fields: '70,80'}})
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
        expect(step.text()).toContain('Scan eingereicht, wartet auf Bestätigung')
        expect(step.find('[data-testid="document-scan-received"]').text())
            .toBe('Scan hochgeladen. Die Terminverwaltung prüft ihn jetzt.')
        expect(step.find('[data-testid="registration-signing-scan"]').exists()).toBe(false)
        expect(step.find('[data-testid="registration-signing-now"]').exists()).toBe(false)
    })

    it('leaves a copy whose scan already waits out of signing in one go', async () => {
        const waiting = copy(11, 'Lena Schmidt', true)
        waiting.document = {...waiting.document, paper: {
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
        }}
        const step = await mountStep({eventId: 3, date: '2026-10-08', copies: [waiting, copy(12, 'Tom Schmidt', true)]})

        expect(step.findAll('[data-testid="registration-signing-copy"]')[0]!.text())
            .toContain('Scan eingereicht, wartet auf Bestätigung')
        await step.get('[data-testid="registration-signing-now"]').trigger('click')
        expect(push).toHaveBeenCalledWith({name: 'station-signing-all', query: {fields: '70'}})
    })

    it('closes for later', async () => {
        const step = await mountStep({eventId: 3, date: '2026-10-08', copies: [copy(11, 'Lena Schmidt', true)]})

        await step.find('[data-testid="registration-signing-later"]').trigger('click')

        expect(step.emitted('update:modelValue')?.[0]).toEqual([false])
    })
})
