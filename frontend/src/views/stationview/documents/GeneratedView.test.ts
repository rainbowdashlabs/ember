/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {beforeEach, describe, expect, it, vi} from 'vitest'
import {flushPromises} from '@vue/test-utils'
import {mountSuspended} from '@nuxt/test-utils/runtime'
import {RequestState, type GeneratedDocumentEntry} from '@/api/generated/schema'
import GeneratedView from './GeneratedView.vue'

const generationLog = vi.fn()
const getSignatureRequest = vi.fn()
const getSignatureAsk = vi.fn()

vi.mock('@/api', () => ({
    documentTemplates: {generationLog: (...args: unknown[]) => generationLog(...args)},
    signing: {
        getSignatureRequest: (...args: unknown[]) => getSignatureRequest(...args),
        getSignatureAsk: (...args: unknown[]) => getSignatureAsk(...args),
    },
}))

function entries(count: number, from: number): GeneratedDocumentEntry[] {
    return Array.from({length: count}, (_, index) => ({
        id: from + index,
        documentId: null,
        generatedAt: '2026-10-01T08:00:00Z',
        generatedByName: 'Rita Ablage',
        issuerFunction: null,
        issuerName: null,
        memberId: 1,
        memberName: 'Mara Nager',
        ofAssociation: false,
        selfService: false,
        templateId: 1,
        templateName: 'Bescheinigung',
        templateVersion: 1,
        signature: null,
    }))
}

/**
 * The log arrives a page at a time, and older pages are added on request. Each row says how the
 * signatures on its document stand, and opens the request or the step that asks.
 */
describe('GeneratedView', () => {
    beforeEach(() => {
        generationLog.mockReset()
        getSignatureRequest.mockReset()
        getSignatureAsk.mockReset()
    })

    it('shows how the signatures stand and opens the request a document was asked to be signed on', async () => {
        const [entry] = entries(1, 0)
        generationLog.mockResolvedValueOnce([{
            ...entry!,
            documentId: 9,
            signature: {requestUid: 'r-1', state: RequestState.OPEN, signed: 1, expected: 3, open: 2, nobodyCanSign: 1},
        }])
        getSignatureRequest.mockReturnValue(new Promise(() => {}))
        const view = await mountSuspended(GeneratedView)
        await flushPromises()

        const state = view.get('[data-testid="signature-state"]').text()
        expect(state).toContain('Teilweise unterschrieben · 1 von 3')
        expect(state).toContain('Niemand kann unterschreiben')

        await view.get('[data-testid="generated-document-row"]').trigger('click')
        await flushPromises()

        expect(getSignatureRequest).toHaveBeenCalledWith('r-1')
        expect(getSignatureAsk).not.toHaveBeenCalled()
    })

    it('opens asking for signatures where they were withdrawn', async () => {
        const [entry] = entries(1, 0)
        generationLog.mockResolvedValueOnce([{
            ...entry!,
            documentId: 9,
            signature: {requestUid: 'r-2', state: RequestState.WITHDRAWN, signed: 0, expected: 0, open: 0, nobodyCanSign: 0},
        }])
        getSignatureAsk.mockReturnValue(new Promise(() => {}))
        const view = await mountSuspended(GeneratedView)
        await flushPromises()

        await view.get('[data-testid="generated-document-row"]').trigger('click')
        await flushPromises()

        expect(getSignatureAsk).toHaveBeenCalledWith(entry!.id)
        expect(getSignatureRequest).not.toHaveBeenCalled()
    })

    it('offers older entries only while a page came back full', async () => {
        generationLog.mockResolvedValueOnce(entries(500, 0)).mockResolvedValueOnce(entries(3, 500))
        const view = await mountSuspended(GeneratedView)
        await flushPromises()

        await view.get('[data-testid="generated-documents-more"]').trigger('click')
        await flushPromises()

        expect(generationLog).toHaveBeenNthCalledWith(1, 500, 0)
        expect(generationLog).toHaveBeenNthCalledWith(2, 500, 500)
        expect(view.find('[data-testid="generated-documents-more"]').exists()).toBe(false)
    })

    it('offers nothing more when the first page is not full', async () => {
        generationLog.mockResolvedValueOnce(entries(2, 0))
        const view = await mountSuspended(GeneratedView)
        await flushPromises()

        expect(view.find('[data-testid="generated-documents-more"]').exists()).toBe(false)
    })
})
