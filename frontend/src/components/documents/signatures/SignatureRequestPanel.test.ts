/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {beforeEach, describe, expect, it, vi} from 'vitest'
import {flushPromises, mount} from '@vue/test-utils'
import {
    FieldRole,
    FieldState,
    RequestState,
    SignerCapacity,
    StepUpProof,
    type ManagedFieldResponse,
    type ManagedRequestResponse,
} from '@/api/generated/schema'
import SignatureRequestPanel from './SignatureRequestPanel.vue'

const getSignatureRequest = vi.fn()
const settleSignatureField = vi.fn()
const withdrawSignatureRequest = vi.fn()
const rectifySignatureRequest = vi.fn()

vi.mock('@/api', () => ({
    signing: {
        getSignatureRequest: (...args: unknown[]) => getSignatureRequest(...args),
        settleSignatureField: (...args: unknown[]) => settleSignatureField(...args),
        withdrawSignatureRequest: (...args: unknown[]) => withdrawSignatureRequest(...args),
        rectifySignatureRequest: (...args: unknown[]) => rectifySignatureRequest(...args),
    },
}))

const UID = '7b0d3e0c-9f4e-4f0e-8a51-0c9d6c8f2a11'
const NEW_UID = '9c1e4f1d-0a5f-4a1f-9b62-1d0e7d903b22'

function field(overrides: Partial<ManagedFieldResponse> = {}): ManagedFieldResponse {
    return {
        fieldName: 'guardian1',
        role: FieldRole.GUARDIAN,
        signerName: 'Gerda Erste',
        capacity: SignerCapacity.GUARDIAN,
        statement: 'Ich bin einverstanden.',
        state: FieldState.OPEN,
        nobodyCanSign: false,
        settledAt: null,
        settledByName: null,
        act: null,
        ...overrides,
    }
}

function managed(overrides: Partial<ManagedRequestResponse> = {}): ManagedRequestResponse {
    return {
        uid: UID,
        state: RequestState.OPEN,
        documentId: 20,
        documentTitle: 'Einverständnis',
        memberName: 'Kim Kind',
        contentSha256: 'a'.repeat(64),
        createdAt: '2026-10-08T09:00:00Z',
        closedAt: null,
        supersededBy: null,
        retentionMonths: 48,
        fields: [
            field({
                state: FieldState.SIGNED,
                act: {
                    signerName: 'Gerda Erste',
                    accountHolderName: 'Gerda Erste',
                    capacity: SignerCapacity.GUARDIAN,
                    proof: StepUpProof.PASSKEY,
                    bound: true,
                    userVerified: true,
                    signedAt: '2026-10-08T10:00:00Z',
                    sealed: false,
                },
            }),
            field({fieldName: 'guardian2', signerName: null, nobodyCanSign: true}),
        ],
        corrections: [],
        ...overrides,
    }
}

function open(onChanged = vi.fn()) {
    return mount(SignatureRequestPanel, {props: {requestUid: UID, onChanged}})
}

function fieldRow(view: ReturnType<typeof open>, index: number) {
    return view.findAll('[data-testid="signature-field"]')[index]!
}

/**
 * A manager looks after the signatures asked for on a document: each field with its signer and the act,
 * a field nobody can sign named as such, and what no signing act settles asked once more and then done.
 */
describe('SignatureRequestPanel', () => {
    beforeEach(() => {
        vi.clearAllMocks()
        getSignatureRequest.mockResolvedValue(managed())
    })

    it('shows each field with its act and names the one nobody can sign', async () => {
        const view = open()
        await flushPromises()

        expect(getSignatureRequest).toHaveBeenCalledWith(UID)
        const signed = fieldRow(view, 0)
        expect(signed.text()).toContain('Gerda Erste')
        expect(signed.get('[data-testid="signature-field-act"]').text()).toContain('einem Passkey')
        expect(signed.text()).toContain('an genau dieses Dokument gebunden')
        expect(signed.text()).toContain('Noch in keiner versiegelten Fassung')
        expect(signed.find('[data-testid="signature-field-paper"]').exists()).toBe(false)
        const nobody = fieldRow(view, 1)
        expect(nobody.get('[data-testid="signature-field-nobody"]').text()).toBe('Niemand kann unterschreiben')
        expect(nobody.text()).toContain('niemand eingetragen')
        expect(nobody.find('[data-testid="signature-field-waive"]').exists()).toBe(true)
    })

    it('settles a field only once it was confirmed and tells the list', async () => {
        const onChanged = vi.fn()
        settleSignatureField.mockResolvedValue(managed({
            fields: [field({state: FieldState.WAIVED, settledByName: 'Maria Leitung', settledAt: '2026-10-09T08:00:00Z'})],
        }))
        const view = open(onChanged)
        await flushPromises()

        await fieldRow(view, 1).get('[data-testid="signature-field-waive"]').trigger('click')
        expect(settleSignatureField).not.toHaveBeenCalled()
        expect(fieldRow(view, 1).text()).toContain('ohne Unterschrift gelten lassen')
        await fieldRow(view, 1).get('[data-testid="signature-field-confirm-yes"]').trigger('click')
        await flushPromises()

        expect(settleSignatureField).toHaveBeenCalledWith(UID, 'guardian2', 'waive')
        expect(onChanged).toHaveBeenCalledOnce()
        expect(fieldRow(view, 0).text()).toContain('Erlassen von Maria Leitung')
    })

    it('withdraws the whole request after asking once more', async () => {
        withdrawSignatureRequest.mockResolvedValue(managed({state: RequestState.WITHDRAWN}))
        const view = open()
        await flushPromises()

        await view.get('[data-testid="signature-request-withdraw"]').trigger('click')
        await view.get('[data-testid="signature-request-withdraw-yes"]').trigger('click')
        await flushPromises()

        expect(withdrawSignatureRequest).toHaveBeenCalledWith(UID)
        expect(view.find('[data-testid="signature-request-withdraw"]').exists()).toBe(false)
        expect(view.find('[data-testid="signature-field-waive"]').exists()).toBe(false)
    })

    it('asks anew on the corrected document and shows the new request', async () => {
        getSignatureRequest.mockResolvedValue(managed({
            corrections: [
                {generationId: 31, templateName: 'Einverständnis', generatedAt: '2026-10-09T08:00:00Z'},
                {generationId: 30, templateName: 'Einverständnis', generatedAt: '2026-10-08T12:00:00Z'},
            ],
        }))
        rectifySignatureRequest.mockResolvedValue(managed({uid: NEW_UID}))
        const view = open()
        await flushPromises()

        await view.get('[data-testid="signature-correction-ask"]').trigger('click')
        await flushPromises()

        expect(rectifySignatureRequest).toHaveBeenCalledWith(UID, 31)
        expect(view.find('[data-testid="signature-correction"]').exists()).toBe(false)
    })

    it('leads from a replaced request to its replacement', async () => {
        getSignatureRequest
            .mockResolvedValueOnce(managed({state: RequestState.SUPERSEDED, supersededBy: NEW_UID}))
            .mockResolvedValueOnce(managed({uid: NEW_UID}))
        const view = open()
        await flushPromises()

        expect(view.find('[data-testid="signature-request-withdraw"]').exists()).toBe(false)
        await view.get('[data-testid="signature-request-replacement"]').trigger('click')
        await flushPromises()

        expect(getSignatureRequest).toHaveBeenLastCalledWith(NEW_UID)
        expect(view.find('[data-testid="signature-request-withdraw"]').exists()).toBe(true)
    })
})
