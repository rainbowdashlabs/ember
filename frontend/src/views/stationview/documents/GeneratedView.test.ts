/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {beforeEach, describe, expect, it, vi} from 'vitest'
import {flushPromises} from '@vue/test-utils'
import {mountSuspended} from '@nuxt/test-utils/runtime'
import type {GeneratedDocumentEntry} from '@/api/generated/schema'
import GeneratedView from './GeneratedView.vue'

const generationLog = vi.fn()

vi.mock('@/api', () => ({
    documentTemplates: {generationLog: (...args: unknown[]) => generationLog(...args)},
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
    }))
}

/** The log arrives a page at a time, and older pages are added on request. */
describe('GeneratedView', () => {
    beforeEach(() => {
        generationLog.mockReset()
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
