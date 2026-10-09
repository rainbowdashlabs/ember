/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {beforeEach, describe, expect, it, vi} from 'vitest'
import {flushPromises} from '@vue/test-utils'
import {mountSuspended} from '@nuxt/test-utils/runtime'
import {RequirementSignatureState, type AgreementSigner} from '@/api/generated/schema'
import {appointmentDocuments} from '@/api'
import AgreementSignersPanel from './AgreementSignersPanel.vue'

vi.mock('@/api', () => ({
    appointmentDocuments: {agreementSigners: vi.fn()},
}))

function signer(memberId: number, name: string, overrides: Partial<AgreementSigner> = {}): AgreementSigner {
    return {
        memberId,
        name,
        templateId: 8,
        documentName: 'Einverständnis',
        state: RequirementSignatureState.SIGNED,
        signedAt: '2026-10-07T10:00:00Z',
        withdrawnAt: null,
        refused: false,
        ...overrides,
    }
}

async function mountPanel(signers: AgreementSigner[]) {
    vi.mocked(appointmentDocuments.agreementSigners).mockResolvedValue(signers)
    const panel = await mountSuspended(AgreementSignersPanel, {props: {eventId: 3, date: '2026-10-08'}})
    await flushPromises()
    return panel
}

/** Whoever runs an appointment without registrations sees who said they will come by signing. */
describe('AgreementSignersPanel', () => {
    beforeEach(() => {
        vi.mocked(appointmentDocuments.agreementSigners).mockReset()
    })

    it('lists every signer with the document, where it stands and when', async () => {
        const panel = await mountPanel([
            signer(11, 'Lena Schmidt'),
            signer(12, 'Paul Weber', {
                state: RequirementSignatureState.REVOKED,
                withdrawnAt: '2026-10-07T12:00:00Z',
            }),
            signer(13, 'Tom Berg', {refused: true}),
        ])

        expect(appointmentDocuments.agreementSigners).toHaveBeenCalledWith(3, '2026-10-08')
        const rows = panel.findAll('[data-testid="agreement-signer"]')
        expect(rows).toHaveLength(3)
        expect(rows[0]!.text()).toContain('Lena Schmidt')
        expect(rows[0]!.text()).toContain('Einverständnis')
        expect(rows[0]!.text()).toContain('Unterschrieben am')
        expect(rows[1]!.text()).toContain('Widerrufen am')
        expect(rows[2]!.text()).toContain('Später abgesagt')
    })

    it('shows nothing where nobody signed', async () => {
        const panel = await mountPanel([])

        expect(panel.find('[data-testid="agreement-signers"]').exists()).toBe(false)
    })
})
