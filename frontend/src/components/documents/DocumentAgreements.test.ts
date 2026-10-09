/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {beforeEach, describe, expect, it, vi} from 'vitest'
import {flushPromises} from '@vue/test-utils'
import {mountSuspended} from '@nuxt/test-utils/runtime'
import {RequestState, type DocumentAgreement} from '@/api/generated/schema'
import {signing} from '@/api'
import DocumentAgreements from './DocumentAgreements.vue'

vi.mock('@/api', () => ({
    signing: {documentAgreements: vi.fn(), withdrawAgreement: vi.fn()},
}))

const UID = '0b9f5c1e-8f6d-4a39-9d55-2c1b7f3d4e10'

const standing: DocumentAgreement = {
    requestUid: UID,
    state: RequestState.COMPLETE,
    withdrawable: true,
    withdrawnAt: null,
    withdrawnBy: null,
}

async function mountAgreements() {
    const view = await mountSuspended(DocumentAgreements, {
        props: {documentId: 40},
        global: {stubs: {Modal: {template: '<div><slot/></div>'}}},
    })
    await flushPromises()
    return view
}

/** A signer withdraws their agreement from the document itself, and reads when it was withdrawn. */
describe('DocumentAgreements', () => {
    beforeEach(() => {
        vi.mocked(signing.documentAgreements).mockReset()
        vi.mocked(signing.withdrawAgreement).mockReset()
    })

    it('withdraws a standing agreement and reads it back as withdrawn', async () => {
        vi.mocked(signing.documentAgreements).mockResolvedValueOnce([standing]).mockResolvedValueOnce([{
            ...standing,
            state: RequestState.REVOKED,
            withdrawable: false,
            withdrawnAt: '2026-10-08T09:00:00Z',
            withdrawnBy: 'Anna Schmidt',
        }])
        vi.mocked(signing.withdrawAgreement).mockResolvedValue({requestUid: UID, withdrawnAt: '2026-10-08T09:00:00Z'})
        const view = await mountAgreements()

        await view.find('[data-testid="document-agreement-withdraw"]').trigger('click')
        await view.find('[data-testid="agreement-withdraw-form"]').trigger('submit')
        await flushPromises()

        expect(signing.withdrawAgreement).toHaveBeenCalledWith(UID, '')
        expect(view.find('[data-testid="document-agreement-withdrawn"]').text()).toContain('Anna Schmidt')
        expect(view.find('[data-testid="document-agreement-withdraw"]').exists()).toBe(false)
    })

    it('shows nothing to somebody who is no party, or where the agreements cannot be read', async () => {
        vi.mocked(signing.documentAgreements).mockResolvedValueOnce([])
        expect((await mountAgreements()).find('[data-testid="document-agreements"]').exists()).toBe(false)

        vi.mocked(signing.documentAgreements).mockRejectedValueOnce(new Error('forbidden'))
        expect((await mountAgreements()).find('[data-testid="document-agreements"]').exists()).toBe(false)
    })
})
